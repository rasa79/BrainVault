package com.brainvault.ui

import javafx.geometry.Orientation
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority

/**
 * Bottom status bar: vault path, index status ("Indexing 12/240…", "N notes
 * indexed"), and a dirty/saved indicator for the open note.
 */
class StatusBar {

    val view: HBox = HBox(12.0)

    private val vaultLabel = Label()
    private val indexLabel = Label()
    private val dirtyLabel = Label()

    init {
        vaultLabel.styleClass.add("status-label")
        indexLabel.styleClass.add("status-label")
        dirtyLabel.styleClass.add("status-label")
        val spacer = javafx.scene.layout.Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        view.children.addAll(
            vaultLabel,
            Separator(Orientation.VERTICAL),
            indexLabel,
            spacer,
            dirtyLabel,
        )
    }

    fun showIndexing(done: Int, total: Int) {
        indexLabel.text = if (total > 0) "Indexing $done/$total…" else "Indexing…"
    }

    fun showIdle(noteCount: Int) {
        indexLabel.text = "$noteCount notes indexed"
    }

    fun showDirty(dirty: Boolean) {
        dirtyLabel.text = if (dirty) "● unsaved changes" else ""
        dirtyLabel.styleClass.removeAll("dirty")
        if (dirty) dirtyLabel.styleClass.add("dirty")
    }

    fun setVaultPath(path: String) {
        vaultLabel.text = path
    }
}
