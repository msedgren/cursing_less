package org.cursing_less.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.listeners.CursingApplicationListener
import java.awt.Graphics
import java.awt.Rectangle
import kotlin.collections.ArrayList

@Service(Service.Level.APP)
class CursingMarkupService {

    private val reg = Regex("(?<=^|\\s)\\S+(?=\\s|\$)")

    @Volatile
    private var enabled = true
    private val debouncer = Debouncer(250)

    companion object {
        private const val INLAY_NAME = "CURSING_INLAY"
        val INLAY_KEY = Key.create<CursingColorShape>(INLAY_NAME)
    }

    fun toggleEnabled() {
        enabled = !enabled
        val isEnabled = enabled  // Capture current value for use inside the lambda

        EditorFactory.getInstance().allEditors.forEach { editor ->
            ApplicationManager.getApplication().invokeLater {
                if (isEnabled) {
                    updateCursingTokens(editor, editor.caretModel.offset)
                } else {
                    removeAllCursingTokens(editor)
                }
            }
        }
    }


    fun updateCursingTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce(this, editor, cursorOffset)
    }

    private fun updateCursingTokensNow(editor: Editor, cursorOffset: Int) {
        val project = editor.project
        ApplicationManager.getApplication().invokeLater {
            if (enabled && project != null && !project.isDisposed && project.isInitialized && !editor.isDisposed) {
                val colorAndShapeManager = createAndSetColorAndShapeManager(editor, project)
                colorAndShapeManager.freeAll()
                val pulled = pullCursingTokenUpdates(editor, project, cursorOffset, colorAndShapeManager)
                if (pulled.hasTokens) {
                    editor.inlayModel.execute(false) {
                        pulled.cursingTokens.forEach { (offset, cursingColorShape) ->
                            val existing = pulled.existingInlays[offset]
                            if (existing?.second == cursingColorShape) {
                                pulled.existingInlays.remove(offset)?.first?.repaint()
                            } else {
                                addColoredShapeAboveCursingToken(editor, cursingColorShape, offset)
                            }
                        }

                        pulled.existingInlays.forEach { (_, pair) ->
                            pair.first.dispose()
                        }
                    }
                    editor.contentComponent.repaint()
                }
            }
        }
    }

    private fun pullCursingTokenUpdates(
        editor: Editor, project: Project,
        cursorOffset: Int, colorAndShapeManager: ColorAndShapeManager
    ): CursingTokenUpdates {
        return ApplicationManager.getApplication().runReadAction(Computable {
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            if (file != null) {
                val existingInlays = pullExistingInlaysByOffset(editor, file)
                val tokens = findCursingTokensBasedOffOffset(editor, file, cursorOffset, colorAndShapeManager)
                CursingTokenUpdates(existingInlays, tokens)
            } else {
                CursingTokenUpdates(mutableMapOf(), emptyList())
            }
        })
    }

    data class CursingTokenUpdates(
        val existingInlays: MutableMap<Int, Pair<Inlay<*>, CursingColorShape>>,
        val cursingTokens: List<Pair<Int, CursingColorShape>>
    ) {
        val hasTokens = cursingTokens.isNotEmpty()
    }


    private fun removeAllCursingTokens(editor: Editor) {
        val project = editor.project
        ApplicationManager.getApplication().invokeLater {
            if (project != null && !project.isDisposed && project.isInitialized && !editor.isDisposed) {
                val existingInlays = pullExistingInlays(editor, project)
                editor.inlayModel.execute(false) {
                    existingInlays.forEach { it.first.dispose() }
                }
                editor.contentComponent.repaint()
                createAndSetColorAndShapeManager(editor, project).freeAll()
            }
        }
    }

    private fun pullExistingInlays(editor: Editor, project: Project): List<Pair<Inlay<*>, CursingColorShape>> {
        return ApplicationManager.getApplication().runReadAction(Computable {
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            pullExistingInlaysByOffset(editor, file).values.toList()
        })
    }

    private fun pullExistingInlaysByOffset(
        editor: Editor,
        file: PsiFile?
    ): MutableMap<Int, Pair<Inlay<*>, CursingColorShape>> {
        return editor.inlayModel.getInlineElementsInRange(0, file?.endOffset ?: (editor.document.textLength - 1))
            .mapNotNull { it.getUserData(INLAY_KEY)?.let { data -> Pair(it, data) } }
            .associateByTo(mutableMapOf()) { it.first.offset }
    }


    private fun createAndSetColorAndShapeManager(editor: Editor, project: Project): ColorAndShapeManager {
        val cursingPreferenceService = project.getService(CursingPreferenceService::class.java)
        return editor.getOrCreateUserData(ColorAndShapeManager.KEY) {
            ColorAndShapeManager(cursingPreferenceService.colors, cursingPreferenceService.shapes)
        }
    }

    private fun findCursingTokensBasedOffOffset(
        editor: Editor,
        file: PsiFile,
        offset: Int,
        colorAndShapeManager: ColorAndShapeManager
    ):
            List<Pair<Int, CursingColorShape>> {
        val found = ArrayList<Pair<Int, CursingColorShape>>()
        var nextElement = findElementClosestToOffset(file, offset)
        var previousElement = if (nextElement != null) PsiTreeUtil.prevVisibleLeaf(nextElement) else null
        val visibleArea = editor.calculateVisibleRange()
        // An attempt to bubble out from the cursor.
        while ((nextElement != null && isAnyPartVisible(visibleArea, nextElement)) ||
            (previousElement != null && isAnyPartVisible(visibleArea, previousElement))
        ) {
            if (nextElement != null && isAnyPartVisible(visibleArea, nextElement)) {
                found.addAll(consumeIfVisible(nextElement, visibleArea, colorAndShapeManager))
                nextElement = PsiTreeUtil.nextVisibleLeaf(nextElement)
            } else {
                nextElement = null;
            }

            if (previousElement != null && isAnyPartVisible(visibleArea, previousElement)) {
                found.addAll(consumeIfVisible(previousElement, visibleArea, colorAndShapeManager))
                previousElement = PsiTreeUtil.prevVisibleLeaf(previousElement)
            } else {
                previousElement = null;
            }
        }

        return found
    }

    private fun consumeIfVisible(
        element: PsiElement,
        visibleArea: ProperTextRange,
        colorAndShapeManager: ColorAndShapeManager
    ):
            List<Pair<Int, CursingColorShape>> {
        if (!StringUtil.isEmptyOrSpaces(element.text) && isAnyPartVisible(visibleArea, element)) {
            return findAllCursingTokensWithinElement(element)
                .mapNotNull {
                    val startOffset = it.startOffset
                    val endOffset = it.endOffset
                    val character = it.text[0]
                    val consumed = colorAndShapeManager.consume(character, startOffset, endOffset)
                    if (consumed != null) Pair(startOffset, consumed) else null
                }
                .toList()
        }
        return emptyList()
    }

    private fun findAllCursingTokensWithinElement(element: PsiElement?): List<CursingToken> {
        val text = if (element == null) "" else element.text

        return if (StringUtil.isEmptyOrSpaces(text)) {
            emptyList()
        } else {
            val startOffset = element?.startOffset ?: 0
            reg.findAll(text).iterator()
                .asSequence()
                .map { CursingToken(startOffset + it.range.first, startOffset + it.range.last + 1, it.value) }
                .toList()
        }
    }

    data class CursingToken(val startOffset: Int, val endOffset: Int, val text: String)

    private fun findElementClosestToOffset(file: PsiFile, offset: Int): PsiElement? {
        var next = offset
        var previous = offset
        var element = file.findElementAt(offset)
        while (element == null && (previous > 0 || next < (file.endOffset - 1))) {
            if (previous > 0) {
                element = file.findElementAt(--previous)
            }
            if (element == null && next < (file.endOffset - 1)) {
                element = file.findElementAt(++next)
            }
        }
        return element
    }

    private fun isAnyPartVisible(visibleArea: ProperTextRange, element: PsiElement): Boolean {
        return visibleArea.intersects(element.textRange)
    }

    private fun addColoredShapeAboveCursingToken(
        editor: Editor,
        cursingColorShape: CursingColorShape,
        offset: Int
    ) {
        editor.inlayModel.addInlineElement(offset, ColoredShapeRenderer(cursingColorShape))
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


    class Debouncer(delay: Int) {
        private val updateQueue = MergingUpdateQueue("cursing_less_debounce", delay, true, null)

        fun debounce(cursingMarkupService: CursingMarkupService, editor: Editor, cursorOffset: Int) {
            val task = object : Update(editor) {
                override fun run() {
                    if (CursingApplicationListener.handler.initialized) {
                        cursingMarkupService.updateCursingTokensNow(editor, cursorOffset)
                    }
                }
            }
            updateQueue.queue(task)
        }
    }

}
