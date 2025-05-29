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

    private val cursingMarkupService: CursingMarkupService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
    }

    override fun visibleAreaChanged(event: VisibleAreaEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            if (!event.editor.isDisposed) {
                cursingMarkupService.updateCursingTokens(event.editor, event.editor.caretModel.offset)
            }
        }
    }

}