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
    fun consumedAtOffset(offset: Int): ConsumedData? {
        return consumed[offset]
    }

    @Synchronized
    fun consume(offset: Int, text: String): CursingColorShape? {
        require(text.isNotEmpty()) { "text must not be empty!" }

        if (consumed.contains(offset)) {
            thisLogger().error("Attempted to consume at offset $offset but it has already been consumed!")
            return null
        }

        val character = text[0]
        val lowerCaseCharacter = character.lowercaseChar()
        // pull the object containing free colors and shapr for the given character
        val existing =
            characterState.computeIfAbsent(lowerCaseCharacter) { FreeCursingColorShape(generatePermutations()) }
        // get the preference (what it was previously set to) at that offset.
        val preference = offsetPreference[offset]
        // attempt to consume
        val consumedThing = existing.consume(preference)
        var textToConsume = text
        if (consumedThing != null) {
            // Check for overlapping consumed data and alter the end offset accordingly.
            consumed = consumed.mapValues {
                val startOffsetOverlaps = offset >= it.value.startOffset && offset < it.value.endOffset
                val currentEnd = offset + textToConsume.length
                //done here so we don't need to loop twice.
                val endOffsetOverlaps = currentEnd >= it.value.startOffset && currentEnd < it.value.endOffset
                if (endOffsetOverlaps && !startOffsetOverlaps) {
                    textToConsume = textToConsume.substring(0, textToConsume.length - (currentEnd - it.value.startOffset)).trimEnd()
                }
                if (startOffsetOverlaps) {
                    if (endOffsetOverlaps) {
                        textToConsume = textToConsume +
                                it.value.consumedText.substring(currentEnd - it.value.startOffset,  it.value.consumedText.length).trimEnd()
                    }
                    it.value.copy(consumedText = it.value.consumedText.substring(0, offset - it.value.startOffset).trimEnd())
                } else {
                    it.value
                }
            }.toMutableMap()

            consumed[offset] =
                ConsumedData(consumedThing, offset, textToConsume, text)
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
    fun find(color: CursingColor, next: Boolean, offset: Int): ConsumedData? {
        return consumed
            .toSortedMap({ a, b ->
                Math.abs(a - offset) - Math.abs(b - offset)
            })
            .filter { (consumedOffset, consumedData) ->
                consumedData.colorShape.color == color && (next && consumedOffset > offset || !next && consumedOffset < offset)
            }
            .firstOrNull()
            ?.value
    }

    @Synchronized
    fun find(colorShape: CursingColorShape, character: Char): ConsumedData? {
        val lowerCaseCharacter = character.lowercaseChar()
        return consumed
            .filter { it.value.colorShape == colorShape && it.value.characterConsumed == lowerCaseCharacter }
            .firstOrNull()
            ?.value
    }

    /**
     * Generates a list of all possible [CursingColorShape]s in a random order.
     */
    private fun generatePermutations(): MutableList<CursingColorShape> {
        return ArrayList(permutations)
    }

    /**
     * Represents a consumed [CursingColorShape] at a given offset.
     *
     * @property character The consumed character.
     * @property colorShape The consumed [CursingColorShape].
     * @property startOffset The start offset of the consumed text.
     */
    data class ConsumedData(
        val colorShape: CursingColorShape,
        val startOffset: Int,
        val consumedText: String,
        val originalText: String = consumedText,
    ) {
        val character = consumedText[0]
        val characterConsumed = character.lowercaseChar()
        val endOffset = startOffset + consumedText.length
    }


    /**
     * Represents a set of free [CursingColorShape]s.
     *
     * @property free The list of free [CursingColorShape]s.
     */
    @Suppress("unused")
    private data class FreeCursingColorShapeState(val free: MutableList<CursingColorShape>) {}
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
