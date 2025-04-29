package org.cursing_less.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingMarkStorageServiceTest {

    private lateinit var markStorageService: CursingMarkStorageService

    @BeforeEach
    fun setUp() {
        markStorageService = CursingMarkStorageService()
    }

    @Test
    fun testStoreAndRetrieveMarkedText() {
        // Given a mark, text/, and starting offset
        val markNumber = 42
        val text = "Sample marked text"
        val startOffset = 10
        // when we store the marked text and retrieve it
        markStorageService.storeMarkedText(markNumber, text, startOffset)
        val retrievedInfo = markStorageService.getMarkedTextInfo(markNumber)
        val retrievedText = markStorageService.getMarkedText(markNumber)
        // then the results are as we expect
        assertNotNull(retrievedInfo)
        assertEquals(text, retrievedInfo?.text)
        assertEquals(startOffset, retrievedInfo?.startOffset)
        assertEquals(text, retrievedText)
    }

    @Test
    fun testClearMarkedText() {
        // given a mark, text, and starting offset
        val markNumber = 42
        val text = "Sample marked text"
        val startOffset = 10
        // when we store the marked text
        markStorageService.storeMarkedText(markNumber, text, startOffset)
        // and clear it and attempt to retrieve it afterward
        markStorageService.clearMarkedText(markNumber)
        val retrievedInfo = markStorageService.getMarkedTextInfo(markNumber)
        val retrievedText = markStorageService.getMarkedText(markNumber)
        // then it is cleared
        assertNull(retrievedInfo)
        assertNull(retrievedText)
    }

    @Test
    fun testClearAllMarkedText() {
        // given multiple marks that are stored
        val markNumber1 = 42
        val markNumber2 = 100
        val text1 = "Sample marked text 1"
        val text2 = "Sample marked text 2"
        val startOffset1 = 10
        val startOffset2 = 20
        markStorageService.storeMarkedText(markNumber1, text1, startOffset1)
        markStorageService.storeMarkedText(markNumber2, text2, startOffset2)
        // when we clear all of them and then attempt to retrieve them
        markStorageService.clearAllMarkedText()
        val retrievedInfo1 = markStorageService.getMarkedTextInfo(markNumber1)
        val retrievedInfo2 = markStorageService.getMarkedTextInfo(markNumber2)
        val retrievedText1 = markStorageService.getMarkedText(markNumber1)
        val retrievedText2 = markStorageService.getMarkedText(markNumber2)
        // expect that they are not there
        assertNull(retrievedInfo1)
        assertNull(retrievedInfo2)
        assertNull(retrievedText1)
        assertNull(retrievedText2)
    }

    @Test
    fun testGetAllMarkedTextInfo() {
        // given multiple marked texts
        val markNumber1 = 42
        val markNumber2 = 100
        val text1 = "Sample marked text 1"
        val text2 = "Sample marked text 2"
        val startOffset1 = 10
        val startOffset2 = 20
        markStorageService.storeMarkedText(markNumber1, text1, startOffset1)
        markStorageService.storeMarkedText(markNumber2, text2, startOffset2)
        // when we attempt to pull all of them
        val allMarkedTextInfo = markStorageService.getAllMarkedTextInfo()
        // then everything is pulled
        assertEquals(2, allMarkedTextInfo.size)
        assertEquals(text1, allMarkedTextInfo[markNumber1]?.text)
        assertEquals(startOffset1, allMarkedTextInfo[markNumber1]?.startOffset)
        assertEquals(text2, allMarkedTextInfo[markNumber2]?.text)
        assertEquals(startOffset2, allMarkedTextInfo[markNumber2]?.startOffset)
    }

    @Test
    fun testOverwriteMarkedText() {
        // given some marked text
        val markNumber = 42
        val text1 = "Sample marked text 1"
        val text2 = "Sample marked text 2"
        val startOffset1 = 10
        val startOffset2 = 20
        markStorageService.storeMarkedText(markNumber, text1, startOffset1)
        // when we override it and retrieve it again
        markStorageService.storeMarkedText(markNumber, text2, startOffset2)
        val retrievedInfo = markStorageService.getMarkedTextInfo(markNumber)
        val retrievedText = markStorageService.getMarkedText(markNumber)
        // then the updated text is returned
        assertNotNull(retrievedInfo)
        assertEquals(text2, retrievedInfo?.text)
        assertEquals(startOffset2, retrievedInfo?.startOffset)
        assertEquals(text2, retrievedText)
    }
}
