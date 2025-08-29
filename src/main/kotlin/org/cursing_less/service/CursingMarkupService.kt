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

    suspend fun updateCursingTokensNow(editor: Editor, cursorOffset: Int) {
        try {
            performUpdateOfCursingTokens(editor, cursorOffset)
        } catch (e: IllegalStateException) {
            thisLogger().warn("Failed to update cursing tokens due to an illegal state", e)
            // If it fails then start fresh and try again.
            removeAllCursingTokens(editor)
            performUpdateOfCursingTokens(editor, cursorOffset)
        }
    }

    private suspend fun performUpdateOfCursingTokens(editor: Editor, cursorOffset: Int) {
        if (enabled.get() && !editor.isDisposed) {
            val editorState = getOrCreateAndSetEditorState(editor)
            val colorAndShapeManager = editorState.colorAndShapeManager
            colorAndShapeManager.freeAll()

            val tokensByOffset =
                tokenService.findCursingTokens(editor).associateTo(mutableMapOf(), { it.first to it.second })

            withContext(Dispatchers.EDT + NonCancellable) {
                val existingGraphics = pullExistingGraphics(editor)

                accountForAndCleanExistingGraphics(
                    existingGraphics,
                    tokensByOffset,
                    colorAndShapeManager
                )

                val sortedTokens = tokensByOffset
                    .asSequence()
                    .map { it.toPair() }
                    .sortedBy { distance(cursorOffset, it.first) }
                    .toList()

                addUnknownGraphics(existingGraphics, sortedTokens, colorAndShapeManager, cursorOffset, editor)

                editor.contentComponent.repaint()
            }

            validateNoDuplicateGraphics(editor)
        }
    }

    private fun accountForAndCleanExistingGraphics(
        existingGraphics: MutableMap<Int, MutableList<CursingGraphics>>,
        tokensByOffset: MutableMap<Int, String>,
        colorAndShapeManager: ColorAndShapeManager
    ) {
        val iterator = existingGraphics.iterator()
        // go through all existing graphics
        while (iterator.hasNext()) {
            val (offset, graphics) = iterator.next()
            val existingToken = tokensByOffset[offset]
            // if there is no token at the offset, remove the graphics
            if (existingToken != null) {
                removeAlreadyConsumedExistingGraphics(graphics, existingToken, colorAndShapeManager)
                // if there are graphics that are valid at that offset then use them.
                if (graphics.isNotEmpty()) {
                    val graphic = reduceToSingleGraphicAndClean(graphics, offset, existingToken, colorAndShapeManager)
                    tokensByOffset.remove(offset)
                    consumeExistingGraphic(graphic, offset, colorAndShapeManager)
                }
            } else {
                graphics.forEach { it.highlighter.dispose() }
                iterator.remove()
            }
        }
    }

    private fun consumeExistingGraphic(graphic: CursingGraphics, offset: Int, colorAndShapeManager: ColorAndShapeManager) {
        checkNotNull(
            colorAndShapeManager.consume(offset, graphic.text, graphic.cursingColorShape)
        ) {
            "Color and shape mismatch. Failed to consume color and shape that should be available."
        }
    }

    private fun reduceToSingleGraphicAndClean(
        graphics: MutableList<CursingGraphics>,
        offset: Int,
        existingToken: String,
        colorAndShapeManager: ColorAndShapeManager
    ): CursingGraphics {
        val graphic = graphics[0]
        // but make sure there is only one
        while (graphics.size > 1) {
            thisLogger().warn("Multiple graphics found at offset $offset: $graphics")
            graphics.removeLast().highlighter.dispose()
        }
        // and update it to be correct.
        colorAndShapeManager.setPreference(graphic.offset, graphic.cursingColorShape)
        if (graphic.text != existingToken) {
            graphic.text = existingToken
        }
        return graphic
    }

    private fun removeAlreadyConsumedExistingGraphics(
        graphics: MutableList<CursingGraphics>,
        token: String,
        colorAndShapeManager: ColorAndShapeManager
    ) {
        val graphicsAtOffsetIterator = graphics.iterator()
        val character = token[0].lowercaseChar()
        while (graphicsAtOffsetIterator.hasNext()) {
            val next = graphicsAtOffsetIterator.next()
            if (!colorAndShapeManager.isFree(next.cursingColorShape, character)) {
                graphicsAtOffsetIterator.remove()
                next.highlighter.dispose()
            }
        }
    }

    private fun addUnknownGraphics(
        existingGraphics: Map<Int, List<CursingGraphics>>,
        tokens: List<Pair<Int, String>>,
        colorAndShapeManager: ColorAndShapeManager,
        cursorOffset: Int,
        editor: Editor
    ) {
        val existingGraphicsByChar = graphicsByChar(existingGraphics, cursorOffset)
        tokens
            .forEach { (tokenOffset, token) ->
                var consumed = colorAndShapeManager.consume(tokenOffset, token)
                if (consumed == null) {
                    val candidateToken = token[0].lowercaseChar()
                    val candidateToSteal = existingGraphicsByChar[candidateToken]?.removeLastOrNull()
                    val candidateOffset = candidateToSteal?.offset

                    if (candidateToSteal != null && candidateOffset != null &&
                        distance(cursorOffset, candidateOffset) > distance(cursorOffset, tokenOffset)
                    ) {
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
                }

                if (consumed != null) {
                    addColoredShapeAboveCursingToken(editor, consumed, tokenOffset, token)
                }
            }
    }

    fun workToProcess() = debouncer.workToProcess()
    fun resetProcessingCount() = debouncer.resetProcessingCount()

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
                        .flatMap { it }
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

    fun validateNoDuplicateGraphics(editor: Editor): Boolean {
        val existing = mutableMapOf<Int, CursingGraphics>()
        var valid = true
        editor.markupModel.allHighlighters
            .asSequence()
            .filter { it.isValid }
            .mapNotNull { it.getUserData(CURSING_EDITOR_GRAPHICS) }
            .forEach {
                val existingMapping = existing.putIfAbsent(it.offset, it)
                if (existingMapping != null) {
                    thisLogger().error("Duplicate graphics found at offset ${it.offset}: existing: $existingMapping, new: $it")
                    valid = false
                }
            }
        return valid
    }

    fun pullExistingGraphics(editor: Editor): MutableMap<Int, MutableList<CursingGraphics>> {
        val map = mutableMapOf<Int, MutableList<CursingGraphics>>()
        editor.markupModel.allHighlighters
            .asSequence()
            .filter { it.isValid }
            .mapNotNull { it.getUserData(CURSING_EDITOR_GRAPHICS) }
            .forEach {
                map.getOrPut(it.offset) { mutableListOf() }.add(it)
            }
        return map
    }

    fun graphicsByChar(
        graphics: Map<Int, List<CursingGraphics>>,
        offset: Int
    ): Map<Char, MutableList<CursingGraphics>> {
        val byChar = mutableMapOf<Char, MutableList<CursingGraphics>>()
        graphics
            .asSequence()
            .filter { it.value.isNotEmpty() }
            .map { it.value[0] }
            .forEach {
                val char = it.character.lowercaseChar()
                byChar.getOrPut(char) { mutableListOf() }.add(it)
            }
        byChar.forEach { entry -> entry.value.sortWith(OffsetDistanceComparator(offset) { it.offset }) }

        return byChar
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
        val graphics = CursingGraphics(cursingColorShape, highlighter, text)
        highlighter.putUserData(CURSING_EDITOR_GRAPHICS, graphics)
        return graphics
    }

    data class CursingGraphics(
        val cursingColorShape: CursingColorShape,
        val highlighter: RangeHighlighter,
        var text: String,
    ) {
        val character: Char = text[0].lowercaseChar()
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
                            if (current != null && (current.first != triple.first || current.second.id != triple.second.id)) {
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
