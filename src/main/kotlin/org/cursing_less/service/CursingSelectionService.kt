package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape

@Service(Service.Level.APP)
class CursingSelectionService {

    /**
     * Handles the mode selection for cursing commands.
     */
    suspend fun handleMode(mode: String, startOffset: Int, endOffset: Int, editor: Editor, project: Project) {
        when (mode) {
            "select" -> handleSelect(startOffset, endOffset, editor)
            "copy" -> handleCopy(startOffset, endOffset, editor)
            "cut" -> handleCut(startOffset, endOffset, editor, project)
            "clear" -> deleteText(startOffset, endOffset, editor, project)
        }
    }

    /**
     * Handles the select mode.
     */
    private fun handleSelect(startOffset: Int, endOffset: Int, editor: Editor) {
        select(startOffset, endOffset, editor)
    }

    /**
     * Handles the copy mode.
     */
    private fun handleCopy(startOffset: Int, endOffset: Int, editor: Editor) {
        select(startOffset, endOffset, editor)
        copySelectionToClipboard(editor)
    }

    /**
     * Handles the cut mode.
     */
    private suspend fun handleCut(startOffset: Int, endOffset: Int, editor: Editor, project: Project) {
        select(startOffset, endOffset, editor)
        copySelectionToClipboard(editor)
        deleteText(
            startOffset,
            endOffset,
            editor,
            project
        )
    }

    fun find(parameters: List<String>, editor: Editor): ColorAndShapeManager.ConsumedData? {
        require(parameters.size == 3) { "Invalid parameters count: ${parameters.size}" }
        return find(
            parameters[0].toInt(),
            parameters[1].toInt(),
            parameters[2].firstOrNull(),
            editor
        )

    }

    fun find(color: Int, shape: Int, character: Char?, editor: Editor): ColorAndShapeManager.ConsumedData? {
        val cursingColorShapeLookupService = ApplicationManager.getApplication()
            .getService(CursingColorShapeLookupService::class.java)
        val colorShape =
            cursingColorShapeLookupService.parseToColorShape(color, shape)

        return colorShape?.let {
            character?.let { cursingColorShapeLookupService.findConsumed(colorShape, character, editor) }
        }
    }

    /**
     * Finds text based on color shape and character.
     *
     * @param colorShape The color shape to look for
     * @param character The character to look for
     * @param editor The editor to operate on
     * @return The consumed data if found, null otherwise
     */
    fun find(
        colorShape: CursingColorShape,
        character: Char,
        editor: Editor
    ): ColorAndShapeManager.ConsumedData? {
        val cursingColorShapeLookupService = ApplicationManager.getApplication()
            .getService(CursingColorShapeLookupService::class.java)

        return cursingColorShapeLookupService.findConsumed(colorShape, character, editor)
    }

    /**
     * Selects text in editor between start and end offsets.
     *
     * @param startOffset The start offset of the selection
     * @param endOffset The end offset of the selection
     * @param editor The editor to operate on
     */
    fun select(startOffset: Int, endOffset: Int, editor: Editor) {
        editor.caretModel.moveToOffset(endOffset)
        editor.selectionModel.setSelection(startOffset, endOffset)
    }

    /**
     * Copies the current selection to clipboard.
     *
     * @param editor The editor to operate on
     */
    fun copySelectionToClipboard(editor: Editor) {
        editor.selectionModel.copySelectionToClipboard()
    }

    /**
     * Deletes the text.
     *
     * @param consumedData The data describing the selection
     * @param editor The editor to operate on
     * @param project The project context
     */
    suspend fun deleteText(startOffset: Int, endOffset: Int, editor: Editor, project: Project) {
        readAndWriteAction {
            val document = editor.document
            val writable = document.isWritable
            val cp = CommandProcessor.getInstance()

            writeAction {
                document.setReadOnly(false)
                try {
                    cp.executeCommand(
                        project,
                        { document.deleteString(startOffset, endOffset) },
                        "Delete",
                        "deleteGroup"
                    )
                } finally {
                    document.setReadOnly(!writable)
                }
            }
        }
    }
}