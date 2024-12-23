package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import org.cursing_less.services.CursingMarkupService

class CursingEditorListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val cursingMarkupService =
            ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateHighlightedTokens(event.editor, event.editor.caretModel.offset)
    }

}
