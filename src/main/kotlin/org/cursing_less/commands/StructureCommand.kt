package org.cursing_less.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilBase
import java.util.*

data object StructureCommand : VoiceCommand {

    override fun matches(command: String) = command == "psi"

    override fun run(commandParameters: List<String>, project: Project, editor: Editor?): String {
        if(editor != null) {
            val cursorMovement = commandParameters[0]
            val rawClasses =
                commandParameters.subList(1, commandParameters.size).joinToString(separator = " ").split(",")
            val startNavType = rawClasses[0]
            val classes = rawClasses.subList(1, rawClasses.size)

            ApplicationManager.getApplication().invokeAndWait {
                val psiFile = pullPsiFile(project, editor)
                if (psiFile != null) {
                    val startingOffset = editor.caretModel.offset
                    var currentElement: PsiElement? = PsiUtilBase.getElementAtOffset(psiFile, startingOffset)
                    printHierarchy(currentElement)
                    currentElement = parentElementOfNavType(currentElement, startNavType)

                    if (currentElement == null) {
                        return@invokeAndWait
                    }

                    for (clazz in classes) {
                        currentElement = childElementOfNavType(currentElement, clazz, startingOffset)
                        if (currentElement == null) {
                            return@invokeAndWait
                        }
                    }

                    // CurrentElement is where we want to go
                    val result = currentElement!!.textRange
                    when (cursorMovement) {
                        "start" -> {
                            editor.caretModel.moveToOffset(result.startOffset)
                        }

                        "end" -> {
                            editor.caretModel.moveToOffset(result.endOffset)
                        }

                        else -> {
                            editor.caretModel.moveToOffset(result.startOffset)
                            editor.selectionModel.setSelection(result.startOffset, result.endOffset)
                        }
                    }
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
                }
            }
        }
        return "OK"
    }

    private fun printHierarchy(element: PsiElement?) {
        var element = element
        while (element != null && element.node != null) {
            thisLogger().debug(
                element.toString() + " " + element.javaClass.name + " " + element
                    .navigationElement.toString() + " " + element.node.elementType.toString()
            )

            element = element.parent
        }
    }

    private fun parentElementOfNavType(element: PsiElement?, specifier: String): PsiElement? {
        var currentElement = element
        val navType = specifier.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
        while (currentElement != null && currentElement.node != null) {
            thisLogger()
                .debug("$element ${currentElement::class.java.name} ${currentElement.navigationElement} ${currentElement.node.elementType}")
            if (matches(navType, currentElement.node.elementType.toString())) {
                return currentElement
            }
            currentElement = currentElement.parent
        }
        return null
    }

    private fun childElementOfNavType(element: PsiElement?, specifier: String, offset: Int): PsiElement? {
        val toSearch: Queue<PsiElement> = ArrayDeque()
        val split = specifier.split("#".toRegex()).dropLastWhile { it.isEmpty() }
        val navType: String
        var direction: String? = null
        var index: Int
        if (split.size == 2) {
            navType = split[0]
            try {
                index = split[1].toInt()
            } catch (e: NumberFormatException) {
                direction = split[1]
                index = 0
            }
        } else {
            navType = specifier
            index = 0
        }
        val results: MutableList<PsiElement> = ArrayList()
        val seen: MutableSet<PsiElement> = HashSet()
        toSearch.add(element)
        while (toSearch.peek() != null) {
            val current = toSearch.remove()
            if (current.node != null && matches(
                    navType,
                    current.node.elementType.toString()
                )
            ) {
                results.add(current)
                //        continue;
            }
            var child = current.firstChild
            while (child != null) {
                if (!seen.contains(child)) {
                    seen.add(child)
                    toSearch.add(child)
                }
                child = child.nextSibling
            }
        }
        if (results.size == 0) {
            return null
        }
        thisLogger().debug("Results $results")
        if (direction == null) {
            if (index < 0) {
//      LOG.debug("Negative index bump " + index);
                index += results.size
                //      LOG.debug("Negative index bump " + index);
            }
            return results[index]
        } else if (direction == "next") {
            results.sortWith(Comparator.comparingInt { obj: PsiElement -> obj.textOffset })
            var best: PsiElement? = null
            var distance = 9999
            for (result in results) {
                thisLogger().debug("Result " + result + " offset: " + result.textRange.startOffset + " > " + offset)
                val dist = result.textRange.startOffset - offset
                if (dist in 1..distance) {
                    best = result
                    distance = dist
                }
            }
            return best
        } else if (direction == "last") {
            results.sortWith(Comparator { o1: PsiElement, o2: PsiElement ->
                -o1.textRange.endOffset.compareTo(o2.textRange.endOffset)
            })
            var best: PsiElement? = null
            var distance = 9999
            for (result in results) {
                thisLogger().debug(
                    ("Result " + result + " offset: " + result.textRange.endOffset + " < "
                            + offset)
                )
                val dist = offset - result.textRange.endOffset
                if (dist in 1..distance) {
                    best = result
                    distance = dist
                }
            }
            return best
        } else if (direction == "this") {
            var best: PsiElement? = null
            var size = 999999
            for (result in results) {
                if (result.textRange.contains(offset) && result.textLength < size) {
                    best = result
                    size = result.textLength
                }
            }
            return best
        }
        return null
    }

    private fun matches(navType: String, type: String): Boolean {
        val matchingTypes = navType.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (matchingType in matchingTypes) {
            thisLogger().debug("type " + type + " matches " + matchingType + "? " + type.matches(matchingType.toRegex()))
            var matchingType = matchingType
            if (!matchingType.startsWith("^")) {
                matchingType = ".*$matchingType"
            }
            if (type.matches(matchingType.toRegex())) {
                return true
            }
        }
        return false
    }

    private fun pullPsiFile(project: Project, editor: Editor) =
        PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
}
