# BrainVault v1 — Implementation Plan

**Audience:** an implementing coding LLM with no other context. This document is prescriptive, not
exploratory. Every decision is already made. Do not add features, dependencies, or alternatives.
If something is genuinely ambiguous, choose the simplest option and record the decision in
`DECISIONS.md` (see Section 12).

---

## 1. Executive summary

**BrainVault v1** is a local-first desktop Personal Knowledge Management (PKM) application. The source of
truth is plain Markdown files in a user-selected folder (the **vault**). Everything else — the SQLite
search index, the links graph, tags, favorites — is **derived data** and can be rebuilt from the Markdown
files at any time.

- **UI:** JavaFX, written in Kotlin. Plain-text monospaced editor (no WYSIWYG), side-by-side rendered HTML
  preview in a WebView.
- **Search:** SQLite FTS5 over note title + body, search-as-you-type, snippet highlighting.
- **Offline:** 100% offline. No network calls, no accounts, no telemetry, no AI features.
- **Architecture:** clean/hexagonal layering with manual constructor-based dependency wiring in a single
  composition root. No Spring, no DI framework, no ORM.
- **Learning goal:** the codebase doubles as a Kotlin-vs-Java course. Every Kotlin↔Java concept is
  explained exactly once at first occurrence in a `LEARN[KJV-nnn]` block comment (Section 5 and 11).

### Architecture diagram (four layers)

```
+---------------------------------------------------------------+
|                            ui                                 |
|  JavaFX views/controllers. Depends on application services    |
|  only. No SQL, no java.nio file logic, no flexmark imports.   |
+-----------------------------+---------------------------------+
                              |  calls use-case services
                              v
+---------------------------------------------------------------+
|                        application                            |
|  Services: NoteService, SearchService, IndexingService, ...   |
|  Orchestrates domain objects through domain ports.            |
|  Depends on domain only. No framework imports.                |
+-----------------------------+---------------------------------+
                              |  implements ports defined in domain
                              v
+---------------------------------------------------------------+
|                       infrastructure                          |
|  persistence (SQLite/JDBC/FTS5) | fs (files, WatchService)    |
|  markdown (flexmark, SnakeYAML) | config (properties file)    |
+-----------------------------+---------------------------------+
                              |  uses
                              v
+---------------------------------------------------------------+
|                           domain                              |
|  Pure Kotlin: models (Note, NoteMeta, Tag, Link, SearchHit,   |
|  FolderNode) + port interfaces. Zero framework imports.       |
+---------------------------------------------------------------+

Composition root: com.brainvault.ui.MainApp builds infrastructure
implementations, injects them into application services, injects
services into UI. Manual constructor wiring. No DI framework.
```

**Dependency rule (enforced):** `ui` → `application` → `domain`; `infrastructure` → `domain`.
`application` never imports `infrastructure.persistence` or `infrastructure.config` — those are
reached only through `domain.port` interfaces. One approved exception: `application` may import
`infrastructure.fs` and `infrastructure.markdown` directly (rationale in §5.2, `VaultService`).
`domain` imports nothing outside `kotlin.*`, `java.time`, and `java.nio.file.Path` (used only as
a type in signatures).

---

## 2. Tech stack

All versions are pinned exactly. These were verified against Maven Central / Gradle plugin portal on
2026-09-05 and are mutually compatible. Do not change any version.

| Component | Coordinates / tool | Exact version | Purpose |
| --- | --- | --- | --- |
| JDK toolchain | Gradle `languageVersion` | **25** (version only, never a vendor) | compile + run |
| Kotlin | `org.jetbrains.kotlin.jvm` plugin | **2.4.10** | language |
| Gradle | wrapper | **9.6.1** | build (wrapper committed; no global install) |
| JavaFX | `org.openjfx:javafx-base/graphics/controls/web` | **25.0.4** | UI toolkit (classifier resolved per OS by the plugin) |
| JavaFX Gradle plugin | `org.openjfx.javafxplugin` | **0.1.0** | JavaFX modules + native lib resolution |
| SQLite JDBC | `org.xerial:sqlite-jdbc` | **3.53.4.0** | persistence (raw JDBC, no ORM) |
| Markdown | `com.vladsch.flexmark:flexmark-all` | **0.64.8** | parsing + HTML rendering |
| YAML | `org.yaml:snakeyaml` | **2.7** | YAML front matter |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | **1.11.0** | async indexing/watching |
| Coroutines/JavaFX | `org.jetbrains.kotlinx:kotlinx-coroutines-javafx` | **1.11.0** | `Dispatchers.JavaFx` for UI callbacks |
| Tests | `org.junit.jupiter:junit-jupiter` | **5.14.4** | unit + integration tests |

**Deliberately NOT used (decisions, do not revisit in v1):**

- **TestFX / Monocle:** not in v1. Headless JavaFX UI tests on JDK 25 are a maintenance risk; the
  instructions permit keeping tests at the service/repository level, so UI classes are kept thin and all
  logic is tested below the UI layer.
- **Kotest / AssertJ:** plain JUnit 5 assertions only, to minimize dependencies.
- **JUnit 6.x, JavaFX 26.x, Kotlin RCs:** newer artifacts exist but are out of spec.
- **No ORM (Exposed/jOOQ/Hibernate), no DI framework, no Spring, no TornadoFX, no Compose.**
- **No jpackage/installer in v1.** Run via `gradlew.bat run` (Windows production) / `./gradlew run` (WSL dev).

---

## 3. Complete project directory tree

The project root is `~/dev2/BrainVault` in WSL (the **code repository** — never confuse it with the notes
vault, which is a separate data directory; see Section 10). The implementing LLM must create exactly this
tree. Every file listed here is specified in Section 5.

```
BrainVault/
├── gradlew                                   # Gradle wrapper script (Unix) — committed
├── gradlew.bat                               # Gradle wrapper script (Windows) — committed
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar                # committed
│   │   └── gradle-wrapper.properties         # distributionUrl = gradle-9.6.1-bin.zip
│   └── libs.versions.toml                    # version catalog — single source of all versions
├── settings.gradle.kts                       # rootProject.name = "BrainVault"
├── build.gradle.kts                          # plugins, toolchain 25, JavaFX config, test config
├── gradle.properties                         # JVM args for the Gradle daemon
├── .gitignore                                # build/, .gradle/, .idea/, *.db, out/
├── README.md                                 # run instructions for WSL + Windows (Section 10 content)
├── KOTLIN_VS_JAVA.md                         # LEARN[KJV-nnn] index table (Section 11 audit target)
├── DECISIONS.md                              # log of ambiguity resolutions made during implementation
├── BACKLOG.md                                # post-v1 feedback log — header + usage instructions only
├── src/
│   ├── main/
│   │   ├── kotlin/com/brainvault/
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── Note.kt               # Note data class (id, path, title, meta, body)
│   │   │   │   │   ├── NoteMeta.kt           # front-matter model: title/tags/created/modified/extras
│   │   │   │   │   ├── Tag.kt                # Tag data class (name, noteCount)
│   │   │   │   │   ├── Link.kt               # Link data class + LinkKind enum (WIKI, MARKDOWN)
│   │   │   │   │   ├── SearchHit.kt          # search result: path, title, snippet, rank
│   │   │   │   │   └── FolderNode.kt         # vault tree model for the UI tree view
│   │   │   │   └── port/
│   │   │   │       ├── NoteRepository.kt     # CRUD + lookup port for notes metadata
│   │   │   │       ├── SearchIndex.kt        # FTS port: upsert/remove/search/clear
│   │   │   │       ├── TagRepository.kt      # tag listing + notes-by-tag port
│   │   │   │       ├── LinkRepository.kt     # link edges replace/query port (backlinks)
│   │   │   │       ├── FavoriteRepository.kt # favorites toggle/list port
│   │   │   │       ├── SettingsStore.kt      # key/value app settings port
│   │   │   │       └── FileEventSource.kt    # vault change events port (watcher abstraction)
│   │   │   ├── application/
│   │   │   │   ├── NoteService.kt            # create/edit/save/delete/rename/move notes
│   │   │   │   ├── SearchService.kt          # query escaping, delegates to SearchIndex
│   │   │   │   ├── IndexingService.kt        # full rebuild + incremental reindex from file events
│   │   │   │   ├── VaultService.kt           # open/select vault, scan files, tree model
│   │   │   │   ├── DailyNoteService.kt       # create/open today's daily note from template
│   │   │   │   ├── FavoriteService.kt        # toggle + list favorites
│   │   │   │   ├── TagService.kt             # tag list with counts, notes by tag
│   │   │   │   ├── ImportExportService.kt    # import folder of .md; export to folder or zip
│   │   │   │   └── SettingsService.kt        # typed access over SettingsStore
│   │   │   ├── infrastructure/
│   │   │   │   ├── persistence/
│   │   │   │   │   ├── Database.kt           # JDBC connection holder, PRAGMAs, schema init/migrate
│   │   │   │   │   ├── SqliteNoteRepository.kt    # NoteRepository over SQLite
│   │   │   │   │   ├── Fts5SearchIndex.kt         # SearchIndex over FTS5 + snippet()
│   │   │   │   │   ├── SqliteTagRepository.kt     # TagRepository over SQLite
│   │   │   │   │   ├── SqliteLinkRepository.kt    # LinkRepository over SQLite
│   │   │   │   │   └── SqliteFavoriteRepository.kt# FavoriteRepository over SQLite
│   │   │   │   ├── fs/
│   │   │   │   │   ├── VaultFileStore.kt     # read/write/list/move .md files and folders in the vault
│   │   │   │   │   └── VaultWatcher.kt       # WatchService on vault, debounced, implements FileEventSource
│   │   │   │   ├── markdown/
│   │   │   │   │   ├── MarkdownParser.kt     # flexmark: parse, extract links, render HTML
│   │   │   │   │   └── FrontMatterCodec.kt   # SnakeYAML: split/parse/serialize front matter blocks
│   │   │   │   └── config/
│   │   │   │       └── PropertiesSettingsStore.kt # SettingsStore over a .properties file
│   │   │   └── ui/
│   │   │       ├── MainApp.kt                # Application entry + composition root (manual wiring)
│   │   │       ├── MainView.kt               # BorderPane shell, menu bar, shortcut registration
│   │   │       ├── VaultTreeView.kt          # folder/note tree panel (left)
│   │   │       ├── EditorView.kt             # monospaced TextArea editor + dirty tracking
│   │   │       ├── PreviewView.kt            # WebView HTML preview, debounced refresh
│   │   │       ├── SearchPanel.kt            # search box + results list with snippets
│   │   │       ├── BacklinksPanel.kt         # backlinks list for the open note
│   │   │       ├── TagsPanel.kt              # tag list; click filters note list
│   │   │       ├── FavoritesPanel.kt         # favorites list section
│   │   │       ├── QuickOpenDialog.kt        # Ctrl+P quick-open dialog
│   │   │       ├── SettingsDialog.kt         # Ctrl+, settings: vault path, daily note prefs
│   │   │       └── StatusBar.kt              # bottom bar: index status, save state, progress
│   │   └── resources/com/brainvault/ui/
│   │       └── app.css                       # app stylesheet (monospaced editor font, panel styling)
│   └── test/
│       └── kotlin/com/brainvault/
│           ├── testutil/
│           │   └── TestVaults.kt             # helpers: temp vault dirs, temp SQLite files, fixtures
│           ├── domain/
│           │   └── model/
│           │       └── NoteMetaTest.kt       # front-matter model merge/defaults logic
│           ├── application/
│           │   ├── NoteServiceTest.kt        # CRUD + rename/move + front-matter round-trip
│           │   ├── SearchServiceTest.kt      # query escaping, result mapping
│           │   ├── IndexingServiceTest.kt    # full rebuild + incremental events
│           │   ├── VaultServiceTest.kt       # vault scan + tree model
│           │   ├── DailyNoteServiceTest.kt   # pattern, template, idempotent open
│           │   ├── FavoriteServiceTest.kt    # toggle/list ordering
│           │   ├── TagServiceTest.kt         # counts, filter by tag
│           │   └── ImportExportServiceTest.kt# import folder, export folder, export zip
│           └── infrastructure/
│               ├── persistence/
│               │   ├── DatabaseTest.kt             # schema init idempotent, PRAGMAs applied
│               │   ├── SqliteNoteRepositoryTest.kt # CRUD against temp SQLite file
│               │   ├── Fts5SearchIndexTest.kt      # FTS match, snippet, ranking, delete sync
│               │   ├── SqliteTagRepositoryTest.kt  # tag CRUD + counts
│               │   ├── SqliteLinkRepositoryTest.kt # edges, backlinks, unresolved links
│               │   └── SqliteFavoriteRepositoryTest.kt
│               ├── fs/
│               │   ├── VaultFileStoreTest.kt       # file CRUD, folder ops, .brainvault exclusion
│               │   └── VaultWatcherTest.kt         # event debounce, create/modify/delete detection
│               ├── markdown/
│               │   ├── MarkdownParserTest.kt       # link extraction (wiki+md), HTML rendering
│               │   └── FrontMatterCodecTest.kt     # parse/serialize round-trip, extras preserved
│               └── config/
│                   └── PropertiesSettingsStoreTest.kt
```

**Counts:** 45 main Kotlin files + 1 CSS + 21 test Kotlin files (20 test classes + 1 shared test
helper, `TestVaults.kt`) + 13 build/root files (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`,
`gradle-wrapper.properties`, `libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`,
`gradle.properties`, `.gitignore`, `README.md`, `KOTLIN_VS_JAVA.md`, `DECISIONS.md`, `BACKLOG.md`).
If implementation needs an additional file, add it to this tree and to Section 5 in the same
change, and log why in `DECISIONS.md`.

---

## 4. Data model

### 4.1 Storage locations (decisions, fixed)

- **Index database:** `<vault>/.brainvault/index.db`. The `.brainvault/` directory inside the vault is
  excluded from indexing, watching, the tree view, and import/export. Rationale: the index travels with
  the vault and deleting it is an obvious, discoverable reset operation.
- **Application settings:** `${user.home}/.brainvault/config.properties` (Java `user.home` system property,
  platform-independent). On Windows this resolves to `%USERPROFILE%\.brainvault\config.properties`.
- **Default vault path:** `${user.home}/Documents/BrainVault` (= `%USERPROFILE%\Documents\BrainVault` on
  Windows). Configurable via the settings dialog; stored as the `vault.path` settings key.

### 4.2 SQLite DDL (complete — reproduce verbatim)

Executed by `Database.initSchema()` on every startup; all statements are idempotent. PRAGMAs are applied
on every new connection by `Database`.

```sql
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;

CREATE TABLE IF NOT EXISTS schema_meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
INSERT OR IGNORE INTO schema_meta(key, value) VALUES ('schema_version', '1');

CREATE TABLE IF NOT EXISTS notes (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  path        TEXT    NOT NULL UNIQUE,   -- vault-relative, forward slashes, e.g. "projects/idea.md"
  title       TEXT    NOT NULL,          -- front-matter title, else filename without ".md"
  body        TEXT    NOT NULL,          -- Markdown body WITHOUT the front-matter block (§4.2 body rule)
  created     TEXT,                      -- ISO-8601 local datetime from front matter, nullable
  modified    TEXT,                      -- ISO-8601 local datetime from front matter, nullable
  file_mtime  INTEGER NOT NULL,          -- file lastModified (epoch millis) at index time
  size_bytes  INTEGER NOT NULL
);

-- FTS5 external-content table; stays in sync with `notes` via triggers below.
CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(
  title,
  body,
  content  = 'notes',
  content_rowid = 'id',
  tokenize = 'unicode61'
);

CREATE TRIGGER IF NOT EXISTS notes_ai AFTER INSERT ON notes BEGIN
  INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
END;

CREATE TRIGGER IF NOT EXISTS notes_ad AFTER DELETE ON notes BEGIN
  INSERT INTO notes_fts(notes_fts, rowid, title, body)
  VALUES ('delete', old.id, old.title, old.body);
END;

CREATE TRIGGER IF NOT EXISTS notes_au AFTER UPDATE ON notes BEGIN
  INSERT INTO notes_fts(notes_fts, rowid, title, body)
  VALUES ('delete', old.id, old.title, old.body);
  INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
END;

CREATE TABLE IF NOT EXISTS tags (
  id   INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE IF NOT EXISTS note_tags (
  note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
  tag_id  INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (note_id, tag_id)
);

CREATE TABLE IF NOT EXISTS links (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  source_id  INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
  target_id  INTEGER REFERENCES notes(id) ON DELETE CASCADE,  -- NULL = unresolved target
  raw_target TEXT    NOT NULL,               -- as written: "Some Note" or "sub/other.md"
  kind       TEXT    NOT NULL CHECK (kind IN ('wiki', 'markdown')),
  UNIQUE (source_id, raw_target, kind)
);
CREATE INDEX IF NOT EXISTS idx_links_target ON links(target_id);

CREATE TABLE IF NOT EXISTS favorites (
  note_id  INTEGER PRIMARY KEY REFERENCES notes(id) ON DELETE CASCADE,
  added_at TEXT NOT NULL                     -- ISO-8601 local datetime
);
```

**FTS5 content-sync strategy (decision):** external-content table + triggers, as written above. All note
mutations go through `SqliteNoteRepository` upsert/delete, so the FTS index is consistent as long as every
write path uses the repository — this is a hard rule for the implementing LLM. A full index rebuild
(Section 6, M3) additionally runs `INSERT INTO notes_fts(notes_fts) VALUES('rebuild')` after reloading
`notes`, as a safety net.

**Body storage rule (decision, binding):** `notes.body` stores the Markdown body **without** the
front-matter block — identical to `domain.model.Note.body` (§5.1). `FrontMatterCodec.split` strips the
YAML block before any repository upsert, so front-matter keys never pollute FTS search results, and the
database body and domain model body are always the same text. The raw file on disk remains the only place
the full front matter lives; the derived-data rule is unchanged (the entire index can be rebuilt from
files at any time).

**Link resolution rules (decision):**

- Wiki link `[[Some Note]]` resolves to the note whose **title** (case-insensitive) equals `Some Note`,
  else whose **filename without extension** (case-insensitive, any folder) equals `Some Note`, else
  unresolved (`target_id = NULL`).
- Markdown link `[text](path.md)` resolves the relative `path.md` against the source note's folder;
  URL-encoded spaces (`%20`) are decoded before resolution. Non-`.md` targets and external URLs
  (`http://`, `https://`, `mailto:`) are not stored in `links` at all.
- Unresolved links are still stored (with `target_id = NULL`) so that creating the target later and
  reindexing makes them resolve.

### 4.3 YAML front-matter schema

A note file consists of an optional YAML front-matter block followed by the Markdown body:

```markdown
---
title: "Kotlin vs Java"          # string, optional — default: filename without ".md"
tags: [kotlin, java]             # list of strings, optional — default: []
created: 2026-09-05T10:00:00     # ISO-8601 local datetime, optional
modified: 2026-09-05T12:30:00    # ISO-8601 local datetime, optional — refreshed on every save
rating: 4                        # arbitrary extra keys are allowed
project: "BrainVault"            #   and must be preserved verbatim on save
---

Body text in *Markdown* …
```

Rules:

1. The block starts at line 1 with exactly `---` and ends at the next line that is exactly `---`. A file
   without a valid leading block is treated as body-only with default metadata.
2. Known keys: `title` (string), `tags` (list of strings; scalars coerced to string), `created`,
   `modified` (ISO-8601 `YYYY-MM-DDTHH:MM:SS`, local time, no timezone — this is a local-only app).
3. **Unknown keys must be preserved verbatim** through a load→save cycle (order may change; values may be
   re-emitted in canonical YAML form; no key may be dropped). Implementation: parse into a
   `LinkedHashMap<String, Any?>`, mutate only known keys, re-serialize.
4. On save, `modified` is set to the current local datetime. `created` is set once when a note is created
   if absent; never overwritten afterwards.
5. Malformed YAML in a front-matter block: treat the whole file as body-only (no metadata), do not crash,
   and log a warning. This is tested (`FrontMatterCodecTest`).

---

## 5. File-by-file build specification

For every file: package, public API (Kotlin signatures), and behavior contract. Implement exactly this
surface. Internal helpers are free, but no additional public types may leak across packages.

The `LEARN[KJV-nnn]` comment convention applies from the first file written (see 5.6).

### 5.0 Build files

**`settings.gradle.kts`**

```kotlin
rootProject.name = "BrainVault"
```

**`gradle/libs.versions.toml`** — single source of versions:

```toml
[versions]
kotlin = "2.4.10"
javafx = "25.0.4"
javafxplugin = "0.1.0"
sqlite = "3.53.4.0"
flexmark = "0.64.8"
snakeyaml = "2.7"
coroutines = "1.11.0"
junit = "5.14.4"

[libraries]
javafx-base = { group = "org.openjfx", name = "javafx-base", version.ref = "javafx" }
javafx-graphics = { group = "org.openjfx", name = "javafx-graphics", version.ref = "javafx" }
javafx-controls = { group = "org.openjfx", name = "javafx-controls", version.ref = "javafx" }
javafx-web = { group = "org.openjfx", name = "javafx-web", version.ref = "javafx" }
sqlite-jdbc = { group = "org.xerial", name = "sqlite-jdbc", version.ref = "sqlite" }
flexmark-all = { group = "com.vladsch.flexmark", name = "flexmark-all", version.ref = "flexmark" }
snakeyaml = { group = "org.yaml", name = "snakeyaml", version.ref = "snakeyaml" }
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-javafx = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-javafx", version.ref = "coroutines" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
javafx = { id = "org.openjfx.javafxplugin", version.ref = "javafxplugin" }
```

**`build.gradle.kts`** — required content (behavior contract; formatting free):

- Plugins: `kotlin("jvm")` (alias from catalog), `application`, `javafx` (alias from catalog).
- `java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }` — version only, never a vendor.
- `javafx { version = "25.0.4"; modules("javafx.controls", "javafx.web") }`.
- Dependencies: the 7 main libraries from the catalog; `testImplementation(libs.junit.jupiter)`.
- `application { mainClass.set("com.brainvault.ui.MainAppKt") }` (top-level `main` in `MainApp.kt`).
- `tasks.test { useJUnitPlatform() }`.
- Kotlin compile options: `compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25) }`
  (or toolchain-derived; do not pin a lower target).

**`gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2g
org.gradle.caching=true
```

**`gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
```

Generate the wrapper once with `gradle wrapper --gradle-version 9.6.1` (a one-time global Gradle or the
SDKMAN Gradle may be used only to bootstrap the wrapper; afterwards only `./gradlew` / `gradlew.bat`).

**`.gitignore`**: `build/`, `.gradle/`, `.idea/`, `out/`, `*.db`, `*.db-wal`, `*.db-shm`.

### 5.1 `domain` layer — pure Kotlin, zero framework imports

Package root: `com.brainvault.domain`. Allowed imports: `kotlin.*`, `java.time.*`, `java.nio.file.Path`.

**`domain/model/Note.kt`**

```kotlin
package com.brainvault.domain.model

data class Note(
    val id: Long,            // SQLite rowid; 0 or negative = not yet persisted
    val path: String,        // vault-relative, forward slashes, e.g. "projects/idea.md"
    val title: String,
    val meta: NoteMeta,
    val body: String,        // full Markdown body WITHOUT the front-matter block
)
```

Contract: `path` is the identity used by the UI and services; `id` is the database identity. `body` never
contains the front-matter block — codecs strip it on read and prepend it on write.

**`domain/model/NoteMeta.kt`**

```kotlin
package com.brainvault.domain.model

import java.time.LocalDateTime

data class NoteMeta(
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val created: LocalDateTime? = null,
    val modified: LocalDateTime? = null,
    val extras: Map<String, Any?> = emptyMap(),  // unknown YAML keys, preserved verbatim
) {
    fun effectiveTitle(fallback: String): String   // title ?: fallback
    fun withModified(now: LocalDateTime): NoteMeta // copy(modified = now)
    fun mergeInto(raw: MutableMap<String, Any?>)   // write known keys into a raw YAML map,
                                                   // leave unknown keys untouched
}
```

**`domain/model/Tag.kt`**

```kotlin
package com.brainvault.domain.model

data class Tag(val name: String, val noteCount: Int)
```

**`domain/model/Link.kt`**

```kotlin
package com.brainvault.domain.model

enum class LinkKind { WIKI, MARKDOWN }

data class Link(
    val sourcePath: String,        // vault-relative path of the note containing the link
    val targetPath: String?,       // resolved vault-relative target; null = unresolved
    val rawTarget: String,         // as written in the source
    val kind: LinkKind,
)
```

**`domain/model/SearchHit.kt`**

```kotlin
package com.brainvault.domain.model

data class SearchHit(
    val path: String,
    val title: String,
    val snippet: String,   // contains <b>...</b> highlight markers from FTS5 snippet()
    val rank: Double,      // bm25 score, lower = better
)
```

**`domain/model/FolderNode.kt`**

```kotlin
package com.brainvault.domain.model

data class FolderNode(
    val name: String,                    // "" for the vault root
    val path: String,                    // vault-relative folder path; "" for root
    val folders: List<FolderNode>,       // subfolders, sorted by name
    val notePaths: List<String>,         // notes directly in this folder, sorted
)
```

**`domain/port/NoteRepository.kt`**

```kotlin
package com.brainvault.domain.port

import com.brainvault.domain.model.Note

interface NoteRepository {
    fun upsert(note: Note): Long                       // insert or update by path; returns rowid
    fun deleteByPath(path: String)                     // no-op if absent
    fun findByPath(path: String): Note?
    fun findByTitle(title: String): Note?              // case-insensitive; also matches filename w/o .md
    fun allPaths(): List<String>
    fun count(): Int
}
```

**`domain/port/SearchIndex.kt`**

```kotlin
package com.brainvault.domain.port

import com.brainvault.domain.model.SearchHit

interface SearchIndex {
    fun search(query: String, limit: Int = 100): List<SearchHit>
    fun rebuild()        // runs FTS 'rebuild' command after bulk note reload
    fun clear()          // delete all rows (used by full reindex before reload)
}
```

**`domain/port/TagRepository.kt`**

```kotlin
package com.brainvault.domain.port

import com.brainvault.domain.model.Tag

interface TagRepository {
    fun replaceTagsForNote(noteId: Long, tags: List<String>)
    fun allTagsWithCounts(): List<Tag>                 // sorted by name
    fun notePathsForTag(tagName: String): List<String>
}
```

**`domain/port/LinkRepository.kt`**

```kotlin
package com.brainvault.domain.port

import com.brainvault.domain.model.Link

interface LinkRepository {
    fun replaceLinksFrom(sourceId: Long, links: List<Link>)   // delete-then-insert for one note
    fun backlinksFor(targetId: Long): List<Link>              // resolved inbound links
    fun unresolvedLinks(): List<Link>
}
```

**`domain/port/FavoriteRepository.kt`**

```kotlin
package com.brainvault.domain.port

interface FavoriteRepository {
    fun add(noteId: Long)
    fun remove(noteId: Long)
    fun isFavorite(noteId: Long): Boolean
    fun favoriteNoteIds(): List<Long>    // ordered by added_at
}
```

**`domain/port/SettingsStore.kt`**

```kotlin
package com.brainvault.domain.port

interface SettingsStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun save()                            // persist to disk
}
```

**`domain/port/FileEventSource.kt`**

```kotlin
package com.brainvault.domain.port

import java.nio.file.Path

enum class FileEventKind { CREATED, MODIFIED, DELETED }

data class FileEvent(val kind: FileEventKind, val vaultRelativePath: String)

interface FileEventSource {
    fun start(vaultRoot: Path, listener: (List<FileEvent>) -> Unit)  // batches after debounce
    fun stop()
}
```

### 5.2 `application` layer

Package root: `com.brainvault.application`. Depends only on `domain`. All services are plain classes with
constructor-injected ports. Services are blocking (called from coroutines by the UI layer with
`Dispatchers.IO`); none of them import coroutines or JavaFX.

**`application/SettingsService.kt`**

```kotlin
package com.brainvault.application

import com.brainvault.domain.port.SettingsStore
import java.nio.file.Path

class SettingsService(private val store: SettingsStore) {
    var vaultPath: Path                 // key "vault.path"; default ${user.home}/Documents/BrainVault
    var dailyFolder: String             // key "daily.folder"; default "daily"
    var dailyPattern: String            // key "daily.pattern"; default "yyyy-MM-dd"
    var dailyTemplate: String           // key "daily.template"; default "---\ntitle: {{title}}\ntags: [daily]\ncreated: {{created}}\n---\n\n# {{date}}\n"
    fun persist()                       // delegates to store.save()
}
```

Contract: every property setter writes through to the store immediately; `persist()` flushes to disk.
Unknown/future keys in the properties file are left untouched.

**`application/VaultService.kt`**

**Approved exception to the layering rule (stated once, applies to all of §5.2):** `VaultFileStore`,
`VaultWatcher`, `FrontMatterCodec`, and `MarkdownParser` are filesystem/parsing utilities with no
persistence semantics. To avoid port-interface noise, the `application` layer may depend on
`infrastructure.fs` and `infrastructure.markdown` directly. The dependency rule (`application` never
imports `infrastructure`) applies to `infrastructure.persistence` and `infrastructure.config` only —
those are always reached through `domain.port` interfaces. Record this exception in `DECISIONS.md`
during M0.

```kotlin
package com.brainvault.application

import com.brainvault.domain.model.FolderNode
import com.brainvault.infrastructure.fs.VaultFileStore
import java.nio.file.Path

class VaultService(private val fileStore: VaultFileStore) {
    fun tree(vaultRoot: Path): FolderNode          // recursive scan, .brainvault excluded, sorted
    fun markdownFiles(vaultRoot: Path): List<Path> // all *.md, vault-relative sort order
}
```

Contract: scans the vault via the injected `VaultFileStore`; no SQL and no persistence imports.

**`application/NoteService.kt`**

```kotlin
package com.brainvault.application

import com.brainvault.domain.model.Note
import com.brainvault.domain.model.NoteMeta
import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.infrastructure.markdown.FrontMatterCodec
import com.brainvault.infrastructure.markdown.MarkdownParser
import java.nio.file.Path

class NoteService(
    private val fileStore: VaultFileStore,
    private val codec: FrontMatterCodec,
    private val parser: MarkdownParser,
) {
    fun read(vaultRoot: Path, relPath: String): Note
    fun create(vaultRoot: Path, folder: String, title: String): Note   // filename = sanitized title + ".md",
                                                                       // collision → "title-2.md", …
    fun save(vaultRoot: Path, note: Note)                              // sets meta.modified=now, created if absent,
                                                                       // writes front matter + body atomically
    fun delete(vaultRoot: Path, relPath: String)
    fun rename(vaultRoot: Path, relPath: String, newTitle: String): String  // returns new relPath
    fun move(vaultRoot: Path, relPath: String, targetFolder: String): String
    fun renderHtml(body: String): String                               // delegates to parser
    fun extractLinks(body: String): List<com.brainvault.domain.model.Link>  // raw (sourcePath filled by caller)
}
```

Contract: filename sanitization strips `\/:*?"<>|` and trims; empty result falls back to `untitled`.
`save` writes via temp-file-then-move (atomic within the same filesystem). `create`/`save` always emit a
front-matter block (even if empty apart from known keys).

**`application/SearchService.kt`**

```kotlin
package com.brainvault.application

import com.brainvault.domain.model.SearchHit
import com.brainvault.domain.port.SearchIndex

class SearchService(private val index: SearchIndex) {
    fun search(rawQuery: String, limit: Int = 100): List<SearchHit>
}
```

Contract: escapes the user query into a safe FTS5 MATCH string: split on whitespace, drop empty tokens,
wrap each token in double quotes (embedded `"` removed), join with spaces (implicit AND). Blank query →
empty list. Never throws on arbitrary user input — tested.

**`application/IndexingService.kt`**

```kotlin
package com.brainvault.application

import com.brainvault.domain.model.Link
import com.brainvault.domain.port.*
import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.infrastructure.markdown.FrontMatterCodec
import com.brainvault.infrastructure.markdown.MarkdownParser
import java.nio.file.Path

class IndexingService(
    private val notes: NoteRepository,
    private val tags: TagRepository,
    private val links: LinkRepository,
    private val index: SearchIndex,
    private val fileStore: VaultFileStore,
    private val codec: FrontMatterCodec,
    private val parser: MarkdownParser,
) {
    fun fullRebuild(vaultRoot: Path, progress: (done: Int, total: Int) -> Unit = { _, _ -> })
    fun onFileEvents(vaultRoot: Path, events: List<FileEvent>)  // incremental upsert/delete per event
    fun indexOne(vaultRoot: Path, relPath: String)              // public for reuse by NoteService save flow
}
```

Contract: `fullRebuild` clears all derived tables (notes/tags/links/favorites rows are rebuilt; favorites
for paths that still exist are re-created by matching path → new id) then walks all `.md` files and
reindexes each via `indexOne`, reporting progress. `indexOne`: read file → codec split → upsert note →
replaceTagsForNote → extract+resolve links → replaceLinksFrom. Link resolution uses
`NoteRepository.findByTitle` / path rules from §4.2. `onFileEvents` ignores events under `.brainvault/`.

**`application/DailyNoteService.kt`**

```kotlin
package com.brainvault.application

import com.brainvault.domain.model.Note
import java.nio.file.Path
import java.time.LocalDate

class DailyNoteService(
    private val noteService: NoteService,
    private val settings: SettingsService,
) {
    fun relPathFor(date: LocalDate): String                 // "${dailyFolder}/${date.format(pattern)}.md"
    fun openOrCreate(vaultRoot: Path, date: LocalDate): Note  // creates from template if absent
}
```

Contract: template placeholders `{{date}}` (formatted with `dailyPattern`), `{{title}}` (same), and
`{{created}}` (ISO-8601 now) are substituted in both front matter and body. Existing daily note is opened
verbatim, never overwritten.

**`application/FavoriteService.kt`**

```kotlin
package com.brainvault.application

import com.brainvault.domain.model.Note
import com.brainvault.domain.port.FavoriteRepository
import com.brainvault.domain.port.NoteRepository

class FavoriteService(
    private val favorites: FavoriteRepository,
    private val notes: NoteRepository,
) {
    fun toggle(notePath: String): Boolean     // returns new state
    fun isFavorite(notePath: String): Boolean
    fun list(): List<Note>                    // resolved notes, ordered by added_at
}
```

**`application/TagService.kt`**

```kotlin
package com.brainvault.application

import com.brainvault.domain.model.Note
import com.brainvault.domain.model.Tag
import com.brainvault.domain.port.NoteRepository
import com.brainvault.domain.port.TagRepository

class TagService(
    private val tags: TagRepository,
    private val notes: NoteRepository,
) {
    fun allTags(): List<Tag>
    fun notesFor(tagName: String): List<Note>
}
```

**`application/ImportExportService.kt`**

```kotlin
package com.brainvault.application

import com.brainvault.infrastructure.fs.VaultFileStore
import java.nio.file.Path

class ImportExportService(private val fileStore: VaultFileStore) {
    fun importFolder(vaultRoot: Path, source: Path, targetSubfolder: String,
                     progress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int  // returns files copied
    fun exportToFolder(vaultRoot: Path, target: Path): Int                          // returns files copied
    fun exportToZip(vaultRoot: Path, zipFile: Path)
}
```

Contract: import copies `*.md` recursively from `source` into `vaultRoot/targetSubfolder`, preserving
relative subpaths, never overwriting — on collision appends `-2`, `-3`, … before `.md`. `.brainvault/` is
never imported or exported. Zip export uses `java.util.zip`, one entry per vault file (excluding
`.brainvault/`), stored with vault-relative forward-slash names. Caller (UI) triggers a reindex after
import.

### 5.3 `infrastructure` layer

**`infrastructure/persistence/Database.kt`**

```kotlin
package com.brainvault.infrastructure.persistence

import java.nio.file.Path
import java.sql.Connection

class Database(dbFile: Path) : AutoCloseable {
    val connection: Connection          // single shared connection; WAL mode allows concurrent reads
    fun initSchema()                    // executes §4.2 DDL verbatim; idempotent
    fun <T> tx(block: (Connection) -> T): T   // begin/commit, rollback on throw
    override fun close()
}
```

Contract: constructor creates parent dirs, opens the connection, applies the three PRAGMAs from §4.2.
`initSchema` is called once at startup. All repositories receive this `Database` and use `tx { }` for
multi-statement writes. Single shared connection is acceptable for v1 (one writer thread via
`Dispatchers.IO.limitedParallelism(1)` in the UI layer — stated here as the rule).

**`infrastructure/persistence/SqliteNoteRepository.kt`** — implements `NoteRepository`.

Contract: `upsert` = `INSERT … ON CONFLICT(path) DO UPDATE SET title, body, created, modified,
file_mtime, size_bytes`, returns rowid. The repository persists `note.body` exactly as received — the
caller has already stripped the front matter (§4.2 body storage rule). `findByTitle`: `WHERE
lower(title)=lower(?) OR lower(path) LIKE lower(?) ESCAPE '\'` with pattern `"%/${title}.md"` plus
root-level `"${title}.md"`. Row mapping reads columns by name.

**`infrastructure/persistence/Fts5SearchIndex.kt`** — implements `SearchIndex`.

Query (verbatim, parameter binding for the MATCH string):

```sql
SELECT n.path, n.title,
       snippet(notes_fts, 1, '<b>', '</b>', '…', 12) AS snippet,
       bm25(notes_fts) AS rank
FROM notes_fts
JOIN notes n ON n.id = notes_fts.rowid
WHERE notes_fts MATCH ?
ORDER BY rank
LIMIT ?
```

Contract: `rebuild()` executes `INSERT INTO notes_fts(notes_fts) VALUES('rebuild')`. `clear()` executes
`DELETE FROM notes`. FTS errors on malformed MATCH strings propagate — escaping is `SearchService`'s job.

**`infrastructure/persistence/SqliteTagRepository.kt`** — implements `TagRepository`.

Contract: `replaceTagsForNote` runs in one `tx`: delete existing `note_tags` rows for the note; for each
tag name `INSERT OR IGNORE INTO tags(name)`, select id, insert `note_tags`. `allTagsWithCounts`:
`SELECT t.name, COUNT(nt.note_id) … GROUP BY t.id ORDER BY t.name`. Tags orphaned (count 0) are deleted
in the same transaction.

**`infrastructure/persistence/SqliteLinkRepository.kt`** — implements `LinkRepository`.

Contract: `replaceLinksFrom(sourceId, links)` = one `tx`: `DELETE FROM links WHERE source_id=?`, then
insert each edge, resolving `target_id` via `NoteRepository.findByTitle`/path lookup done in
`IndexingService` and passed in already resolved. Wait — resolution needs note ids; to keep the repository
dumb, `IndexingService` passes `Link` objects whose `targetPath` is resolved; the repository maps paths to
ids by querying `notes`. `backlinksFor(targetId)` joins `notes` to return full `Link` objects ordered by
source path.

**`infrastructure/persistence/SqliteFavoriteRepository.kt`** — implements `FavoriteRepository`.

Contract: `add` = `INSERT OR IGNORE … VALUES(?, <ISO-8601 now>)`. `favoriteNoteIds` ordered by
`added_at` ascending.

**`infrastructure/fs/VaultFileStore.kt`**

```kotlin
package com.brainvault.infrastructure.fs

import java.nio.file.Path

class VaultFileStore {
    fun readText(vaultRoot: Path, relPath: String): String
    fun writeText(vaultRoot: Path, relPath: String, content: String)   // atomic: tmp + move
    fun delete(vaultRoot: Path, relPath: String)
    fun move(vaultRoot: Path, fromRel: String, toRel: String)          // creates target dirs
    fun listMarkdown(vaultRoot: Path): List<String>                    // rel paths, .brainvault excluded
    fun listFolders(vaultRoot: Path): List<String>                     // rel folder paths, .brainvault excluded
    fun createFolder(vaultRoot: Path, relFolder: String)
    fun toRel(vaultRoot: Path, absolute: Path): String                 // forward-slash relative path
    fun exists(vaultRoot: Path, relPath: String): Boolean
}
```

Contract: all text I/O is UTF-8. `listMarkdown`/`listFolders` never descend into `.brainvault`. Paths
inside the API are always vault-relative with forward slashes, on every OS (conversion in `toRel`).

**`infrastructure/fs/VaultWatcher.kt`** — implements `FileEventSource`.

```kotlin
package com.brainvault.infrastructure.fs

import com.brainvault.domain.port.FileEvent
import com.brainvault.domain.port.FileEventSource
import java.nio.file.Path

class VaultWatcher : FileEventSource {
    override fun start(vaultRoot: Path, listener: (List<FileEvent>) -> Unit)
    override fun stop()
}
```

Contract: registers the vault recursively with `WatchService` (pre-register existing folders; register
new folders on CREATED events of directories). Events for paths under `.brainvault/` are dropped.
Debounce: collect events for **300 ms** of quiet, then deliver one batch with duplicate
(path) events coalesced keeping the latest kind per path (CREATED+DELETED cancel out → dropped; CREATED
then MODIFIED → CREATED). Runs its own daemon thread. `stop()` closes the watch service and joins the
thread (≤ 2 s). Overflow events (`OVERFLOW`) trigger a synthetic full-scan signal by emitting one
`FileEvent(CREATED, "")` with empty path — `IndexingService` treats empty path as "rescan all".

**`infrastructure/markdown/FrontMatterCodec.kt`**

```kotlin
package com.brainvault.infrastructure.markdown

import com.brainvault.domain.model.NoteMeta

class FrontMatterCodec {
    fun split(rawFile: String): Pair<Map<String, Any?>, String>  // (rawYamlMap, body); body excludes block
    fun toMeta(raw: Map<String, Any?>): NoteMeta                 // known keys → typed; rest → extras
    fun serialize(meta: NoteMeta, body: String): String          // "---\n<yaml>---\n\n<body>"
}
```

Contract: `serialize` writes known keys first in order `title, tags, created, modified`, then `extras`
keys (sorted), via SnakeYAML `DumperOptions` with `defaultFlowStyle = BLOCK`. Dates serialize as
`yyyy-MM-dd'T'HH:mm:ss` strings (quoted). Malformed YAML → `split` returns `(emptyMap(), rawFile)` and
prints a warning to stdout. Round-trip guarantee: `serialize(toMeta(split(f).first), split(f).second)`
preserves all keys and the body byte-for-byte (modulo YAML reformatting).

**`infrastructure/markdown/MarkdownParser.kt`**

```kotlin
package com.brainvault.infrastructure.markdown

import com.brainvault.domain.model.Link

class MarkdownParser {
    fun renderHtml(markdownBody: String): String      // full HTML doc with inline <style> (below)
    fun extractLinks(markdownBody: String): List<Link> // sourcePath/targetPath unset ("" / null);
                                                       // rawTarget + kind filled
}
```

Contract: flexmark `Parser`/`HtmlRenderer` built once in `init` with the default extension set from
`flexmark-all` (tables, strikethrough, autolink). `extractLinks` walks the AST: `WikiLink` nodes →
`LinkKind.WIKI` with the link text as rawTarget; `Link` nodes → `LinkKind.MARKDOWN` with the URL as
rawTarget, skipping `http(s)://`, `mailto:` and non-`.md` targets. `renderHtml` output must include a
minimal inline stylesheet (monospaced `code`, max-width body, styled `<b>` highlights) so the WebView
needs no external resources (offline rule).

**`infrastructure/config/PropertiesSettingsStore.kt`** — implements `SettingsStore`.

```kotlin
package com.brainvault.infrastructure.config

import java.nio.file.Path

class PropertiesSettingsStore(configFile: Path) : SettingsStore { … }
```

Contract: loads on construction (missing file → empty), `put` marks dirty, `save()` writes only when
dirty, creating parent dirs. Default path supplied by composition root:
`Path.of(System.getProperty("user.home"), ".brainvault", "config.properties")`.

### 5.4 `ui` layer

Package root: `com.brainvault.ui`. UI classes depend on `application` services only (plus
`domain` model types). **No SQL, no `java.nio` file logic, no flexmark/SnakeYAML imports in this
layer.** UI classes are intentionally thin — all logic lives below the UI layer and is tested
there (see §2, TestFX decision).

**Threading rule (stated once, applies to the whole layer):** application services are blocking.
Every service call from the UI runs in a coroutine on `Dispatchers.IO` — writes that touch the
database use `Dispatchers.IO.limitedParallelism(1)` (per the `Database` contract in §5.3) — and
results are posted back to the UI on `Dispatchers.JavaFx`. No service is ever called directly on
the JavaFX Application Thread.

**`ui/MainApp.kt`**

```kotlin
package com.brainvault.ui

import javafx.application.Application
import javafx.stage.Stage

class MainApp : Application() {
    override fun start(stage: Stage)    // composition root — see contract
    override fun stop()                 // orderly shutdown — see contract
}

fun main(args: Array<String>)           // top-level; build.gradle.kts mainClass = MainAppKt
```

Contract (composition root — the only place implementations are constructed):

1. Build `PropertiesSettingsStore` at the default config path (§5.3) → `SettingsService`.
2. Resolve the vault path from settings (default §4.1); create the vault directory if absent.
3. Build `Database(<vault>/.brainvault/index.db)`, call `initSchema()`.
4. Build all SQLite repositories, `Fts5SearchIndex`, `VaultFileStore`, `FrontMatterCodec`,
   `MarkdownParser`, `VaultWatcher`.
5. Wire all application services (NoteService, SearchService, IndexingService, VaultService,
   DailyNoteService, FavoriteService, TagService, ImportExportService) by constructor injection.
6. If the index is empty (notes count = 0) while the vault contains `.md` files, run
   `IndexingService.fullRebuild` in the background with progress reported to `StatusBar`.
7. Start `VaultWatcher` with a listener that delegates to `IndexingService.onFileEvents`.
8. Construct `MainView` with all services and show the stage (min size 1000×700, title
   "BrainVault").
9. `stop()`: `VaultWatcher.stop()`, `Database.close()`, `SettingsService.persist()` — in that
   order.

**`ui/MainView.kt`**

```kotlin
package com.brainvault.ui

import javafx.scene.layout.BorderPane

class MainView(
    /* all application services, constructor-injected */
) {
    val root: BorderPane                // the scene root
}
```

Contract: layout = left accordion (vault tree, favorites, tags panels), center `SplitPane`
(`EditorView` | `PreviewView`, side-by-side, 50/50 default divider), right `BacklinksPanel`,
bottom `StatusBar`, top menu bar. Menus: **File** (new note, save, quick open, import, export,
settings, exit), **Note** (rename, move, delete, favorite toggle, daily note), **Tools** (rebuild
index), **Help** (shortcut list, about). Registers the full §8 shortcut map on the scene. The Help
menu's shortcut list is generated from the same table so the two never drift.

**`ui/VaultTreeView.kt`**

```kotlin
package com.brainvault.ui

import com.brainvault.domain.model.FolderNode
import javafx.scene.control.TreeView

class VaultTreeView(/* VaultService, NoteService, vaultRoot */) {
    val view: TreeView<String>
    var onNoteSelected: (relPath: String) -> Unit = {}
    fun refresh(tree: FolderNode)       // rebuild from domain tree model
}
```

Contract: renders `FolderNode` (folders expandable, notes as leaves, both sorted per the domain
model). Selecting a note fires `onNoteSelected`. Context menus: on folders — new note, new
folder, rename, delete (recursive delete requires a confirmation dialog); on notes — rename,
move, delete (confirmation), toggle favorite. No file I/O here — all actions call services.

**`ui/EditorView.kt`**

```kotlin
package com.brainvault.ui

import javafx.scene.control.TextArea

class EditorView {
    val view: TextArea                  // monospaced font via app.css
    val dirty: Boolean                  // true when text differs from last saved state
    var onTextChanged: (String) -> Unit = {}
    var onSaveRequested: () -> Unit = {}
    fun load(text: String)              // resets dirty
    fun markSaved()
}
```

Contract: plain monospaced `TextArea` — no WYSIWYG, no syntax highlighting in v1. Edits set
`dirty = true` and fire `onTextChanged` (consumed by `PreviewView` debounce). **No autosave** —
saving is explicit via Ctrl+S (`onSaveRequested`). Loading a note with unsaved changes elsewhere
shows a discard confirmation.

**`ui/PreviewView.kt`**

```kotlin
package com.brainvault.ui

import javafx.scene.web.WebView

class PreviewView(/* NoteService */) {
    val view: WebView
    fun update(markdownBody: String)    // debounced render
}
```

Contract: renders `NoteService.renderHtml(body)` into the WebView via `loadContent`. Updates are
debounced **300 ms** after the last `update` call; rendering runs off the JavaFX Application
Thread (threading rule above). The HTML is fully self-contained (inline stylesheet, §5.3
`MarkdownParser`) — no external resources, honoring the offline rule.

**`ui/SearchPanel.kt`**

```kotlin
package com.brainvault.ui

class SearchPanel(/* SearchService */) {
    var onHitSelected: (relPath: String) -> Unit = {}
    fun focusQuery()                    // called by Ctrl+Shift+F
}
```

Contract: query text field with search-as-you-type (300 ms debounce), results list showing title,
vault-relative path, and snippet with `<b>` highlight markers rendered as bold. Selecting a hit
fires `onHitSelected`. Empty query shows an empty list, not an error.

**`ui/BacklinksPanel.kt`**

```kotlin
package com.brainvault.ui

class BacklinksPanel(/* LinkRepository-backed service access per composition root */) {
    var onBacklinkSelected: (relPath: String) -> Unit = {}
    fun showFor(notePath: String)
}
```

Contract: lists all resolved inbound links for the currently open note (source title + path,
ordered by source path per §5.3 `SqliteLinkRepository`). Clicking navigates via
`onBacklinkSelected`. Shows an explicit "no backlinks" placeholder rather than a blank panel.

**`ui/TagsPanel.kt`**

```kotlin
package com.brainvault.ui

class TagsPanel(/* TagService */) {
    var onTagSelected: (tagName: String) -> Unit = {}
    fun refresh()
}
```

Contract: lists `TagService.allTags()` as "name (count)". Clicking a tag fires `onTagSelected`;
`MainView` responds by filtering the note list to `TagService.notesFor(tag)`. A "clear filter"
affordance restores the full tree.

**`ui/FavoritesPanel.kt`**

```kotlin
package com.brainvault.ui

class FavoritesPanel(/* FavoriteService */) {
    var onFavoriteSelected: (relPath: String) -> Unit = {}
    fun refresh()
}
```

Contract: lists favorites (title + path, ordered by `added_at` per §5.2 `FavoriteService`).
Clicking opens the note. A remove button per entry calls `FavoriteService.toggle`.

**`ui/QuickOpenDialog.kt`**

```kotlin
package com.brainvault.ui

class QuickOpenDialog(/* NoteRepository */) {
    var onChosen: (relPath: String) -> Unit = {}
    fun show()
}
```

Contract: modal dialog bound to Ctrl+P. A text field filters `NoteRepository.allPaths()` by
case-insensitive substring match (path and title both matched), updated per keystroke without
debounce (the list is in memory and small). Enter or double-click chooses the highlighted entry;
Escape closes.

**`ui/SettingsDialog.kt`**

```kotlin
package com.brainvault.ui

class SettingsDialog(/* SettingsService */) {
    fun show()
}
```

Contract: modal dialog bound to Ctrl+, and File → Settings. Edits the four §5.2 `SettingsService`
values: vault path (text field + directory picker), daily note folder, daily filename pattern, and
daily template. The Save button writes through the setters and calls `SettingsService.persist()`;
Cancel discards. A vault-path change takes effect only after an app restart — when the path field
is modified, the dialog shows a label saying so (v1 simplification: the composition root builds
everything around one vault; runtime vault switching is post-v1). On open, fields are pre-filled
from current settings.

**`ui/StatusBar.kt`**

```kotlin
package com.brainvault.ui

import javafx.scene.layout.HBox

class StatusBar {
    val view: HBox
    fun showIndexing(done: Int, total: Int)
    fun showIdle(noteCount: Int)
    fun showDirty(dirty: Boolean)
    fun setVaultPath(path: String)
}
```

Contract: bottom bar showing (left to right) vault path, index status ("Indexing 12/240…" during
rebuild, "N notes indexed" when idle), and dirty/saved indicator for the open note.

### 5.5 Resources

**`resources/com/brainvault/ui/app.css`** — the only stylesheet.

Contract: defines (1) a monospaced font stack for the editor
(`"JetBrains Mono", "Consolas", "Monospaced"`, size 13), (2) padding/spacing for the left
accordion panels, (3) list-cell styling for search snippets, (4) status bar styling. Loaded once
by `MainView` via `scene.stylesheets.add(...)` from the classpath. No other CSS files; no inline
JavaFX styles outside this file.

### 5.6 The `LEARN[KJV-nnn]` comment convention (CRITICAL — learning goal)

This project doubles as a Kotlin-for-Java-developers course. The convention is mandatory from
the first file written and is audited in the Definition of Done (§11).

1. **Explain once.** Each Kotlin↔Java concept is explained exactly once, at its first occurrence
   in the codebase (milestone order, then file order), in a block comment of exactly this form:

   ```
   // ============================================================
   // LEARN[KJV-001] Data classes
   // Kotlin:
   //   <what the Kotlin code below does and why>
   // Java 25 equivalent:
   //   <comparable Java 25 code, e.g. a record>
   // Differences:
   //   <concise explanation of what differs and why>
   // ============================================================
   ```

2. **Numbering.** IDs are assigned sequentially (`KJV-001`, `KJV-002`, …) in the order concepts
   first appear. An ID, once assigned, is never reused or renumbered.

3. **Reference later.** Later occurrences of an already-explained concept get only a one-line
   comment: `// see LEARN[KJV-001]`.

4. **Minimum concept list** (all must appear by the end of M7; the list is a floor, not a
   ceiling — first occurrence of any other Kotlin↔Java difference also gets a block):

   `data class` vs record · null safety (`?`, `?.`, `?:`, `!!`) vs `Optional` · `val`/`var` vs
   `final` · extension functions · `companion object` vs static · default/named arguments vs
   overloads · `when` vs switch · sealed classes/interfaces · coroutines vs virtual
   threads/`CompletableFuture` · scope functions (`let/apply/run/also/with`) · delegated
   properties · collections operators (`map/filter/fold`) vs Streams · string templates ·
   primary constructors · `object` singletons · `init` blocks · `by lazy`

5. **Index file.** `KOTLIN_VS_JAVA.md` in the repository root contains an index table:

   | ID | Concept | Explained in (file → symbol) | One-line summary |
   | --- | --- | --- | --- |

   Every `LEARN[KJV-nnn]` block added to the code adds its row to this table **in the same
   change**. The Definition of Done (§11) requires the audit "every LEARN id in code appears in
   the index and vice versa".

---

## 6. Milestone plan

Eight milestones, executed strictly in order. **Each milestone is independently executable** and
ends with `./gradlew test` green (WSL) and the app launchable via `./gradlew run`. A milestone is
not complete until its exit criteria are met; do not start the next milestone before that. After
each milestone, report the test result (per §12) before continuing.

### M0 — Bootstrap

- **Files:** all §5.0 build files (`settings.gradle.kts`, `gradle/libs.versions.toml`,
  `build.gradle.kts`, `gradle.properties`, wrapper files, `.gitignore`), plus a minimal
  `MainApp.kt` showing an empty stage titled "BrainVault", and an empty placeholder test.
- **Steps:** `mkdir -p ~/dev2/BrainVault`; verify JDK (§10); bootstrap the wrapper once with
  `gradle wrapper --gradle-version 9.6.1`; from then on only `./gradlew` / `gradlew.bat`.
- **Exit criteria:** `./gradlew test` green; `./gradlew run` opens the empty window (WSLg);
  `gradlew.bat run` opens it on Windows.

### M1 — Domain + Markdown/YAML parsing

- **Files:** all of `domain/model/` and `domain/port/` (§5.1); `infrastructure/markdown/`
  (`FrontMatterCodec.kt`, `MarkdownParser.kt`); tests: `NoteMetaTest`, `FrontMatterCodecTest`,
  `MarkdownParserTest`; `testutil/TestVaults.kt`.
- **Notes:** first LEARN blocks land here (data classes, null safety, default arguments, …);
  `KOTLIN_VS_JAVA.md` created with its first rows.
- **Exit criteria:** front-matter round-trip and link extraction tests green; `./gradlew test`
  green; `./gradlew run` still opens the empty window.

### M2 — SQLite repositories + FTS5

- **Files:** `infrastructure/persistence/` (`Database`, `SqliteNoteRepository`,
  `Fts5SearchIndex`, `SqliteTagRepository`, `SqliteLinkRepository`, `SqliteFavoriteRepository`);
  tests: all six `infrastructure/persistence` test classes.
- **Notes:** DDL from §4.2 reproduced verbatim; FTS trigger-sync verified by tests.
- **Exit criteria:** repository + FTS tests green against temp SQLite files; `./gradlew test`
  green; `./gradlew run` opens the window.

### M3 — Filesystem watcher + indexing

- **Files:** `infrastructure/fs/` (`VaultFileStore`, `VaultWatcher`); `application/` indexing
  trio (`IndexingService`, `VaultService`, `SettingsService` + `PropertiesSettingsStore` in
  `infrastructure/config/`); tests: `VaultFileStoreTest`, `VaultWatcherTest`,
  `IndexingServiceTest`, `VaultServiceTest`, `PropertiesSettingsStoreTest`.
- **Exit criteria:** full rebuild over a fixture vault produces a queryable index; file events
  trigger incremental reindex in tests; `./gradlew test` green; `./gradlew run` opens the window.

### M4 — UI shell (tree, editor, preview)

- **Files:** `ui/MainApp.kt` (full composition root), `MainView`, `VaultTreeView`, `EditorView`,
  `PreviewView`, `StatusBar`, `app.css`; `application/NoteService.kt`; tests:
  `NoteServiceTest`.
- **Exit criteria:** the running app shows the vault tree, opens a note in the editor, edits it
  with dirty tracking, saves via Ctrl+S, and renders a live debounced preview; `./gradlew test`
  green.

### M5 — Search, backlinks, tags, favorites

- **Files:** `ui/SearchPanel.kt`, `BacklinksPanel.kt`, `TagsPanel.kt`, `FavoritesPanel.kt`,
  `QuickOpenDialog.kt`; `application/SearchService.kt`, `FavoriteService.kt`, `TagService.kt`;
  tests: `SearchServiceTest`, `FavoriteServiceTest`, `TagServiceTest`.
- **Exit criteria:** search-as-you-type with snippets, backlinks panel, tag filtering, favorites
  toggle, and Ctrl+P quick open all work in the running app against a real vault; `./gradlew
  test` green.

### M6 — Daily notes, shortcuts, import/export

- **Files:** `application/DailyNoteService.kt`, `ImportExportService.kt`, `ui/SettingsDialog.kt`;
  tests: `DailyNoteServiceTest`, `ImportExportServiceTest`; complete §8 shortcut registration in
  `MainView` (anything not already wired).
- **Exit criteria:** daily note create/open, full shortcut map, import folder, export folder,
  export zip all work; `./gradlew test` green; `gradlew.bat test` green on Windows too.

### M7 — Hardening, comment audit, README

- **Work:** fix edge cases found in manual testing (both WSL and Windows runs); complete the
  LEARN minimum concept list (§5.6) and audit every id against `KOTLIN_VS_JAVA.md`; write
  `README.md` (§10 content); create `DECISIONS.md` (header + any logged decisions) and
  `BACKLOG.md` (§13 template only).
- **Exit criteria:** full §11 Definition of Done checklist passes in both environments.

---

## 7. Test plan

**Framework:** JUnit 5 only (`junit-jupiter`, §2). No TestFX, no Kotest, no AssertJ.

**Naming conventions:** test class = `<ClassUnderTest>Test`; test methods use backtick
descriptive names, e.g. ``fun `malformed YAML falls back to body-only`() ``.

**How to run (headless):** `./gradlew test` in WSL, `gradlew.bat test` on Windows. All tests are
plain JVM tests — no JavaFX toolkit startup, no display needed. Repositories run against temp
SQLite files and vault fixtures in temp directories created by `testutil/TestVaults.kt`
(`@TempDir`); nothing touches the real vault or `${user.home}`.

**Coverage expectation:** every application service and every infrastructure class has a
dedicated test class — 100% class coverage below the UI layer. The UI layer is untested by
design (kept thin; §2 TestFX decision).

**Test-to-milestone map** (all 20 test files from §3):

| Milestone | Test classes |
| --- | --- |
| M0 | placeholder smoke test only |
| M1 | `NoteMetaTest`, `FrontMatterCodecTest`, `MarkdownParserTest` (+ `TestVaults` helpers) |
| M2 | `DatabaseTest`, `SqliteNoteRepositoryTest`, `Fts5SearchIndexTest`, `SqliteTagRepositoryTest`, `SqliteLinkRepositoryTest`, `SqliteFavoriteRepositoryTest` |
| M3 | `VaultFileStoreTest`, `VaultWatcherTest`, `IndexingServiceTest`, `VaultServiceTest`, `PropertiesSettingsStoreTest` |
| M4 | `NoteServiceTest` |
| M5 | `SearchServiceTest`, `FavoriteServiceTest`, `TagServiceTest` |
| M6 | `DailyNoteServiceTest`, `ImportExportServiceTest` |
| M7 | no new tests — audit + hardening; any regression found gets a test first |

**Key behaviors that must be covered** (beyond happy paths):

- `FrontMatterCodecTest`: malformed YAML → body-only fallback, unknown keys preserved on
  round-trip, scalar tags coerced to strings.
- `Fts5SearchIndexTest`: snippet markers present, bm25 ordering, delete/update trigger sync.
- `SearchServiceTest`: arbitrary hostile input (quotes, operators, blank) never throws.
- `VaultWatcherTest`: debounce coalescing, `.brainvault/` events dropped, overflow → synthetic
  rescan event.
- `IndexingServiceTest`: full rebuild + incremental create/modify/delete; unresolved links
  resolve after the target note appears.
- `DailyNoteServiceTest`: pattern formatting, template substitution, idempotent open.
- `ImportExportServiceTest`: collision suffixes, `.brainvault/` exclusion, zip entry names.

---

## 8. Keyboard shortcut map (complete)

All shortcuts are registered on the scene in `MainView` and listed in the Help menu from the
same source table. Shortcuts marked ★ were required by the project instructions.

| Shortcut | Action | Scope |
| --- | --- | --- |
| Ctrl+N ★ | New note (in selected/current folder) | global |
| Ctrl+S ★ | Save current note | editor |
| Ctrl+P ★ | Quick open dialog | global |
| Ctrl+Shift+F ★ | Focus search box | global |
| Ctrl+Shift+D ★ | Create/open today's daily note | global |
| Ctrl+B ★ | Toggle favorite on current note | global |
| Ctrl+W | Close current note (prompt if dirty) | editor |
| Ctrl+Shift+S | Save as… / save copy to folder | editor |
| F2 | Rename current note (or selected tree item) | tree/editor |
| Delete | Delete selected tree item (with confirmation) | tree |
| Ctrl+Shift+R | Rebuild index (menu Tools → Rebuild, with progress) | global |
| Ctrl+, | Settings dialog | global |
| Ctrl+I | Import folder of Markdown files | global |
| Ctrl+E | Export vault (folder or zip) | global |
| Escape | Close dialog / clear tag filter / clear search | contextual |
| Ctrl+Q | Exit (watcher stop, DB close, settings persist) | global |

No other shortcuts may be added in v1. Any conflict discovered during implementation is resolved
by keeping this table unchanged and scoping the shortcut more narrowly; log the conflict in
`DECISIONS.md`.

---

## 9. Import/export format spec

### 9.1 Import (`ImportExportService.importFolder`)

- Input: an arbitrary source folder. Only `*.md` files are copied, recursively.
- Destination: `vaultRoot/<targetSubfolder>/`, preserving the source's relative subpaths.
- Collision rule: never overwrite — on a name collision append `-2`, `-3`, … before `.md`
  (`idea.md` → `idea-2.md`).
- Front matter is **not** modified on import; files are copied byte-for-byte.
- `.brainvault/` directories anywhere in the source are skipped.
- Returns the number of files copied; progress callback fires per file.
- After import, the caller (UI) triggers `IndexingService.fullRebuild` with a progress indicator.

### 9.2 Export to folder (`exportToFolder`)

- Copies every vault `.md` file (and only `.md` files) into the target folder, preserving
  vault-relative subpaths. `.brainvault/` is excluded. Existing files at the destination are
  overwritten only inside the chosen export folder (the user picked it explicitly).
- Returns the number of files copied.

### 9.3 Export to zip (`exportToZip`)

- One zip archive via `java.util.zip` (no extra dependency). One entry per vault `.md` file,
  excluding `.brainvault/`.
- Entry names are vault-relative paths with **forward slashes on every OS**, UTF-8 encoded
  (set the EFS flag via `ZipEntry` defaults from `java.util.zip` on UTF-8 names).
- Default compression (`DEFLATED`). No timestamps normalization requirement beyond defaults.
- The zip contains notes only — no index, no settings, no app metadata.

---

## 10. Run & environment instructions

### 10.1 Environments

- **Development/testing:** WSL 2 (Ubuntu) on Windows 11 with WSLg. Project root is exactly
  `~/dev2/BrainVault` — create it with `mkdir -p ~/dev2/BrainVault` and run every WSL command
  from there. JDK 25 (Temurin) is installed via SDKMAN.
- **Production:** native Windows 11 via `gradlew.bat run`. Oracle JDK 25.0.2 is already
  installed. Any JDK 25 vendor works — the Gradle toolchain pins only the language version
  (§5.0), never a vendor.
- **How Windows reaches the repo:** the code lives in the WSL filesystem and is accessed from
  Windows through the WSL file bridge at `\\wsl$\Ubuntu\home\<user>\dev2\BrainVault`. All Windows
  commands must be run from **PowerShell** — cmd.exe does not support UNC working directories.
  Gradle builds over the bridge are noticeably slower than native; acceptable for v1 (cloning the
  repo to a native Windows path is a post-v1 option — candidate `BACKLOG.md` item).

### 10.2 JDK verification (no installation steps)

WSL:

```bash
java --version    # must report 25.x
javac --version   # must report 25.x
```

Windows (PowerShell or cmd):

```bat
java --version
javac --version
```

Both must report 25.x before M0 proceeds. If they do not, stop and report — do not attempt a JDK
install as part of this build.

### 10.3 Command table

| Task | WSL (dev/test) | Windows (production) |
| --- | --- | --- |
| Create project root | `mkdir -p ~/dev2/BrainVault` | n/a (repo is shared) |
| Repo location | `~/dev2/BrainVault` | `\\wsl$\Ubuntu\home\<user>\dev2\BrainVault` — **PowerShell only**, never cmd.exe |
| Bootstrap wrapper (once) | `gradle wrapper --gradle-version 9.6.1` | n/a — wrapper committed |
| Build + test | `./gradlew test` | `gradlew.bat test` |
| Run app | `./gradlew run` | `gradlew.bat run` |
| Clean | `./gradlew clean` | `gradlew.bat clean` |

### 10.4 Vault locations (do not mix — critical)

- **Production vault (Windows):** `%USERPROFILE%\Documents\BrainVault` (§4.1 default). The
  Windows-native app watches it with the native Windows `WatchService`, which works reliably.
- **WSL dev/test vault:** a throwaway vault inside the WSL filesystem, `~/brainvault-dev-vault`,
  created fresh for dev runs; tests always use `@TempDir` fixtures instead.
- **README must carry this warning verbatim in spirit:** during WSL-based development/testing,
  do NOT point the WSL instance at the Windows vault via `/mnt/c/...` — filesystem watching
  across that bridge is unreliable and slow. The two vaults must never be mixed.

### 10.5 Application data locations

- Index DB: `<vault>/.brainvault/index.db` — delete it to force a full rebuild (§4.1).
- Settings: `${user.home}/.brainvault/config.properties` (Windows: `%USERPROFILE%\.brainvault\config.properties`).

### 10.6 README.md content requirement

`README.md` (written in M7) contains: one-paragraph description, the §10.2 verification steps,
the §10.3 command table, the §10.4 vault warning, and the data locations from §10.5. Nothing
else — no screenshots, no roadmap (roadmap feedback goes to `BACKLOG.md`).

---

## 11. Definition of Done checklist

v1 is done only when **every** box is checked, in **both** environments where applicable:

- [ ] Project tree matches §3 exactly: 45 main Kotlin files + 1 CSS + 21 test Kotlin files (20
      test classes + 1 shared test helper) + 13 build/root files (any deliberate deviation is
      reflected in §3/§5 and logged in `DECISIONS.md`).
- [ ] `./gradlew test` green in WSL.
- [ ] `gradlew.bat test` green on Windows.
- [ ] `./gradlew run` works in WSL (WSLg); `gradlew.bat run` works on Windows.
- [ ] All 14 v1 features from the product spec are demonstrable in the running app: Markdown
      notes CRUD, YAML front matter, tags, folders, full-text search, backlinks, daily notes,
      favorites, full shortcut map (§8), live preview, filesystem watching, index rebuild,
      import/export, tests.
- [ ] Dependency list matches §2 exactly — no added or changed versions.
- [ ] **Comment-convention audit:** every `LEARN[KJV-nnn]` id appearing in code appears in
      `KOTLIN_VS_JAVA.md`, and every row in `KOTLIN_VS_JAVA.md` corresponds to an id in code —
      both directions. The §5.6 minimum concept list is fully covered.
- [ ] `DECISIONS.md` exists and contains every ambiguity resolution made during implementation.
- [ ] `BACKLOG.md` exists and contains only the §13 template (header + usage instructions).
- [ ] `README.md` matches §10.6.
- [ ] A full index rebuild on a real vault completes with visible progress and the app remains
      usable afterwards.
- [ ] No network access of any kind at runtime (verified by inspection — no HTTP client
      imports anywhere).

---

## 12. Execution rules for the implementing LLM

These rules are binding. They exist because the plan is prescriptive — deviating from them is a
defect, not initiative.

1. **Implement milestone by milestone**, in the order M0 → M7 (§6). Never work ahead into a
   later milestone's files.
2. **After each milestone**, run the full test suite and the app, then **report the result**
   (command, pass/fail counts, any failures) before continuing to the next milestone.
3. **Do not add features** beyond this plan. v1 scope is frozen; "nice to have" ideas go to
   `BACKLOG.md` as notes, not into code. Stop at v1.
4. **Do not add or change dependencies.** The §2 table is final.
5. **Do not skip tests.** Every milestone's test files are part of its definition of done.
6. **Ambiguity protocol:** if something in this plan is genuinely ambiguous, choose the simplest
   option consistent with the surrounding contracts, implement it, and append an entry to
   `DECISIONS.md` in the format: date — decision taken — one-line rationale. Do not stop to ask.
7. **Tree discipline:** if implementation truly needs a file not listed in §3, add it to the §3
   tree and the §5 specification in the same change, and log why in `DECISIONS.md`.
8. **Comment convention from day one:** LEARN blocks are written as the code is written (§5.6),
   not retrofitted in M7. `KOTLIN_VS_JAVA.md` is updated in the same change as each block.
9. **Hard write-path rule:** every note mutation goes through `SqliteNoteRepository` so FTS
   triggers stay consistent (§4.2). No direct SQL outside `infrastructure/persistence`.
10. **Offline rule:** no network calls, no telemetry, no update checks, no AI features. If a
    library appears to phone home, stop and report rather than shipping it.
11. **Environment discipline:** WSL commands assume `~/dev2/BrainVault` as working directory;
    Windows commands assume the same repository. The WSL dev vault and the Windows production
    vault are never mixed (§10.4).

---

## 13. BACKLOG.md template

v1 ships with `BACKLOG.md` containing **only** the following content — header and usage
instructions, no items:

```markdown
# BrainVault Backlog

Post-v1 feedback log. While using BrainVault daily, every annoyance, friction point, or missing
capability becomes an item here. Nothing in this file is scheduled; items are reviewed together
before any post-v1 work starts.

## How to use this file

- One annoyance = one item. Add it at the **top** of the Items section the moment you notice it.
- Do not evaluate, prioritize, or design solutions while writing an item — capture raw.
- Item format:

  ### <short title>
  - **Date:** YYYY-MM-DD
  - **Context:** what you were doing when it hurt
  - **Annoyance:** what happened / what was missing
  - **Desired behavior:** what you wished had happened instead

## Items

(newest first — nothing here yet)
```

---

**End of plan. Implement from M0. Good luck.**
