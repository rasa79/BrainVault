package com.brainvault.ui

import com.brainvault.application.TagService
import com.brainvault.domain.model.Tag
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tag list showing "name (count)". Clicking a tag fires [onTagSelected]. Data is
 * loaded off the FX thread (via [scope]) and applied on the FX thread.
 */
class TagsPanel(
    private val tagService: TagService,
    private val scope: CoroutineScope,
) {

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
        scope.launch {
            val tags = withContext(Dispatchers.IO) { tagService.allTags() }
            Platform.runLater { list.items.setAll(tags) }
        }
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
