package org.cursing_less.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.openapi.util.removeUserData
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.listener.CursingApplicationListener
import org.cursing_less.renderer.ColoredShapeRenderer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.lazy

@Service(Service.Level.APP)
class CursingMarkupService(private val coroutineScope: CoroutineScope) : Disposable {

    private val debouncer  by lazy {
        Debouncer(250, coroutineScope)
    }
    private val enabled = AtomicBoolean(true)
    private val preferenceService: CursingPreferenceService by lazy {
        ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
    }
    private val tokenService: CursingTokenService by lazy {
        ApplicationManager.getApplication().getService(CursingTokenService::class.java)
    }


    companion object {
        private const val INLAY_NAME = "CURSING_INLAY"
        private const val ID_KEY_NAME = "CURSING_EDITOR_ID"

        val INLAY_KEY = Key.create<CursingColorShape>(INLAY_NAME)
        val ID_KEY = Key.create<Long>(ID_KEY_NAME)

        private val idGenerator = AtomicLong(0)
        private val unsafeDataMutex = Mutex()
        private val operationMutex = Mutex()
    }

    override fun dispose() {
        enabled.set(false)
        debouncer.dispose()
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                EditorFactory.getInstance().allEditors.forEach { editor ->
                    removeAllCursingTokensNow(editor)
                }
            }
        }
    }

    suspend fun toggleEnabled() {
        enabled.set(!enabled.get())

        withContext(Dispatchers.EDT) {
            EditorFactory.getInstance().allEditors.forEach { editor ->
                if (enabled.get()) {
                    updateCursingTokens(editor, editor.caretModel.offset)
                } else {
                    removeAllCursingTokens(editor)
                }
            }
        }
    }

    suspend fun fullyRefreshAllTokens() {
        withContext(Dispatchers.EDT) {
            // Get all open editors
            val editors = EditorFactory.getInstance().allEditors

            // Update each editor
            for (editor in editors) {
                // Remove the existing ColorAndShapeManager
                editor.removeUserData(ColorAndShapeManager.KEY)
                updateCursingTokens(editor, editor.caretModel.offset)
            }
        }
    }

    suspend fun updateCursingTokens(editor: Editor, cursorOffset: Int) {
        debouncer.debounce("update", createAndSetId(editor), suspend {
            updateCursingTokensNow(editor, cursorOffset)
        })
    }

    suspend fun updateCursingTokensNow(editor: Editor, cursorOffset: Int) {
        operationMutex.withLock {
            if (enabled.get() && !editor.isDisposed && editorEligibleForMarkup(editor)) {
                // thisLogger().trace("Updating cursing tokens for ${editor.editorKind.name} with ID ${createAndSetId(editor)}")
                val colorAndShapeManager = createAndSetColorAndShapeManager(editor)
                colorAndShapeManager.freeAll()

                val tokens = tokenService.findCursingTokens(editor, cursorOffset, colorAndShapeManager).toMutableMap()

                withContext(Dispatchers.EDT) {
                    val existingInlays = pullExistingInlaysByOffset(editor)

                    existingInlays.forEach { (offset, oldConsumedList) ->
                        var found = false
                        oldConsumedList.forEach { (inlay, consumedCursing) ->
                            if (inlay.isValid) {
                                val wanted = tokens[offset]
                                if (found || wanted == null || wanted.second != consumedCursing) {
                                    inlay.dispose()
                                } else {
                                    inlay.repaint()
                                    found = true
                                    tokens.remove(offset)
                                }
                            }
                        }
                    }


                    if(tokens.contains(cursorOffset)) {
                        // exclude the current positon if there are multiple inlays to prevent
                        // the current position from being removed when the caret moves
                        val inlays = editor.inlayModel.getInlineElementsInRange(cursorOffset, cursorOffset)
                        if(inlays.isNotEmpty()) {
                            colorAndShapeManager.free(cursorOffset)
                            tokens.remove(cursorOffset)
                        }
                    }

                    tokens.forEach { (offset, pair) ->
                        addColoredShapeAboveCursingToken(editor, pair.second, offset, pair.first)
                    }

                     editor.contentComponent.repaint()
                }
            }
        }
    }

    suspend fun processExistingWork() {
        withContext(Dispatchers.EDT) {
            debouncer.flush()
        }
        this.coroutineScope.coroutineContext.job.children.forEach { it.join() }
    }

    private fun editorEligibleForMarkup(editor: Editor): Boolean {
        val lineHeight = editor.lineHeight
        val fontMetrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(null))
        val fontHeight = fontMetrics.height

        return (lineHeight - fontHeight) >= 2
    }

    private suspend fun removeAllCursingTokens(editor: Editor) {
        debouncer.debounce("remove", createAndSetId(editor), suspend {
            removeAllCursingTokensNow(editor)
        })
    }

    private suspend fun removeAllCursingTokensNow(editor: Editor) {
        operationMutex.withLock {
            withContext(Dispatchers.EDT) {
                if (!editor.isDisposed) {
                    val existingInlays = pullExistingInlays(editor)
                    if (existingInlays.isNotEmpty()) {
                        editor.inlayModel.execute(false) {
                            existingInlays
                                .asSequence()
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
    }

    private suspend fun pullExistingInlays(editor: Editor): List<Pair<Inlay<*>, CursingColorShape>> {
        return pullExistingInlaysByOffset(editor).values.toList().flatten()
    }

    private suspend fun pullExistingInlaysByOffset(editor: Editor): Map<Int, List<Pair<Inlay<*>, CursingColorShape>>> {
        return withContext(Dispatchers.EDT) {
            editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
                .asSequence()
                .filter { it.getUserData(INLAY_KEY) != null }
                .groupBy { it.offset }
                .mapValues { it.value.map { inlay -> Pair(inlay, inlay.getUserData(INLAY_KEY) as CursingColorShape) } }
        }
    }


    private suspend fun createAndSetColorAndShapeManager(editor: Editor): ColorAndShapeManager {
        unsafeDataMutex.withLock {
            return editor.getOrCreateUserDataUnsafe(ColorAndShapeManager.KEY) {
                ColorAndShapeManager(preferenceService.colors, preferenceService.shapes)
            }
        }
    }

    private suspend fun createAndSetId(editor: Editor): Long {
        unsafeDataMutex.withLock {
            return editor.getOrCreateUserDataUnsafe(ID_KEY) {
                idGenerator.getAndIncrement()
            }
        }
    }


    private fun addColoredShapeAboveCursingToken(
        editor: Editor, cursingColorShape: CursingColorShape, offset: Int, character: Char
    ) {
        val renderer = ColoredShapeRenderer(cursingColorShape, character, offset)

        editor.inlayModel.addInlineElement(offset, false, Int.MIN_VALUE, renderer)
            ?.putUserData(INLAY_KEY, cursingColorShape)
    }



    class Debouncer(
        delay: Int,
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

        fun flush() {
            updateQueue.flush()
        }
    }

}
