package org.cursing_less.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.listeners.CursingApplicationListener
import java.awt.Graphics
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Pair
import kotlin.collections.ArrayList
import com.intellij.psi.util.startOffset
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.APP)
class CursingMarkupService(private val coroutineScope: CoroutineScope) {

    private val reg = Regex("(?<=^|\\s)[^\\s.]+(?=\\s|\$)")
    private val enabled = AtomicBoolean(true)
    private val debouncer = Debouncer(250, coroutineScope)

    companion object {
        private const val INLAY_NAME = "CURSING_INLAY"
        val INLAY_KEY = Key.create<CursingColorShape>(INLAY_NAME)
    }

    suspend fun toggleEnabled() {
        enabled.set(!enabled.get())

        EditorFactory.getInstance().allEditors.forEach { editor ->
            if (enabled.get()) {
                updateCursingTokens(editor, editor.caretModel.offset)
            } else {
                removeAllCursingTokens(editor)
            }
        }
    }


    suspend fun updateCursingTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce("update", editor, suspend {
            updateCursingTokensNow(editor, cursorOffset)
        })
    }

    private suspend fun updateCursingTokensNow(editor: Editor, cursorOffset: Int) {
        val project = editor.project
        if (enabled.get() && project != null && !project.isDisposed && project.isInitialized && !editor.isDisposed) {
            val colorAndShapeManager = createAndSetColorAndShapeManager(editor)
            colorAndShapeManager.freeAll()

            val tokens = findCursingTokens(editor, project, cursorOffset, colorAndShapeManager)

            withContext(Dispatchers.EDT) {
                val existingInlays = pullExistingInlaysByOffset(editor)
                tokens.forEach { (offset, cursingColorShape) ->
                    val existing = existingInlays[offset]
                    if (existing?.second == cursingColorShape) {
                        val inlay = existingInlays.remove(offset)?.first
                        if (inlay?.isValid == true) {
                            inlay.repaint()
                        }
                    } else {
                        addColoredShapeAboveCursingToken(editor, cursingColorShape, offset)
                    }
                }

                existingInlays.forEach { (_, pair) ->
                    if (pair.first.isValid) {
                        pair.first.dispose()
                    }
                }

                editor.contentComponent.repaint()
            }
        }
    }

    private suspend fun removeAllCursingTokens(editor: Editor) {
        debouncer.debounce("remove", editor, suspend {
            removeAllCursingTokensNow(editor)
        })
    }

    private suspend fun removeAllCursingTokensNow(editor: Editor) {
        if (!editor.isDisposed) {
            withContext(Dispatchers.EDT) {
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
                    createAndSetColorAndShapeManager(editor).freeAll()
                }
            }
        }
    }

    private suspend fun pullExistingInlays(editor: Editor): List<Pair<Inlay<*>, CursingColorShape>> {
        return pullExistingInlaysByOffset(editor).values.toList()

    }

    private suspend fun pullExistingInlaysByOffset(editor: Editor): MutableMap<Int, Pair<Inlay<*>, CursingColorShape>> {
        return withContext(Dispatchers.EDT) {
            editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength - 1)
                .mapNotNull { inlay -> inlay.getUserData(INLAY_KEY)?.let { data -> Pair(inlay, data) } }
                .associateByTo(mutableMapOf()) { it.first.offset }
        }
    }


    private fun createAndSetColorAndShapeManager(editor: Editor): ColorAndShapeManager {
        val cursingPreferenceService =
            ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
        return editor.getOrCreateUserDataUnsafe(ColorAndShapeManager.KEY) {
            ColorAndShapeManager(cursingPreferenceService.colors, cursingPreferenceService.shapes)
        }
    }

    private suspend fun findCursingTokens(
        editor: Editor, project: Project, offset: Int, colorAndShapeManager: ColorAndShapeManager
    ): List<Pair<Int, CursingColorShape>> {
        val visibleArea = withContext(Dispatchers.EDT) {
            editor.calculateVisibleRange()
        }

        return readAction {
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            val found = ArrayList<Pair<Int, CursingColorShape>>()
            if (file != null) {
                var nextElement = findElementClosestToOffset(file, offset)
                if(nextElement != null) {
                    var previousElement = PsiTreeUtil.prevVisibleLeaf(nextElement)
                    var nextElementVisible = isAnyPartVisible(visibleArea, nextElement.textRange)
                    var previousElementVisible = previousElement != null && isAnyPartVisible(visibleArea, previousElement.textRange)
                    // An attempt to bubble out from the cursor.
                    while ((nextElementVisible || previousElementVisible)) {
                        if (nextElement != null && nextElementVisible) {
                            found.addAll(consumeIfVisible(nextElement, visibleArea, colorAndShapeManager))
                            nextElement = PsiTreeUtil.nextVisibleLeaf(nextElement)
                            nextElementVisible = nextElement != null && isAnyPartVisible(visibleArea, nextElement.textRange)
                        } else {
                            nextElement = null
                            nextElementVisible = false
                        }

                        if (previousElement != null && previousElementVisible) {
                            found.addAll(consumeIfVisible(previousElement, visibleArea, colorAndShapeManager))
                            previousElement = PsiTreeUtil.prevVisibleLeaf(previousElement)
                            previousElementVisible = previousElement != null && isAnyPartVisible(visibleArea, previousElement.textRange)
                        } else {
                            previousElement = null
                            previousElementVisible = false
                        }
                    }
                }
            }

            found
        }
    }

    private fun consumeIfVisible(
        element: PsiElement, visibleArea: ProperTextRange, colorAndShapeManager: ColorAndShapeManager
    ): List<Pair<Int, CursingColorShape>> {

        if (isAnyPartVisible(visibleArea, element.textRange)) {
            return findAllCursingTokensWithinElement(element).mapNotNull {
                val startOffset = it.startOffset
                val consumed = colorAndShapeManager.consume(it.text[0], startOffset, it.endOffset)
                if (consumed != null) Pair(startOffset, consumed) else null
            }.toList()
        }
        return emptyList()
    }

    private fun findAllCursingTokensWithinElement(element: PsiElement): List<CursingToken> {
        val startOffset = element.startOffset
        val text = element.text
        return reg.findAll(text).iterator().asSequence()
            .map { CursingToken(startOffset + it.range.first, startOffset + it.range.last + 1, text) }.toList()
    }

    data class CursingToken(val startOffset: Int, val endOffset: Int, val text: String)

    private fun findElementClosestToOffset(file: PsiFile, offset: Int): PsiElement? {
        var next = offset
        var previous = offset

        var element = file.findElementAt(offset)
        while (element == null && (previous > 0 || next < (file.textLength - 1))) {
            if (previous > 0) {
                element = file.findElementAt(--previous)
            }
            if (element == null && next < (file.textLength - 1)) {
                element = file.findElementAt(++next)
            }
        }
        return element
    }

    private fun isAnyPartVisible(visibleArea: ProperTextRange, elementArea: TextRange): Boolean {
        return visibleArea.intersects(elementArea)
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


    class Debouncer(
        private val delay: Int,
        private val coroutineScope: CoroutineScope
    ) {
        private val updateQueue = MergingUpdateQueue("cursing_less_debounce", delay, true, null)

        companion object {
            private const val KEY_NAME = "CURSING_EDITOR_ID"
            private val idGenerator = AtomicLong(0)
            private val KEY = Key.create<Long>(KEY_NAME)
            private val mutex = Mutex()
        }

        suspend fun debounce(operation: String, editor: Editor, function: suspend () -> Unit) {
            val id = createAndSetId(editor)
            val task = object : Update("${operation}_${id}") {
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

        private suspend fun createAndSetId(editor: Editor): Long {
            mutex.withLock {
                return editor.getOrCreateUserDataUnsafe(KEY) {
                    idGenerator.getAndIncrement()
                }
            }
        }
    }

}
