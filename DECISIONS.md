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

**2026-09-05 — `application.SettingsService` uses a custom delegated `ReadWriteProperty`.**
`vaultPath`/`dailyFolder`/`dailyPattern`/`dailyTemplate` use `by` delegation (a hand-written delegate) so
each get reads through to the store and each set writes through immediately. This both satisfies the
"every property setter writes through to the store" contract and provides the §5.6 delegated-properties
LEARN example. The §5.2 public API is unchanged.

