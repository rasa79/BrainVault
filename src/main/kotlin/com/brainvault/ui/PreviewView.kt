package com.brainvault.ui

import com.brainvault.application.NoteService
import javafx.animation.PauseTransition
import javafx.scene.web.WebView
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext

/**
 * Live HTML preview in a [WebView]. Updates are debounced (300 ms); rendering
 * (flexmark) runs on [Dispatchers.Default] and the resulting HTML is pushed to
 * the WebView on [Dispatchers.JavaFx]. The HTML is self-contained (no external
 * resources — the offline rule).
 */
class PreviewView(private val noteService: NoteService) {

    val view: WebView = WebView()

    private val debounce = PauseTransition(Duration.millis(300.0))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingBody: String = ""

    init {
        debounce.setOnFinished {
            val body = pendingBody
            scope.launch {
                val html = withContext(Dispatchers.Default) { noteService.renderHtml(body) }
                withContext(Dispatchers.JavaFx) { view.engine.loadContent(html) }
            }
        }
    }

    /** Debounced render of the given Markdown body. */
    fun update(markdownBody: String) {
        pendingBody = markdownBody
        debounce.playFromStart()
    }
}
