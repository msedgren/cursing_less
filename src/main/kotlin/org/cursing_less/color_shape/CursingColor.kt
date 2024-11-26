package org.cursing_less.color_shape

import com.intellij.ui.JBColor

class CursingColor(val name: String, val color: JBColor) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CursingColor

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "CursingColor(name='$name')"
    }
}