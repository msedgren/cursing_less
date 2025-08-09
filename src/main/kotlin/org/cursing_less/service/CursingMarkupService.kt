package org.cursing_less.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
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
        private const val INLAY_NAME = "CURSING_INLAY"
        private const val ID_KEY_NAME = "CURSING_EDITOR_ID"

        val INLAY_KEY = Key.create<CursingColorShape>(INLAY_NAME)
        val ID_KEY = Key.create<DebounceEditorState>(ID_KEY_NAME)

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
                // Remove the existing ColorAndShapeManager
                editor.removeUserData(ColorAndShapeManager.KEY)
                updateCursingTokens(editor, editor.caretModel.offset)
            }
        }
    }

    suspend fun updateCursingTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce(true, createAndSetId(editor), suspend {
            updateCursingTokensNow(editor, cursorOffset)
        })
    }

    suspend fun updateCursingTokensNow(editor: Editor, cursorOffset: Int) {
        if (enabled.get() && !editor.isDisposed && editorEligibleForMarkup(editor)) {
            // thisLogger().trace("Updating cursing tokens for ${editor.editorKind.name} with ID ${createAndSetId(editor)}")
            val colorAndShapeManager = createAndSetColorAndShapeManager(editor)
            colorAndShapeManager.freeAll()

            val existingInlays = withContext(Dispatchers.EDT) {
                pullExistingInlaysByOffset(editor)
            }

            val tokens = tokenService.findCursingTokens(editor, cursorOffset, colorAndShapeManager, existingInlays)
                .toMutableMap()

            withContext(Dispatchers.EDT + NonCancellable) {
                // Build operation batches
                val toDispose = mutableListOf<Inlay<*>>()
                val toRepaint = mutableListOf<Inlay<*>>()
                existingInlays.forEach { (offset, oldConsumedList) ->
                    var found = false
                    oldConsumedList.forEach { (inlay, consumedCursing) ->
                        if (inlay.isValid) {
                            val wanted = tokens[offset]
                            val otherInlaysPresent = editor.inlayModel.getInlineElementsInRange(offset, offset)
                                .asSequence()
                                .filter { it.isValid && it.getUserData(INLAY_KEY) == null }
                                .any()
                            if (found || wanted == null || wanted.second != consumedCursing || otherInlaysPresent) {
                                toDispose.add(inlay)
                            } else {
                                toRepaint.add(inlay)
                                found = true
                                tokens.remove(offset)
                            }
                        }
                    }
                }

                // Exclude current caret position if other inlays are present at that offset
                if (tokens.contains(cursorOffset)) {
                    val inlaysAtCursor = editor.inlayModel.getInlineElementsInRange(cursorOffset, cursorOffset)
                    if (inlaysAtCursor.isNotEmpty()) {
                        colorAndShapeManager.free(cursorOffset)
                        tokens.remove(cursorOffset)
                    }
                }

                val toAdd = tokens.map { (offset, pair) -> Triple(offset, pair.first, pair.second) }

                // Apply operations in a single fast, non-cancellable EDT batch: dispose -> repaint -> add
                editor.inlayModel.execute(false) {
                    toDispose.forEach { if (it.isValid) it.dispose() }
                    // Repaint remaining inlays after disposals
                    toRepaint.forEach { if (it.isValid) it.repaint() }
                    // Add new inlays
                    toAdd.forEach { (offset, ch, colorShape) ->
                        addColoredShapeAboveCursingToken(editor, colorShape, offset, ch)
                    }
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
        debouncer.debounce(false, createAndSetId(editor), suspend {
            removeAllCursingTokensNow(editor)
        })
    }

    private suspend fun removeAllCursingTokensNow(editor: Editor) {
        if (!editor.isDisposed) {
            val existingInlays = pullExistingInlays(editor)
            if (existingInlays.isNotEmpty()) {
                withContext(Dispatchers.EDT) {
                    editor.inlayModel.execute(false) {
                        existingInlays
                            .asSequence()
                            .map { it.first }
                            .forEach {
                                if (it.isValid) {
                                    it.dispose()
                                }
                            }
                    }
                    editor.contentComponent.repaint()
                    editor.removeUserData(ColorAndShapeManager.KEY)
                }
            }
        }
    }

    private suspend fun pullExistingInlays(editor: Editor): List<Pair<Inlay<*>, CursingColorShape>> {
        return pullExistingInlaysByOffset(editor)
            .asSequence()
            .map { it.value }
            .flatten()
            .toList()
    }

    private suspend fun pullExistingInlaysByOffset(editor: Editor): Map<Int, List<Pair<Inlay<*>, CursingColorShape>>> {
        return withContext(Dispatchers.EDT) {
            editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
                .asSequence()
                .filter { it.getUserData(INLAY_KEY) != null }
                .groupBy { it.offset }
                .mapValues { it.value.map { inlay -> Pair(inlay, inlay.getUserData(INLAY_KEY) as CursingColorShape) } }
        }
    }


    private suspend fun createAndSetColorAndShapeManager(editor: Editor): ColorAndShapeManager {
        unsafeDataMutex.withLock {
            return editor.getOrCreateUserDataUnsafe(ColorAndShapeManager.KEY) {
                ColorAndShapeManager(preferenceService.colors, preferenceService.shapes)
            }
        }
    }

    private suspend fun createAndSetId(editor: Editor): DebounceEditorState {
        unsafeDataMutex.withLock {
            return editor.getOrCreateUserDataUnsafe(ID_KEY) {
                DebounceEditorState(idGenerator.getAndIncrement())
            }
        }
    }


    private fun addColoredShapeAboveCursingToken(
        editor: Editor, cursingColorShape: CursingColorShape, offset: Int, character: Char
    ) {
        val renderer = ColoredShapeRenderer(cursingColorShape, character, offset)

        editor.inlayModel.addInlineElement(offset, false, Int.MAX_VALUE, renderer)
            ?.putUserData(INLAY_KEY, cursingColorShape)
    }

    data class DebounceEditorState(val id: Long)

    class Debouncer(
        delay: Int,
        private val coroutineScope: CoroutineScope
    ) {
        private val flow =
            MutableSharedFlow<Triple<Boolean, DebounceEditorState, suspend () -> Unit>>(
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
                        var currentAdd = false
                        var currentId = -1L
                        it.forEach { (addOrRemove, state, function) ->
                            try {
                                if (state.id != currentId || currentAdd != addOrRemove) {
                                    currentAdd = addOrRemove
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
            addOrRemove: Boolean,
            debounceEditorState: DebounceEditorState,
            function: suspend () -> Unit
        ) {
            processingCount.incrementAndGet()
            flow.emit(Triple(addOrRemove, debounceEditorState, function))
        }

        fun workToProcess(): Boolean {
            return processingCount.get() > 0 && coroutineScope.isActive
        }

        fun resetProcessingCount() {
            if(processingCount.get() > 0) {
                thisLogger().error("processing count: ${processingCount.get()} reset to zero")
            }
            processingCount.set(0)
        }
    }

}
