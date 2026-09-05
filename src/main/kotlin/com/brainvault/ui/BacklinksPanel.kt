package com.brainvault.ui

import com.brainvault.domain.model.Link
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lists resolved backlinks (inbound links) for the current note. Clicking a
 * backlink navigates to it. Shows an explicit "no backlinks" placeholder.
 *
 * The `backlinksFor: (notePath) -> List<Link>` provider is injected by the
 * composition root; it is invoked off the FX thread and the result applied on
 * the FX thread.
 */
class BacklinksPanel(
    private val backlinksFor: (String) -> List<Link>,
    private val scope: CoroutineScope,
) {

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

    /** Loads backlinks off the FX thread and applies the result on the FX thread. */
    fun showFor(notePath: String) {
        scope.launch {
            val links = withContext(Dispatchers.IO) { backlinksFor(notePath) }
            Platform.runLater { apply(links) }
        }
    }

    private fun apply(links: List<Link>) {
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
