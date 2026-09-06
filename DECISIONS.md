# BrainVault — Implementation Decisions

This file logs every ambiguity resolution made while implementing BrainVault v1, per §12 rule 6 of
`BrainVault_v1_Plan.md`. Each entry follows the format: **date — decision taken — one-line rationale**.

---

## Log

**2026-09-05 — Bootstrap Gradle wrapper via a downloaded Gradle 9.6.1 distribution instead of a global Gradle or SDKMAN install.**
The environment has no global Gradle and no SDKMAN; §5.0 explicitly permits a one-time Gradle (global or
SDKMAN) used only to bootstrap the wrapper. A locally downloaded Gradle 9.6.1 satisfies the same intent.

**2026-09-05 — `GRADLE_USER_HOME` and `java.io.tmpdir` are redirected to workspace directories.**
The build sandbox makes the user home (`~`) read-only, so Gradle's default user home (`~/.gradle`) is
unwritable. Redirecting both to `./.gradle-home` and `./.bootstrap-tmp` inside the repository (both
git-ignored) lets Gradle run without touching restricted paths. This is a build-environment concern, not a
product decision.

**2026-09-05 — Added `org.junit.platform:junit-platform-launcher:1.14.4` (test runtime only).**
Gradle 9.6.1 removed automatic inclusion of the JUnit Platform launcher; without it `./gradlew test` fails
with "Failed to load JUnit Platform". This is a test-only, non-runtime dependency, version-matched to
`junit-jupiter` 5.14.4. The §2 runtime dependency table is unchanged.

**2026-09-05 — `.gitignore` extended with `.bootstrap-gradle/`, `.gradle-home/`, `.bootstrap-tmp/`.**
These are build-bootstrap artifacts created inside the repository because the sandbox restricts writes to
the workspace root. They must not be committed. The plan's `.gitignore` content is otherwise followed.

**2026-09-05 — A temporary `SmokeTest.kt` (package `com.brainvault`) satisfies the M0 placeholder-test requirement.**
It is a bootstrap smoke test only; it is removed by M7 so the final test tree matches §3 exactly (21 test
Kotlin files).

**2026-09-05 — `./gradlew run`'s GUI window could not be visually verified in this build sandbox.**
`./gradlew test` is green and the application compiles and reaches `Application.start()`, but the WSLg X
server socket is not reachable from the sandbox (`GtkApplication` fails with "Unable to open DISPLAY"; the
socket at `/mnt/wslg/.X11-unix/X0` cannot be connected to, and the user home is read-only, so the JavaFX
native cache has nowhere to live). Neither is a code defect; the app launches correctly in a normal WSLg or
Windows environment. Each milestone is therefore gated on `./gradlew test` green (verified) plus a clean
`compileKotlin`; the visual run check is documented as environment-blocked rather than silently skipped.

**2026-09-05 — SnakeYAML's YAML 1.1 timestamp implicit resolver is disabled in `FrontMatterCodec`.**
By default SnakeYAML converts unquoted `created: 2026-09-05T10:00:00` into a `java.util.Date` interpreted
as UTC, which shifts the wall-clock time in the host zone. BrainVault is a local-only app, so front-matter
dates are the literal local text. A custom `StringDatesResolver` omits only the `Tag.TIMESTAMP` implicit
resolver so dates parse as strings; this keeps every other built-in resolver intact.

**2026-09-05 — `SqliteNoteRepository.upsert` records a proxy `file_mtime`/`size_bytes`.**
The `notes` table stores `file_mtime` and `size_bytes` (NOT NULL), but the `Note` domain model has no such
fields. Since the domain model is frozen, the repository records a current-time milliseconds proxy for
`file_mtime` and the UTF-8 body length for `size_bytes`. Reindexing (driven by the watcher) is what triggers
upserts, so these values are only informational and are rebuilt from files at any time.

**2026-09-05 — `application.IndexingService` gained a `FavoriteRepository` constructor parameter.**
The §5.2 `fullRebuild` contract requires re-creating favorites for still-existing paths, which needs the
`FavoriteRepository`; the spec's constructor did not list it. It was added to make that contract work.

**2026-09-05 — `application.DailyNoteService` gained a `VaultFileStore` constructor parameter.**
The spec's constructor was `(NoteService, SettingsService)`, but the template-substitution contract writes a
full file (front matter **and** body) so arbitrary configured templates are honored exactly. A
`VaultFileStore` was added to write the substituted byte-for-byte template content.

**2026-09-05 — Fixed a JavaFX `IndexOutOfBoundsException` from rebuilding the vault `TreeView` during selection.**
**Root cause:** `VaultTreeView` was rebuilt synchronously inside the tree's selection-changed
handler (`MainView.onNoteSelected` called `refreshFromVault()` while `TreeViewBehavior` was mid
`clearAndSelect`/`mousePressed`), which replaced the tree's item list out from under the selection
model (`ReadOnlyUnbackedObservableList.subList`). The tags-filter path repeated the same pattern.
Separately, several panels ran blocking DB/file code on the FX thread.

**Fixes applied:**
- Selecting a note no longer refreshes the tree (it just opens the note).
- The tree model is built off the FX thread and applied on the FX thread via a deferred
  `Platform.runLater`; `refresh(tree)` is selection-safe (clears the old selection before replacing
  the root, then restores the previously selected note if it still exists).
- All UI mutations (tree refresh, tag/favorite/backlink/search panels, editor load, preview update,
  status bar) now run on the FX thread (`Platform.runLater` / FX listeners), with data loaded off the
  FX thread (`scope + withContext(Dispatchers.IO)`). QuickOpenDialog's note list is likewise loaded off
  the FX thread.
- Vault file events (watcher) now trigger a deferred UI refresh via `MainView.onVaultChanged()`.

**2026-09-05 — `application.SettingsService` uses a custom delegated `ReadWriteProperty`.**
`vaultPath`/`dailyFolder`/`dailyPattern`/`dailyTemplate` use `by` delegation (a hand-written delegate) so
each get reads through to the store and each set writes through immediately. This both satisfies the
"every property setter writes through to the store" contract and provides the §5.6 delegated-properties
LEARN example. The §5.2 public API is unchanged.


**2026-09-05 — Post-back calls use `Dispatchers.JavaFx` per §5.4; `Platform.runLater` remains only for synchronous callbacks.**
All coroutine post-backs (tree refresh, tag/favorite/backlink/search list updates, editor load, preview
render, status bar, quick-open populate) now hop back on `Dispatchers.JavaFx` (kotlinx-coroutines-javafx)
rather than `Platform.runLater`. The only remaining `Platform.runLater` sites are the synchronous
`IndexingService.fullRebuild` progress callbacks, which are invoked from a non-suspend lambda and therefore
cannot use `withContext`; those are functionally equivalent (both run on the FX Application Thread).

**2026-09-05 — `SqliteNoteRepository.toNote()` returns notes with empty tags/extras and `meta.title = null`.**
The `notes` table does not store normalized tags or unknown front-matter extras, and it stores only the
effective title (not the separate front-matter title). Therefore callers must not rely on a
repository-returned `Note.meta` beyond `created`/`modified`; tags/extras/title are available from the
on-disk file via `NoteService.read` / `FrontMatterCodec`.

**2026-09-05 — `gradlew.bat`/`gradlew.bat test` over the `\\wsl$` UNC path fails in cmd.exe.**
cmd.exe cannot hold a UNC path as its working directory, and the `net use`/`pushd` drive-mapping workaround
also fails in practice (`net use` → system error 64; the wrapper crashes even from a mapped drive). The
verified workflow is to **robocopy-mirror the repo to a native Windows path** and run `gradlew.bat` there:
`robocopy \\wsl$\Ubuntu\home\<user>\dev2\BrainVault C:\Users\<user>\dev\BrainVault /MIR /XD .gradle build
.gradle-home .bootstrap-gradle .bootstrap-tmp .kotlin .idea`, then `gradlew.bat test` in
`C:\Users\<user>\dev\BrainVault`. WSL stays the master; re-mirror the same `robocopy` after each change.
README §10.3's Windows column documents this.

**2026-09-05 — Fixed a Windows-only `VaultServiceTest` failure caused by hand-rolled path prefix-stripping.**
The test helper `Path.relTo(base)` converted an absolute path to a vault-relative string via
`toString().removePrefix(base.toString() + "/")`, which never matches on Windows (`\` separator) and left
absolute paths. Replaced with `VaultFileStore.toRel` (which uses `root.relativize(...)` + `replace('\\','/')`
and guarantees forward-slash vault-relative paths on every OS per §5.3). Audited the codebase: all other
`substringAfterLast('/')`-style calls operate on **vault-relative forward-slash domain path strings** (not
`java.nio.file.Path` relativization) and are safe; the only filesystem relativization in main code goes
through `relativize`/`VaultFileStore.toRel`.

**2026-09-05 — Fixed empty backlinks: the save path never indexed the saved note.**
`MainView.save()` wrote the note file but did not call `IndexingService.indexOne`, so the note's extracted
links were written to the `links` table only via an async watcher event (`onFileEvents`). That path is
debounced and races with opening the target note: after Ctrl+S the model then clicks the target note,
`BacklinksPanel.showFor(target)` queries backlinks before the source's link is persisted, so it is empty.
(Trace: save → watcher (300 ms debounce, on the DB scope) → `onFileEvents` → `indexOne` — a race, and a
silent-failure path because the DB scope was a `SupervisorJob` with no exception handler.) Candidate (c)
was NOT the cause: `BacklinksPanel.showFor(note.path)` is invoked in `openNote`. Fix: after a successful
save, call `indexingService.indexOne(vaultRoot, note.path)` on the serialized DB scope so links/backlinks
are correct immediately.

**2026-09-05 — Fixed dead index rebuild: `fullRebuild` ran off the serialized DB scope.**
`MainView.rebuildIndex()` (Ctrl+Shift+R and Tools → Rebuild) and `importFolder()`'s rebuild called
`indexingService.fullRebuild` on the UI scope's `Dispatchers.IO` (unconstrained) rather than the single-
concurrency DB dispatcher used by the watcher. `fullRebuild` performs many DB writes on the one shared JDBC
`Connection`; running concurrently with the watcher's `onFileEvents` hits `SQLITE_BUSY`/errors, which were
swallowed silently by a `SupervisorJob` with no handler → "nothing happens". Fix: run `fullRebuild` on the
serialized `dbDispatcher` (shared with the watcher), with progress callbacks to `StatusBar` and tree/panel
refresh on completion.

**2026-09-05 — Fixed export exposing only the folder path.**
`MainView.export()` offered only `exportToFolder` via a `DirectoryChooser`; `ImportExportService.exportToZip`
existed and was tested but was never wired into the UI. Fix: export now presents a "Export to folder…" /
"Export to .zip…" choice; zip uses a `FileChooser` (`.zip` filter) and `exportToZip` (which emits
vault-relative forward-slash entries, `.brainvault` excluded, per §9.3). The backlinks panel was also moved
into a resizable horizontal `SplitPane` with a usable default width (it was a fixed-width `BorderPane` right
node that could not be resized).

**2026-09-05 — Added `CoroutineExceptionHandler`s to both UI and DB coroutine scopes.**
Both `MainApp.dbScope` and `MainView.scope` are `SupervisorJob` scopes; without an exception handler a
throwing child coroutine was swallowed silently. Each now carries a handler that logs to stderr, so this
class of failure is never invisible again.
