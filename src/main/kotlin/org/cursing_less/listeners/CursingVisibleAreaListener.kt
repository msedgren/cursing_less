package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.ScrollRequestListener
import org.cursing_less.services.CursingMarkupService
import javax.swing.plaf.basic.BasicSliderUI.ScrollListener

class CursingVisibleAreaListener : VisibleAreaListener {

    override fun visibleAreaChanged(event: VisibleAreaEvent) {
        val range = event.editor.calculateVisibleRange()
        val difference = range.endOffset - range.startOffset
        val cursorOffset = range.startOffset + difference / 2

        val cursingMarkupService =
            ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateHighlightedTokens(event.editor, cursorOffset)
    }

}