package org.cursing_less.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
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
import org.cursing_less.color_shape.CursingCodedColorShape
import java.awt.Graphics
import java.awt.Rectangle
import kotlin.collections.ArrayList

@Service(Service.Level.APP)
class CursingMarkupService {

    private val reg = Regex("[^|\\s+]\\S+")
    private val debouncer = Debouncer(250)


    companion object {
        private val INLAY_NAME = "CURSING_INLAY"
        val INLAY_KEY = Key.create<CursingCodedColorShape>(INLAY_NAME);

    }

    fun updateHighlightedTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce { updateHighlightedTokensNow(editor, cursorOffset) }
    }

    fun clearTokensAround(editor: Editor, cursorOffset: Int) {
        ApplicationManager.getApplication().invokeAndWait {
            if (!editor.isDisposed) {
                editor.inlayModel.getInlineElementsInRange(cursorOffset, cursorOffset + 1)
                    .filter { it.getUserData(INLAY_KEY) != null }
                    .forEach { it.dispose() }
            }
        }
    }

    private fun updateHighlightedTokensNow(editor: Editor, cursorOffset: Int) {
        val project = editor.project
        if (project != null && !editor.isDisposed) {
            val cursingPreferenceService = project.getService(CursingPreferenceService::class.java)
            val colorAndShapeManager = editor.getOrCreateUserData(ColorAndShapeManager.KEY) {
                thisLogger().info("Generating new ColorAndShapeInlayManager for editor")
                ColorAndShapeManager(
                    cursingPreferenceService.codedColors,
                    cursingPreferenceService.codedShapes
                )
            }
            colorAndShapeManager.freeAll()

            this.thisLogger().info("Removing old inlays")
            WriteAction.run<Exception> {
                editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
                    .filter { it.getUserData(INLAY_KEY) != null }
                    .forEach { it.dispose() }
            }

            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            if (file != null) {
                thisLogger().debug("looking for tokens")
                val found: List<Triple<PsiElement, Int, CursingCodedColorShape>> = findTokensBasedOffOffset(
                    editor,
                    file,
                    cursorOffset,
                    colorAndShapeManager
                )
                    .filter { !editor.inlayModel.hasInlineElementAt(it.first.textRange.startOffset + it.second) }

                this.thisLogger().debug("adding inlays")
                found.forEach { (element, additionalOffset, cursingColorShape) ->
                    addColoredShapeAboveToken(editor, element, cursingColorShape, additionalOffset)
                }
            }
        }
    }

    private fun findTokensBasedOffOffset(
        editor: Editor,
        file: PsiFile,
        offset: Int,
        colorAndShapeManager: ColorAndShapeManager
    ): List<Triple<PsiElement, Int, CursingCodedColorShape>> {
        val found = ArrayList<Triple<PsiElement, Int, CursingCodedColorShape>>()
        var nextElement = findElementFromOffset(file, offset)
        var previousElement = if (nextElement != null) PsiTreeUtil.prevVisibleLeaf(nextElement) else null
        while ((nextElement != null && isAnyPartVisible(
                editor,
                nextElement
            )) || (previousElement != null && isAnyPartVisible(editor, previousElement))
        ) {
            if (nextElement != null && isAnyPartVisible(editor, nextElement)) {
                found.addAll(consumeIfVisible(nextElement, editor, colorAndShapeManager))
                nextElement = PsiTreeUtil.nextVisibleLeaf(nextElement)
            }

            if (previousElement != null && isAnyPartVisible(editor, previousElement)) {
                found.addAll(consumeIfVisible(previousElement, editor, colorAndShapeManager))
                previousElement = PsiTreeUtil.prevVisibleLeaf(previousElement)
            }
        }

        return found
    }

    private fun consumeIfVisible(
        element: PsiElement,
        editor: Editor,
        colorAndShapeManager: ColorAndShapeManager
    ): List<Triple<PsiElement, Int, CursingCodedColorShape>> {
        if (!StringUtil.isEmptyOrSpaces(element.text) && isAnyPartVisible(editor, element)) {
            return findTokensWithin(element)
                .map { Pair(it, element.text.get(it)) }
                .mapNotNull { pair ->
                    val consumed = colorAndShapeManager.consume(pair.second, element.startOffset + pair.first)
                    if (consumed != null) Triple(element, pair.first, consumed) else null
                }
                .toList()
        }
        return emptyList()
    }

    fun findTokensWithin(element: PsiElement?): List<Int> {
        val text = if (element == null) "" else element.text

        return if (StringUtil.isEmptyOrSpaces(text)) {
            emptyList()
        } else {
            reg.findAll(text).iterator()
                .asSequence()
                .map { it.range.start }
                .toList()
        }
    }

    private fun findElementFromOffset(file: PsiFile, offset: Int): PsiElement? {
        var next = offset;
        var previous = offset;
        var element = file.findElementAt(offset);
        while (element == null && (previous > 0 || next < (file.endOffset - 1))) {
            if (previous > 0) {
                element = file.findElementAt(--previous)
            }
            if (element == null && next < (file.endOffset - 1)) {
                element = file.findElementAt(++next)
            }
        }
        return element;
    }

    private fun isAnyPartVisible(editor: Editor, element: PsiElement): Boolean {
        val visibleArea = editor.calculateVisibleRange()
        return visibleArea.contains(element.startOffset) || visibleArea.contains(element.endOffset)
    }

    private fun addColoredShapeAboveToken(
        editor: Editor,
        token: PsiElement,
        cursingCodedColorShape: CursingCodedColorShape,
        relativeOffset: Int
    ) {
        val offset = token.textRange.startOffset + relativeOffset
        val inlay = editor.inlayModel.addInlineElement(offset, ColoredShapeRenderer(cursingCodedColorShape))

        inlay?.putUserData(INLAY_KEY, cursingCodedColorShape)
    }

    class SpaceRenderer : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            return 1
        }

        override fun calcHeightInPixels(inlay: Inlay<*>): Int {
            return 1
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        }
    }


    class ColoredShapeRenderer(
        private val cursingCodedColorShape: CursingCodedColorShape
    ) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            return cursingCodedColorShape.shape.value.calcWidthInPixels(inlay)
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.color = cursingCodedColorShape.color.value.color
            cursingCodedColorShape.shape.value.paint(inlay, g, targetRegion, textAttributes)
        }
    }


    class Debouncer(private val delay: Int) {
        private val updateQueue = MergingUpdateQueue("cursing_less_debounce", delay, true, null)

        fun debounce(func: () -> Unit) {

            val task = object : Update("cursing_less_markup") {
                override fun run() {
                    ApplicationManager.getApplication().invokeLater(func)
                }
            }
            updateQueue.queue(task)
        }
    }

}
