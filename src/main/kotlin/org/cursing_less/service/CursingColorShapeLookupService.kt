package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.wm.IdeFocusManager
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingColorShape
import javax.swing.SwingUtilities

@Service(Service.Level.APP)
class CursingColorShapeLookupService {

    fun parseToColorShape(colorString: String, shapeString: String): CursingColorShape? {
        val cursingPreferenceService = ApplicationManager.getApplication()
            .getService(CursingPreferenceService::class.java)

        val colorToFind = cursingPreferenceService.encodeToColor(colorString.toInt())
        val shapeToFind = cursingPreferenceService.encodeToShape(shapeString.toInt())
        val color = cursingPreferenceService.codedColors[colorToFind]
        val shape = cursingPreferenceService.codedShapes[shapeToFind]

        if (color == null || shape == null) {
            return null
        } else {
            return CursingColorShape(color, shape)
        }
    }

    fun parseColor(colorString: String): CursingColor? {
        val cursingPreferenceService = ApplicationManager.getApplication()
            .getService(CursingPreferenceService::class.java)

        val colorToFind = cursingPreferenceService.encodeToColor(colorString.toInt())
        return cursingPreferenceService.codedColors[colorToFind]
    }

    suspend fun findConsumed(color: CursingColor, next: Boolean, editor: Editor): ColorAndShapeManager.ConsumedData? {
        val colorAndShapeManager = editor.getUserData(ColorAndShapeManager.KEY)
        return colorAndShapeManager?.find(color, next, editor.caretModel.offset)
    }

    suspend fun findConsumed(colorShape: CursingColorShape, character: Char, editor: Editor): ColorAndShapeManager.ConsumedData? {
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