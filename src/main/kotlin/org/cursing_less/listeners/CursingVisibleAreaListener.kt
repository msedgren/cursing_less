package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import org.cursing_less.services.CursingMarkupService

class CursingVisibleAreaListener : VisibleAreaListener {

    override fun visibleAreaChanged(event: VisibleAreaEvent) {
        val range = event.editor.calculateVisibleRange()
        val difference = range.endOffset - range.startOffset
        val cursorOffset = range.startOffset + difference / 2

        val cursingMarkupService =
            ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokens(event.editor, cursorOffset)
    }

}