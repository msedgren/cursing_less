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
     * Finds and selects text based on color shape and character.
     * 
     * @param colorShape The color shape to look for
     * @param character The character to look for
     * @param editor The editor to operate on
     * @return The consumed data if found, null otherwise
     */
    suspend fun findAndSelect(colorShape: CursingColorShape, character: Char, editor: Editor): ColorAndShapeManager.ConsumedData? {
        val cursingColorShapeLookupService = ApplicationManager.getApplication()
            .getService(CursingColorShapeLookupService::class.java)
        
        val consumedData = cursingColorShapeLookupService.findConsumed(colorShape, character, editor)
        
        withContext(Dispatchers.EDT) {
            if (consumedData != null) {
                editor.caretModel.moveToOffset(consumedData.endOffset)
                editor.selectionModel.setSelection(consumedData.startOffset, consumedData.endOffset)
            }
        }
        
        return consumedData
    }
    
    /**
     * Copies the current selection to clipboard.
     * 
     * @param editor The editor to operate on
     */
    suspend fun copySelectionToClipboard(editor: Editor) {
        withContext(Dispatchers.EDT) {
            editor.selectionModel.copySelectionToClipboard()
        }
    }
    
    /**
     * Cuts (deletes) the selected text.
     * 
     * @param consumedData The data describing the selection
     * @param editor The editor to operate on
     * @param project The project context
     */
    suspend fun cutSelectedText(consumedData: ColorAndShapeManager.ConsumedData, editor: Editor, project: Project) {
        readAndWriteAction {
            val document = editor.document
            val writable = document.isWritable
            val cp = CommandProcessor.getInstance()
            
            writeAction {
                document.setReadOnly(false)
                try {
                    cp.executeCommand(
                        project,
                        { document.deleteString(consumedData.startOffset, consumedData.endOffset) },
                        "Cut",
                        "cutGroup"
                    )
                } finally {
                    document.setReadOnly(!writable)
                }
            }
        }
    }
}