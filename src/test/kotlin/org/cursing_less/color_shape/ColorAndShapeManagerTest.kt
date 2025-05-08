package org.cursing_less.color_shape

import com.intellij.ui.JBColor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class ColorAndShapeManagerTest {


    private val colors = listOf(
        CursingColor("red", JBColor.RED),
        CursingColor("2", JBColor.BLUE)
    )
    private val shapes = listOf(
        CursingShape.Circle,
        CursingShape.Square
    )


    @Test
    fun testConsuming() {
        // given a color and shape manager
        val manager = ColorAndShapeManager(colors, shapes)
        // when we consume the 'a' character 4 times
        val consumedOne = manager.consume(0, "a")
        val consumedTwo = manager.consume(1, "a")
        val consumedThree = manager.consume(2, "a")
        val consumedFour = manager.consume(3, "a#")
        // expect it worked for all
        assertNotNull(consumedOne)
        assertNotNull(consumedTwo)
        assertNotNull(consumedThree)
        assertNotNull(consumedFour)
        // and that they are all distinct
        val consumed = setOf(consumedOne!!, consumedTwo!!, consumedThree!!, consumedFour!!)
        assertEquals(4, consumed.size)
        // and we cannot consume the 'a' character again or the 'A'
        assertNull(manager.consume(4, "a"))
        assertNull(manager.consume(4, "A"))
        // bute we can consume the 'b' character
        assertNotNull(manager.consume(5, "b"))
    }

    @Test
    fun testConsumeWithStartOverlaps() {
        // Given some imaginary text that we are pretending to navigate and mark
        // "Let us remember how the sky went dark."
        // and a manager
        val manager = ColorAndShapeManager(colors, shapes)
        // and we consume 'Let us remember'
        manager.consume(0, "Let us remember")
        // if we next consume remember
        manager.consume(7, "remember")
        // then it should shrink the first
        val first = manager.consumedAtOffset(0)
        assertNotNull(first)
        assertEquals(ColorAndShapeManager.ConsumedData(first!!.colorShape, 0, "Let us", "Let us remember"), first)
        // and the last should also be correct
        val last = manager.consumedAtOffset(7)
        assertNotNull(last)
        assertEquals(ColorAndShapeManager.ConsumedData(last!!.colorShape, 7, "remember"), last)
    }

    @Test
    fun testConsumeWithEndOverlaps() {
        // Given some imaginary text that we are pretending to navigate and mark
        // "Let us remember how the sky went dark."
        // and a manager
        val manager = ColorAndShapeManager(colors, shapes)
        // and we consume 'Let us remember'
        manager.consume(24, "sky went dark.")
        // if we next consume how the sky
        manager.consume(16, "how the sky")
        // then it should shrink the first
        val first = manager.consumedAtOffset(24)
        assertNotNull(first)
        assertEquals(ColorAndShapeManager.ConsumedData(first!!.colorShape, 24, "sky went dark."), first)
        // and the last should also be correct
        val last = manager.consumedAtOffset(16)
        assertNotNull(last)
        assertEquals(ColorAndShapeManager.ConsumedData(last!!.colorShape, 16, "how the", "how the sky"), last)
    }


    @Test
    fun testFreeingConsumed() {
        // given a color and shape manager
        val manager = ColorAndShapeManager(colors, shapes)

        // when we consume a 'c' character
        val consumedOne = manager.consume(0, "c")
        // then it is consumed.
        assertNotNull(consumedOne)
        assertNotNull(manager.find(consumedOne!!, 'c'))
        assertNotNull(manager.consumedAtOffset(0))

        // and when we free the consumed character
        manager.free(0)
        // then it is no longer consumed.
        assertNull(manager.find(consumedOne, 'c'))
        assertNull(manager.consumedAtOffset(0))
        // and we can still consume 4 c characters
        (1..4).forEach {
            assertNotNull(manager.consume(it, "c"))
        }
    }

    @Test
    fun testFind() {
        // given a color and shape manager
        val color = CursingColor("red", JBColor.RED)
        val manager = ColorAndShapeManager(listOf(color), shapes)
        // and we consumed stuff at certain offsets.
        manager.consume(439, "Let us remember how the sky went dark.")
        manager.consume(567, "Other text")
        manager.consume(573, "text Yet Other random text")
        manager.consume(578, "Yet Other random text")
        // expect we can find the correct next and previous offsets
        assertEquals(578, manager.find(color, false, 600)?.startOffset)
        assertEquals(573, manager.find(color, false, 578)?.startOffset)
        assertEquals(567, manager.find(color, false, 573)?.startOffset)
        assertEquals(439, manager.find(color, false, 567)?.startOffset)
        assertNull(manager.find(color, false, 439))

        assertEquals(439, manager.find(color, true, 400)?.startOffset)
        assertEquals(567, manager.find(color, true, 439)?.startOffset)
        assertEquals(573, manager.find(color, true, 567)?.startOffset)
        assertEquals(578, manager.find(color, true, 573)?.startOffset)
        assertNull(manager.find(color, true, 578))
    }

    @Test
    fun testFindTokenContainingOffset() {
        // given a color and shape manager
        val manager = ColorAndShapeManager(colors, shapes)

        // and we consume some tokens
        val token1 = "hello"
        val token2 = "world"
        val token3 = "testing"

        manager.consume(10, token1)
        manager.consume(20, token2)
        manager.consume(30, token3)

        // Test finding token when cursor is at the start of a token
        val foundAtStart = manager.findTokenContainingOffset(10)
        assertNotNull(foundAtStart)
        assertEquals(10, foundAtStart?.startOffset)

        // Test finding token when cursor is in the middle of a token
        val foundInMiddle = manager.findTokenContainingOffset(22)
        assertNotNull(foundInMiddle)
        assertEquals(20, foundInMiddle?.startOffset)

        // Test finding token when cursor is at the end of a token (but not past it)
        val foundAtEnd = manager.findTokenContainingOffset(34)
        assertNotNull(foundAtEnd)
        assertEquals(30, foundAtEnd?.startOffset)

        // Test when cursor is not within any token
        assertNull(manager.findTokenContainingOffset(5))  // Before first token
        assertNull(manager.findTokenContainingOffset(16)) // Between tokens
        assertNull(manager.findTokenContainingOffset(38)) // After last token
    }
}
