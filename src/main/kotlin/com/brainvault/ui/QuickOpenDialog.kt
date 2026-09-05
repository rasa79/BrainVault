package com.brainvault.ui

import com.brainvault.domain.port.NoteRepository
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Ctrl+P quick-open dialog. A text field filters the in-memory list of note
 * paths+titles by case-insensitive substring on each keystroke (no debounce —
 * the list is small). Enter or double-click chooses; Escape closes.
 *
 * The plan injects the NoteRepository directly here (see §5.4) so the dialog can
 * list every note path.
 */
class QuickOpenDialog(private val noteRepository: NoteRepository) {

    var onChosen: (relPath: String) -> Unit = {}

    fun show() {
        val dialog = Dialog<String>()
        dialog.title = "Quick Open"
        dialog.headerText = "Jump to a note"

        val query = TextField()
        query.promptText = "Type to filter…"
        val list = ListView<String>()
        val content = VBox(6.0, query, list)
        VBox.setVgrow(list, Priority.ALWAYS)
        dialog.dialogPane.content = content

        val entries = noteRepository.allPaths().map { path ->
            path to (noteRepository.findByPath(path)?.title ?: path)
        }

        fun filter() {
            val q = query.text.trim().lowercase()
            val matches = if (q.isEmpty()) entries else entries.filter { (p, t) ->
                p.lowercase().contains(q) || t.lowercase().contains(q)
            }
            list.items.setAll(matches.map { it.first })
        }

        list.setCellFactory { _ ->
            object : ListCell<String>() {
                private val title = Label()
                private val path = Label()
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                        return
                    }
                    title.text = entries.firstOrNull { it.first == item }?.second ?: item
                    title.styleClass.add("search-hit-title")
                    path.text = item
                    path.styleClass.add("search-hit-path")
                    val box = VBox(2.0, title, path)
                    graphic = box
                }
            }
        }

        fun choose() {
            val sel = list.selectionModel.selectedItem ?: list.items.firstOrNull()
            if (sel != null) dialog.result = sel
            dialog.close()
        }

        query.textProperty().addListener { _, _, _ -> filter() }
        list.setOnMouseClicked { e -> if (e.clickCount >= 2) choose() }
        query.setOnKeyPressed { e ->
            when (e.code) {
                KeyCode.ENTER -> { choose(); e.consume() }
                KeyCode.ESCAPE -> dialog.close()
                KeyCode.DOWN -> list.requestFocus().also { list.selectionModel.selectFirst() }
                else -> Unit
            }
        }
        list.setOnKeyPressed { e ->
            when (e.code) {
                KeyCode.ENTER -> { choose(); e.consume() }
                KeyCode.ESCAPE -> dialog.close()
                else -> Unit
            }
        }

        filter()
        dialog.showAndWait().ifPresent { onChosen(it) }
    }
}
