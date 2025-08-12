package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingSelectionService

data object CursingTokenCommand : VoiceCommand {

    private val cursingSelectionService: CursingSelectionService by lazy {
        getApplication().getService(CursingSelectionService::class.java)
    }

    private val cursingMarkupService: CursingMarkupService by lazy {
        getApplication().getService(CursingMarkupService::class.java)
    }

    override fun matches(command: String) = command == "curse_token"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 1) {
            val mode = commandParameters[0]

            // Use readAction to safely access the editor's caret model
            val caretOffset = readAction { editor.caretModel.offset }

            // Find token that contains the caret
            val token = findTokenAtOffset(editor, caretOffset)

            if (token != null) {
                withContext(Dispatchers.EDT) {
                    cursingSelectionService
                        .handleMode(mode, token.startOffset, token.endOffset, editor, project)
                }
                return CursingCommandService.OkayResponse
            }
        }
        return CursingCommandService.BadResponse
    }

    private suspend fun findTokenAtOffset(editor: Editor, offset: Int): ColorAndShapeManager.ConsumedData? {
        return withContext(Dispatchers.EDT) { cursingMarkupService.getOrCreateAndSetEditorState(editor) }.let {
            it.colorAndShapeManager.consumedAtOffset(offset) ?: it.colorAndShapeManager.findTokenContainingOffset(offset)
        }
    }
}
