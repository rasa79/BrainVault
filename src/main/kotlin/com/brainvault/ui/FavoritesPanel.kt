package com.brainvault.ui

import com.brainvault.application.FavoriteService
import com.brainvault.domain.model.Note
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext

/**
 * Favorites list (title + path, ordered by `added_at`). Clicking opens the note;
 * a per-entry remove button toggles it off. Data loads and toggles off the FX
 * thread via [scope].
 */
class FavoritesPanel(
    private val favoriteService: FavoriteService,
    private val scope: CoroutineScope,
) {

    val view: VBox = VBox(6.0)
    var onFavoriteSelected: (relPath: String) -> Unit = {}

    private val list: ListView<Note> = ListView()

    init {
        list.setCellFactory { _ -> FavoriteCell(favoriteService, scope) }
        list.selectionModel.selectedItemProperty().addListener { _, _, note ->
            if (note != null) onFavoriteSelected(note.path)
        }
        view.children.addAll(list)
        VBox.setVgrow(list, Priority.ALWAYS)
    }

    fun refresh() {
        scope.launch {
            val notes = withContext(Dispatchers.IO) { favoriteService.list() }
            withContext(Dispatchers.JavaFx) { list.items.setAll(notes) }
        }
    }

    private class FavoriteCell(
        private val favorites: FavoriteService,
        private val scope: CoroutineScope,
    ) : ListCell<Note>() {
        override fun updateItem(note: Note?, empty: Boolean) {
            super.updateItem(note, empty)
            if (empty || note == null) {
                graphic = null
                text = ""
                return
            }
            val title = Label(note.title)
            val remove = Button("Remove").apply {
                setOnAction {
                    scope.launch {
                        val updated = withContext(Dispatchers.IO) {
                            favorites.toggle(note.path)
                            favorites.list()
                        }
                        withContext(Dispatchers.JavaFx) {
                            (listView ?: return@withContext).items.setAll(updated)
                        }
                    }
                }
            }
            graphic = HBox(6.0, title, remove)
        }
    }
}
