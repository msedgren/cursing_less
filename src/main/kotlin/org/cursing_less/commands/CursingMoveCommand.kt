package org.cursing_less.commands

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.services.CursingPreferenceService

class CursingMoveCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_to_location"

    override fun run(commandParameters: List<String>, project: Project, editor: Editor?): String {
        if (editor != null && commandParameters.size == 4) {
            val cursingPreferenceService = project.getService(CursingPreferenceService::class.java)
            val pre = commandParameters[0] == "pre"
            val colorToFind = cursingPreferenceService.encodeToColor(commandParameters[1].toInt())
            val shapeToFind = cursingPreferenceService.encodeToShape(commandParameters[2].toInt())
            val color = cursingPreferenceService.codedColors[colorToFind]
            val shape = cursingPreferenceService.codedShapes[shapeToFind]
            val character = commandParameters[3].firstOrNull()
            val colorAndShapeManager = editor.getUserData(ColorAndShapeManager.KEY)
            if (colorAndShapeManager != null && color != null && shape != null && character != null) {
                val colorShape = CursingColorShape(color, shape)
                colorAndShapeManager.find(colorShape, character)?.let { offset ->
                    editor.caretModel.moveToOffset(offset)
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                }
            }
        }
        return "OK"
    }
}