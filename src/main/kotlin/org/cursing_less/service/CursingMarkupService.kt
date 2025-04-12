package org.cursing_less.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.listener.CursingApplicationListener
import java.awt.Graphics
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Pair
import kotlin.collections.ArrayList
import com.intellij.psi.util.startOffset
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

@Service(Service.Level.APP)
class CursingMarkupService(private val coroutineScope: CoroutineScope) : Disposable {

    private val enabled = AtomicBoolean(true)
    private val debouncer = Debouncer(250, coroutineScope)

    companion object {
        private const val INLAY_NAME = "CURSING_INLAY"
        private const val ID_KEY_NAME = "CURSING_EDITOR_ID"

        val INLAY_KEY = Key.create<CursingColorShape>(INLAY_NAME)
        val ID_KEY = Key.create<Long>(ID_KEY_NAME)

        private val idGenerator = AtomicLong(0)
        private val mutex = Mutex()
    }


    override fun dispose() {
        enabled.set(false)

        runBlocking {
            EditorFactory.getInstance().allEditors.forEach { editor ->
                removeAllCursingTokensNow(editor)
            }
        }
    }

    fun toggleEnabled() {
        enabled.set(!enabled.get())

        coroutineScope.launch(Dispatchers.EDT) {
            EditorFactory.getInstance().allEditors.forEach { editor ->
                if (enabled.get()) {
                    updateCursingTokens(editor, editor.caretModel.offset)

                } else {
                    removeAllCursingTokens(editor)
                }
            }
        }
    }


    suspend fun updateCursingTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce("update", createAndSetId(editor), suspend {
            updateCursingTokensNow(editor, cursorOffset)
        })
    }

    suspend fun updateCursingTokensNow(editor: Editor, cursorOffset: Int) {

        if (enabled.get() && !editor.isDisposed) {
            // thisLogger().trace("Updating cursing tokens for ${editor.editorKind.name} with ID ${createAndSetId(editor)}")

            val colorAndShapeManager = createAndSetColorAndShapeManager(editor)
            colorAndShapeManager.freeAll()

            val tokens = findCursingTokens(editor, cursorOffset, colorAndShapeManager)

            withContext(Dispatchers.EDT) {
                val existingInlays = pullExistingInlaysByOffset(editor)
                val goodInlays = mutableSetOf<Inlay<*>>()

                tokens.forEach { (offset, cursingColorShape) ->
                    var found = false
                    existingInlays[offset]?.forEach { existing ->
                        if (!found && existing.second == cursingColorShape) {
                            val inlay = existing.first
                            if (inlay.isValid) {
                                found = true
                                inlay.repaint()
                                goodInlays.add(inlay)
                            }
                        }
                    }
                    if (!found) {
                        addColoredShapeAboveCursingToken(editor, cursingColorShape, offset)
                    }
                }
                removeInlays(existingInlays.values.flatten().map { it.first }.filter { !goodInlays.contains(it) })
                editor.contentComponent.repaint()
            }
        }
    }

    private fun removeInlays(inlaysToRemove: List<Inlay<*>>) {
        inlaysToRemove.forEach {
            if (it.isValid) {
                it.dispose()
            }
        }
    }

    private suspend fun removeAllCursingTokens(editor: Editor) {
        debouncer.debounce("remove", createAndSetId(editor), suspend {
            removeAllCursingTokensNow(editor)
        })
    }

    private suspend fun removeAllCursingTokensNow(editor: Editor) {
        if (!editor.isDisposed) {
            withContext(Dispatchers.EDT) {
                val existingInlays = pullExistingInlays(editor)

                if (existingInlays.isNotEmpty()) {
                    editor.inlayModel.execute(false) {
                        existingInlays
                            .map { it.first }
                            .forEach {
                                if (it.isValid) {
                                    it.dispose()
                                }
                            }
                    }
                    editor.contentComponent.repaint()
                    editor.removeUserData(ColorAndShapeManager.KEY)
                }
            }
        }
    }

    private suspend fun pullExistingInlays(editor: Editor): List<Pair<Inlay<*>, CursingColorShape>> {
        return pullExistingInlaysByOffset(editor).values.toList().flatten()

    }

    private suspend fun pullExistingInlaysByOffset(editor: Editor): Map<Int, List<Pair<Inlay<*>, CursingColorShape>>> {
        return withContext(Dispatchers.EDT) {
            editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength - 1)
                .filter { it.getUserData(INLAY_KEY) != null }
                .groupBy { it.offset }
                .mapValues { it.value.map { inlay -> Pair(inlay, inlay.getUserData(INLAY_KEY) as CursingColorShape) } }
        }
    }


    suspend fun createAndSetColorAndShapeManager(editor: Editor): ColorAndShapeManager {
        val cursingPreferenceService =
            ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
        mutex.withLock {
            return editor.getOrCreateUserDataUnsafe(ColorAndShapeManager.KEY) {
                ColorAndShapeManager(cursingPreferenceService.colors, cursingPreferenceService.shapes)
            }
        }
    }

    suspend fun createAndSetId(editor: Editor): Long {
        mutex.withLock {
            return editor.getOrCreateUserDataUnsafe(ID_KEY) {
                idGenerator.getAndIncrement()
            }
        }
    }

    private suspend fun findCursingTokens(
        editor: Editor, offset: Int, colorAndShapeManager: ColorAndShapeManager
    ): List<Pair<Int, CursingColorShape>> {
        val visibleArea = withContext(Dispatchers.EDT) {
            editor.calculateVisibleRange()
        }

        return readAction {
            val project = editor.project ?: ProjectManager.getInstance().defaultProject
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            val found = ArrayList<Pair<Int, CursingColorShape>>()
            if (file != null) {
                var nextElement = findElementClosestToOffset(file, offset)
                if (nextElement != null) {
                    var previousElement = PsiTreeUtil.prevVisibleLeaf(nextElement)
                    var nextElementVisible = isAnyPartVisible(visibleArea, nextElement.textRange)
                    var previousElementVisible =
                        previousElement != null && isAnyPartVisible(visibleArea, previousElement.textRange)
                    // An attempt to bubble out from the cursor.
                    while ((nextElementVisible || previousElementVisible)) {
                        if (nextElement != null && nextElementVisible) {
                            found.addIfNotNull(consumeVisible(nextElement, visibleArea, colorAndShapeManager))
                            nextElement = PsiTreeUtil.nextVisibleLeaf(nextElement)
                            nextElementVisible =
                                nextElement != null && isAnyPartVisible(visibleArea, nextElement.textRange)
                        } else {
                            nextElement = null
                            nextElementVisible = false
                        }

                        if (previousElement != null && previousElementVisible) {
                            found.addIfNotNull(consumeVisible(previousElement, visibleArea, colorAndShapeManager))
                            previousElement = PsiTreeUtil.prevVisibleLeaf(previousElement)
                            previousElementVisible =
                                previousElement != null && isAnyPartVisible(visibleArea, previousElement.textRange)
                        } else {
                            previousElement = null
                            previousElementVisible = false
                        }
                    }
                }
            }
            found.addAll(
                consumeVisible(
                    colorAndShapeManager,
                    offset,
                    found.map { it.first }.toSet(),
                    editor.document.getText(visibleArea),
                    visibleArea
                )
            )
            found
        }
    }

    private fun consumeVisible(
        colorAndShapeManager: ColorAndShapeManager,
        currentOffset: Int,
        alreadyKnown: Set<Int>,
        text: String,
        visibleArea: ProperTextRange
    ): List<Pair<Int, CursingColorShape>> {
        val tokens = findAllCursingTokensWithin(text, visibleArea.startOffset)
        return tokens
            .filterNot { alreadyKnown.contains(it.startOffset) }
            .sortedWith { a, b ->
                abs(currentOffset - a.startOffset) - abs(currentOffset - b.startOffset)
            }
            .mapNotNull {
                val startOffset = it.startOffset
                val consumed = colorAndShapeManager.consume(it.text[0], startOffset, it.endOffset)
                if (consumed != null) Pair(startOffset, consumed) else null
            }.toList()
    }

    private fun consumeVisible(
        element: PsiElement, visibleArea: ProperTextRange, colorAndShapeManager: ColorAndShapeManager
    ): Pair<Int, CursingColorShape>? {
        val text = element.text
        if (isAnyPartVisible(visibleArea, element.textRange) && text.isNotBlank() && !text[0].isWhitespace()) {
            val consumed = colorAndShapeManager.consume(text[0], element.startOffset, element.endOffset)
            return if (consumed != null) Pair(element.startOffset, consumed) else null
        }
        return null
    }
    private fun findAllCursingTokensWithin(text: String, startOffset: Int): List<CursingToken> {
        val reg = ApplicationManager.getApplication().getService(CursingPreferenceService::class.java).tokenPattern

        return reg.findAll(text).iterator().asSequence()
            .filter { it.value.isNotBlank() && !it.value[0].isWhitespace()}
            .map { CursingToken(startOffset + it.range.first, startOffset + it.range.last + 1, it.value) }.toList()
    }


    data class CursingToken(val startOffset: Int, val endOffset: Int, val text: String)

    private fun findElementClosestToOffset(file: PsiFile, offset: Int): PsiElement? {
        var next = offset
        var previous = offset

        var element = file.findElementAt(offset)
        while (element == null && (previous > 0 || next < (file.textLength - 1))) {
            if (previous > 0) {
                element = file.findElementAt(--previous)
            }
            if (element == null && next < (file.textLength - 1)) {
                element = file.findElementAt(++next)
            }
        }
        return element
    }

    private fun isAnyPartVisible(visibleArea: ProperTextRange, elementArea: TextRange): Boolean {
        return visibleArea.intersects(elementArea)
    }

    private fun addColoredShapeAboveCursingToken(
        editor: Editor, cursingColorShape: CursingColorShape, offset: Int
    ) {
        editor.inlayModel.addInlineElement(offset, true, Int.MIN_VALUE, ColoredShapeRenderer(cursingColorShape))
            ?.putUserData(INLAY_KEY, cursingColorShape)
    }


    class ColoredShapeRenderer(
        private val cursingColorShape: CursingColorShape
    ) : EditorCustomElementRenderer {

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            return 1
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.color = cursingColorShape.color.color
            cursingColorShape.shape.paint(inlay, g, targetRegion, textAttributes)
        }
    }

    class Debouncer(
        private val delay: Int,
        private val coroutineScope: CoroutineScope
    ) : Disposable {
        private val updateQueue = MergingUpdateQueue("cursing_less_debounce", delay, true, null)

        companion object {
            private val mutex = Mutex()
        }

        fun debounce(operation: String, editorId: Long, function: suspend () -> Unit) {
            val task = object : Update("${operation}_${editorId}") {
                override fun run() {
                    if (CursingApplicationListener.handler.initialized.get()) {
                        coroutineScope.launch {
                            mutex.withLock {
                                function()
                            }
                        }
                    }
                }
            }
            updateQueue.queue(task)
        }

        override fun dispose() {
            runBlocking {
                mutex.withLock {
                    updateQueue.dispose()
                }
            }
        }
    }

}
