package org.cursing_less.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.wm.IdeFrame

internal class CursingApplicationActivationListener : ApplicationActivationListener {

    var caretListener: CursingCaretListener? = null

    fun cleanupListeners() {
        if (caretListener != null) {
            EditorFactory.getInstance().eventMulticaster.removeCaretListener(caretListener!!)
            caretListener = null
        }
    }

    override fun applicationActivated(ideFrame: IdeFrame) {
        thisLogger().info("Exiting dumb mode and now setting up cursing listeners...")
        caretListener = CursingCaretListener()
        EditorFactory.getInstance().eventMulticaster.addCaretListener(caretListener!!, DoNothingDisposable())
    }

    override fun applicationDeactivated(ideFrame: IdeFrame) {
        cleanupListeners()
    }

    class DoNothingDisposable : Disposable.Default {
    }


}
