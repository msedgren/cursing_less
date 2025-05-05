package org.cursing_less.settings

import com.intellij.ui.ColorPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Settings panel for Cursing Less plugin preferences.
 */
class CursingPreferenceSettingsPanel(private val state: CursingPreferenceState) {

    // Main panel
    val panel: JPanel = JPanel(BorderLayout())

    // UI components
    private val colorsPanel = JPanel(GridLayout(0, 1, 5, 5))
    private val colorItems = mutableMapOf<String, ColorItem>()
    private val shapesPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    private val shapeCheckboxes = mutableMapOf<String, JBCheckBox>()
    private val scaleSpinner = JSpinner(SpinnerNumberModel(state.scale, 0.1, 2.0, 0.1))
    private val tokenPatternField = JBTextField(state.tokenPattern)

    // New color components
    private val newColorNameField = JBTextField()
    private val newLightColorPanel = ColorPanel()
    private val newDarkColorPanel = ColorPanel()

    // Inner class for color items
    private inner class ColorItem(
        val name: String,
        enabled: Boolean,
        lightColor: Color,
        darkColor: Color
    ) {
        val panel = JPanel(GridBagLayout())
        val checkbox = JBCheckBox(name, enabled)
        val lightColorPanel = ColorPanel()
        val darkColorPanel = ColorPanel()

        init {
            val gbc = GridBagConstraints()
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.anchor = GridBagConstraints.WEST
            gbc.insets = JBUI.emptyInsets()

            // Add checkbox
            addCheckbox(gbc, panel, 0, 1.0, checkbox)

            // Add light mode label and panel
            addColorLabel(gbc, panel, 1, "Light Mode:")
            addColorPanel(gbc, panel, 2, lightColorPanel, lightColor)


            // Add dark color label and panel
            addColorLabel(gbc, panel, 3, "Dark Mode:")
            addColorPanel(gbc, panel, 4, darkColorPanel, darkColor)
        }

        private fun addCheckbox(
            gbc: GridBagConstraints,
            panel: JPanel,
            gridX: Int,
            weight: Double,
            checkbox: JBCheckBox
        ) {
            gbc.gridx = gridX
            gbc.gridy = 0
            gbc.weightx = weight
            gbc.insets = JBUI.insetsRight(100) // Add more space after the name
            panel.add(checkbox, gbc)
        }

        private fun addColorPanel(
            gbc: GridBagConstraints,
            panel: JPanel,
            gridX: Int,
            colorPanel: ColorPanel,
            color: Color
        ) {
            gbc.gridx = gridX
            gbc.gridy = 0
            gbc.weightx = 0.0 // Take up remaining space
            gbc.insets = JBUI.insetsRight(20)
            colorPanel.selectedColor = color
            panel.add(colorPanel, gbc)
        }

        private fun addColorLabel(gbc: GridBagConstraints, panel: JPanel, gridX: Int, labelText: String) {
            val label = JLabel("")
            label.preferredSize = Dimension(80, label.preferredSize.height)
            gbc.gridx = gridX
            gbc.gridy = 0
            gbc.weightx = 0.0
            gbc.insets = JBUI.insetsRight(10)
            panel.add(label, gbc)
        }


        fun getColorState(): CursingPreferenceState.ColorState {
            return CursingPreferenceState.ColorState(
                name,
                checkbox.isSelected,
                ColorUtil.toHex(lightColorPanel.selectedColor ?: JBColor.foreground()),
                ColorUtil.toHex(darkColorPanel.selectedColor ?: JBColor.foreground())
            )
        }
    }

    init {
        setupUI()
        load(state)
    }

    private fun setupUI() {
        // Create the main panel using IntelliJ's UI DSL
        val mainPanel = panel {
            group("Colors") {
                buildEditColorUi()
                buildNewColorUi()
            }

            group("Shapes") {
                buildShapesUi()
            }

            group("Other Settings") {
                buildScaleUi()
                buildTokenUi()
            }

            row {
                button("Restore Defaults") {
                    resetToDefaults()
                }
            }
        }

        // Add the main panel to a scroll pane
        panel.add(JBScrollPane(mainPanel), BorderLayout.CENTER)
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildEditColorUi() {
        row {
            // Setup colors panel
            cell(JBScrollPane(colorsPanel))
                .align(Align.FILL)
                .resizableColumn()
                .comment("Enable or disable colors and edit their properties")
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildNewColorUi() {
        row {
            // Add new color controls
            label("Name:")
            cell(newColorNameField)
                .align(Align.FILL)
                .resizableColumn()
                .comment("Enter a name for the new color")
            label("Light Mode:")
            cell(newLightColorPanel)
                .align(Align.FILL)
                .comment("Select a light mode color")

            label("Dark Mode:")
            cell(newDarkColorPanel)
                .align(Align.FILL)
                .comment("Select a dark mode color")

            button("Add Color") {
                addColor()
            }
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildShapesUi() {
        row {
            // Setup shapes panel with checkboxes
            for (shapeState in state.shapes) {
                val checkbox = JBCheckBox(shapeState.name, shapeState.enabled)
                shapeCheckboxes[shapeState.name] = checkbox
                shapesPanel.add(checkbox)
            }
            cell(shapesPanel)
                .align(Align.FILL)
                .comment("Enable or disable shapes")
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildScaleUi() {
        row("Scale:") {
            cell(scaleSpinner)
                .align(Align.FILL)
                .comment("Adjust the scale of the cursing marks")
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildTokenUi() {
        row("Token Pattern:") {
            cell(tokenPatternField)
                .align(Align.FILL)
                .resizableColumn()
                .comment("Regular expression pattern for tokenizing text")
        }
    }


    private fun addColor() {
        val name = newColorNameField.text.trim()
        val lightColor = newLightColorPanel.selectedColor
        val darkColor = newDarkColorPanel.selectedColor

        if (name.isNotEmpty() && lightColor != null && darkColor != null) {
            // Check if a color with this name already exists
            if (!colorItems.containsKey(name)) {
                // Add new color item
                val colorItem = ColorItem(name, true, lightColor, darkColor)
                colorItems[name] = colorItem
                colorsPanel.add(colorItem.panel)
                colorsPanel.revalidate()
                colorsPanel.repaint()

                // Clear the fields
                newColorNameField.text = ""
                newLightColorPanel.selectedColor = JBColor.foreground()
                newDarkColorPanel.selectedColor = JBColor.foreground()
            }
        }
    }

    /**
     * Check if the settings have been modified
     */
    fun isModified(state: CursingPreferenceState): Boolean {
        val shapesChanged = state.shapes.any {
            val checkbox = shapeCheckboxes[it.name]
            checkbox != null && checkbox.isSelected != it.enabled
        }

        // Get current colors from UI
        val currentColors = colorItems.values.map { it.getColorState() }

        // Check other settings
        return shapesChanged ||
                currentColors != state.colors ||
                (scaleSpinner.value as Double) != state.scale ||
                tokenPatternField.text != state.tokenPattern
    }

    fun generateUpdatedState(): CursingPreferenceState {
        val colors = colorItems.values.map { it.getColorState() }
        val shapes = state.shapes.map { shapeState ->
            shapeState.copy(enabled = shapeCheckboxes[shapeState.name]?.isSelected ?: false)
        }
        val scale = scaleSpinner.value as Double
        val tokenPattern = tokenPatternField.text

        return CursingPreferenceState(colors, shapes, scale, tokenPattern)
    }

    /**
     * Reset UI to match current settings
     */
    fun load(state: CursingPreferenceState) {
        // Reset colors
        colorsPanel.removeAll()
        colorItems.clear()

        for (colorState in state.colors) {
            val colorItem = ColorItem(
                colorState.name,
                colorState.enabled,
                colorState.lightColor,
                colorState.darkColor
            )
            colorItems[colorState.name] = colorItem
            colorsPanel.add(colorItem.panel)
        }

        colorsPanel.revalidate()
        colorsPanel.repaint()

        // Reset shapes
        for (shapeState in state.shapes) {
            val checkbox = shapeCheckboxes[shapeState.name]
            if (checkbox != null) {
                checkbox.isSelected = shapeState.enabled
            }
        }

        // Reset other settings
        scaleSpinner.value = state.scale
        tokenPatternField.text = state.tokenPattern
    }

    /**
     * Reset to default values
     */
    private fun resetToDefaults() {
        load(CursingPreferenceState())
    }
}
