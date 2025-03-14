package org.cursing_less.commands

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.services.CursingPreferenceService

class CursingMoveCommand : VoiceCommand {

    override fun matches(command: String) = command == "cursing_move"


    override fun run(commandParameters: List<String>, project: Project, editor: Editor?): String {
        if (editor != null && commandParameters.size >= 2) {
            val cursingPreferenceService = project.getService(CursingPreferenceService::class.java)
            val color = cursingPreferenceService.codedColors.find { it.code == commandParameters[0] }
            val shape = cursingPreferenceService.codedShapes.find { it.code == commandParameters[1] }
            val colorAndShapeManager = editor.getUserData(ColorAndShapeManager.KEY)
            if (colorAndShapeManager != null && color != null && shape != null) {
                colorAndShapeManager.find(color.value, shape.value)?.let { offset ->
                    editor.caretModel.moveToOffset(offset)
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                }
            }
        }
        return "OK"
    }
}