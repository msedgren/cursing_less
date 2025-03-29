package org.cursing_less.services

import com.intellij.openapi.components.Service
import com.intellij.ui.JBColor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingShape
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class CursingPreferenceService {

    val colors = listOf(
        CursingColor("red", JBColor.RED),
        CursingColor("blue", JBColor.BLUE),
        CursingColor("green", JBColor.GREEN),
        CursingColor("yellow", JBColor(Color(255, 212, 0), Color(255, 212, 0))),
        CursingColor("purple", JBColor(Color(191, 64, 191), Color(218, 112, 214)))
    )

    val shapes = listOf(
        CursingShape.Circle(),
        CursingShape.Square(),
        CursingShape.Slash(),
        CursingShape.BackSlash(),
        CursingShape.Line(),
        CursingShape.X()
    )

    val codedColors = colors.withIndex().associateTo(mutableMapOf()) { Pair(encodeToColor(it.index + 1), it.value) }
    val codedShapes = shapes.withIndex().associateTo(mutableMapOf()) { Pair(encodeToShape(it.index + 1), it.value) }

    fun encodeToColor(given: Int) = "color_$given"
    fun encodeToShape(given: Int) = "shape_$given"

    private val echoCommandsAtomic = AtomicBoolean(false)

    fun toggleEchoCommands() {
        echoCommandsAtomic.set(!echoCommandsAtomic.get())
    }

    val echoCommands: Boolean
        get() = echoCommandsAtomic.get()

}
