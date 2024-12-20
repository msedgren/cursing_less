package org.cursing_less.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
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
        private const val INLAY_NAME = "CURSING_INLAY"
        val INLAY_KEY = Key.create<CursingCodedColorShape>(INLAY_NAME)
    }

    fun updateHighlightedTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce { updateHighlightedTokensNow(editor, cursorOffset) }
    }

    private fun updateHighlightedTokensNow(editor: Editor, cursorOffset: Int) {
        val project = editor.project
        if (project != null && !editor.isDisposed) {
            val colorAndShapeManager = pullColorAndShapeManager(editor, project)
            colorAndShapeManager.freeAll()
            val existingInlays = pullExistingInlaysByOffset(editor)

            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            if (file != null) {
                findTokensBasedOffOffset(editor, file, cursorOffset, colorAndShapeManager)
                    .forEach { (offset, cursingColorShape) ->
                        val existing = existingInlays[offset]
                        if (existing?.second == cursingColorShape) {
                            // thisLogger().trace("Already showing ${cursingColorShape} at ${offset}")
                            existingInlays.remove(offset)
                            existing.first.repaint()
                        } else {
                            //thisLogger().trace("Showing ${cursingColorShape} at ${offset}")
                            addColoredShapeAboveToken(editor, cursingColorShape, offset)
                        }
                    }

                existingInlays.forEach { (_, pair) ->
                    // thisLogger().trace("Removing ${pair.second} at ${pair.first.offset}")
                    pair.first.dispose()
                }
            }
        }
    }

    private fun pullExistingInlaysByOffset(editor: Editor): MutableMap<Int, Pair<Inlay<*>, CursingCodedColorShape>> {
        return editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength - 1)
            .map { it.getUserData(INLAY_KEY)?.let { data -> Pair(it, data) } }
            .filterNotNull()
            .associateTo(mutableMapOf()) { Pair(it.first.offset, it) }
    }


    private fun pullColorAndShapeManager(editor: Editor, project: Project): ColorAndShapeManager {
        val cursingPreferenceService = project.getService(CursingPreferenceService::class.java)
        return editor.getOrCreateUserData(ColorAndShapeManager.KEY) {
            thisLogger().info("Generating new ColorAndShapeInlayManager for editor")
            ColorAndShapeManager(cursingPreferenceService.codedColors, cursingPreferenceService.codedShapes)
        }
    }

    private fun findTokensBasedOffOffset(
        editor: Editor,
        file: PsiFile,
        offset: Int,
        colorAndShapeManager: ColorAndShapeManager
    ):
            List<Pair<Int, CursingCodedColorShape>> {
        val found = ArrayList<Pair<Int, CursingCodedColorShape>>()
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

    private fun consumeIfVisible(element: PsiElement, editor: Editor, colorAndShapeManager: ColorAndShapeManager):
            List<Pair<Int, CursingCodedColorShape>> {
        if (!StringUtil.isEmptyOrSpaces(element.text) && isAnyPartVisible(editor, element)) {
            return findTokensWithin(element)
                .map { Pair(it, element.text[it]) }
                .mapNotNull { pair ->
                    val offset = element.startOffset + pair.first
                    val consumed = colorAndShapeManager.consume(pair.second, offset)
                    if (consumed != null) Pair(offset, consumed) else null
                }
                .toList()
        }
        return emptyList()
    }

    private fun findTokensWithin(element: PsiElement?): List<Int> {
        val text = if (element == null) "" else element.text

        return if (StringUtil.isEmptyOrSpaces(text)) {
            emptyList()
        } else {
            reg.findAll(text).iterator()
                .asSequence()
                .map { it.range.first }
                .toList()
        }
    }

    private fun findElementFromOffset(file: PsiFile, offset: Int): PsiElement? {
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

    private fun isAnyPartVisible(editor: Editor, element: PsiElement): Boolean {
        val visibleArea = editor.calculateVisibleRange()
        return visibleArea.contains(element.startOffset) || visibleArea.contains(element.endOffset)
    }

    private fun addColoredShapeAboveToken(
        editor: Editor,
        cursingCodedColorShape: CursingCodedColorShape,
        offset: Int
    ) {
        editor.inlayModel.addInlineElement(offset, ColoredShapeRenderer(cursingCodedColorShape))
            ?.putUserData(INLAY_KEY, cursingCodedColorShape)
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
