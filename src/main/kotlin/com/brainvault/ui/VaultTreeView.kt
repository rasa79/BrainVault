package com.brainvault.ui

import com.brainvault.application.NoteService
import com.brainvault.application.VaultService
import com.brainvault.domain.model.FolderNode
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * The vault tree (left panel): folders expandable, notes as leaves, both sorted
 * per the domain model. Selecting a note fires [onNoteSelected].
 *
 * Threading: the tree model is built off the FX thread and **applied on the FX
 * thread** via `refresh(tree)`, which is selection-safe (it clears the old
 * selection before replacing the root and restores the previously selected note
 * if it still exists). Nothing here mutates the TreeView from a background
 * thread, and `refresh` is never called from within a selection handler.
 */
class VaultTreeView(
    private val vaultService: VaultService,
    private val noteService: NoteService,
    private val vaultRoot: Path,
    private val scope: CoroutineScope,
) {
    val view: TreeView<String> = TreeView<String>()

    var onNoteSelected: (relPath: String) -> Unit = {}

    /** True while we are programmatically re-selecting after a refresh. */
    private var programmaticSelection = false

    init {
        view.isShowRoot = false
        view.setCellFactory { _ ->
            object : TreeCell<String>() {
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = ""
                    } else {
                        text = if (item.endsWith(".md")) item.substringAfterLast('/') else item
                    }
                }
            }
        }
        view.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (!programmaticSelection && selected != null && selected.value.endsWith(".md")) {
                onNoteSelected(selected.value)
            }
        }
    }

    /**
     * Rebuilds the tree asynchronously: build the model on IO, apply it on the FX
     * thread. Safe to call from anywhere (including a background thread).
     */
    fun refreshAsync() {
        scope.launch {
            val tree = withContext(Dispatchers.IO) { vaultService.tree(vaultRoot) }
            withContext(Dispatchers.JavaFx) { refresh(tree) }
        }
    }

    /** Applies a [FolderNode] snapshot. Selection-safe; must run on the FX thread. */
    fun refresh(tree: FolderNode) {
        val selectedPath = selectionPath()
        view.selectionModel.clearSelection()
        view.root = buildItem(tree)
        // Restore the previously selected note (best-effort) if it still exists.
        if (selectedPath != null) {
            programmaticSelection = true
            selectPath(selectedPath)
            programmaticSelection = false
        }
    }

    private fun selectionPath(): String? {
        val value = view.selectionModel.selectedItem?.value
        return if (value != null && value.endsWith(".md")) value else null
    }

    private fun selectPath(path: String) {
        val target = findItem(view.root, path) ?: return
        var p = target.parent
        while (p != null) {
            p.isExpanded = true
            p = p.parent
        }
        view.selectionModel.select(target)
    }

    private fun findItem(node: TreeItem<String>?, path: String): TreeItem<String>? {
        if (node == null) return null
        if (node.value == path) return node
        for (child in node.children) {
            val found = findItem(child, path)
            if (found != null) return found
        }
        return null
    }

    private fun buildItem(node: FolderNode): TreeItem<String> {
        val item = TreeItem<String>(node.path)
        node.folders.forEach { item.children.add(buildItem(it)) }
        node.notePaths.forEach { notePath ->
            item.children.add(TreeItem<String>(notePath))
        }
        return item
    }
}
