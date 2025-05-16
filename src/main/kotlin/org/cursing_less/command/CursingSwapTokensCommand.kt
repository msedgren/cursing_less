package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingSelectionService

/**
 * Command that swaps the content of two tokens identified by their color, shape, and character.
 */
data object CursingSwapTokensCommand : VoiceCommand {

    val cursingMarkupService = getApplication().getService(org.cursing_less.service.CursingMarkupService::class.java)

    // Get the service on-demand instead of during class initialization
    private val cursingSelectionService: CursingSelectionService
        get() = getApplication().getService(CursingSelectionService::class.java)

    override fun matches(command: String) = command == "curse_swap_tokens"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 6) {
            // Parameters should be: color1, shape1, char1, color2, shape2, char2
            val firstTokenParams = commandParameters.subList(0, 3)
            val secondTokenParams = commandParameters.subList(3, 6)

            // Find the first token
            val firstToken = cursingSelectionService.find(firstTokenParams, editor)
            // Find the second token
            val secondToken = cursingSelectionService.find(secondTokenParams, editor)

            if (firstToken != null && secondToken != null && firstToken != secondToken) {
                // Perform the swap on EDT
                withContext(Dispatchers.EDT) {
                    swapTokens(editor, firstToken, secondToken, project)
                }
                // Update the markup to reflect the changes

                cursingMarkupService.updateCursingTokensNow(editor, 0)
                return CursingCommandService.OkayResponse
            }
        }
        return CursingCommandService.BadResponse
    }

    private suspend fun swapTokens(
        editor: Editor,
        firstToken: ColorAndShapeManager.ConsumedData,
        secondToken: ColorAndShapeManager.ConsumedData,
        project: Project
    ) {
        val document = editor.document

        // Read the token text
        readAndWriteAction {
            // Get the token text
            val firstStart = firstToken.startOffset
            val firstEnd = firstToken.endOffset
            val secondStart = secondToken.startOffset
            val secondEnd = secondToken.endOffset

            // Get the selected text
            val firstText = document.getText(TextRange(firstStart, firstEnd))
            val secondText = document.getText(TextRange(secondStart, secondEnd))

            writeAction {
                WriteCommandAction.writeCommandAction(project)
                    .withName("Swap Selections")
                    .compute<Unit, Throwable> {
                        document.replaceString(secondStart, secondEnd, firstText)
                        document.replaceString(firstStart, firstEnd, secondText)
                    }
            }
        }
    }

}
