package org.cursing_less.color_shape

import java.awt.Color

data class CursingColorShape(val color: Color, val shape: CursingShape) : Comparable<CursingColorShape> {
    override fun compareTo(other: CursingColorShape) = compareValuesBy(
        this, other,
        { it.color.rgb },
        { it.shape.name }
    )
}
