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
import org.cursing_less.MyBundle
import java.awt.*
import java.math.BigDecimal
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
    private val scaleSpinner = JSpinner(SpinnerNumberModel(state.scale,
        0.01,
        1.5,
        0.01))
    private val tokenPatternField = JBTextField(state.tokenPattern)
    private val usePsiTreeCheckbox = JBCheckBox("Use PSI Tree", state.usePsiTree)
    private val useRegexCheckbox = JBCheckBox("Use Regex", state.useRegex)

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
            addColorLabel(gbc, panel, 1, MyBundle.message("cursing_less.settings.label.light_mode"))
            addColorPanel(gbc, panel, 2, lightColorPanel, lightColor)


            // Add dark color label and panel
            addColorLabel(gbc, panel, 3, MyBundle.message("cursing_less.settings.label.dark_mode"))
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
            group(MyBundle.message("cursing_less.settings.group.colors")) {
                buildEditColorUi()
                buildNewColorUi()
            }

            group(MyBundle.message("cursing_less.settings.group.shapes")) {
                buildShapesUi()
            }

            group(MyBundle.message("cursing_less.settings.group.other")) {
                buildScaleUi()
                buildTokenUi()
            }

            row {
                button(MyBundle.message("cursing_less.settings.button.restore_defaults")) {
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
                .comment(MyBundle.message("cursing_less.settings.comment.colors"))
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildNewColorUi() {
        row {
            // Add new color controls
            label(MyBundle.message("cursing_less.settings.label.name"))
            cell(newColorNameField)
                .align(Align.FILL)
                .resizableColumn()
                .comment(MyBundle.message("cursing_less.settings.comment.new_color_name"))
            label(MyBundle.message("cursing_less.settings.label.light_mode"))
            cell(newLightColorPanel)
                .align(Align.FILL)
                .comment(MyBundle.message("cursing_less.settings.comment.light_color"))

            label(MyBundle.message("cursing_less.settings.label.dark_mode"))
            cell(newDarkColorPanel)
                .align(Align.FILL)
                .comment(MyBundle.message("cursing_less.settings.comment.dark_color"))

            button(MyBundle.message("cursing_less.settings.button.add_color")) {
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
                .comment(MyBundle.message("cursing_less.settings.comment.shapes"))
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildScaleUi() {
        row(MyBundle.message("cursing_less.settings.label.scale")) {
            cell(scaleSpinner)
                .align(Align.FILL)
                .comment(MyBundle.message("cursing_less.settings.comment.scale"))
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildTokenUi() {
        row(MyBundle.message("cursing_less.settings.label.token_pattern")) {
            cell(tokenPatternField)
                .align(Align.FILL)
                .resizableColumn()
                .comment(MyBundle.message("cursing_less.settings.comment.token_pattern"))
        }

        row {
            cell(usePsiTreeCheckbox)
                .align(Align.FILL)
                .comment("Enable token finding using PSI tree")
        }

        row {
            cell(useRegexCheckbox)
                .align(Align.FILL)
                .comment("Enable token finding using regex pattern")
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
                tokenPatternField.text != state.tokenPattern ||
                usePsiTreeCheckbox.isSelected != state.usePsiTree ||
                useRegexCheckbox.isSelected != state.useRegex
    }

    fun generateUpdatedState(): CursingPreferenceState {
        val colors = colorItems.values.map { it.getColorState() }
        val shapes = state.shapes.map { shapeState ->
            shapeState.copy(enabled = shapeCheckboxes[shapeState.name]?.isSelected ?: false)
        }
        val scale = scaleSpinner.value as Double
        val tokenPattern = tokenPatternField.text
        val usePsiTree = usePsiTreeCheckbox.isSelected
        val useRegex = useRegexCheckbox.isSelected

        return CursingPreferenceState(colors, shapes, scale, tokenPattern, usePsiTree, useRegex)
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
                colorState.generateLightColor(),
                colorState.generateDarkColor()
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
        usePsiTreeCheckbox.isSelected = state.usePsiTree
        useRegexCheckbox.isSelected = state.useRegex
    }

    /**
     * Reset to default values
     */
    private fun resetToDefaults() {
        load(CursingPreferenceState())
    }
}
