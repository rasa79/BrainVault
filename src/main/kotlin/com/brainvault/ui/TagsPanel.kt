package com.brainvault.ui

import com.brainvault.application.TagService
import com.brainvault.domain.model.Tag
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Tag list showing "name (count)". Clicking a tag fires [onTagSelected]; the
 * parent MainView filters the note list accordingly. A "clear filter" affordance
 * re-expands the full tree.
 */
class TagsPanel(private val tagService: TagService) {

    val view: VBox = VBox(6.0)
    var onTagSelected: (tagName: String) -> Unit = {}

    private val list: ListView<Tag> = ListView()
    private val clearLabel: Label = Label("Clear tag filter")

    init {
        clearLabel.styleClass.add("clear-filter")
        clearLabel.setOnMouseClicked { onTagSelected("") }
        list.setCellFactory { _ -> TagCell() }
        list.selectionModel.selectedItemProperty().addListener { _, _, tag ->
            if (tag != null) onTagSelected(tag.name)
        }
        view.children.addAll(clearLabel, list)
        VBox.setVgrow(list, Priority.ALWAYS)
    }

    fun refresh() {
        list.items.setAll(tagService.allTags())
    }

    private class TagCell : ListCell<Tag>() {
        override fun updateItem(tag: Tag?, empty: Boolean) {
            super.updateItem(tag, empty)
            if (empty || tag == null) {
                text = ""
            } else {
                text = "${tag.name} (${tag.noteCount})"
            }
        }
    }
}
