package org.cursing_less.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.service.CursingMarkupService

/**
 * Listener for inlay model changes.
 * 
 * This listener detects when inlays are added at offsets that already contain inlays added by this plugin.
 * When this happens, it removes and invalidates the existing inlays and frees the tokens from the ColorShapeManager.
 */
class CursingInlayListener(
    private val editor: Editor,
    private val coroutineScope: CoroutineScope
) : InlayModel.Listener {

    val cursingMarkupService: CursingMarkupService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
    }
    
    override fun onAdded(inlay: Inlay<*>) {
        // Check if this inlay was added at an offset that already contains an inlay added by this plugin
        val offset = inlay.offset
        val existingInlays = editor.inlayModel.getInlineElementsInRange(offset, offset)
            .filter { it != inlay && it.getUserData(CursingMarkupService.INLAY_KEY) != null }

        if (existingInlays.isNotEmpty() && inlay.getUserData(CursingMarkupService.INLAY_KEY) == null) {
            // A new inlay was added at an offset that already has our inlay, and the new inlay was not created by this plugin
            // Remove and invalidate our inlays at this offset
            editor.inlayModel.execute(false) {
                existingInlays.forEach { existingInlay ->
                    if (existingInlay.isValid) {
                        // Free the token from the ColorShapeManager
                        val colorShapeManager = editor.getUserData(ColorAndShapeManager.KEY)
                        colorShapeManager?.free(offset)
                        // Dispose the inlay
                        existingInlay.dispose()
                    }
                }
            }

            // Schedule for cursing tokens to be redone
            val currentCaretOffset = editor.caretModel.offset
            coroutineScope.launch(Dispatchers.Default) {
                cursingMarkupService.updateCursingTokens(editor, currentCaretOffset)
            }
        }
    }

    override fun onRemoved(inlay: Inlay<*>) {
        // Not needed for this implementation
    }

    override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) {
        // Not needed for this implementation
    }
}