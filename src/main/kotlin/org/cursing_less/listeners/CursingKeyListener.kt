package org.cursing_less.listeners


import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

class CursingKeyListener : KeyListener, Disposable {

    companion object {
        private const val LEFT_ARROW = KeyEvent.VK_LEFT
        private const val RIGHT_ARROW = KeyEvent.VK_RIGHT
    }


    override fun keyTyped(e: KeyEvent) {
        thisLogger().warn("typed ${e.keyChar} ${e.keyCode}")
    }

    override fun keyPressed(e: KeyEvent) {
        thisLogger().warn("pressed ${e.keyChar} ${e.keyCode}")
    }

    override fun keyReleased(e: KeyEvent) {
        thisLogger().warn("released ${e.keyChar} ${e.keyCode}")
    }

    override fun dispose() {
    }
}
