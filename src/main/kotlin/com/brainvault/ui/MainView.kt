package com.brainvault.ui

import com.brainvault.application.DailyNoteService
import com.brainvault.application.FavoriteService
import com.brainvault.application.ImportExportService
import com.brainvault.application.IndexingService
import com.brainvault.application.NoteService
import com.brainvault.application.SearchService
import com.brainvault.application.SettingsService
import com.brainvault.application.TagService
import com.brainvault.application.VaultService
import com.brainvault.domain.model.FolderNode
import com.brainvault.domain.model.Link
import com.brainvault.domain.model.Note
import com.brainvault.domain.port.NoteRepository
import javafx.application.Platform
import javafx.scene.control.Accordion
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ChoiceDialog
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.SplitPane
import javafx.scene.control.TextInputControl
import javafx.scene.control.TextInputDialog
import javafx.scene.control.TitledPane
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.LocalDate

/** The §8 shortcut map, single source of truth for the Help dialog list. */
private val SHORTCUT_HELP: List<Pair<String, String>> = listOf(
    "Ctrl+N" to "New note",
    "Ctrl+S" to "Save current note",
    "Ctrl+P" to "Quick open",
    "Ctrl+Shift+F" to "Focus search box",
    "Ctrl+Shift+D" to "Create/open today's daily note",
    "Ctrl+B" to "Toggle favorite on current note",
    "Ctrl+W" to "Close current note",
    "Ctrl+Shift+S" to "Save as… / save copy to folder",
    "F2" to "Rename current note",
    "Delete" to "Delete selected tree item",
    "Ctrl+Shift+R" to "Rebuild index",
    "Ctrl+," to "Settings dialog",
    "Ctrl+I" to "Import folder of Markdown files",
    "Ctrl+E" to "Export vault",
    "Ctrl+Q" to "Exit",
)

/** Makes a throwing coroutine visible (log to stderr) instead of being silently swallowed. */
private val EXCEPTION_HANDLER = CoroutineExceptionHandler { _, t ->
    System.err.println("BrainVault UI coroutine failed: $t")
    t.printStackTrace()
}

/**
 * The application shell. Layout: left accordion (vault tree, favorites, tags),
 * center [SplitPane] (editor | preview) below a search panel, right backlinks
 * panel, bottom [StatusBar], top [MenuBar].
 */
class MainView(
    private val vaultService: VaultService,
    private val noteService: NoteService,
    private val indexingService: IndexingService,
    private val settingsService: SettingsService,
    private val vaultRoot: Path,
    private val searchService: SearchService,
    private val favoriteService: FavoriteService,
    private val tagService: TagService,
    private val noteRepository: NoteRepository,
    private val backlinksFor: (String) -> List<Link>,
    private val dailyNoteService: DailyNoteService,
    private val importExportService: ImportExportService,
    private val dbDispatcher: CoroutineDispatcher,
) {
    val root: BorderPane = BorderPane()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + EXCEPTION_HANDLER)
    private val vaultTree = VaultTreeView(vaultService, noteService, vaultRoot, scope)
    private val editor = EditorView()
    private val preview = PreviewView(noteService)
    private val statusBar = StatusBar()
    private val searchPanel = SearchPanel(searchService, scope)
    private val favoritesPanel = FavoritesPanel(favoriteService, scope)
    private val tagsPanel = TagsPanel(tagService, scope)
    private val backlinksPanel = BacklinksPanel(backlinksFor, scope)
    private val quickOpen = QuickOpenDialog(noteRepository, scope)
    private val settingsDialog = SettingsDialog(settingsService)

    private var openNote: Note? = null
    private var currentFolder: String = ""
    private var tagFilterActive = false

    init {
        buildLayout()
        wireEvents()
    }

    private fun buildLayout() {
        val treePane = TitledPane("Vault", vaultTree.view.also { VBox.setVgrow(it, Priority.ALWAYS) })
        val favPane = TitledPane("Favorites", favoritesPanel.view.also { VBox.setVgrow(it, Priority.ALWAYS) })
        val tagPane = TitledPane("Tags", tagsPanel.view.also { VBox.setVgrow(it, Priority.ALWAYS) })
        val left = Accordion().apply {
            panes.addAll(treePane, favPane, tagPane)
            expandedPane = treePane
        }

        val split = SplitPane(editor.view, preview.view).apply {
            setDividerPositions(0.5)
        }
        SplitPane.setResizableWithParent(editor.view, true)
        SplitPane.setResizableWithParent(preview.view, true)
        val center = VBox(searchPanel.view, split)
        VBox.setVgrow(split, Priority.ALWAYS)

        // Center (search + editor/preview) and the backlinks panel share a resizable
        // horizontal SplitPane, so the backlinks panel has a usable default width and
        // can be dragged to resize.
        val centerAndBacklinks = SplitPane(center, backlinksPanel.view).apply {
            setDividerPositions(0.78)
        }
        SplitPane.setResizableWithParent(backlinksPanel.view, true)

        root.top = menuBar()
        root.left = left
        root.center = centerAndBacklinks
        root.bottom = statusBar.view
    }

    private fun menuBar(): MenuBar {
        val file = Menu("File")
        val newNote = MenuItem("New Note").apply { setOnAction { newNote() } }
        val save = MenuItem("Save").apply { setOnAction { save() } }
        val quickOpenItem = MenuItem("Quick Open…").apply { setOnAction { quickOpen.show() } }
        val importF = MenuItem("Import Folder…").apply { setOnAction { importFolder() } }
        val export = MenuItem("Export…").apply { setOnAction { export() } }
        val settings = MenuItem("Settings…").apply { setOnAction { settingsDialog.show() } }
        val exit = MenuItem("Exit").apply { setOnAction { Platform.exit() } }
        file.items.addAll(newNote, save, quickOpenItem, SeparatorMenuItem(), importF, export, SeparatorMenuItem(), settings, SeparatorMenuItem(), exit)

        val note = Menu("Note")
        val rename = MenuItem("Rename…").apply { setOnAction { renameCurrent() } }
        val move = MenuItem("Move…").apply { setOnAction { moveCurrent() } }
        val delete = MenuItem("Delete").apply { setOnAction { deleteCurrent() } }
        val favorite = MenuItem("Toggle Favorite").apply { setOnAction { toggleFavorite() } }
        val daily = MenuItem("Open Daily Note").apply { setOnAction { openDailyNote() } }
        note.items.addAll(rename, move, delete, SeparatorMenuItem(), favorite, daily)

        val tools = Menu("Tools")
        val rebuild = MenuItem("Rebuild Index").apply { setOnAction { rebuildIndex() } }
        tools.items.add(rebuild)

        val help = Menu("Help")
        val shortcuts = MenuItem("Keyboard Shortcuts").apply { setOnAction { showShortcuts() } }
        val about = MenuItem("About").apply {
            setOnAction {
                Alert(Alert.AlertType.INFORMATION).apply {
                    title = "About"
                    headerText = "BrainVault"
                    contentText = "Local-first Markdown personal knowledge management."
                    showAndWait()
                }
            }
        }
        help.items.addAll(shortcuts, about)

        return MenuBar(file, note, tools, help)
    }

    private fun wireEvents() {
        // Do NOT refresh the tree from the selection handler — rebuilding the
        // tree root during a clearAndSelect/mousePressed corrupts the TreeView
        // item list (IndexOutOfBoundsException). Selecting a note just opens it.
        vaultTree.onNoteSelected = { relPath -> openNote(relPath) }
        editor.onTextChanged = { text -> preview.update(text) }
        editor.onSaveRequested = { save() }
        editor.view.textProperty().addListener { _, _, _ -> statusBar.showDirty(editor.dirty) }
        searchPanel.onHitSelected = { openNote(it) }
        backlinksPanel.onBacklinkSelected = { openNote(it) }
        favoritesPanel.onFavoriteSelected = { openNote(it) }
        tagsPanel.onTagSelected = { tagName -> filterByTag(tagName) }
    }

    /** Binds scene accelerators (called by MainApp once the scene is attached). */
    fun bindSceneShortcuts() {
        val scene = root.scene ?: return
        val shortcuts = LinkedHashMap<KeyCombination, Runnable>()
        shortcuts[KeyCombination.keyCombination("Shortcut+S")] = Runnable { save() }
        shortcuts[KeyCombination.keyCombination("Shortcut+N")] = Runnable { newNote() }
        shortcuts[KeyCombination.keyCombination("Shortcut+P")] = Runnable { quickOpen.show() }
        shortcuts[KeyCombination.keyCombination("Shortcut+Shift+F")] = Runnable { searchPanel.focusQuery() }
        shortcuts[KeyCombination.keyCombination("Shortcut+B")] = Runnable { toggleFavorite() }
        shortcuts[KeyCombination.keyCombination("Shortcut+Shift+D")] = Runnable { openDailyNote() }
        shortcuts[KeyCombination.keyCombination("Shortcut+Shift+R")] = Runnable { rebuildIndex() }
        shortcuts[KeyCombination.keyCombination("Shortcut+W")] = Runnable { closeNote() }
        shortcuts[KeyCombination.keyCombination("Shortcut+Shift+S")] = Runnable { saveAs() }
        shortcuts[KeyCombination.keyCombination("F2")] = Runnable { renameCurrent() }
        shortcuts[KeyCombination.keyCombination("Delete")] = Runnable { deleteCurrent() }
        shortcuts[KeyCombination.keyCombination("Shortcut+,")] = Runnable { settingsDialog.show() }
        shortcuts[KeyCombination.keyCombination("Shortcut+I")] = Runnable { importFolder() }
        shortcuts[KeyCombination.keyCombination("Shortcut+E")] = Runnable { export() }
        shortcuts[KeyCombination.keyCombination("Shortcut+Q")] = Runnable { Platform.exit() }

        // Capture-phase filter so the §8 shortcuts fire even when a focused
        // TextArea/TextField consumes the key event (the cause of Ctrl+P no-oping).
        scene.addEventFilter(KeyEvent.KEY_PRESSED) { evt ->
            // Delete is tree-scoped: never intercept while editing text.
            if (evt.code == KeyCode.DELETE && scene.focusOwner is TextInputControl) {
                return@addEventFilter
            }
            for ((combo, action) in shortcuts) {
                if (combo.match(evt)) {
                    action.run()
                    evt.consume()
                    return@addEventFilter
                }
            }
            // Escape: clear tag filter / clear search (contextual, non-dialog).
            if (evt.code == KeyCode.ESCAPE) {
                clearTagFilter()
                searchPanel.clearQuery()
                evt.consume()
            }
        }
    }

    fun showInitialState() {
        statusBar.setVaultPath(vaultRoot.toString())
        refreshTree()
        favoritesPanel.refresh()
        tagsPanel.refresh()
        statusBar.showIdle(0)
    }

    private fun refreshTree() = vaultTree.refreshAsync()

    /** Refresh the tree + derived panels after a vault change (file events). */
    fun onVaultChanged() {
        refreshTree()
        tagsPanel.refresh()
        favoritesPanel.refresh()
    }

    fun showIndexing(done: Int, total: Int) = statusBar.showIndexing(done, total)

    fun showIdle(noteCount: Int) = statusBar.showIdle(noteCount)

    private fun showShortcuts() {
        val text = SHORTCUT_HELP.joinToString("\n") { (k, v) -> "$k  → $v" }
        Alert(Alert.AlertType.INFORMATION).apply {
            title = "Keyboard Shortcuts"
            headerText = "BrainVault shortcuts"
            contentText = text
            showAndWait()
        }
    }

    private fun openNote(relPath: String) {
        scope.launch {
            val note = withContext(Dispatchers.IO) { runCatching { noteService.read(vaultRoot, relPath) }.getOrNull() } ?: return@launch
            val currentPath = openNote?.path
            val currentTitle = openNote?.title
            withContext(Dispatchers.JavaFx) {
                if (editor.dirty) {
                    if (currentPath == relPath) {
                        // Same note already open with unsaved edits — don't discard.
                        return@withContext
                    }
                    val ok = Alert(Alert.AlertType.CONFIRMATION).apply {
                        title = "Discard changes"
                        headerText = "Discard unsaved changes to \"${currentTitle ?: currentPath}\"?"
                        contentText = "Your changes will be lost."
                    }.showAndWait().orElse(null)
                    if (ok != ButtonType.OK) return@withContext
                }
                openNote = note
                editor.load(note.body)
                preview.update(note.body)
                statusBar.showDirty(false)
                backlinksPanel.showFor(note.path)
            }
        }
    }

    private fun save() {
        val current = openNote ?: return
        val body = editor.currentText()
        val notes = noteService
        scope.launch {
            withContext(Dispatchers.IO) { notes.save(vaultRoot, current.copy(body = body)) }
            // Index the saved note immediately (on the serialized DB scope) so its
            // extracted links/backlinks reflect the just-written content right away —
            // not merely after an async watcher event that may race with the next
            // open. Otherwise backlinks stay empty.
            withContext(dbDispatcher) { indexingService.indexOne(vaultRoot, current.path) }
            withContext(Dispatchers.JavaFx) {
                editor.markSaved()
                statusBar.showDirty(false)
                tagsPanel.refresh()
            }
        }
    }

    private fun newNote() {
        scope.launch {
            val note = withContext(Dispatchers.IO) { noteService.create(vaultRoot, currentFolder, "Untitled Note") }
            withContext(Dispatchers.JavaFx) {
                openNote = note
                editor.load(note.body)
                preview.update(note.body)
                statusBar.showDirty(false)
                refreshTree()
            }
        }
    }

    // ============================================================
    // LEARN[KJV-020] Scope functions (let/apply/run/also/with)
    // Kotlin:
    //   `TextInputDialog().apply { ... }` runs a block *on* the receiver and
    //   returns the receiver — perfect for configuring a JavaFX node/property.
    //   `.also { ... }` (used in buildLayout) runs a block for its side effect
    //   and returns the receiver; `.let { ... }` runs on a non-null receiver and
    //   returns the block's result (used with `?.let`). `with(obj) { }` is the
    //   non-extension form and `run { }` returns block + runs on receiver.
    // Java 25 equivalent:
    //   TextInputDialog d = new TextInputDialog(); d.setTitle(...); d.setHeaderText(...);
    //   return d;  — verbose manual mutation, no "configure-and-return" idiom.
    // Differences:
    //   - `apply`/`also` return the receiver for chaining; `let`/`run` return the
    //     block result. The Java equivalent needs an explicit local variable.
    // ============================================================
    private fun renameCurrent() {
        val current = openNote ?: return
        val dialog = TextInputDialog(current.title).apply {
            title = "Rename Note"
            headerText = "New name"
        }
        val name = dialog.showAndWait().orElse(null) ?: return
        scope.launch {
            val newPath = withContext(Dispatchers.IO) { noteService.rename(vaultRoot, current.path, name) }
            withContext(Dispatchers.JavaFx) {
                refreshTree()
                openNote(newPath)
            }
        }
    }

    private fun moveCurrent() {
        val current = openNote ?: return
        val dialog = TextInputDialog().apply {
            title = "Move Note"
            headerText = "Target folder (vault-relative)"
            contentText = "Folder:"
        }
        val folder = dialog.showAndWait().orElse(null) ?: return
        scope.launch {
            val newPath = withContext(Dispatchers.IO) { noteService.move(vaultRoot, current.path, folder) }
            withContext(Dispatchers.JavaFx) {
                refreshTree()
                openNote(newPath)
            }
        }
    }

    private fun deleteCurrent() {
        val current = openNote ?: return
        val confirmed = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Delete"
            headerText = "Delete ${current.title}?"
            contentText = "This cannot be undone."
        }.showAndWait().orElse(null)
        if (confirmed != javafx.scene.control.ButtonType.OK) return
        scope.launch {
            withContext(Dispatchers.IO) { noteService.delete(vaultRoot, current.path) }
            withContext(Dispatchers.JavaFx) {
                openNote = null
                editor.load("")
                preview.update("")
                refreshTree()
                tagsPanel.refresh()
            }
        }
    }

    private fun toggleFavorite() {
        val current = openNote ?: return
        scope.launch {
            withContext(Dispatchers.IO) { favoriteService.toggle(current.path) }
            withContext(Dispatchers.JavaFx) { favoritesPanel.refresh() }
        }
    }

    private fun filterByTag(tagName: String) {
        tagFilterActive = tagName.isNotEmpty()
        if (tagName.isEmpty()) {
            refreshTree()
            return
        }
        // Load off the FX thread and apply on FX deferred — never rebuild the
        // tree from within a selection-change event.
        scope.launch {
            val tree = withContext(Dispatchers.IO) {
                val paths = tagService.notesFor(tagName).map { it.path }
                if (paths.isEmpty()) {
                    vaultService.tree(vaultRoot)
                } else {
                    FolderNode(name = "Tag: $tagName", path = "", folders = emptyList(), notePaths = paths)
                }
            }
            withContext(Dispatchers.JavaFx) { vaultTree.refresh(tree) }
        }
    }

    private fun clearTagFilter() {
        if (tagFilterActive) {
            tagFilterActive = false
            refreshTree()
        }
    }

    private fun openDailyNote() {
        scope.launch {
            val note = withContext(Dispatchers.IO) { dailyNoteService.openOrCreate(vaultRoot, LocalDate.now()) }
            withContext(Dispatchers.JavaFx) {
                openNote = note
                editor.load(note.body)
                preview.update(note.body)
                statusBar.showDirty(false)
                refreshTree()
            }
        }
    }

    private fun importFolder() {
        val dir = DirectoryChooser().apply { title = "Import Markdown folder" }.showDialog(root.scene?.window)
        if (dir == null) return
        scope.launch {
            val count = withContext(Dispatchers.IO) { importExportService.importFolder(vaultRoot, dir.toPath(), "") }
            val totalNotes = withContext(dbDispatcher) {
                indexingService.fullRebuild(vaultRoot) { done, total ->
                    Platform.runLater { statusBar.showIndexing(done, total) }
                }
                noteRepository.count()
            }
            withContext(Dispatchers.JavaFx) {
                statusBar.showIdle(totalNotes)
                refreshTree()
                Alert(Alert.AlertType.INFORMATION).apply {
                    title = "Import"
                    headerText = "Imported $count note(s)"
                    contentText = "The index has been rebuilt."
                    showAndWait()
                }
            }
        }
    }

    private fun export() {
        val folderLabel = "Export to folder…"
        val zipLabel = "Export to .zip…"
        val choice = ChoiceDialog(folderLabel, folderLabel, zipLabel).apply {
            title = "Export"
            headerText = "How would you like to export the vault?"
        }
        val selected = choice.showAndWait().orElse(null) ?: return
        if (selected == zipLabel) {
            val file = FileChooser().apply {
                title = "Export vault to .zip"
                extensionFilters.add(FileChooser.ExtensionFilter("Zip archive", "*.zip"))
                initialFileName = "BrainVault.zip"
            }.showSaveDialog(root.scene?.window)
            if (file != null) {
                scope.launch {
                    withContext(Dispatchers.IO) { importExportService.exportToZip(vaultRoot, file.toPath()) }
                    withContext(Dispatchers.JavaFx) {
                        statusBar.showIdle(0)
                        Alert(Alert.AlertType.INFORMATION).apply {
                            title = "Export"
                            headerText = "Exported vault to zip"
                            contentText = file.toString()
                            showAndWait()
                        }
                    }
                }
            }
        } else {
            val dir = DirectoryChooser().apply { title = "Export vault to folder" }.showDialog(root.scene?.window)
            if (dir != null) {
                scope.launch {
                    val count = withContext(Dispatchers.IO) { importExportService.exportToFolder(vaultRoot, dir.toPath()) }
                    withContext(Dispatchers.JavaFx) {
                        statusBar.showIdle(count)
                        Alert(Alert.AlertType.INFORMATION).apply {
                            title = "Export"
                            headerText = "Exported $count note(s)"
                            contentText = "Folder: $dir"
                            showAndWait()
                        }
                    }
                }
            }
        }
    }

    private fun closeNote() {
        val current = openNote ?: return
        if (editor.dirty) {
            val ok = Alert(Alert.AlertType.CONFIRMATION).apply {
                title = "Close Note"
                headerText = "Discard unsaved changes to ${current.title}?"
            }.showAndWait().orElse(null)
            if (ok != ButtonType.OK) return
        }
        openNote = null
        editor.load("")
        preview.update("")
        statusBar.showDirty(false)
        backlinksPanel.showFor("")
    }

    private fun saveAs() {
        val current = openNote ?: return
        val dialog = TextInputDialog().apply {
            title = "Save As"
            headerText = "Target folder (vault-relative)"
            contentText = "Folder:"
        }
        val folder = dialog.showAndWait().orElse(null) ?: return
        val body = editor.currentText()
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                val base = noteService.create(vaultRoot, folder, current.title)
                noteService.save(vaultRoot, base.copy(body = body))
                base
            }
            // The saved copy may carry links; index it so backlinks are correct.
            withContext(dbDispatcher) { indexingService.indexOne(vaultRoot, created.path) }
            withContext(Dispatchers.JavaFx) {
                openNote = created.copy(body = body)
                editor.load(body)
                preview.update(body)
                statusBar.showDirty(false)
                refreshTree()
            }
        }
    }

    private fun rebuildIndex() {
        scope.launch {
            // Rebuild on the serialized DB scope (single-concurrency), with progress
            // callbacks to the status bar; on completion refresh the tree/panels.
            val count = withContext(dbDispatcher) {
                indexingService.fullRebuild(vaultRoot) { done, total ->
                    Platform.runLater { statusBar.showIndexing(done, total) }
                }
                noteRepository.count()
            }
            withContext(Dispatchers.JavaFx) {
                statusBar.showIdle(count)
                refreshTree()
                favoritesPanel.refresh()
                tagsPanel.refresh()
            }
        }
    }
}
