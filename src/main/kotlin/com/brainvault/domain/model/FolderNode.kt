package com.brainvault.domain.model

// ============================================================
// LEARN[KJV-007] Sealed-adjacent immutable tree model with a nullable field
// Kotlin:
//   A recursive data structure like FolderNode is a plain data class. The root
//   has name "" and path ""; a `List<FolderNode>` models the children. Kotlin
//   lists are immutable by default (read-only view), which suits a derived
//   UI snapshot that MainView rebuilds on every refresh.
//   `name` and `path` are non-null here; the empty string conveys "root" so we
//   avoid nullable strings for the common case.
// Java 25 equivalent:
//   sealed interface FolderNode permits Root/Sub folders in newer Java, or a
//   record FolderNode(String name, String path, List<FolderNode> folders, List<String> notePaths).
// Differences:
//   - Kotlin models the tree with a single data type + non-nullable empty-string
//     sentinels; Java would favor a sealed hierarchy or subtypes.
//   - Kotlin's List<...> is a read-only interface; the caller cannot mutate the
//     snapshot (copy-on-write via `.plus`/listOf is used by the builder).
// ============================================================
data class FolderNode(
    val name: String,                    // "" for the vault root
    val path: String,                    // vault-relative folder path; "" for root
    val folders: List<FolderNode>,       // subfolders, sorted by name
    val notePaths: List<String>,         // notes directly in this folder, sorted
)
