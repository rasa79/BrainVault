package com.brainvault.domain.port

import java.nio.file.Path

// ============================================================
// LEARN[KJV-009] Data classes in the domain module + enum
// Kotlin:
//   FileEvent is a small value type carrying a kind and a path. The enum
//   FileEventKind expresses the three possible change classes. Both live in
//   the `domain` module with a `java.nio.file.Path` type used only as a
//   signature parameter — the single allowed framework/type import for domain.
//   (domain may import java.time.* and java.nio.file.Path per §5.1.)
// Java 25 equivalent:
//   record FileEvent(FileEventKind kind, String vaultRelativePath) {} with an
//   enum FileEventKind { CREATED, MODIFIED, DELETED }
// Differences:
//   - Kotlin carries the event kind as a strongly-typed enum and the path as a
//     plain string (vault-relative, forward slashes) — no java.nio.Path leaks as
//     a value, only as a signature type (kept per the layering rule).
// ============================================================
enum class FileEventKind { CREATED, MODIFIED, DELETED }

data class FileEvent(val kind: FileEventKind, val vaultRelativePath: String)

// see LEARN[KJV-008] (ports as interfaces)
interface FileEventSource {
    fun start(vaultRoot: Path, listener: (List<FileEvent>) -> Unit)  // batches after debounce
    fun stop()
}
