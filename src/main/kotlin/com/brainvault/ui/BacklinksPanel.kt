package com.brainvault.ui

import com.brainvault.domain.model.Link
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Lists resolved backlinks (inbound links) for the current note. Clicking a
 * backlink navigates to it. Shows an explicit "no backlinks" placeholder.
 *
 * A `backlinksFor: (notePath: String) -> List<Link>` provider is injected by the
 * composition root so this panel never needs a repository/port import.
 */
class BacklinksPanel(private val backlinksFor: (String) -> List<Link>) {

    val view: VBox = VBox(6.0)
    var onBacklinkSelected: (relPath: String) -> Unit = {}

    private val label: Label = Label("Backlinks")
    private val list: ListView<Link> = ListView()

    init {
        list.setCellFactory { _ -> BacklinkCell() }
        list.selectionModel.selectedItemProperty().addListener { _, _, link ->
            if (link != null) onBacklinkSelected(link.sourcePath)
        }
        view.children.addAll(label, list)
        VBox.setVgrow(list, Priority.ALWAYS)
    }

    fun showFor(notePath: String) {
        val links = backlinksFor(notePath)
        if (links.isEmpty()) {
            list.items.clear()
            list.placeholder = Label("No backlinks")
        } else {
            list.items.setAll(links)
        }
    }

    private class BacklinkCell : ListCell<Link>() {
        override fun updateItem(link: Link?, empty: Boolean) {
            super.updateItem(link, empty)
            if (empty || link == null) {
                text = ""
                graphic = null
                return
            }
            val title = Label(link.sourcePath.substringAfterLast('/').removeSuffix(".md"))
            val path = Label(link.sourcePath).apply { styleClass.add("search-hit-path") }
            graphic = HBox(6.0, title, path)
        }
    }
}
