package org.cursing_less.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cursing_less.service.CursingMarkupService

class CursingVisibleAreaListener(private val coroutineScope: CoroutineScope) : VisibleAreaListener {

    override fun visibleAreaChanged(event: VisibleAreaEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            if (!event.editor.isDisposed) {
                val cursingMarkupService =
                    ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                cursingMarkupService.updateCursingTokens(event.editor, event.editor.caretModel.offset)
            }
        }
    }

}