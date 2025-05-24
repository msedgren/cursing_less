package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.util.OffsetDistanceComparator
import kotlin.collections.putAll

/**
 * Service responsible for finding and processing tokens in text and a PsiTree.
 * This service encapsulates the token-related functionality that was previously
 * part of CursingMarkupService.
 */
@Service(Service.Level.APP)
class CursingTokenService() {
    private val preferenceService by lazy {
        ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
    }

    /**
     * Represents a token found in text with its start and end offsets and the text content.
     */
    data class CursingToken(val startOffset: Int, val endOffset: Int, val text: String)

    /**
     * Finds cursing tokens in the given editor's visible area.
     *
     * @param editor The editor to find tokens in
     * @param offset The current cursor offset
     * @param manager The manager for assigning colors and shapes
     * @return A map of offset to a pair containing the token character and the cursing color shape
     */
    suspend fun findCursingTokens(
        editor: Editor, offset: Int, manager: ColorAndShapeManager
    ): Map<Int, Pair<Char, CursingColorShape>> {
        val visibleArea = withContext(Dispatchers.EDT) { editor.calculateVisibleRange() }
        return withContext(Dispatchers.Default) {
            val currentJob = coroutineContext[Job]

            ReadAction.nonBlocking<Map<Int, Pair<Char, CursingColorShape>>> {
                val found = mutableMapOf<Int, Pair<Char, CursingColorShape>>()

                // Only use regex if enabled in preferences
                if (preferenceService.useRegex) {
                    found.putAll(consumeVisible(manager, offset,
                        found.keys, editor.document.getText(visibleArea), visibleArea))
                }

                ProgressManager.checkCanceled()

                // Only use PSI tree if enabled in preferences
                if (preferenceService.usePsiTree) {
                    found.putAll(consumeVisiblePsi(manager, offset, editor, visibleArea))
                }
                found
            }
                .expireWhen { currentJob?.isCancelled == true } // Cancel when the coroutine is cancelled
                .submit(AppExecutorUtil.getAppExecutorService())
                .await() // Wait for the result

        }
    }

    private fun findVisibleElements(visibleArea: ProperTextRange, file: PsiFile): List<PsiElement> {
        var startOffset = visibleArea.startOffset
        var element = file.findElementAt(startOffset)
        while (element == null && startOffset < visibleArea.endOffset) {
            element = file.findElementAt(++startOffset)
        }

        val elements = mutableListOf<PsiElement>()
        while (element != null && element.textRange.startOffset < visibleArea.endOffset) {
            elements.add(element)
            element = PsiTreeUtil.nextLeaf(element)
        }
        return elements
    }

    /**
     * Finds all tokens within the given text starting from the specified offset.
     *
     * @param text The text to search for tokens
     * @param startOffset The starting offset in the document
     * @return A sequence of CursingToken objects representing the tokens found
     */
    fun findAllCursingTokensWithin(text: String, startOffset: Int): Sequence<CursingToken> {
        val reg = preferenceService.tokenPattern

        return reg.findAll(text)
            .iterator()
            .asSequence()
            .filter { it.value.isNotBlank() && !it.value[0].isWhitespace() }
            .map { CursingToken(startOffset + it.range.first, startOffset + it.range.last + 1, it.value) }
    }


    /**
     * Processes visible PSI elements in the editor and consumes tokens for markup.
     *
     * @param manager The color and shape manager responsible for token consumption and style assignment
     * @param offset The current cursor offset in the editor
     * @param editor The editor instance containing the text to process
     * @param visibleArea The visible text range in the editor
     * @return A sequence of pairs containing the offset and corresponding character-style pairs for markup
     */
    fun consumeVisiblePsi(
        manager: ColorAndShapeManager,
        offset: Int,
        editor: Editor,
        visibleArea: ProperTextRange
    ): Sequence<Pair<Int, Pair<Char, CursingColorShape>>> {
        val project = editor.project ?: ProjectManager.getInstance().defaultProject
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        if (file != null) {
            val elements = findVisibleElements(visibleArea, file)
            ProgressManager.checkCanceled()
            val offsetComparator =
                OffsetDistanceComparator<Pair<PsiElement, String>>(offset) { it.first.startOffset }
            return elements
                .asSequence()
                .filter { visibleArea.contains(it.startOffset) }
                .map { Pair(it, it.text) }
                .filter { it.second.isNotBlank() && !it.second[0].isWhitespace() && it.second[0] != '.' }
                .sortedWith(offsetComparator)
                .mapNotNull { (element, text) ->
                    manager.consume(element.startOffset, text)?.let {
                        Pair(element.startOffset, Pair(text[0], it))
                    }
                }
        } else {
            return emptySequence()
        }
    }

    /**
     * Consumes tokens in the visible area of the given text, filtering out already known tokens.
     *
     * @param colorAndShapeManager The manager for assigning colors and shapes
     * @param currentOffset The current cursor offset
     * @param alreadyKnown Set of offsets that are already known/processed
     * @param text The visible text
     * @param visibleArea The range of visible text
     * @return Sequence of pairs containing offset and consumed token information
     */
    fun consumeVisible(
        colorAndShapeManager: ColorAndShapeManager,
        currentOffset: Int,
        alreadyKnown: Set<Int>,
        text: String,
        visibleArea: ProperTextRange
    ): Sequence<Pair<Int, Pair<Char, CursingColorShape>>> {
        val tokens = findAllCursingTokensWithin(text, visibleArea.startOffset)
        val offsetComparator = OffsetDistanceComparator<CursingToken>(currentOffset) { it.startOffset }
        return tokens
            .filterNot { alreadyKnown.contains(it.startOffset) }
            .sortedWith(offsetComparator)
            .mapNotNull {
                colorAndShapeManager.consume(it.startOffset, it.text)?.let { consumed ->
                    Pair(it.startOffset, Pair(it.text[0], consumed))
                }
            }
    }
}
