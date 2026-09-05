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
import com.brainvault.domain.model.Link
import com.brainvault.infrastructure.config.PropertiesSettingsStore
import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.infrastructure.fs.VaultWatcher
import com.brainvault.infrastructure.markdown.FrontMatterCodec
import com.brainvault.infrastructure.markdown.MarkdownParser
import com.brainvault.infrastructure.persistence.Database
import com.brainvault.infrastructure.persistence.Fts5SearchIndex
import com.brainvault.infrastructure.persistence.SqliteFavoriteRepository
import com.brainvault.infrastructure.persistence.SqliteLinkRepository
import com.brainvault.infrastructure.persistence.SqliteNoteRepository
import com.brainvault.infrastructure.persistence.SqliteTagRepository
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

/**
 * Application entry point and the single composition root: it constructs every
 * infrastructure implementation, injects them into application services, and
 * injects those into the UI. Manual constructor wiring — no DI framework.
 */
class MainApp : Application() {

    private lateinit var database: Database
    private lateinit var vaultWatcher: VaultWatcher
    private lateinit var settingsService: SettingsService

    // ============================================================
    // LEARN[KJV-019] Coroutines vs virtual threads / CompletableFuture
    // Kotlin:
    //   `CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))`
    //   is a lightweight concurrency primitive. `launch { ... }` schedules a
    //   suspendable block that may hop between dispatchers (IO here) cheaply. The
    //   scope defines the lifetime; SupervisorJob keeps one child failure from
    //   cancelling siblings. This serializes all DB/index writes to one thread.
    // Java 25 equivalent:
    //   Executors.newFixedThreadPool(1) or a CompletableFuture chain with a
    //   virtual thread (Thread.ofVirtual().start(...)) — heavier, and combining
    //   structured cancellation with off-thread UI updates requires manual work.
    // Differences:
    //   - Coroutines are structured (a parent scope owns child jobs) and
    //     suspend without blocking a thread.
    //   - `Dispatchers.JavaFx` lets results hop back to the FX thread natively;
    //     Java would use Platform.runLater(...) or a Task.
    // ============================================================
    // All database/index writes are serialized through this single-concurrency scope.
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun start(stage: Stage) {
        // 1. Application settings (§5.4 step 1).
        settingsService = SettingsService(
            PropertiesSettingsStore(
                Path.of(System.getProperty("user.home"), ".brainvault", "config.properties"),
            ),
        )

        // 2. Resolve the vault path, creating it if absent (§5.4 step 2).
        val vaultRoot = settingsService.vaultPath
        Files.createDirectories(vaultRoot)

        // 3. Index database + schema (§5.4 step 3).
        database = Database(vaultRoot.resolve(".brainvault").resolve("index.db"))
        database.initSchema()

        // 4. Repositories + infrastructure (§5.4 step 4).
        val noteRepo = SqliteNoteRepository(database)
        val tagRepo = SqliteTagRepository(database)
        val linkRepo = SqliteLinkRepository(database)
        val favRepo = SqliteFavoriteRepository(database)
        val searchIndex = Fts5SearchIndex(database)
        val fileStore = VaultFileStore()
        val codec = FrontMatterCodec()
        val parser = MarkdownParser()
        vaultWatcher = VaultWatcher()

        // 5. Wire application services (§5.4 step 5).
        val noteService = NoteService(fileStore, codec, parser)
        val vaultService = VaultService(fileStore)
        val indexingService = IndexingService(noteRepo, tagRepo, linkRepo, searchIndex, favRepo, fileStore, codec, parser)
        val searchService = SearchService(searchIndex)
        val favoriteService = FavoriteService(favRepo, noteRepo)
        val tagService = TagService(tagRepo, noteRepo)
        val importExportService = ImportExportService(fileStore)
        val dailyNoteService = DailyNoteService(noteService, settingsService, fileStore)

        val backlinksFor: (String) -> List<Link> = { notePath ->
            val id = noteRepo.findByPath(notePath)?.id
            if (id == null) emptyList() else linkRepo.backlinksFor(id)
        }

        // 8. UI (§5.4 step 8).
        val mainView = MainView(
            vaultService = vaultService,
            noteService = noteService,
            indexingService = indexingService,
            settingsService = settingsService,
            vaultRoot = vaultRoot,
            searchService = searchService,
            favoriteService = favoriteService,
            tagService = tagService,
            noteRepository = noteRepo,
            backlinksFor = backlinksFor,
            dailyNoteService = dailyNoteService,
            importExportService = importExportService,
        )

        val scene = Scene(mainView.root, 1000.0, 700.0)
        scene.stylesheets.add(javaClass.getResource("/com/brainvault/ui/app.css")!!.toExternalForm())
        stage.title = "BrainVault"
        stage.minWidth = 1000.0
        stage.minHeight = 700.0
        stage.scene = scene

        mainView.bindSceneShortcuts()
        mainView.showInitialState()

        // 6. If the index is empty but the vault has markdown files, build it in the
        //    background, reporting progress to the status bar (§5.4 step 6).
        if (noteRepo.count() == 0 && !fileStore.listMarkdown(vaultRoot).isEmpty()) {
            dbScope.launch {
                indexingService.fullRebuild(vaultRoot) { done, total ->
                    Platform.runLater { mainView.showIndexing(done, total) }
                }
                Platform.runLater { mainView.showIdle(noteRepo.count()) }
            }
        }

        // 7. Watch the vault for file events (§5.4 step 7). Reindex on the DB scope,
        //    then refresh the UI panels on the FX thread via MainView.
        vaultWatcher.start(vaultRoot) { events ->
            dbScope.launch {
                indexingService.onFileEvents(vaultRoot, events)
                mainView.onVaultChanged()
            }
        }

        stage.show()
    }

    override fun stop() {
        // §5.4 step 9: watcher, then database, then settings persist.
        vaultWatcher.stop()
        database.close()
        settingsService.persist()
    }
}

/** Top-level entry; build.gradle.kts mainClass = com.brainvault.ui.MainAppKt. */
fun main(args: Array<String>) {
    Application.launch(MainApp::class.java, *args)
}
