package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService
import org.cursing_less.service.CursingMarkupService

/**
 * Command to paste content from a given numeric mark into the editor at the current cursor position.
 */
data object CursingPasteMarkCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_paste_mark"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        // Validate input parameters
        val markNumber = pullMark(commandParameters, editor)
        if (markNumber == null) {
            return CursingCommandService.BadResponse
        }

        // Get the marked text
        val markedText = getMarkedText(markNumber)
        if (markedText == null) {
            return CursingCommandService.BadResponse
        }

        // Insert the marked text at the current cursor position(s) on EDT
        return withContext(Dispatchers.EDT) {
            // Capture caret data
            val caretData = captureCaretData(editor!!)

            // Execute the command to insert text
            insertTextAtCarets(project, editor, caretData, markedText)

            // Update markup
            updateMarkup(editor)

            CursingCommandService.OkayResponse
        }
    }

    /**
     * Validates the input parameters for the command and returns the mark.
     *
     * @param commandParameters The parameters passed to the command
     * @param editor The editor instance
     * @return the mark
     */
    private fun pullMark(commandParameters: List<String>, editor: Editor?): Int? {
        if (editor == null || commandParameters.isEmpty()) {
            return null
        }

        val markNumber = commandParameters[0].toIntOrNull()
        if (markNumber == null || markNumber < 1) {
            return null
        }

        return markNumber
    }

    /**
     * Retrieves the marked text from the storage service.
     *
     * @param markNumber The mark number to retrieve
     * @return The marked text, or null if not found or empty
     */
    private fun getMarkedText(markNumber: Int): String? {
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        val markedText = markStorageService.getMarkedText(markNumber)
        return if (markedText.isNullOrEmpty()) null else markedText
    }

    /**
     * Captures the current carets and their positions.
     *
     * @param editor The editor instance
     * @return List of caret data reversed.
     */
    private suspend fun captureCaretData(editor: Editor): List<CaretData> {
        return readAction {
            editor.caretModel.allCarets
                .map { caret ->
                    CaretData(caret, caret.offset)
                }
                .reversed()
        }
    }

    /**
     * Inserts text at all caret positions.
     *
     * @param project The project instance
     * @param editor The editor instance
     * @param caretData The list of caret data
     * @param markedText The text to insert
     */
    private fun insertTextAtCarets(
        project: Project,
        editor: Editor,
        caretData: List<CaretData>,
        markedText: String
    ) {
        val cp = CommandProcessor.getInstance()
        cp.executeCommand(
            project,
            {
                ApplicationManager.getApplication().runWriteAction {
                    val document = editor.document

                    caretData.forEach { data ->
                        // Use replaceString instead of insertString to ensure text is inserted at the correct position
                        document.replaceString(data.offset, data.offset, markedText)
                        // Update the caret position to be after the inserted text
                        data.caret.moveToOffset(data.offset + markedText.length)
                    }
                }
            },
            "Paste Mark",
            "pasteMarkGroup"
        )
    }

    /**
     * Updates the markup to reflect the changes.
     *
     * @param editor The editor instance
     */
    private suspend fun updateMarkup(editor: Editor) {
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
    }

    /**
     * Helper class to store a caret and its offset.
     */
    private data class CaretData(val caret: Caret, val offset: Int)
}
