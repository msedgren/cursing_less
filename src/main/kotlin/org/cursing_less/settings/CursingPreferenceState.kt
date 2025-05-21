package org.cursing_less.settings

import com.intellij.ui.ColorUtil
import com.intellij.ui.DarculaColors
import com.intellij.ui.JBColor
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingShape
import org.cursing_less.settings.CursingPreferenceState.ColorState.Companion.fromColors
import org.cursing_less.settings.CursingPreferenceState.ShapeState.Companion.fromShape
import java.awt.Color


/**
 * State class for storing Cursing Less plugin preferences.
 */
data class CursingPreferenceState(
    @JvmField @OptionTag(converter = ColorStateConverter::class) val colors: List<ColorState> = defaultColors,
    @JvmField @OptionTag(converter = ShapeStateConverter::class) val shapes: List<ShapeState> = defaultShapes,
    @JvmField val scale: Double = defaultScale,
    @JvmField val tokenPattern: String = defaultTokenPattern
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
        // word characters, parens, braces, angles, square brackets, elvis, block comment start, block comment end,
        // command or " or ' or ` or : or #, and any other non-whitespace character that does not match the others
        const val defaultTokenPattern: String =
            "([\\w]+)|([()]+)|([{}]+)|([<>]+)|([\\[\\]]+)|(\\?:)|(/\\*)|(\\*/)|([,\"'`:#])|([^\\w(){}<>\\[\\]\\s.\"'`:#]+)"
    }

    /**
     * Convert the state to a list of CursingColor objects
     */
    fun generateCursingColors(): List<CursingColor> {
        return colors
            .asSequence()
            .filter { it.enabled }
            .map {
                CursingColor(
                    it.name,
                    it.generateJbColor()
                )
            }
            .toList()
    }

    /**
     * Convert the state to a list of CursingShape objects
     */
    fun generateCursingShapes(): List<CursingShape> {
        return shapes
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { shapeState ->
                CursingShape::class.sealedSubclasses
                    .mapNotNull { it.objectInstance }
                    .firstOrNull { it.name == shapeState.name }
            }
            .toList()
    }

    /**
     * State class for storing color information
     */
    data class ColorState(
        @JvmField val name: String = "",
        @JvmField val enabled: Boolean = false,
        @JvmField val lightColorHex: String = "",
        @JvmField val darkColorHex: String = ""
    ) {
        fun generateLightColor(): Color = ColorUtil.fromHex(lightColorHex)
        fun generateDarkColor(): Color = ColorUtil.fromHex(darkColorHex)
        fun generateJbColor(): JBColor = JBColor(generateLightColor(), generateDarkColor())

        companion object {
            fun fromColors(name: String, enabled: Boolean, lightColor: Color, darkColor: Color): ColorState {
                return ColorState(name, enabled, ColorUtil.toHex(lightColor), ColorUtil.toHex(darkColor))
            }
        }
    }

    class ColorStateConverter: Converter<List<ColorState>>() {
        override fun fromString(value: String): List<ColorState>? {
            return value.split(" ")
                .chunked(4)
                .map { ColorState(it[0], it[1].toBooleanStrict(), it[2], it[3]) }
        }

        override fun toString(value: List<ColorState>) =
            value.joinToString(" ") { "${it.name} ${it.enabled} ${it.lightColorHex} ${it.darkColorHex}" }

    }

    /**
     * State class for storing shape information
     */
    data class ShapeState(
        @JvmField val name: String = "",
        @JvmField val enabled: Boolean = false
    ) {
        companion object {
            fun fromShape(shape: CursingShape, enabled: Boolean): ShapeState {
                return ShapeState(shape.name, enabled)
            }
        }
    }

    class ShapeStateConverter: Converter<List<ShapeState>>() {
        override fun toString(value: List<ShapeState>): String? {
            return value.joinToString(" ") { "${it.name} ${it.enabled}" }
        }

        override fun fromString(value: String): List<ShapeState>? {
            return value.split(" ")
                .chunked(2)
                .map { ShapeState(it[0], it[1].toBooleanStrict()) }
        }
    }
}
