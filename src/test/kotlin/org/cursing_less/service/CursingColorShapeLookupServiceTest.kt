package org.cursing_less.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingColorShapeLookupServiceTest {

    lateinit var codeInsightFixture: CodeInsightTestFixture
    lateinit var lookupService: CursingColorShapeLookupService
    lateinit var preferenceService: CursingPreferenceService


    @BeforeEach
    fun setUp() {
        codeInsightFixture = CursingTestUtils.setupTestFixture()

        val project = codeInsightFixture.project
        lookupService = project.service<CursingColorShapeLookupService>()
        preferenceService = project.service<CursingPreferenceService>()
    }

    @AfterEach
    fun tearDown() {
        runInEdtAndWait(true) {
            codeInsightFixture.tearDown()
        }
    }

    @Test
    fun itCanParseColorShape() {
        val firstParsed = lookupService.parseToColorShape(preferenceService.colors[0].name, preferenceService.shapes[0].name)
        assertEquals(CursingColorShape(preferenceService.colors[0], preferenceService.shapes[0]), firstParsed)

        val secondParsed = lookupService.parseToColorShape(preferenceService.colors[1].name, preferenceService.shapes[2].name)
        assertEquals(CursingColorShape(preferenceService.colors[1], preferenceService.shapes[2]), secondParsed)
    }
}