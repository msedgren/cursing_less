package org.cursing_less.listeners

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

class CursingEditorFactoryListener  : EditorFactoryListener {
    // private val keyListener = CursingTypedActionHandler()

    override fun editorCreated(event: EditorFactoryEvent) {
        //val editor = event.editor
        //editor.contentComponent.addKeyListener(keyListener)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        //val editor = event.editor
        //editor.contentComponent.removeKeyListener(keyListener)
    }
}
