package org.cursing_less.color_shape

import com.intellij.openapi.diagnostic.thisLogger
import org.cursing_less.util.OffsetDistanceComparator

class ColorAndShapeManager(
    colors: Collection<CursingColor>,
    private val shapes: Collection<CursingShape>
) {

    private val permutations =
        colors
            .asSequence()
            .flatMap { color -> shapes.asSequence().map { CursingColorShape(color, it) } }
            .toSet()

    private var consumed: MutableMap<Int, ConsumedData> = HashMap()
    private var offsetPreference: MutableMap<Int, CursingColorShape> = HashMap()

    private var characterState: MutableMap<Char, FreeCursingColorShape> = HashMap()

    @Synchronized
    fun consumedAtOffset(offset: Int): ConsumedData? {
        return consumed[offset]
    }

    @Synchronized
    fun consume(offset: Int, text: String, requiredColorShape: CursingColorShape? = null): CursingColorShape? {
        require(text.isNotEmpty()) { "text must not be empty!" }

        if (consumed.contains(offset)) {
            thisLogger().warn("Attempted to consume at offset $offset but it has already been consumed!")
            return null
        }

        val character = text[0]
        val lowerCaseCharacter = character.lowercaseChar()
        // pull the object containing free colors and shapes for the given character
        val existing =
            characterState.computeIfAbsent(lowerCaseCharacter) { FreeCursingColorShape(generatePermutations()) }
        // get the preference (what it was previously set to) at that offset.
        val preference = offsetPreference[offset]

        // get the preference (what it was previously set to) at that offset.
        // attempt to consume
        val consumedThing = existing.consume(requiredColorShape, preference)
        if (consumedThing != null) {
            val (textToConsume, updatedMap) = correctConsumedForConsumed(offset, text)
            consumed = updatedMap

            consumed[offset] =
                ConsumedData(consumedThing, offset, textToConsume, text)
            offsetPreference[offset] = consumedThing

        }
        return consumedThing
    }

    private fun correctConsumedForConsumed(offset: Int, text: String): Pair<String, MutableMap<Int, ConsumedData>> {
        var textToConsume = text
        val map = consumed.mapValues {
            val startOffsetOverlaps = offset > it.value.startOffset && offset < it.value.endOffset
            val endOffset = offset + textToConsume.length
            //done here so we don't need to loop twice.
            val endOffsetOverlaps = endOffset > it.value.startOffset && endOffset <= it.value.endOffset
            if (endOffsetOverlaps && !startOffsetOverlaps) {
                textToConsume =
                    textToConsume.substring(0, it.value.startOffset - offset)
            }
            if (startOffsetOverlaps) {
                it.value.copy(
                    consumedText = it.value.consumedText.substring(0, offset - it.value.startOffset)
                )
            } else {
                it.value
            }
        }.toMutableMap()

        return Pair(textToConsume, map)
    }

    @Synchronized
    fun setPreference(offset: Int, colorShape: CursingColorShape) {
        offsetPreference[offset] = colorShape
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
            // Return the color shape to the free pool
            val previouslyConsumed = characterState[freed.characterConsumed.lowercaseChar()]
            if (previouslyConsumed != null) {
                previouslyConsumed.returnFreed(freed.colorShape)
            } else {
                thisLogger().error("unable to free element at offset $offset as there is no existing state there!")
            }

            // Copy all existing consumed data to the new map
            consumed = correctConsumedForFreed(freed)
        } else {
            thisLogger().error("unable to free element at offset $offset!")
        }
    }

    private fun correctConsumedForFreed(freed: ConsumedData): MutableMap<Int, ConsumedData> {
        return consumed
            .mapValues { (_, existingData) ->
                val isTheOneToStretch = freed.startOffset == existingData.endOffset
                if (isTheOneToStretch) {
                    val newLength =
                        minOf(existingData.originalEndOffset, freed.endOffset) - existingData.startOffset
                    existingData.copy(consumedText = existingData.originalText.substring(0, newLength))
                } else {
                    // No overlap, keep as is
                    existingData
                }
            }.toMutableMap()
    }

    @Synchronized
    fun find(color: CursingColor, next: Boolean, offset: Int): ConsumedData? {
        val comparator = OffsetDistanceComparator<Map.Entry<Int, ConsumedData>>(offset) { it.key }
        val foundOffset = consumed
            .asSequence()
            .filter { (consumedOffset, consumedData) ->
                consumedData.colorShape.color == color && ((next && consumedOffset > offset) || (!next && consumedOffset < offset))
            }
            .minWithOrNull(comparator)

        return foundOffset?.value
    }

    @Synchronized
    fun find(colorShape: CursingColorShape, character: Char): ConsumedData? {
        val lowerCaseCharacter = character.lowercaseChar()
        return consumed
            .asSequence()
            .filter { it.value.colorShape == colorShape && it.value.characterConsumed == lowerCaseCharacter }
            .firstOrNull()
            ?.value
    }

    @Synchronized
    fun findTokenContainingOffset(offset: Int): ConsumedData? {
        return consumed
            .asSequence()
            .filter { it.value.startOffset <= offset && offset <= it.value.endOffset }
            .firstOrNull()
            ?.value
    }

    /**
     * Returns a copy of all consumed data. Used for debugging purposes.
     */
    @Synchronized
    fun getAllConsumed(): Map<Int, ConsumedData> {
        return HashMap(consumed)
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
        val originalEndOffset = startOffset + originalText.length
    }

    class FreeCursingColorShape(private val free: MutableList<CursingColorShape>) {

        init {
            free.shuffle()
        }

        fun consume(requiredColorShape: CursingColorShape?, preference: CursingColorShape?): CursingColorShape? {
            return if (requiredColorShape != null && free.remove(requiredColorShape)) {
                requiredColorShape
            } else if (requiredColorShape == null && preference != null && free.remove(preference)) {
                preference
            } else if (requiredColorShape == null && free.isNotEmpty()) {
                free.removeFirst()
            } else {
                null
            }
        }

        fun returnFreed(freed: CursingColorShape) {
            free.add(freed)
        }
    }
}
