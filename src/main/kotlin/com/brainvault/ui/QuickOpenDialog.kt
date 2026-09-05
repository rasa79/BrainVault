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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext

/**
 * Ctrl+P quick-open dialog. A text field filters the in-memory list of note
 * paths+titles by case-insensitive substring on each keystroke. Enter or
 * double-click chooses; Escape closes.
 *
 * The note list is loaded off the FX thread (via [scope]) and applied on the FX
 * thread, so the modal dialog never blocks the UI on a database read.
 */
class QuickOpenDialog(
    private val noteRepository: NoteRepository,
    private val scope: CoroutineScope,
) {

    var onChosen: (relPath: String) -> Unit = {}

    @Volatile
    private var entries: List<Pair<String, String>> = emptyList()

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

        fun filter() {
            val q = query.text.trim().lowercase()
            val e = entries
            val matches = if (q.isEmpty()) e else e.filter { (p, t) ->
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
                    graphic = VBox(2.0, title, path)
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

        // Load the note list off the FX thread, then populate the dialog.
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                noteRepository.allPaths().map { p -> p to (noteRepository.findByPath(p)?.title ?: p) }
            }
            entries = loaded
            withContext(Dispatchers.JavaFx) { filter() }
        }
        filter() // initial (empty) layout; repopulated when `loaded` arrives.

        dialog.showAndWait().ifPresent { onChosen(it) }
    }
}
