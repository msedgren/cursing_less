package org.cursing_less.util

import kotlin.math.abs

/**
 * Comparator for sorting elements based on their distance from a reference offset.
 * Elements closer to the reference offset will be ranked higher.
 */
class OffsetDistanceComparator<T>(private val offset: Int, private val offsetExtractor: (T) -> Int) :
    Comparator<T> {

    companion object {
        fun distance(offsetOne: Int, offsetTwo: Int) = abs(offsetOne - offsetTwo)
    }

    override fun compare(a: T, b: T): Int {
        return compareValues(distance(offset, offsetExtractor(a)), distance(offset, offsetExtractor(b)))
    }
}