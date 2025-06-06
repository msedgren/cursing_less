package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape

@Service(Service.Level.APP)
class CursingSelectionService {

    private val cursingColorShapeLookupService: CursingColorShapeLookupService by lazy {
        ApplicationManager.getApplication().getService(CursingColorShapeLookupService::class.java)
    }

    /**
     * Handles the mode selection for cursing commands.
     */
    suspend fun handleMode(mode: String, startOffset: Int, endOffset: Int, editor: Editor, project: Project) {
        when (mode) {
            "select" -> handleSelect(startOffset, endOffset, editor)
            "copy" -> handleCopy(startOffset, endOffset, editor)
            "cut" -> handleCut(startOffset, endOffset, editor, project)
            "clear" -> handleClear(startOffset, endOffset, editor, project)
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

    private suspend fun handleClear(startOffset: Int, endOffset: Int, editor: Editor, project: Project) {
        editor.caretModel.moveToOffset(endOffset)
        deleteText(
            startOffset,
            endOffset,
            editor,
            project
        )
    }

    fun find(parameters: List<String>, editor: Editor): ColorAndShapeManager.ConsumedData? {
        require(parameters.size == 3) { "Invalid parameters count: ${parameters.size}" }
        val colorShape = cursingColorShapeLookupService.parseToColorShape(parameters[0], parameters[1])
        val character = parameters[2].firstOrNull()

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
     * @param startOffset the beginning offset of the text to delete
     * @param endOffset the ending offset of the text to delete
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