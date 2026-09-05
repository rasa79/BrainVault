package com.brainvault.ui

import com.brainvault.application.SettingsService
import javafx.geometry.Insets
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.stage.DirectoryChooser

/**
 * Modal settings dialog (Ctrl+, / File → Settings). Edits the vault path, daily
 * note folder, daily filename pattern, and daily template. Save writes through
 * the setters and calls `SettingsService.persist()`; Cancel discards. A vault
 * path change takes effect only after an app restart (shown to the user).
 */
class SettingsDialog(private val settings: SettingsService) {

    fun show() {
        val dialog = Dialog<ButtonType>()
        dialog.title = "Settings"
        dialog.headerText = "BrainVault settings"

        val vaultPath = TextField(settings.vaultPath.toString())
        val browse = javafx.scene.control.Button("Browse…").apply {
            setOnAction {
                DirectoryChooser().apply { title = "Choose vault" }
                    .showDialog(dialog.dialogPane.scene?.window)
                    ?.let { vaultPath.text = it.toString() }
            }
        }
        val dailyFolder = TextField(settings.dailyFolder)
        val dailyPattern = TextField(settings.dailyPattern)
        val dailyTemplate = TextArea(settings.dailyTemplate).apply { prefRowCount = 5 }

        val grid = GridPane().apply {
            hgap = 8.0
            vgap = 8.0
            padding = Insets(12.0)
            addRow(0, Label("Vault path:"), HBox(6.0, vaultPath, browse))
            addRow(1, Label("Daily note folder:"), dailyFolder)
            addRow(2, Label("Daily filename pattern:"), dailyPattern)
            addRow(3, Label("Daily template:"), dailyTemplate)
        }
        val restartNote = Label("* Vault path changes take effect after restart.")
        dialog.dialogPane.content = javafx.scene.layout.VBox(grid, restartNote)

        dialog.dialogPane.buttonTypes.addAll(ButtonType.CANCEL, ButtonType.OK)

        val result = dialog.showAndWait().orElse(null)
        if (result == ButtonType.OK) {
            settings.vaultPath = java.nio.file.Path.of(vaultPath.text.trim())
            settings.dailyFolder = dailyFolder.text.trim()
            settings.dailyPattern = dailyPattern.text.trim()
            settings.dailyTemplate = dailyTemplate.text
            settings.persist()
        }
    }
}
