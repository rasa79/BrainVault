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

/**
 * Favorites list (title + path, ordered by `added_at`). Clicking opens the note;
 * a per-entry remove button toggles it off.
 */
class FavoritesPanel(private val favoriteService: FavoriteService) {

    val view: VBox = VBox(6.0)
    var onFavoriteSelected: (relPath: String) -> Unit = {}

    private val list: ListView<Note> = ListView()

    init {
        list.setCellFactory { _ -> FavoriteCell(onFavoriteSelected, favoriteService, ::refresh) }
        list.selectionModel.selectedItemProperty().addListener { _, _, note ->
            if (note != null) onFavoriteSelected(note.path)
        }
        view.children.addAll(list)
        VBox.setVgrow(list, Priority.ALWAYS)
    }

    fun refresh() {
        list.items.setAll(favoriteService.list())
    }

    private class FavoriteCell(
        private val onSelected: (String) -> Unit,
        private val favorites: FavoriteService,
        private val refresh: () -> Unit,
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
                    favorites.toggle(note.path)
                    refresh()
                }
            }
            graphic = HBox(6.0, title, remove)
        }
    }
}
