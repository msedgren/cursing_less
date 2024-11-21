package org.cursing_less.color_shape

import java.awt.Color
import java.util.*
import java.util.stream.Collectors
import com.intellij.openapi.util.Key
import kotlin.collections.HashMap

class ColorAndShapeInlayManager(
    val colors: Set<Color>,
    val shapes: Set<CursingShape>
) {

    private val permutations = colors.stream()
        .flatMap { color -> shapes.stream().map { CursingColorShape(color, it) } }
        .collect(Collectors.toUnmodifiableSet())

    private val state: MutableMap<Char, FreeAndConsumed> = HashMap()
    private val offsetPreference: MutableMap<Int, CursingColorShape> = HashMap()

    companion object {
        val NAME = "ColorAndShapeInlayManager"
        val KEY = Key.create<ColorAndShapeInlayManager>(NAME);
    }

    fun consume(character: Char, offset: Int): CursingColorShape? {
        synchronized(this) {
            val existing = state.computeIfAbsent(character, { FreeAndConsumed(generatePermutations(), HashSet()) })
            return if (existing.free.isEmpty()) {
                null
            } else {
                val preference = offsetPreference[offset]
                val preferenceFree = preference != null && existing.free.contains(preference)
                val consumed = if (preferenceFree) preference!! else consumeFirstFree(existing)
                existing.free.remove(consumed)
                existing.consumed.add(CursingColorShapeOffset(consumed, offset))
                return consumed
            }
        }
    }

    fun freeAll() {
        synchronized(this) {
            state.clear()
        }
    }

    fun updatePreferences() {
        offsetPreference.clear()
        state.values.forEach { freeAndConsumed ->
            freeAndConsumed.consumed.forEach {
                offsetPreference[it.offset] = it.cursingColorShape
            }
        }
    }

    fun consumeFirstFree(existing: FreeAndConsumed) = existing.free.stream().findFirst().orElseThrow()

    private fun generatePermutations(): MutableSet<CursingColorShape> {
        return HashSet(permutations)
    }


    class FreeAndConsumed(
        val free: MutableSet<CursingColorShape>,
        val consumed: MutableSet<CursingColorShapeOffset>
    ) {
        private val freeList = ArrayList(free)

        consumeRandomFree()
        {

        }
    }
}
