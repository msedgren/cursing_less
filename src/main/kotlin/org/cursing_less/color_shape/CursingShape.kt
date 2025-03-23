package org.cursing_less.color_shape

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics
import java.awt.Rectangle

abstract class CursingShape(val name: String) {

    abstract fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes)

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
            g.fillOval(targetRegion.x, targetRegion.y, 5, 5)
        }
    }

    class Square: CursingShape("square") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.fillRect(targetRegion.x, targetRegion.y, 5, 5)
        }
    }

    class Slash: CursingShape("slash") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.drawLine(targetRegion.x + 1, targetRegion.y + 5, targetRegion.x + 6, targetRegion.y)
        }
    }

    class BackSlash: CursingShape("back_slash") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.drawLine(targetRegion.x + 6, targetRegion.y, targetRegion.x + 1, targetRegion.y + 5)
        }
    }

    class Line: CursingShape("line") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.drawLine(targetRegion.x + 1, targetRegion.y + 3, targetRegion.x + 6, targetRegion.y + 3)
            g.drawLine(targetRegion.x + 1, targetRegion.y + 4, targetRegion.x + 6, targetRegion.y + 4)
        }
    }

    class X: CursingShape("x") {
        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.drawLine(targetRegion.x + 1, targetRegion.y + 5, targetRegion.x + 6, targetRegion.y)
            g.drawLine(targetRegion.x + 1, targetRegion.y, targetRegion.x + 6, targetRegion.y + 5)
        }
    }
}
