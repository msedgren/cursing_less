package org.cursing_less.services

import com.intellij.openapi.components.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class SelectionService {
    var leftMouseSelected: Boolean
        get() = leftMouseSelectedAtomic.get()
        set(value) = leftMouseSelectedAtomic.set(value)

    private val leftMouseSelectedAtomic = AtomicBoolean(false)


    val makingSelection: Boolean
        get() = leftMouseSelectedAtomic.get()


}