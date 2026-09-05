package com.brainvault.ui

import com.brainvault.application.SearchService
import com.brainvault.domain.model.SearchHit
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search-as-you-type panel: a query box with a 300 ms debounce and a results
 * list showing the title, vault-relative path, and a snippet whose `<b>`
 * highlight markers from FTS5 are rendered as bold text. Queries run off the FX
 * thread; stale results are discarded.
 */
class SearchPanel(
    private val searchService: SearchService,
    private val scope: CoroutineScope,
) {

    val view: VBox = VBox(6.0)
    var onHitSelected: (relPath: String) -> Unit = {}

    private val query: TextField = TextField()
    private val results: ListView<SearchHit> = ListView()
    private val debounce = PauseTransition(Duration.millis(300.0))
    private var generation = 0

    init {
        query.promptText = "Search notes…"
        debounce.setOnFinished { runSearch() }
        query.textProperty().addListener { _, _, _ -> debounce.playFromStart() }

        results.setCellFactory { _ -> SearchHitCell() }
        results.selectionModel.selectedItemProperty().addListener { _, _, hit ->
            if (hit != null) onHitSelected(hit.path)
        }

        view.children.addAll(query, results)
        VBox.setVgrow(results, Priority.ALWAYS)
    }

    fun focusQuery() {
        query.requestFocus()
    }

    fun clearQuery() {
        query.clear()
        results.items.clear()
    }

    private fun runSearch() {
        val q = query.text.trim()
        if (q.isEmpty()) {
            results.items.clear()
            return
        }
        val gen = ++generation
        scope.launch {
            val hits = withContext(Dispatchers.IO) { searchService.search(q) }
            Platform.runLater {
                if (gen == generation) results.items.setAll(hits)
            }
        }
    }

    private class SearchHitCell : ListCell<SearchHit>() {
        override fun updateItem(hit: SearchHit?, empty: Boolean) {
            super.updateItem(hit, empty)
            if (empty || hit == null) {
                text = ""
                graphic = null
                return
            }
            val title = Label(hit.title).apply { styleClass.add("search-hit-title") }
            val path = Label(hit.path).apply { styleClass.add("search-hit-path") }
            val snippet = TextFlow(*highlighted(hit.snippet).toTypedArray())
            val box = VBox(2.0, title, path, snippet)
            graphic = box
        }
    }
}

internal fun highlighted(snippet: String): List<Text> {
    val out = mutableListOf<Text>()
    var rest = snippet
    while (true) {
        val open = rest.indexOf("<b>")
        if (open < 0) {
            if (rest.isNotEmpty()) out += Text(rest)
            break
        }
        if (open > 0) out += Text(rest.substring(0, open))
        val close = rest.indexOf("</b>", open)
        if (close < 0) {
            out += Text(rest.substring(open))
            break
        }
        val bold = Text(rest.substring(open + 3, close)).apply { styleClass.add("search-hit-bold") }
        out += bold
        rest = rest.substring(close + 4)
    }
    return out
}
