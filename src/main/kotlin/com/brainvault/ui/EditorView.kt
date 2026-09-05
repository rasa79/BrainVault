package com.brainvault.ui

import javafx.scene.control.TextArea

/**
 * The plain monospaced Markdown editor. It is deliberately WYSIWYG-free: a
 * single `TextArea` with no syntax highlighting in v1. Dirty tracking compares
 * the current text to the last loaded/saved text.
 */
class EditorView {

    val view: TextArea = TextArea()

    private var savedText: String = ""

    /** True when the current text differs from the last saved/loaded state. */
    val dirty: Boolean
        get() = view.text != savedText

    var onTextChanged: (String) -> Unit = {}

    var onSaveRequested: () -> Unit = {}

    init {
        view.isWrapText = true
        // Fired for both user edits and programmatic `load`; the consumer (preview)
        // debounces, and dirty-tracking is reset in `load`.
        view.textProperty().addListener { _, _, newText -> onTextChanged(newText) }
    }

    /** Loads [text] into the editor and resets the dirty state. */
    fun load(text: String) {
        savedText = text
        view.text = text
    }

    /** Marks the current text as saved (after a successful save). */
    fun markSaved() {
        savedText = view.text
    }

    fun currentText(): String = view.text
}
