package org.cursing_less.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.JBColor
import org.cursing_less.MyBundle
import org.cursing_less.color_shape.CursingShape

@Service(Service.Level.PROJECT)
class CursingPreferenceService() {

    val colors = setOf(
        JBColor.RED,
        JBColor.BLUE,
        JBColor.GREEN,
        JBColor.YELLOW,
        JBColor.foreground(),
        JBColor.PINK
    )

    val shapes = setOf(
        CursingShape.Circle(),
        CursingShape.Square(),
        CursingShape.Slash(),
        CursingShape.BackSlash(),
        CursingShape.Line()
    )

    init {
        thisLogger().info(MyBundle.message("preferenceServiceInitialization"))
    }
}
