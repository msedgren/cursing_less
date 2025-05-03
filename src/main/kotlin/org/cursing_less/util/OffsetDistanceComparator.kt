package org.cursing_less.util

import kotlin.math.abs

/**
 * Comparator for sorting elements based on their distance from a reference offset.
 * Elements closer to the reference offset will be ranked higher.
 */
class OffsetDistanceComparator<T>(private val offset: Int, private val offsetExtractor: (T) -> Int) :
    Comparator<T> {
    override fun compare(a: T, b: T): Int {
        return abs(offset - offsetExtractor(a)) - abs(offset - offsetExtractor(b))
    }
}