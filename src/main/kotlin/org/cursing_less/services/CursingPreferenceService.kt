package org.cursing_less.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.JBColor
import org.cursing_less.color_shape.CursingCoded
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingShape
import java.awt.Color

@Service(Service.Level.PROJECT)
class CursingPreferenceService() {

    private val colors = listOf(
        CursingColor("red", JBColor.RED),
        CursingColor("blue", JBColor.BLUE),
        CursingColor("green", JBColor.GREEN),
        CursingColor("yellow", JBColor(Color(255,212,0), Color(255,212,0))),
        CursingColor("pink", JBColor.PINK),
        CursingColor("purple", JBColor(Color(191, 64, 191), Color(218, 112, 214)))
    )

    private val shapes = listOf(
        CursingShape.Circle(),
        CursingShape.Square(),
        CursingShape.Slash(),
        CursingShape.BackSlash(),
        CursingShape.Line()
    )

    val codedColors = colors.withIndex().map { CursingCoded("color_${it.index}", it.value) }
    val codedShapes = shapes.withIndex().map { CursingCoded("shape_${it.index}", it.value) }


    init {
        thisLogger().info("Initialized CursingPreferenceService")
    }
}
