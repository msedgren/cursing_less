package org.cursing_less.color_shape

import com.intellij.openapi.diagnostic.thisLogger
import java.util.stream.Collectors
import com.intellij.openapi.util.Key
import com.jetbrains.rd.util.firstOrNull
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ColorAndShapeManager(
    private val colors: Collection<CursingColor>,
    private val shapes: Collection<CursingShape>
) {

    private val permutations =
        colors.stream().flatMap { color -> shapes.stream().map { CursingColorShape(color, it) } }
            .collect(Collectors.toUnmodifiableSet())

    private var consumed: MutableMap<Int, ConsumedData> = HashMap()
    private var offsetPreference: MutableMap<Int, CursingColorShape> = HashMap()

    private var characterState: MutableMap<Char, FreeCursingColorShape> = HashMap()

    companion object {
        private const val NAME = "CURSING_COLOR_AND_SHAPE_MANAGER"
        val KEY = Key.create<ColorAndShapeManager>(NAME)
    }

    @Synchronized
    fun consumedAtOffset(offset: Int):ConsumedData? {
        return consumed[offset]
    }

    @Synchronized
    fun consume(character: Char, offset: Int, endOffset: Int): CursingColorShape? {
        val lowerCaseCharacter = character.lowercaseChar()
        val existing =
            characterState.computeIfAbsent(lowerCaseCharacter) { FreeCursingColorShape(generatePermutations()) }
        val preference = offsetPreference[offset]
        val consumedThing = existing.consume(preference)
        if (consumedThing != null) {
            consumed[offset] = ConsumedData(character, consumedThing, offset, endOffset)
            offsetPreference[offset] = consumedThing
        }
        return consumedThing
    }

    @Synchronized
    fun freeAll() {
        this.consumed = HashMap()
        this.characterState = HashMap()
    }

    @Synchronized
    fun free(offset: Int) {
        val freed = consumed.remove(offset)
        if (freed != null) {
            val previouslyConsumed = characterState[freed.characterConsumed.lowercaseChar()]
            if (previouslyConsumed != null) {
                previouslyConsumed.returnFreed(freed.colorShape)
            } else {
                thisLogger().error("unable to free element at offset ${offset} as there is no existing state there!")
            }
        } else {
            thisLogger().error("unable to free element at offset ${offset}!")
        }
    }

    @Synchronized
    fun find(colorShape: CursingColorShape, character: Char): ConsumedData? {
        val lowerCaseCharacter = character.lowercaseChar()
        return consumed
            .filter { it.value.colorShape == colorShape && it.value.characterConsumed == lowerCaseCharacter }
            .firstOrNull()
            ?.value
    }

    private fun generatePermutations(): MutableList<CursingColorShape> {
        return ArrayList(permutations)
    }

    data class ConsumedData(
        val character: Char,
        val colorShape: CursingColorShape,
        val startOffset: Int,
        val endOffset: Int
    ) {
        val characterConsumed = character.lowercaseChar()
    }


    class FreeCursingColorShape(private val free: MutableList<CursingColorShape>) {

        init {
            free.shuffle()
        }

        fun consume(preference: CursingColorShape?): CursingColorShape? {
            return if (preference != null && free.remove(preference)) {
                preference
            } else if (free.isNotEmpty()) {
                val consumedThing = free.removeFirst()
                consumedThing
            } else {
                null
            }
        }

        fun returnFreed(freed: CursingColorShape) {
            free.add(freed)
        }
    }
}
