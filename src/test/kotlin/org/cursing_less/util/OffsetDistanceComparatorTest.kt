package org.cursing_less.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OffsetDistanceComparatorTest {

    @Test
    fun testBasicDistanceCases() {
        // identical
        assertEquals(0, OffsetDistanceComparator.distance(0, 0))
        assertEquals(0, OffsetDistanceComparator.distance(42, 42))
        // simple positives
        assertEquals(3, OffsetDistanceComparator.distance(5, 2))
        assertEquals(3, OffsetDistanceComparator.distance(2, 5))
        assertEquals(7, OffsetDistanceComparator.distance(10, 3))
        // negatives and cross sign
        assertEquals(5, OffsetDistanceComparator.distance(-2, 3))
        assertEquals(9, OffsetDistanceComparator.distance(-4, -13))
    }

    @Test
    fun testSortedByDistanceAscending() {
        val items = listOf(5, 10, 3, 8, 15)
        val offset = 1
        val comparator = OffsetDistanceComparator<Int>(offset) { it }

        val sorted = items.sortedWith(comparator)

        assertEquals(listOf(3, 5, 8, 10, 15), sorted)
    }

    @Test
    fun testComparingCustomObjects() {
        val tokens = listOf(
            TestObject(5, 'a'),
            TestObject(10, 'a'),
            TestObject(3, 'a'),
            TestObject(8, 'a'),
            TestObject(15, 'a'),
        )

        val sorted = tokens.sortedWith(OffsetDistanceComparator(1) { it.offset }).map { it.offset }

        assertEquals(listOf(3, 5, 8, 10, 15), sorted)
    }

    data class TestObject(val offset: Int, val character: Char)

    @Test
    fun testEqualDistanceReturnsZero() {
        val comparator = OffsetDistanceComparator<Int>(100) { it }
        // 95 and 105 are each distance 5 from 100
        assertEquals(0, comparator.compare(95, 105))
    }
}
