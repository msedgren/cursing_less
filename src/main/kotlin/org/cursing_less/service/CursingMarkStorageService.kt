package org.cursing_less.service

import com.intellij.openapi.components.Service

/**
 * Data class to store marked text and its starting offset.
 */
data class MarkedTextInfo(val text: String, val startOffset: Int)

/**
 * Service for storing marked text selections.
 * This service maintains a map of mark numbers to marked text information.
 */
@Service(Service.Level.APP)
class CursingMarkStorageService {

    // Map to store marked text by mark number
    private val markedTextMap = mutableMapOf<Int, MarkedTextInfo>()

    /**
     * Store marked text for a specific mark number.
     *
     * @param markNumber The mark number
     * @param text The text to store
     * @param startOffset The starting offset of the text in the editor
     */
    fun storeMarkedText(markNumber: Int, text: String, startOffset: Int) {
        markedTextMap[markNumber] = MarkedTextInfo(text, startOffset)
    }

    /**
     * Get marked text info for a specific mark number.
     *
     * @param markNumber The mark number
     * @return The marked text info, or null if no text is stored for this mark number
     */
    fun getMarkedTextInfo(markNumber: Int): MarkedTextInfo? {
        return markedTextMap[markNumber]
    }

    /**
     * Get marked text for a specific mark number.
     *
     * @param markNumber The mark number
     * @return The marked text, or null if no text is stored for this mark number
     */
    fun getMarkedText(markNumber: Int): String? {
        return markedTextMap[markNumber]?.text
    }

    /**
     * Clear marked text for a specific mark number.
     *
     * @param markNumber The mark number
     */
    fun clearMarkedText(markNumber: Int) {
        markedTextMap.remove(markNumber)
    }

    /**
     * Clear all marked text.
     */
    fun clearAllMarkedText() {
        markedTextMap.clear()
    }

    /**
     * Get all marked text entries.
     * 	at org.cursing_less.service.CursingColorShapeLookupService.findConsumed(CursingColorShapeLookupService.kt:42)
     * 	at org.cursing_less.command.CursingRelativeMoveByColorCommand.run(CursingRelativeMoveByColorCommand.kt:25)
     * @return Map of mark numbers to marked text info
     */
    fun getAllMarkedTextInfo(): Map<Int, MarkedTextInfo> {
        return markedTextMap.toMap()
    }
}
