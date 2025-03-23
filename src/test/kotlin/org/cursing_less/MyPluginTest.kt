package org.cursing_less

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import org.cursing_less.listeners.CursingApplicationListener
import org.cursing_less.services.CursingMarkupService
import org.cursing_less.services.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.services.CursingPreferenceService

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testFoo() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        
        val cursingMarkupService = project.service<CursingMarkupService>()
        CursingApplicationListener.handler.initialized = true

        cursingMarkupService.updateCursingTokens(myFixture.editor, 0)

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val inlays = myFixture.editor.inlayModel.getInlineElementsInRange(0, 1000)
                .filter { it.getUserData(INLAY_KEY) != null }
                .toList()

            // assertEquals(1, inlays.size)
        }
        
    }

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    fun testProjectService() {
        val projectService = project.service<CursingPreferenceService>()

//        assertNotSame(projectService.getRandomNumber(), projectService.getRandomNumber())
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
