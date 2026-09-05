package com.brainvault.ui

import com.brainvault.application.NoteService
import com.brainvault.application.VaultService
import com.brainvault.domain.model.FolderNode
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import java.nio.file.Path

/**
 * The vault tree (left panel): folders expandable, notes as leaves, both sorted
 * per the domain model. Selecting a note fires [onNoteSelected]. No file I/O
 * here — actions are delegated to services.
 */
class VaultTreeView(
    private val vaultService: VaultService,
    private val noteService: NoteService,
    private val vaultRoot: Path,
) {
    val view: TreeView<String> = TreeView<String>()

    var onNoteSelected: (relPath: String) -> Unit = {}
    var onRequestRefresh: () -> Unit = {}

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
            if (selected != null && selected.value.endsWith(".md")) {
                onNoteSelected(selected.value)
            }
        }
    }

    /** Rebuilds the tree from a fresh [FolderNode] snapshot. */
    fun refresh(tree: FolderNode) {
        view.root = buildItem(tree)
    }

    fun refreshFromVault() {
        refresh(vaultService.tree(vaultRoot))
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
