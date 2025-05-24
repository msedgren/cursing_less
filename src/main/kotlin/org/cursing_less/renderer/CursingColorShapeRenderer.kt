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
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.GeneralPath
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


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
            CursingShape.Triangle -> PaintableTriangle
            CursingShape.Star -> PaintableStar
            CursingShape.Crescent -> PaintableCrescent
            CursingShape.Heart -> PaintableHeart
        }

        val characterWidth = calculateSpace(inlay, character)
        val widthToUse = (characterWidth * preferenceService.scale).toInt().let { if (it % 2 == 0) it else it - 1 }


        val editor = inlay.editor
        val existingInlayWidth =
            editor.inlayModel.getInlineElementsInRange(offset, offset)
                .asSequence()
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

    data object PaintableTriangle : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            val middleX = (horizontalOffset + widthToUse.toDouble() / 2).roundToInt()
            // Draw a simple triangle
            val xPoints = intArrayOf(
                middleX, // top point
                horizontalOffset, // bottom left
                horizontalOffset + widthToUse // bottom right
            )
            val yPoints = intArrayOf(
                verticalOffset, // top point
                verticalOffset + heightToUse, // bottom left
                verticalOffset + heightToUse // bottom right
            )
            g.drawPolygon(xPoints, yPoints, 3)
        }
    }

    data object PaintableStar : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            // Draw a simple 5-point star
            val outerRadius = widthToUse.toDouble() / 2
            val centerX = horizontalOffset + outerRadius
            val centerY = verticalOffset + (heightToUse.toDouble() / 2)
            val innerRadius = widthToUse.toDouble() / 4

            val gp = GeneralPath()
            var moved = false

            gp.moveTo(centerX, centerY)

            for (i in 0 until 10) {
                val outer = i % 2 == 0
                val radius = if (outer) outerRadius else innerRadius
                val angle = Math.PI / 2 + i * Math.PI / 5
                val x = (centerX + radius * cos(angle))
                val y = (centerY - radius * sin(angle))
                if(!moved) {
                    gp.moveTo(x, y)
                    moved = true
                } else {
                    gp.lineTo(x, y)
                }
            }
            gp.closePath()

            val g2 = g as Graphics2D

            g2.draw(gp)
        }
    }

    data object PaintableCrescent : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            val verticalOffsetDouble = verticalOffset.toDouble()
            val heightToUseDouble = heightToUse.toDouble()
            val horizontalOffsetDouble = horizontalOffset.toDouble()
            val widthToUseDouble = widthToUse.toDouble()
            val gp = GeneralPath()
            gp.moveTo(horizontalOffsetDouble + widthToUseDouble, verticalOffsetDouble)
            gp.quadTo(
                horizontalOffsetDouble, verticalOffsetDouble + (heightToUseDouble * 0.5),
                horizontalOffsetDouble + widthToUseDouble, verticalOffsetDouble + heightToUseDouble
            )
            gp.lineTo(horizontalOffsetDouble + widthToUseDouble + 2, verticalOffsetDouble + heightToUseDouble)
            gp.quadTo(
                horizontalOffsetDouble + (widthToUseDouble * 0.65), verticalOffsetDouble + (heightToUseDouble * 0.5),
                horizontalOffsetDouble + widthToUseDouble + 2, verticalOffsetDouble
            )
            gp.lineTo(horizontalOffsetDouble + widthToUseDouble, verticalOffsetDouble)
            gp.closePath()

            val g2 = g as Graphics2D

            g2.fill(gp)
        }
    }

    data object PaintableHeart : PaintableCursingShape {
        override fun paint(
            g: Graphics,
            horizontalOffset: Int,
            widthToUse: Int,
            verticalOffset: Int,
            heightToUse: Int,
        ) {
            val halfOfWidthToUse = (widthToUse/ 2)
            val halfOfHeightToUse = heightToUse / 2
            val quarterOfHeightToUse = heightToUse / 4
            g.fillOval(horizontalOffset, verticalOffset, halfOfWidthToUse, halfOfHeightToUse)
            g.fillOval(horizontalOffset + halfOfWidthToUse, verticalOffset, halfOfWidthToUse, halfOfHeightToUse)
            g.fillPolygon(
                intArrayOf(
                    horizontalOffset,
                    horizontalOffset + widthToUse,
                    horizontalOffset + halfOfWidthToUse
                ),
                intArrayOf(
                    verticalOffset + quarterOfHeightToUse,
                    verticalOffset + quarterOfHeightToUse,
                    verticalOffset + heightToUse),
                3
            )
            g.fillPolygon(
                intArrayOf(
                    horizontalOffset,
                    horizontalOffset + widthToUse,
                    horizontalOffset + halfOfWidthToUse
                ),
                intArrayOf(
                    verticalOffset + quarterOfHeightToUse + 1,
                    verticalOffset + quarterOfHeightToUse + 1,
                    verticalOffset + heightToUse),
                3
            )
        }
    }
}
