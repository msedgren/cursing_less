package org.cursing_less.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape

@Service(Service.Level.APP)
class CursingLookupService {

    fun lookup(colorString: String, shapeString: String): CursingColorShape? {
        val cursingPreferenceService = ApplicationManager.getApplication()
            .getService(CursingPreferenceService::class.java)

        val colorToFind = cursingPreferenceService.encodeToColor(colorString.toInt())
        val shapeToFind = cursingPreferenceService.encodeToShape(shapeString.toInt())
        val color = cursingPreferenceService.codedColors[colorToFind]
        val shape = cursingPreferenceService.codedShapes[shapeToFind]

        if (color == null || shape == null) {
            return null;
        } else {
            return CursingColorShape(color, shape)
        }
    }

    fun lookup(colorShape: CursingColorShape, character: Char, editor: Editor): ColorAndShapeManager.ConsumedData? {
        val colorAndShapeManager = editor.getUserData(ColorAndShapeManager.KEY)
        return colorAndShapeManager?.find(colorShape, character)
            ?: EditorFactory.getInstance().allEditors
                .filter { it.contentComponent.isShowing }
                .mapNotNull { it.getUserData(ColorAndShapeManager.KEY) }
                .firstNotNullOfOrNull { it.find(colorShape, character) }

    }
}