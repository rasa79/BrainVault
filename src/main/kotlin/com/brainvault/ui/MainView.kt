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
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.SplitPane
import javafx.scene.control.TextInputDialog
import javafx.scene.control.TitledPane
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCombination
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
) {
    val root: BorderPane = BorderPane()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vaultTree = VaultTreeView(vaultService, noteService, vaultRoot)
    private val editor = EditorView()
    private val preview = PreviewView(noteService)
    private val statusBar = StatusBar()
    private val searchPanel = SearchPanel(searchService)
    private val favoritesPanel = FavoritesPanel(favoriteService)
    private val tagsPanel = TagsPanel(tagService)
    private val backlinksPanel = BacklinksPanel(backlinksFor)
    private val quickOpen = QuickOpenDialog(noteRepository)
    private val settingsDialog = SettingsDialog(settingsService)

    private var openNote: Note? = null
    private var currentFolder: String = ""

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

        root.top = menuBar()
        root.left = left
        root.center = center
        root.right = backlinksPanel.view
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
        vaultTree.onNoteSelected = { relPath ->
            vaultTree.refreshFromVault()
            openNote(relPath)
        }
        vaultTree.onRequestRefresh = { vaultTree.refreshFromVault() }
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
        scene.accelerators[KeyCombination.keyCombination("Shortcut+S")] = Runnable { save() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+N")] = Runnable { newNote() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+P")] = Runnable { quickOpen.show() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+Shift+F")] = Runnable { searchPanel.focusQuery() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+B")] = Runnable { toggleFavorite() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+Shift+D")] = Runnable { openDailyNote() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+Shift+R")] = Runnable { rebuildIndex() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+W")] = Runnable { closeNote() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+Shift+S")] = Runnable { saveAs() }
        scene.accelerators[KeyCombination.keyCombination("F2")] = Runnable { renameCurrent() }
        scene.accelerators[KeyCombination.keyCombination("Delete")] = Runnable { deleteCurrent() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+,")] = Runnable { settingsDialog.show() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+I")] = Runnable { importFolder() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+E")] = Runnable { export() }
        scene.accelerators[KeyCombination.keyCombination("Shortcut+Q")] = Runnable { Platform.exit() }
    }

    fun showInitialState() {
        statusBar.setVaultPath(vaultRoot.toString())
        vaultTree.refreshFromVault()
        favoritesPanel.refresh()
        tagsPanel.refresh()
        statusBar.showIdle(0)
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
            val note = withContext(Dispatchers.IO) { runCatching { noteService.read(vaultRoot, relPath) }.getOrNull() }
            if (note != null) {
                openNote = note
                Platform.runLater {
                    editor.load(note.body)
                    preview.update(note.body)
                    statusBar.showDirty(false)
                    backlinksPanel.showFor(note.path)
                }
            }
        }
    }

    private fun save() {
        val current = openNote ?: return
        val body = editor.currentText()
        val notes = noteService
        scope.launch {
            withContext(Dispatchers.IO) { notes.save(vaultRoot, current.copy(body = body)) }
            Platform.runLater {
                editor.markSaved()
                statusBar.showDirty(false)
                tagsPanel.refresh()
            }
        }
    }

    private fun newNote() {
        scope.launch {
            val note = withContext(Dispatchers.IO) { noteService.create(vaultRoot, currentFolder, "Untitled Note") }
            Platform.runLater {
                openNote = note
                editor.load(note.body)
                preview.update(note.body)
                statusBar.showDirty(false)
                vaultTree.refreshFromVault()
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
            Platform.runLater {
                vaultTree.refreshFromVault()
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
            Platform.runLater {
                vaultTree.refreshFromVault()
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
            Platform.runLater {
                openNote = null
                editor.load("")
                preview.update("")
                vaultTree.refreshFromVault()
                tagsPanel.refresh()
            }
        }
    }

    private fun toggleFavorite() {
        val current = openNote ?: return
        scope.launch {
            withContext(Dispatchers.IO) { favoriteService.toggle(current.path) }
            Platform.runLater { favoritesPanel.refresh() }
        }
    }

    private fun filterByTag(tagName: String) {
        if (tagName.isEmpty()) {
            vaultTree.refreshFromVault()
            return
        }
        val notes = tagService.notesFor(tagName)
        val paths = notes.map { it.path }
        if (paths.isEmpty()) {
            vaultTree.refreshFromVault()
            return
        }
        vaultTree.refresh(FolderNode(name = "Tag: $tagName", path = "", folders = emptyList(), notePaths = paths))
    }

    private fun openDailyNote() {
        scope.launch {
            val note = withContext(Dispatchers.IO) { dailyNoteService.openOrCreate(vaultRoot, LocalDate.now()) }
            Platform.runLater {
                openNote = note
                editor.load(note.body)
                preview.update(note.body)
                statusBar.showDirty(false)
                vaultTree.refreshFromVault()
            }
        }
    }

    private fun importFolder() {
        val dir = DirectoryChooser().apply { title = "Import Markdown folder" }.showDialog(root.scene?.window)
        if (dir == null) return
        scope.launch {
            val count = withContext(Dispatchers.IO) { importExportService.importFolder(vaultRoot, dir.toPath(), "") }
            withContext(Dispatchers.IO) {
                indexingService.fullRebuild(vaultRoot) { done, total ->
                    Platform.runLater { statusBar.showIndexing(done, total) }
                }
            }
            Platform.runLater {
                statusBar.showIdle(count)
                vaultTree.refreshFromVault()
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
        val dir = DirectoryChooser().apply { title = "Export vault to folder" }.showDialog(root.scene?.window)
        if (dir == null) return
        scope.launch {
            val count = withContext(Dispatchers.IO) { importExportService.exportToFolder(vaultRoot, dir.toPath()) }
            Platform.runLater {
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
            Platform.runLater {
                openNote = created.copy(body = body)
                editor.load(body)
                preview.update(body)
                statusBar.showDirty(false)
                vaultTree.refreshFromVault()
            }
        }
    }

    private fun rebuildIndex() {
        scope.launch {
            withContext(Dispatchers.IO) {
                indexingService.fullRebuild(vaultRoot) { done, total ->
                    Platform.runLater { statusBar.showIndexing(done, total) }
                }
            }
            Platform.runLater {
                statusBar.showIdle(0)
                vaultTree.refreshFromVault()
                favoritesPanel.refresh()
                tagsPanel.refresh()
            }
        }
    }
}
