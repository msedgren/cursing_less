package org.cursing_less.color_shape

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import junit.framework.TestCase


class ColorAndShapeManagerTest : BasePlatformTestCase() {


    private val colors = listOf(
         CursingColor("red", JBColor.RED),
        CursingColor("2", JBColor.BLUE)
    )
    private val shapes = listOf(
        CursingShape.Circle(),
        CursingShape.Square()
    )


    fun testConsuming() {
        // given a color and shape manager
        val manager = ColorAndShapeManager(colors, shapes)
        // when we consume the 'a' character 4 times
        val consumedOne = manager.consume('a', 0, 1)
        val consumedTwo = manager.consume('a', 1, 2)
        val consumedThree = manager.consume('a', 2, 3)
        val consumedFour = manager.consume('a', 3, 4)
        // expect it worked for all
        assertNotNull(consumedOne)
        assertNotNull(consumedTwo)
        assertNotNull(consumedThree)
        assertNotNull(consumedFour)
        // and that they are all distinct
        val consumed = setOf(consumedOne!!, consumedTwo!!, consumedThree!!, consumedFour!!)
        assertEquals(4, consumed.size)
        // and we cannot consume the 'a' character again or the 'A'
        assertNull(manager.consume('a', 4, 5))
        assertNull(manager.consume('A', 4, 5))
        // bute we can consume the 'b' character
        assertNotNull(manager.consume('b', 5, 6))
    }
    

    fun testFreeingConsumed() {
        // given a color and shape manager
        val manager = ColorAndShapeManager(colors, shapes)

        // when we consume a 'c' character
        val consumedOne = manager.consume('c', 0, 1)
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
            assertNotNull(manager.consume('c', it, it + 1))
        }

    }   
}