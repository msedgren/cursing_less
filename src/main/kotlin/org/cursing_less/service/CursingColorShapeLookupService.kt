package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager.getApplication
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

    private val cursingPreferenceService: CursingPreferenceService by lazy {
        getApplication().getService(CursingPreferenceService::class.java)
    }

    private val cursingMarkupService: CursingMarkupService by lazy {
        getApplication().getService(CursingMarkupService::class.java)
    }

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
        val lowerCaseColorString = colorString.lowercase()
        return cursingPreferenceService.colors.find { it.name.lowercase() == lowerCaseColorString }
    }

    fun parseShape(shapeString: String): CursingShape? {
        val lowerCaseShapeString = shapeString.lowercase()
        return cursingPreferenceService.shapes.find { it.name.lowercase() == lowerCaseShapeString }
    }

    suspend fun findConsumed(color: CursingColor, next: Boolean, editor: Editor): ColorAndShapeManager.ConsumedData? {
        val colorAndShapeManager = cursingMarkupService.getOrCreateAndSetEditorState(editor).colorAndShapeManager
        return colorAndShapeManager.find(color, next, editor.caretModel.offset)
    }

    suspend fun findConsumed(
        colorShape: CursingColorShape,
        character: Char,
        editor: Editor
    ): ColorAndShapeManager.ConsumedData? {
        val colorAndShapeManager = cursingMarkupService.getOrCreateAndSetEditorState(editor).colorAndShapeManager
        return colorAndShapeManager.find(colorShape, character)
    }

    fun getFocusedEditor(): Editor? {
        val ideFocusManager = IdeFocusManager.findInstance()
        val focusedComponent = ideFocusManager.focusOwner ?: return null

        // Check each existing editor to determine if its UI component contains the focused component
        return EditorFactory.getInstance().allEditors
            .asSequence()
            .filter { !it.isDisposed }
            .filter { it.contentComponent.isShowing }
            .filter { it.contentComponent.isFocusOwner}
            .find { SwingUtilities.isDescendingFrom(focusedComponent, it.contentComponent) }
    }
}
