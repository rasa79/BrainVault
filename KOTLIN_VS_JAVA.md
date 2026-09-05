# Kotlin vs Java — LEARN index

Every `LEARN[KJV-nnn]` block in the codebase has exactly one row here, and every row here
corresponds to exactly one id in code. See §5.6 of `BrainVault_v1_Plan.md`.

| ID | Concept | Explained in (file → symbol) | One-line summary |
| --- | --- | --- | --- |
| KJV-001 | `data class` vs Java record | `domain/model/Note.kt` → `data class Note` | Value holders with derived equals/hashCode/toString; Java records add no copy()/destructuring by default. |
| KJV-002 | `val`/`var` vs `final` | `domain/model/Note.kt` → `Note` constructor params | `val` = read-only reference, generates getter; `final` only stops reassignment in Java. |
| KJV-003 | Null safety (`?`, `?.`, `?:`, `!!`) vs `Optional` | `domain/model/NoteMeta.kt` → nullable `created`/`modified` | `?` marks nullability in the type system; Java uses `Optional`, which is not type-orthogonal. |
| KJV-004 | Default & named arguments vs overloads | `domain/model/NoteMeta.kt` → constructor defaults | Defaults in the declaration; Java needs one overload (or builder) per combination. |
| KJV-005 | `if`-expression vs Java ternary | `domain/model/NoteMeta.kt` → `mergeInto` | Control structures are expressions; Java needs a ternary or per-branch assignment. |
| KJV-006 | `enum class` vs Java enum | `domain/model/Link.kt` → `LinkKind` | Kotlin `when` over an enum as an expression is compile-time exhaustive; Java switch *expressions* over enums/sealed types are also checked since 14/21 (switch statements never needed a default). |
| KJV-007 | Recursive data model + immutable `List` | `domain/model/FolderNode.kt` → `data class FolderNode` | One data type with empty-string sentinels; Kotlin lists are read-only by default. |
| KJV-008 | Interfaces as ports (dependency inversion) | `domain/port/NoteRepository.kt` → `interface NoteRepository` | Domain defines the contract; infrastructure supplies the implementation. |
| KJV-009 | Data class + enum + `java.nio.file.Path` in domain | `domain/port/FileEventSource.kt` → `FileEvent` | Domain may use `Path` only as a signature type per the layering rule. |
| KJV-010 | `when` vs Java `switch` (expression) | `infrastructure/markdown/FrontMatterCodec.kt` → `toMeta` | `when` is value-producing, needs no break, and can match on types. |
| KJV-011 | Collections operators vs Streams | `infrastructure/markdown/FrontMatterCodec.kt` → `filterKeys` | Eager Kotlin operators vs lazy Java Stream pipelines with a terminal op. |
| KJV-012 | String templates vs concatenation | `infrastructure/markdown/FrontMatterCodec.kt` → `serialize` | `"$var"` and `"${expr}"` built into the language; Java needs manual `+`. |
| KJV-013 | `init` blocks & primary constructors | `infrastructure/markdown/MarkdownParser.kt` → `init { }` | `init` runs at construction after property initializers, for one-time setup. |
| KJV-014 | `by lazy` / delegated properties | `infrastructure/markdown/MarkdownParser.kt` → `styleSheet` | `by lazy` defers + memoizes a value; reads like a property. |
| KJV-015 | `object` / `companion object` vs static | `infrastructure/markdown/MarkdownParser.kt` → `private object StoredLinkPredicate`; `persistence/Database.kt` → `private companion object` | `object` is a one-line singleton; `companion object` is a class's static side. |
| KJV-016 | `AutoCloseable` vs try-with-resources | `infrastructure/persistence/Database.kt` → `class Database(...) : AutoCloseable` | Kotlin `use { }` closes resources RAII-style; Java uses try-with-resources. |
| KJV-017 | Java Streams → Kotlin pipelines (`Files.walk`) | `infrastructure/fs/VaultFileStore.kt` → `listMarkdown` | `.toList()` materializes a Stream; `.use { }` closes the file-backed source. |
| KJV-018 | Delegated properties (`by`) | `application/SettingsService.kt` → `var dailyFolder by stringDelegate(...)` | `by` delegates get/set to a `ReadWriteProperty`; Java needs manual accessors. |
| KJV-019 | Coroutines vs virtual threads / `CompletableFuture` | `ui/MainApp.kt` → `dbScope` | Structured coroutines suspend without blocking; `Dispatchers.JavaFx` returns to the UI thread. |
| KJV-020 | Scope functions (`let`/`apply`/`run`/`also`/`with`) | `ui/MainView.kt` → `TextInputDialog(...).apply { }` | `apply`/`also` return the receiver to configure; `let`/`run` return the block result. |
| KJV-021 | Extension functions vs static helpers | `infrastructure/fs/VaultFileStore.kt` → `private fun Path.isUnderDir(dir)` | Kotlin adds methods to existing types with member syntax; Java needs static utils. |
| KJV-022 | Sealed classes/interfaces & exhaustive `when` | `application/IndexingService.kt` → `when (link.kind)` | Kotlin enforces exhaustion for `when` used as an *expression*; Java switch expressions over sealed/enum are likewise checked (14/21), and a switch statement never required a default. |
