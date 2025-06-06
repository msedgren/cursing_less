package org.cursing_less.handler


import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.cursing_less.service.CursingDirectionState
import org.cursing_less.service.CursingUserDirection
import org.cursing_less.service.CursingUserInteractionService

class CursingEditorActionHandler(
    private val originalHandler: EditorActionHandler,
    private val direction: CursingUserDirection
) : EditorActionHandler() {

    private val userInteractionService: CursingUserInteractionService by lazy {
        ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java)
    }

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        userInteractionService.direction = CursingDirectionState(direction, System.currentTimeMillis())
        originalHandler.execute(editor, caret, dataContext)
    }
}
