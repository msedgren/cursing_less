package org.cursing_less.service

import com.intellij.openapi.components.Service
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class CursingUserInteractionService {
    private val leftMouseSelectedAtomic = AtomicBoolean(false)
    private val directionAtomic = AtomicReference(CursingDirectionState(CursingUserDirection.NONE, 0L))

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

    data class CursingDirectionState(val direction: CursingUserDirection, val timeInMs: Long)

}