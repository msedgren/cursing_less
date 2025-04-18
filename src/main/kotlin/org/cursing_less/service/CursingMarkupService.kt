package org.cursing_less.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.listener.CursingApplicationListener
import java.awt.Graphics
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Pair
import com.intellij.psi.util.startOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

typealias NextFunction = (PsiElement) -> PsiElement?
typealias VisibleChecker = (PsiElement) -> Boolean

@Service(Service.Level.APP)
class CursingMarkupService(private val coroutineScope: CoroutineScope) : Disposable {

    private val enabled = AtomicBoolean(true)
    private val debouncer = Debouncer(250, coroutineScope)

    companion object {
        private const val INLAY_NAME = "CURSING_INLAY"
        private const val ID_KEY_NAME = "CURSING_EDITOR_ID"

        val INLAY_KEY = Key.create<CursingColorShape>(INLAY_NAME)
        val ID_KEY = Key.create<Long>(ID_KEY_NAME)

        private val idGenerator = AtomicLong(0)
        private val mutex = Mutex()
    }


    override fun dispose() {
        enabled.set(false)
        debouncer.dispose()
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                EditorFactory.getInstance().allEditors.forEach { editor ->
                    removeAllCursingTokensNow(editor)
                }
            }
        }
    }

    fun toggleEnabled() {
        enabled.set(!enabled.get())

        coroutineScope.launch(Dispatchers.EDT) {
            EditorFactory.getInstance().allEditors.forEach { editor ->
                if (enabled.get()) {
                    updateCursingTokens(editor, editor.caretModel.offset)

                } else {
                    removeAllCursingTokens(editor)
                }
            }
        }
    }


    suspend fun updateCursingTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce("update", createAndSetId(editor), suspend {
            updateCursingTokensNow(editor, cursorOffset)
        })
    }

    suspend fun updateCursingTokensNow(editor: Editor, cursorOffset: Int) {
        if (enabled.get() && !editor.isDisposed) {
            // thisLogger().trace("Updating cursing tokens for ${editor.editorKind.name} with ID ${createAndSetId(editor)}")
            val colorAndShapeManager = createAndSetColorAndShapeManager(editor)
            colorAndShapeManager.freeAll()

            val tokens = findCursingTokens(editor, cursorOffset, colorAndShapeManager).toMutableMap()

            withContext(Dispatchers.EDT) {
                val existingInlays = pullExistingInlaysByOffset(editor)

                existingInlays.forEach { (offset, oldConsumedList) ->
                    var found = false
                    oldConsumedList.forEach { (inlay, consumedCursing) ->
                        if (inlay.isValid) {
                            val wanted = tokens[offset]
                            if (found || wanted == null || wanted != consumedCursing) {
                                inlay.dispose()
                            } else {
                                inlay.repaint()
                                found = true
                                tokens.remove(offset)
                            }
                        }
                    }
                }

                tokens.forEach { (offset, cursingColorShape) ->
                    addColoredShapeAboveCursingToken(editor, cursingColorShape, offset)
                }

                editor.contentComponent.repaint()
            }
        }
    }

    private suspend fun removeAllCursingTokens(editor: Editor) {
        debouncer.debounce("remove", createAndSetId(editor), suspend {
            removeAllCursingTokensNow(editor)
        })
    }

    private suspend fun removeAllCursingTokensNow(editor: Editor) {
        withContext(Dispatchers.EDT) {
            if (!editor.isDisposed) {
                val existingInlays = pullExistingInlays(editor)
                if (existingInlays.isNotEmpty()) {
                    editor.inlayModel.execute(false) {
                        existingInlays
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
        return pullExistingInlaysByOffset(editor).values.toList().flatten()

    }

    private suspend fun pullExistingInlaysByOffset(editor: Editor): Map<Int, List<Pair<Inlay<*>, CursingColorShape>>> {
        return withContext(Dispatchers.EDT) {
            editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength - 1)
                .filter { it.getUserData(INLAY_KEY) != null }
                .groupBy { it.offset }
                .mapValues { it.value.map { inlay -> Pair(inlay, inlay.getUserData(INLAY_KEY) as CursingColorShape) } }
        }
    }


    private suspend fun createAndSetColorAndShapeManager(editor: Editor): ColorAndShapeManager {
        val cursingPreferenceService =
            ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
        mutex.withLock {
            return editor.getOrCreateUserDataUnsafe(ColorAndShapeManager.KEY) {
                ColorAndShapeManager(cursingPreferenceService.colors, cursingPreferenceService.shapes)
            }
        }
    }

    private suspend fun createAndSetId(editor: Editor): Long {
        mutex.withLock {
            return editor.getOrCreateUserDataUnsafe(ID_KEY) {
                idGenerator.getAndIncrement()
            }
        }
    }

    private suspend fun findCursingTokens(
        editor: Editor, offset: Int, manager: ColorAndShapeManager
    ): Map<Int, CursingColorShape> {
        val visibleArea = withContext(Dispatchers.EDT) { editor.calculateVisibleRange() }
        return readAction {
            val project = editor.project ?: ProjectManager.getInstance().defaultProject
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            val found = mutableMapOf<Int, CursingColorShape>()
            if (file != null) {
                val elements = findVisibleElements(visibleArea, file)
                val consumed = elements
                    .asSequence()
                    .filter { visibleArea.contains(it.startOffset) }
                    .map { Pair(it, it.text) }
                    .filter { it.second.isNotBlank() && !it.second[0].isWhitespace() && it.second[0] != '.' }
                    .sortedWith { a, b -> abs(offset - a.first.startOffset) - abs(offset - b.first.startOffset) }
                    .mapNotNull { (element, text) ->
                        manager.consume(text[0], element.startOffset, element.endOffset)?.let {
                            Pair(element.startOffset, it)
                        }
                    }

                found.putAll(consumed)
            }
            found.putAll(consumeVisible(manager, offset, found.keys, editor.document.getText(visibleArea), visibleArea))
            found
        }
    }

    private fun findVisibleElements(visibleArea: ProperTextRange, file: PsiFile): List<PsiElement> {
        var startOffset = visibleArea.startOffset
        var element = file.findElementAt(startOffset)
        while (element == null && startOffset < visibleArea.endOffset) {
            element = file.findElementAt(++startOffset)
        }

        val elements = mutableListOf<PsiElement>()
        while (element != null && element.textRange.startOffset < visibleArea.endOffset) {
            elements.add(element)
            element = PsiTreeUtil.nextLeaf(element)
        }
        return elements
    }

    private fun consumeVisible(
        colorAndShapeManager: ColorAndShapeManager,
        currentOffset: Int,
        alreadyKnown: Set<Int>,
        text: String,
        visibleArea: ProperTextRange
    ): List<Pair<Int, CursingColorShape>> {
        val tokens = findAllCursingTokensWithin(text, visibleArea.startOffset)
        return tokens
            .filterNot { alreadyKnown.contains(it.startOffset) }
            .sortedWith { a, b ->
                abs(currentOffset - a.startOffset) - abs(currentOffset - b.startOffset)
            }
            .mapNotNull {
                colorAndShapeManager.consume(it.text[0], it.startOffset, it.endOffset)?.let { consumed ->
                    Pair(it.startOffset, consumed)
                }
            }.toList()
    }

    private fun findAllCursingTokensWithin(text: String, startOffset: Int): List<CursingToken> {
        val reg = ApplicationManager.getApplication().getService(CursingPreferenceService::class.java).tokenPattern

        return reg.findAll(text).iterator().asSequence()
            .filter { it.value.isNotBlank() && !it.value[0].isWhitespace() }
            .map { CursingToken(startOffset + it.range.first, startOffset + it.range.last + 1, it.value) }.toList()
    }

    private fun addColoredShapeAboveCursingToken(
        editor: Editor, cursingColorShape: CursingColorShape, offset: Int
    ) {
        editor.inlayModel.addInlineElement(offset, true, Int.MIN_VALUE, ColoredShapeRenderer(cursingColorShape))
            ?.putUserData(INLAY_KEY, cursingColorShape)
    }


    class ColoredShapeRenderer(
        private val cursingColorShape: CursingColorShape
    ) : EditorCustomElementRenderer {

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            return 1
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.color = cursingColorShape.color.color
            cursingColorShape.shape.paint(inlay, g, targetRegion, textAttributes)
        }
    }

    data class CursingToken(val startOffset: Int, val endOffset: Int, val text: String)

    class Debouncer(
        private val delay: Int,
        private val coroutineScope: CoroutineScope
    ) : Disposable {
        private val updateQueue = MergingUpdateQueue("cursing_less_debounce", delay, true, null)

        companion object {
            private val mutex = Mutex()
        }

        fun debounce(operation: String, editorId: Long, function: suspend () -> Unit) {
            val task = object : Update("${operation}_${editorId}") {
                override fun run() {
                    if (CursingApplicationListener.handler.initialized.get()) {
                        coroutineScope.launch {
                            mutex.withLock {
                                function()
                            }
                        }
                    }
                }
            }
            updateQueue.queue(task)
        }

        override fun dispose() {
            runBlocking {
                mutex.withLock {
                    updateQueue.dispose()
                }
            }
        }
    }

}
