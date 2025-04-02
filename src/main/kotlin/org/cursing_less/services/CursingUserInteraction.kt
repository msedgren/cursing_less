package org.cursing_less.services

import com.intellij.openapi.components.Service
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class CursingUserInteraction {
    private val leftMouseSelectedAtomic = AtomicBoolean(false)
    private val directionAtomic = AtomicReference(CursingDirectionState(CursingUserDirection.NONE, false,0L))

    var leftMouseSelected: Boolean
        get() = leftMouseSelectedAtomic.get()
        set(value) = leftMouseSelectedAtomic.set(value)

    val makingSelection: Boolean
        get() = leftMouseSelectedAtomic.get()

    var direction: CursingDirectionState
        get() = directionAtomic.get()
        set(value) = directionAtomic.set(value)


    enum class CursingUserDirection {
        LEFT, RIGHT, NONE
    }

    data class CursingDirectionState(val direction: CursingUserDirection, val pressed: Boolean, val timeInMs: Long)

}