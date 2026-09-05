package com.brainvault.ui

import com.brainvault.application.NoteService
import javafx.application.Platform
import javafx.animation.PauseTransition
import javafx.scene.web.WebView
import javafx.util.Duration
import java.util.concurrent.Executors

/**
 * Live HTML preview in a [WebView]. Updates are debounced (300 ms) and rendering
 * runs off the JavaFX Application Thread; the self-contained HTML is loaded via
 * `loadContent` (no external resources — the offline rule).
 */
class PreviewView(private val noteService: NoteService) {

    val view: WebView = WebView()

    private val debounce = PauseTransition(Duration.millis(300.0))
    private val renderExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "brainvault-preview-render").apply { isDaemon = true }
    }
    private var pendingBody: String = ""

    init {
        debounce.setOnFinished {
            val body = pendingBody
            renderExecutor.submit {
                val html = noteService.renderHtml(body)
                Platform.runLater { view.engine.loadContent(html) }
            }
        }
    }

    /** Debounced render of the given Markdown body. */
    fun update(markdownBody: String) {
        pendingBody = markdownBody
        debounce.playFromStart()
    }
}
