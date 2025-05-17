package org.cursing_less.renderer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.color_shape.CursingShape
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.service.CursingPreferenceService
import java.awt.Graphics
import java.awt.Rectangle

class ColoredShapeRenderer(
    private val cursingColorShape: CursingColorShape,
    private val character: Char,
    private val offset: Int,
) : EditorCustomElementRenderer {

    private val preferenceService: CursingPreferenceService by lazy {
        ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
    }

    fun calculateSpace(inlay: Inlay<*>, character: Char): Int {
        val textMetrics =
            inlay.editor.contentComponent.getFontMetrics(inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN))
        return textMetrics.charWidth(character)
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return 1
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        g.color = cursingColorShape.color.color
        val paintable = when (cursingColorShape.shape) {
            CursingShape.BackSlash -> PaintableBackSlash
            CursingShape.Circle -> PaintableCircle
            CursingShape.Line -> PaintableLine
            CursingShape.Slash -> PaintableSlash
            CursingShape.Square -> PaintableSquare
            CursingShape.X -> PaintableX
        }

        val characterWidth = calculateSpace(inlay, character)
        val widthToUse = (characterWidth * preferenceService.scale).toInt()

        val editor = inlay.editor
        val existingInlayWidth =
            editor.inlayModel.getInlineElementsInRange(offset, offset)
                .filter { it.getUserData(INLAY_KEY) == null }
                .map { it.widthInPixels }
                .firstOrNull() ?: 0
        val adjustedX = if (existingInlayWidth > 0) {
            // Instead of directly using offsetToXY
            val logicalPos = editor.offsetToLogicalPosition(offset)
            val visualPos = editor.logicalToVisualPosition(logicalPos)
            val xy = editor.visualPositionToXY(visualPos)

            // Account for existing inlays at this position
            xy.x + existingInlayWidth + 3
        } else {
            targetRegion.x
        }

        paintable.paint(g, adjustedX, widthToUse, targetRegion.y, widthToUse)
    }


    interface PaintableCursingShape {
        fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
        }
    }

    data object PaintableCircle : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            g.drawOval(horizontalOffset, verticalOffset, widthToUse, heightToUse)
        }
    }

    data object PaintableSquare : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            g.fillRect(horizontalOffset, verticalOffset, widthToUse, heightToUse)
        }
    }

    data object PaintableSlash : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            g.drawLine(horizontalOffset, verticalOffset + heightToUse, horizontalOffset + widthToUse, verticalOffset)
            g.drawLine(horizontalOffset, verticalOffset + heightToUse, horizontalOffset + widthToUse, verticalOffset)
        }
    }

    data object PaintableBackSlash : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            g.drawLine(horizontalOffset + widthToUse, verticalOffset + heightToUse, horizontalOffset, verticalOffset)
            g.drawLine(horizontalOffset + widthToUse, verticalOffset + heightToUse, horizontalOffset, verticalOffset)
        }
    }

    data object PaintableLine : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            g.drawLine(horizontalOffset, verticalOffset + 2, horizontalOffset + widthToUse, verticalOffset + 2)
            g.drawLine(horizontalOffset, verticalOffset + 3, horizontalOffset + widthToUse, verticalOffset + 3)
        }
    }

    data object PaintableX : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            g.drawLine(horizontalOffset, verticalOffset + heightToUse, horizontalOffset + widthToUse, verticalOffset)
            g.drawLine(horizontalOffset, verticalOffset + heightToUse, horizontalOffset + widthToUse, verticalOffset)

            g.drawLine(horizontalOffset, verticalOffset, horizontalOffset + widthToUse, verticalOffset + heightToUse)
            g.drawLine(horizontalOffset, verticalOffset, horizontalOffset + widthToUse, verticalOffset + heightToUse)
        }
    }
}