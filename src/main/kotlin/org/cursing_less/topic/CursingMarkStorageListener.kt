package org.cursing_less.topic

import com.intellij.util.messages.Topic

interface CursingMarkStorageListener {

    fun onStorageChanged()

    companion object {
        val TOPIC = Topic.create("Cursing Mark Storage Changes", CursingMarkStorageListener::class.java)
    }
}
