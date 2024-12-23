package org.cursing_less.color_shape

import com.intellij.openapi.diagnostic.thisLogger
import java.util.stream.Collectors
import com.intellij.openapi.util.Key
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ColorAndShapeManager(
    private val colors: Collection<CursingCoded<CursingColor>>,
    private val shapes: Collection<CursingCoded<CursingShape>>
) {

    private val permutations =
        colors.stream().flatMap { color -> shapes.stream().map { CursingCodedColorShape(color, it) } }
            .collect(Collectors.toUnmodifiableSet())

    private var consumed: MutableMap<Int, Pair<Char, CursingCodedColorShape>> = HashMap()
    private var offsetPreference: MutableMap<Int, CursingCodedColorShape> = HashMap()

    private var state: MutableMap<Char, FreeCursingColorShape> = HashMap()

    companion object {
        private const val NAME = "CURSING_COLOR_AND_SHAPE_MANAGER"
        val KEY = Key.create<ColorAndShapeManager>(NAME)
    }

    fun consumedAtOffset(offset: Int): Pair<Char, CursingCodedColorShape>? {
        return consumed[offset]
    }

    fun consume(character: Char, offset: Int): CursingCodedColorShape? {
        val existing = state.computeIfAbsent(character) { FreeCursingColorShape(generatePermutations()) }
        val preference = offsetPreference[offset]
        val consumedThing = existing.consumeGivenOrRandomFree(preference)
        if (consumedThing != null) {
            // thisLogger().trace("Consumed ${consumedThing} at ${offset} for ${character}")
            consumed[offset] = Pair(character, consumedThing)
            offsetPreference[offset] = consumedThing
        } else {
            // thisLogger().debug("Failed to consume anything at ${offset} for ${character}")
        }
        return consumedThing
    }

    fun freeAll() {
        this.consumed = HashMap()
        this.state = HashMap()
    }

    fun free(offset: Int) {
        val freed = consumed.remove(offset)
        if (freed != null) {
            thisLogger().trace("Freed ${freed} at ${offset}")
            val existing = state[freed.first]
            if (existing != null) {
                existing.returnFreed(freed.second)
            } else {
                thisLogger().error("unable to free element at offset ${offset} as there is no existing state there!")
            }
        } else {
            thisLogger().error("unable to free element at offset ${offset}!")
        }
    }

    private fun generatePermutations(): MutableList<CursingCodedColorShape> {
        return ArrayList(permutations)
    }


    class FreeCursingColorShape(private val free: MutableList<CursingCodedColorShape>) {

        init {
            free.shuffle()
        }

        fun consumeGivenOrRandomFree(preference: CursingCodedColorShape?): CursingCodedColorShape? {
            return if (preference != null && free.remove(preference)) {
                preference
            } else if (free.isNotEmpty()) {
                val consumedThing = free.removeFirst()
                consumedThing
            } else {
                null
            }
        }

        fun returnFreed(freed: CursingCodedColorShape) {
            free.add(freed)
        }
    }
}
