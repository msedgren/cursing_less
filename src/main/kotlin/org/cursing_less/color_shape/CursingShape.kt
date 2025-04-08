package org.cursing_less.color_shape

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicInteger

abstract class CursingShape(val name: String) {

    abstract fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes)

    companion object {
        val size = AtomicInteger(0)

        fun calculateSpace(inlay: Inlay<*>): Int {
            val textMetrics = inlay.editor.contentComponent.getFontMetrics(inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN))
            return (textMetrics.charWidth('X') * 0.7).toInt()
        }

        fun pullSizeCalculateIfOld(inlay: Inlay<*>): Int {
            size.getAndUpdate{ if (it == 0) calculateSpace(inlay) else it }
            return size.get()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CursingShape

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "CursingShape(name='$name')"
    }

    class Circle: CursingShape("circle") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val size = pullSizeCalculateIfOld(inlay)
            g.fillOval(targetRegion.x, targetRegion.y, size, size)
        }
    }

    class Square: CursingShape("square") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val size = pullSizeCalculateIfOld(inlay)
            g.fillRect(targetRegion.x, targetRegion.y, size, size)
        }
    }

    class Slash: CursingShape("slash") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val size = pullSizeCalculateIfOld(inlay)
            g.drawLine(targetRegion.x + 1, targetRegion.y + size - 1, targetRegion.x + size + 1, targetRegion.y)
            g.drawLine(targetRegion.x + 2, targetRegion.y + size - 1, targetRegion.x + size + 2, targetRegion.y)
        }
    }

    class BackSlash: CursingShape("back_slash") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val size = pullSizeCalculateIfOld(inlay)
            g.drawLine(targetRegion.x + size + 1, targetRegion.y + size - 1, targetRegion.x + 1, targetRegion.y)
            g.drawLine(targetRegion.x + size + 2, targetRegion.y + size - 1, targetRegion.x + 2, targetRegion.y)
        }
    }

    class Line: CursingShape("line") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val size = pullSizeCalculateIfOld(inlay)
            g.drawLine(targetRegion.x + 1, targetRegion.y + 3, targetRegion.x + size + 1, targetRegion.y + 3)
            g.drawLine(targetRegion.x + 1, targetRegion.y + 4, targetRegion.x + size + 1, targetRegion.y + 4)
        }
    }

    class X: CursingShape("x") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val size = pullSizeCalculateIfOld(inlay)
            g.drawLine(targetRegion.x + 1, targetRegion.y + size - 1, targetRegion.x + size + 1, targetRegion.y)
            g.drawLine(targetRegion.x + 2, targetRegion.y + size - 1, targetRegion.x + size + 2, targetRegion.y)

            g.drawLine(targetRegion.x + 1, targetRegion.y, targetRegion.x + size + 1, targetRegion.y + size - 1)
            g.drawLine(targetRegion.x + 2, targetRegion.y, targetRegion.x + size + 2, targetRegion.y + size - 1)
        }
    }
}
