package org.cursing_less.settings

import com.intellij.ui.ColorUtil
import com.intellij.ui.DarculaColors
import com.intellij.ui.JBColor
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingShape
import org.cursing_less.settings.CursingPreferenceState.ColorState.Companion.fromColors
import org.cursing_less.settings.CursingPreferenceState.ShapeState.Companion.fromShape
import java.awt.Color


/**
 * State class for storing Cursing Less plugin preferences.
 */
data class CursingPreferenceState(
    val colors: List<ColorState> = defaultColors,
    val shapes: List<ShapeState> = defaultShapes,
    val scale: Double = defaultScale,
    val tokenPattern: String = defaultTokenPattern,
    var echoCommands: Boolean = defaultEchoCommands
) {

    companion object {
        @Suppress("UseJBColor")
        val defaultColors: List<ColorState> = mutableListOf(
            fromColors("red", true, Color.red, DarculaColors.RED),
            fromColors("blue", true, Color.blue.brighter(), DarculaColors.BLUE.brighter()),
            fromColors("green", true, Color.green, Color(98, 150, 85)),
            fromColors("yellow", true, Color(255, 212, 0), Color(255, 212, 0)),
            fromColors("purple", true, Color(191, 64, 191), Color(218, 112, 214))
        )
        val defaultShapes: List<ShapeState> = mutableListOf(
            fromShape(CursingShape.Circle, true),
            fromShape(CursingShape.Square, true),
            fromShape(CursingShape.Slash, true),
            fromShape(CursingShape.BackSlash, true),
            fromShape(CursingShape.Line, true),
            fromShape(CursingShape.X, true)
        )
        const val defaultScale: Double = 0.7
        const val defaultTokenPattern: String =
            "([\\w]+)|([()]+)|([{}]+)|([<>]+)|([\\[\\]]+)|(\\?:)|(/\\*)|(\\*/)|([,\"'`:#])|([^\\w(){}<>\\[\\]\\s.]+)"
        const val defaultEchoCommands: Boolean = false
    }

    /**
     * Convert the state to a list of CursingColor objects
     */
    fun generateCursingColors(): List<CursingColor> {
        return colors
            .filter { it.enabled }
            .map {
                CursingColor(
                    it.name,
                    it.jbColor
                )
            }
    }

    /**
     * Convert the state to a list of CursingShape objects
     */
    fun generateCursingShapes(): List<CursingShape> {
        return shapes
            .filter { it.enabled }
            .mapNotNull { shapeState ->
                CursingShape::class.sealedSubclasses
                    .mapNotNull { it.objectInstance }
                    .firstOrNull { it.name == shapeState.name }
            }
    }

    /**
     * State class for storing color information
     */
    data class ColorState(val name: String, val enabled: Boolean, val lightColorHex: String, val darkColorHex: String) {
        val lightColor: Color = ColorUtil.fromHex(lightColorHex)
        val darkColor: Color = ColorUtil.fromHex(darkColorHex)
        val jbColor: JBColor = JBColor(lightColor, darkColor)

        companion object {
            fun fromColors(name: String, enabled: Boolean, lightColor: Color, darkColor: Color): ColorState {
                return ColorState(name, enabled, ColorUtil.toHex(lightColor), ColorUtil.toHex(darkColor))
            }
        }
    }

    /**
     * State class for storing shape information
     */
    data class ShapeState(val name: String, val enabled: Boolean) {
        companion object {
            fun fromShape(shape: CursingShape, enabled: Boolean): ShapeState {
                return ShapeState(shape.name, enabled)
            }
        }
    }
}
