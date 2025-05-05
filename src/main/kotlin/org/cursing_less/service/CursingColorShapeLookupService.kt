package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.wm.IdeFocusManager
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.color_shape.CursingShape
import javax.swing.SwingUtilities

@Service(Service.Level.APP)
class CursingColorShapeLookupService {

    private val cursingPreferenceService = ApplicationManager.getApplication()
        .getService(CursingPreferenceService::class.java)

    fun parseToColorShape(colorString: String, shapeString: String): CursingColorShape? {
        val color = parseColor(colorString)
        val shape = parseShape(shapeString)

        return if (color == null || shape == null) {
            null
        } else {
            CursingColorShape(color, shape)
        }
    }

    fun parseColor(colorString: String): CursingColor? {
        return colorString.toIntOrNull()
            ?.let { cursingPreferenceService.codedColors[cursingPreferenceService.encodeToColor(it)] }
            ?: cursingPreferenceService.colors.find { it.name == colorString }
    }

    fun parseShape(shapeString: String): CursingShape? {
        return shapeString.toIntOrNull()
            ?.let { cursingPreferenceService.codedShapes[cursingPreferenceService.encodeToShape(it)] }
            ?: cursingPreferenceService.shapes.find { it.name == shapeString }
    }

    fun findConsumed(color: CursingColor, next: Boolean, editor: Editor): ColorAndShapeManager.ConsumedData? {
        val colorAndShapeManager = editor.getUserData(ColorAndShapeManager.KEY)
        return colorAndShapeManager?.find(color, next, editor.caretModel.offset)
    }

    fun findConsumed(
        colorShape: CursingColorShape,
        character: Char,
        editor: Editor
    ): ColorAndShapeManager.ConsumedData? {
        val colorAndShapeManager = editor.getUserData(ColorAndShapeManager.KEY)
        return colorAndShapeManager?.find(colorShape, character)
    }

    fun getFocusedEditor(): Editor? {
        val ideFocusManager = IdeFocusManager.findInstance()
        val focusedComponent = ideFocusManager.focusOwner ?: return null

        // Check each existing editor to determine if its UI component contains the focused component
        return EditorFactory.getInstance().allEditors
            .filter { !it.isDisposed }
            .find { SwingUtilities.isDescendingFrom(focusedComponent, it.contentComponent) }
    }
}