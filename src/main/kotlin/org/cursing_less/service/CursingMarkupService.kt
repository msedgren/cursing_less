package org.cursing_less.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.openapi.util.removeUserData
import com.intellij.platform.util.coroutines.flow.debounceBatch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.renderer.ColoredShapeRenderer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.APP)
class CursingMarkupService(private val coroutineScope: CoroutineScope) : Disposable {

    private val debouncer by lazy {
        Debouncer(125, coroutineScope)
    }
    private val enabled = AtomicBoolean(true)

    fun isEnabled(): Boolean {
        return enabled.get()
    }

    private val preferenceService: CursingPreferenceService by lazy {
        ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
    }

    private val tokenService: CursingTokenService by lazy {
        ApplicationManager.getApplication().getService(CursingTokenService::class.java)
    }


    companion object {
        private const val CURSING_EDITOR_STATE_NAME = "CURSING_EDITOR_STATE"
        private const val CURSING_EDITOR_GRAPHICS_NAME = "CURSING_EDITOR_GRAPHICS"

        private val CURSING_EDITOR_STATE = Key.create<CursingEditorState>(CURSING_EDITOR_STATE_NAME)
        private val CURSING_EDITOR_GRAPHICS = Key.create<CursingGraphics>(CURSING_EDITOR_GRAPHICS_NAME)
        private val idGenerator = AtomicLong(0)
        private val unsafeDataMutex = Mutex()
    }

    override fun dispose() {
        enabled.set(false)
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                EditorFactory.getInstance().allEditors.forEach { editor ->
                    removeAllCursingTokensNow(editor)
                }
            }
        }
    }

    suspend fun toggleEnabled() {
        enabled.set(!enabled.get())
        val currentEnabled = enabled.get()

        withContext(Dispatchers.EDT) {
            EditorFactory.getInstance().allEditors.forEach { editor ->
                if (currentEnabled) {
                    updateCursingTokens(editor, editor.caretModel.offset)
                } else {
                    // Use the debouncer to handle token removal to avoid race conditions
                    // when tokens are in the middle of being updated
                    removeAllCursingTokens(editor)
                }
            }
        }
    }

    suspend fun fullyRefreshAllTokens() {
        withContext(Dispatchers.EDT) {
            // Get all open editors
            val editors = EditorFactory.getInstance().allEditors

            // Update each editor
            for (editor in editors) {
                debouncer.debounce(DebounceTask.REFRESH, getOrCreateAndSetEditorState(editor), suspend {
                    editor.removeUserData(CURSING_EDITOR_STATE)
                    removeAllCursingTokensNow(editor)
                    updateCursingTokensNow(editor, editor.caretModel.offset)
                })
            }
        }
    }

    suspend fun updateCursingTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce(DebounceTask.ADD, getOrCreateAndSetEditorState(editor), suspend {
            updateCursingTokensNow(editor, cursorOffset)
        })
    }

    suspend fun updateCursingTokensNow(editor: Editor, cursorOffset: Int) {
        if (enabled.get() && !editor.isDisposed && editorEligibleForMarkup(editor)) {
            val editorState = getOrCreateAndSetEditorState(editor)
            val colorAndShapeManager = editorState.colorAndShapeManager
            colorAndShapeManager.freeAll()

            val existingGraphics = pullExistingGraphics(editor)

            val tokens = tokenService.findCursingTokens(editor, cursorOffset, colorAndShapeManager, existingGraphics)
                .toMutableMap()

            withContext(Dispatchers.EDT + NonCancellable) {
                // Build operation batches
                val toKeep = mutableMapOf<Int, CursingGraphics>()
                existingGraphics.forEach { (offset, existingGraphic) ->
                    val wanted = tokens[offset]
                    if (existingGraphic.highlighter.isValid && wanted != null && wanted.second == existingGraphic.cursingColorShape) {
                        toKeep[offset] = existingGraphic
                        tokens.remove(offset)
                    } else {
                        if (existingGraphic.highlighter.isValid) {
                            existingGraphic.highlighter.dispose()
                        }
                    }
                }

                // Add new graphics
                tokens.forEach { (offset, pair) ->
                    addColoredShapeAboveCursingToken(editor, pair.second, offset, pair.first)
                }
                editor.contentComponent.repaint()
            }
        }
    }

    fun workToProcess() = debouncer.workToProcess()
    fun resetProcessingCount() = debouncer.resetProcessingCount()

    private fun editorEligibleForMarkup(editor: Editor): Boolean {
        val lineHeight = editor.lineHeight
        val fontMetrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(null))
        val fontHeight = fontMetrics.height

        return (lineHeight - fontHeight) >= 2
    }

    private suspend fun removeAllCursingTokens(editor: Editor) {
        debouncer.debounce(DebounceTask.REMOVE, getOrCreateAndSetEditorState(editor), suspend {
            removeAllCursingTokensNow(editor)
        })
    }

    private suspend fun removeAllCursingTokensNow(editor: Editor) {
        if (!editor.isDisposed) {
            val editorState = getOrCreateAndSetEditorState(editor)
            val existingGraphics = pullExistingGraphics(editor)
            if (existingGraphics.isNotEmpty()) {
                withContext(Dispatchers.EDT) {
                    existingGraphics.values
                        .asSequence()
                        .map { it.highlighter }
                        .forEach {
                            if (it.isValid) {
                                it.dispose()
                            }
                        }
                    editor.contentComponent.repaint()
                    editorState.colorAndShapeManager.freeAll()
                }
            }
        }
    }

    suspend fun pullExistingGraphics(editor: Editor): Map<Int, CursingGraphics> {
        return withContext(Dispatchers.EDT) {
            editor.markupModel.allHighlighters
                .asSequence()
                .filter { it.isValid}
                .mapNotNull { it.getUserData(CURSING_EDITOR_GRAPHICS) }
                .associateBy { it.offset }
        }
    }

    suspend fun getOrCreateAndSetEditorState(editor: Editor): CursingEditorState {
        unsafeDataMutex.withLock {
            return editor.getOrCreateUserDataUnsafe(CURSING_EDITOR_STATE) {
                CursingEditorState(
                    idGenerator.getAndIncrement(),
                    ColorAndShapeManager(preferenceService.colors, preferenceService.shapes)
                )
            }
        }
    }

    private fun addColoredShapeAboveCursingToken(
        editor: Editor, cursingColorShape: CursingColorShape, offset: Int, character: Char
    ): CursingGraphics {
        val renderer = ColoredShapeRenderer(cursingColorShape, character)
        val highlighter = editor.markupModel.addRangeHighlighter(
            offset,
            offset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            null,
            HighlighterTargetArea.EXACT_RANGE
        )
        highlighter.customRenderer = renderer
        val graphics = CursingGraphics(cursingColorShape, character, highlighter)
        highlighter.putUserData(CURSING_EDITOR_GRAPHICS, graphics)
        return graphics
    }

    data class CursingGraphics(
        val cursingColorShape: CursingColorShape,
        val character: Char,
        val highlighter: RangeHighlighter,
    ) {
        val offset: Int
            get() = highlighter.startOffset
    }

    data class CursingEditorState(
        val id: Long,
        val colorAndShapeManager: ColorAndShapeManager
    )

    enum class DebounceTask { ADD, REMOVE, REFRESH }

    class Debouncer(
        delay: Int,
        private val coroutineScope: CoroutineScope
    ) {
        private val flow =
            MutableSharedFlow<Triple<DebounceTask, CursingEditorState, suspend () -> Unit>>(
                0,
                200,
                BufferOverflow.DROP_OLDEST
            )

        private val processingCount: AtomicInteger = AtomicInteger(0)

        init {
            coroutineScope.launch(Dispatchers.Unconfined) {
                var debouncedFlow = flow.debounceBatch(delay.milliseconds)
                debouncedFlow
                    .cancellable()
                    .collect {
                        var task: DebounceTask? = null
                        var currentId = -1L
                        it.forEach { (currentTask, state, function) ->
                            try {
                                if (state.id != currentId || task != currentTask) {
                                    task = currentTask
                                    currentId = state.id
                                    function()
                                }
                            } catch (e: Exception) {
                                thisLogger().warn("Failed to process cursing markup", e)
                            } finally {
                                processingCount.decrementAndGet()
                            }
                        }
                    }
            }.invokeOnCompletion {
                resetProcessingCount()
            }
        }

        suspend fun debounce(
            task: DebounceTask,
            cursingEditorState: CursingEditorState,
            function: suspend () -> Unit
        ) {
            processingCount.incrementAndGet()
            flow.emit(Triple(task, cursingEditorState, function))
        }

        fun workToProcess(): Boolean {
            return processingCount.get() > 0 && coroutineScope.isActive
        }

        fun resetProcessingCount() {
            if (processingCount.get() > 0) {
                thisLogger().error("processing count: ${processingCount.get()} reset to zero")
            }
            processingCount.set(0)
        }
    }

}
