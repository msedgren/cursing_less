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
import org.cursing_less.util.OffsetDistanceComparator
import java.util.SortedSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

import  org.cursing_less.util.OffsetDistanceComparator.Companion.distance
import kotlin.collections.component1
import kotlin.collections.component2

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

    suspend fun updateCursingTokensNow(editor: Editor, cursorOffset: Int): CursingTokenChanges {
        val cursingTokenChanges = CursingTokenChanges()
        if (enabled.get() && !editor.isDisposed && editorEligibleForMarkup(editor)) {
            val editorState = getOrCreateAndSetEditorState(editor)
            val colorAndShapeManager = editorState.colorAndShapeManager
            colorAndShapeManager.freeAll()

            val tokensByOffset =
                tokenService.findCursingTokens(editor).associateTo(mutableMapOf(), { it.first to it.second })

            val existingGraphics = pullExistingGraphics(editor).toMutableMap()

            withContext(Dispatchers.EDT + NonCancellable) {
                val iterator = existingGraphics.iterator()
                while (iterator.hasNext()) {
                    val (offset, graphic) = iterator.next()
                    val existingToken = tokensByOffset[offset]
                    if (existingToken != null) {
                        cursingTokenChanges.incrementResued()
                        tokensByOffset.remove(offset)
                        checkNotNull(
                            colorAndShapeManager.consume(
                                graphic.offset,
                                graphic.text,
                                graphic.cursingColorShape,
                            )
                        ) {
                            "Color and shape mismatch. Failed to consume color and shape that should be available."
                        }
                    } else {
                        cursingTokenChanges.incrementDisposed()
                        graphic.highlighter.dispose()
                        iterator.remove()
                    }
                }

                val sortedTokens = tokensByOffset
                    .asSequence()
                    .map { Pair(it.key, it.value) }
                    .sortedWith(OffsetDistanceComparator(cursorOffset) { it.first })

                cursingTokenChanges.add(
                    addUnknownTokens(existingGraphics, sortedTokens, colorAndShapeManager, cursorOffset, editor)
                )

                editor.contentComponent.repaint()
            }

            validateNoDuplicateGraphics(editor)
        }
        return cursingTokenChanges
    }

    private fun addUnknownTokens(
        existingGraphics: Map<Int, CursingGraphics>,
        tokens: Sequence<Pair<Int, String>>,
        colorAndShapeManager: ColorAndShapeManager,
        cursorOffset: Int,
        editor: Editor
    ): CursingTokenChanges {
        val cursingTokenChanges = CursingTokenChanges()
        val existingGraphicsByChar = graphicsByChar(existingGraphics, cursorOffset)

        tokens.forEach { (tokenOffset, token) ->
            var consumed = colorAndShapeManager.consume(tokenOffset, token)
            if (consumed == null) {
                val candidateToken = token[0].lowercaseChar()
                val candidateToSteal = existingGraphicsByChar[candidateToken]?.removeLastOrNull()
                val candidateOffset = candidateToSteal?.offset

                if (candidateToSteal != null && candidateOffset != null &&
                    distance(cursorOffset, candidateOffset) > distance(cursorOffset, tokenOffset)
                ) {
                    cursingTokenChanges.incrementStolen()
                    cursingTokenChanges.decrementResued()
                    colorAndShapeManager.free(candidateOffset)
                    candidateToSteal.highlighter.dispose()
                    consumed = checkNotNull(
                        colorAndShapeManager.consume(
                            tokenOffset,
                            token,
                            candidateToSteal.cursingColorShape
                        )
                    ) {
                        "Color and shape mismatch. Failed to consume color and shape that were stolen."
                    }
                }
            } else {
                cursingTokenChanges.incrementConsumed()
            }
            if (consumed != null) {
                addColoredShapeAboveCursingToken(editor, consumed, tokenOffset, token)
            } else {
                cursingTokenChanges.incrementFailed()
            }
        }

        return cursingTokenChanges
    }

    data class CursingTokenChanges(
        var resuedCount: Int = 0,
        var consumedCount: Int = 0,
        var stolenCount: Int = 0,
        var failedCount: Int = 0,
        var disposedCount: Int = 0
    ) {

        fun incrementResued() = resuedCount++
        fun decrementResued() = resuedCount--
        fun incrementConsumed() = consumedCount++
        fun incrementStolen() = stolenCount++
        fun incrementFailed() = failedCount++
        fun incrementDisposed() = disposedCount++

        fun add(changes: CursingTokenChanges) {
            resuedCount += changes.resuedCount
            consumedCount += changes.consumedCount
            stolenCount += changes.stolenCount
            failedCount += changes.failedCount
            disposedCount += changes.disposedCount
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

    fun graphicsByChar(graphics: Map<Int, CursingGraphics>, offset: Int): Map<Char, MutableList<CursingGraphics>> {
        val byChar = mutableMapOf<Char, MutableList<CursingGraphics>>()
        graphics.forEach {
            val char = it.value.character.lowercaseChar()
            byChar.getOrPut(char) { mutableListOf() }
                .add(it.value)

        }
        byChar.forEach { entry -> entry.value.sortWith(OffsetDistanceComparator(offset) { it.offset }) }
        return byChar
    }

    suspend fun validateNoDuplicateGraphics(editor: Editor) {
        val existing = mutableMapOf<Int, CursingGraphics>()
        return withContext(Dispatchers.EDT) {
            editor.markupModel.allHighlighters
                .asSequence()
                .filter { it.isValid }
                .mapNotNull { it.getUserData(CURSING_EDITOR_GRAPHICS) }
                .forEach {
                    val existingMapping = existing.putIfAbsent(it.offset, it)
                    if(existingMapping != null) {
                        thisLogger().error("Duplicate graphics found at offset ${it.offset}: existing: $existingMapping, new: $it")
                    }
                }
        }
    }

    suspend fun pullExistingGraphics(editor: Editor): Map<Int, CursingGraphics> {
        return withContext(Dispatchers.EDT) {
            editor.markupModel.allHighlighters
                .asSequence()
                .filter { it.isValid }
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
        editor: Editor, cursingColorShape: CursingColorShape, offset: Int, text: String
    ): CursingGraphics {
        val character = text[0].lowercaseChar()
        val renderer = ColoredShapeRenderer(cursingColorShape, character)
        val highlighter = editor.markupModel.addRangeHighlighter(
            offset,
            offset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            null,
            HighlighterTargetArea.EXACT_RANGE
        )
        highlighter.customRenderer = renderer
        val graphics = CursingGraphics(cursingColorShape, character, highlighter, text)
        highlighter.putUserData(CURSING_EDITOR_GRAPHICS, graphics)
        return graphics
    }

    data class CursingGraphics(
        val cursingColorShape: CursingColorShape,
        val character: Char,
        val highlighter: RangeHighlighter,
        val text: String,
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
            MutableSharedFlow<Triple<DebounceTask, CursingEditorState, suspend () -> Any>>(
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
                        var current: Triple<DebounceTask, CursingEditorState, suspend () -> Any>? = null
                        it.forEach { triple ->
                            if(current != null && (current.first != triple.first || current.second.id != triple.second.id)) {
                                invokeNow(current)
                            }
                            current = triple
                        }
                        invokeNow(current!!)
                        processingCount.getAndAdd(-it.size)
                    }
            }.invokeOnCompletion {
                resetProcessingCount()
            }
        }

        suspend fun debounce(
            task: DebounceTask,
            cursingEditorState: CursingEditorState,
            function: suspend () -> Any
        ) {
            processingCount.incrementAndGet()
            flow.emit(Triple(task, cursingEditorState, function))
        }

        suspend fun invokeNow(triple: Triple<DebounceTask, CursingEditorState, suspend () -> Any>) {
            try {
                triple.third()
            } catch (e: Exception) {
                thisLogger().warn("Failed to process cursing markup", e)
            }
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
