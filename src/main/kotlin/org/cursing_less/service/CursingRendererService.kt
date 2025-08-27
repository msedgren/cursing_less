package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import java.awt.FontMetrics

@Service(Service.Level.APP)
class CursingRendererService {

    private val preferenceService: CursingPreferenceService by lazy {
        ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
    }

    fun calculateTextMetrics(editor: Editor): FontMetrics {
        return editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
    }

    fun calculateSpace(character: Char, textMetrics: FontMetrics): Pair<Int, Int> {
        return Pair(textMetrics.charWidth(character), textMetrics.height)
    }

    fun calculatesSpaceToUse(editor: Editor,
                             character: Char): Pair<Int, Int> {
        return calculatesSpaceToUse(editor, character, calculateTextMetrics(editor))
    }

    private fun calculatesSpaceToUse(editor: Editor,
                             character: Char,
                             textMetrics: FontMetrics): Pair<Int, Int> {
        val lineHeight = editor.lineHeight
        val (characterWidth, characterHeight) = calculateSpace(character, textMetrics)
        val widthToUse = (characterWidth * preferenceService.scale).toInt().let { if (it % 2 == 0) it else it - 1 }
        val heightToUse = (lineHeight - characterHeight).let { if (it % 2 == 0) it else it - 1 }
        val squareSizeToUse = minOf(widthToUse, heightToUse)
        return Pair(squareSizeToUse, squareSizeToUse)
    }

    fun calculateMinimumHeightNeeded(editor: Editor): Int {
        val textMetrics = calculateTextMetrics(editor)
        val (_, height) = calculateSpace('C', textMetrics)
        val lineHeight = editor.lineHeight
        return height + textMetrics.height + 2
    }
}