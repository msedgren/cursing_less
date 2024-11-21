package org.cursing_less.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.cursing_less.color_shape.ColorAndShapeInlayManager
import org.cursing_less.color_shape.CursingColorShape
import java.awt.Graphics
import java.awt.Rectangle

@Service(Service.Level.APP)
class CursingMarkupService {

    private val reg = Regex("[^|\\s+]\\S+")

    fun updateHighlightedTokens(editor: Editor, cursorOffset: Int) {
        val project = editor.project

        if (project != null) {
            val cursingPreferenceService = project.getService(CursingPreferenceService::class.java)
            val colorAndShapeInlayManager = editor.getOrCreateUserData(ColorAndShapeInlayManager.KEY) {
                thisLogger().info("Generating new ColorAndShapeInlayManager for editor")
                ColorAndShapeInlayManager(cursingPreferenceService.colors, cursingPreferenceService.shapes)
            }
            colorAndShapeInlayManager.freeAll()

            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength).forEach { it.dispose() }
            editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength).forEach { it.dispose() }

            if (file != null) {
                findTokensBasedOffOffset(
                    editor,
                    file,
                    cursorOffset,
                    colorAndShapeInlayManager
                ).forEach { (element, additionalOffset, cursingColorShape) ->
                    addColoredShapeAboveToken(editor, element, cursingColorShape, additionalOffset)
                }
                colorAndShapeInlayManager.updatePreferences()
            }
        }
    }

    private fun findTokensBasedOffOffset(
        editor: Editor,
        file: PsiFile,
        offset: Int,
        colorAndShapeInlayManager: ColorAndShapeInlayManager
    ): List<Triple<PsiElement, Int, CursingColorShape>> {
        val found = ArrayList<Triple<PsiElement, Int, CursingColorShape>>()
        var nextElement = findElementFromOffset(file, offset)
        var previousElement = if (nextElement != null) PsiTreeUtil.prevVisibleLeaf(nextElement) else null
        while ((nextElement != null && isAnyPartVisible(
                editor,
                nextElement
            )) || (previousElement != null && isAnyPartVisible(editor, previousElement))
        ) {
            if (nextElement != null && isAnyPartVisible(editor, nextElement)) {
                found.addAll(consumeIfVisible(nextElement, editor, colorAndShapeInlayManager))
                nextElement = PsiTreeUtil.nextVisibleLeaf(nextElement)
            }

            if (previousElement != null && isAnyPartVisible(editor, previousElement)) {
                found.addAll(consumeIfVisible(previousElement, editor, colorAndShapeInlayManager))
                previousElement = PsiTreeUtil.prevVisibleLeaf(previousElement)
            }
        }

        return found
    }

    private fun consumeIfVisible(
        element: PsiElement,
        editor: Editor,
        colorAndShapeInlayManager: ColorAndShapeInlayManager
    ): List<Triple<PsiElement, Int, CursingColorShape>> {
        if (!StringUtil.isEmptyOrSpaces(element.text) && isAnyPartVisible(editor, element)) {
            return findTokensWithin(element)
                .map { Pair(it, element.text.get(it)) }
                .mapNotNull { pair ->
                    val consumed = colorAndShapeInlayManager.consume(pair.second, pair.first)
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
        cursingColorShape: CursingColorShape,
        relativeOffset: Int
    ) {
        val offset = token.textRange.startOffset + relativeOffset
        editor.inlayModel.addBlockElement(offset, false, true, Int.MAX_VALUE, SpaceRenderer())
        editor.inlayModel.addInlineElement(offset, ColoredShapeRenderer(cursingColorShape))
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
        private val cursingColorShape: CursingColorShape
    ) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            return cursingColorShape.shape.calcWidthInPixels(inlay)
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.color = cursingColorShape.color
            cursingColorShape.shape.paint(inlay, g, targetRegion, textAttributes)
        }
    }
}
