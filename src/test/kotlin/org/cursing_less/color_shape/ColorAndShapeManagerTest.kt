package org.cursing_less.color_shape

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor


class ColorAndShapeManagerTest : BasePlatformTestCase() {


    private val colors = listOf(
        CursingCoded("c1", CursingColor("red", JBColor.RED)),
        CursingCoded("c2", CursingColor("2", JBColor.BLUE))
    )
    private val shapes: List<CursingCoded<CursingShape>> = listOf(
        CursingCoded("s1", CursingShape.Circle()),
        CursingCoded("s2", CursingShape.Square())
    )


    fun testConsuming() {
        // given a color and shape manager
        val manager = ColorAndShapeManager(colors, shapes)
        // when we consume the 'a' character 4 times
        val consumedOne = manager.consume('a', 0)
        val consumedTwo = manager.consume('a', 1)
        val consumedThree = manager.consume('a', 2)
        val consumedFour = manager.consume('a', 3)
        // expect it worked for all
        assertNotNull(consumedOne)
        assertNotNull(consumedTwo)
        assertNotNull(consumedThree)
        assertNotNull(consumedFour)
        // and that they are all distinct
        val consumed = setOf(consumedOne!!, consumedTwo!!, consumedThree!!, consumedFour!!)
        assertEquals(4, consumed.size)
        // and we cannot consume the 'a' character again
        val consumedAgain = manager.consume('a', 4)
        assertNull(consumedAgain)
        // bute we can consume the 'b' character
        val consumedB = manager.consume('b', 5)
        assertNotNull(consumedB)
    }
}