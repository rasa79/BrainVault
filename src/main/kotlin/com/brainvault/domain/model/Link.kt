package com.brainvault.domain.model

// ============================================================
// LEARN[KJV-006] Enum classes vs Java enums
// Kotlin:
//   `enum class LinkKind { WIKI, MARKDOWN }` declares a sealed set of named
//   constants, exactly like a Java enum. Kotlin enums may also carry
//   constructor params, properties, and methods (not needed here).
// Java 25 equivalent:
//   public enum LinkKind { WIKI, MARKDOWN }
// Differences:
//   - Kotlin `enum class` is the keyword; Java uses `enum`.
//   - Kotlin enums can hold richer behavior and, with an exhaustive `when`,
//     do not require a `default` branch.
// ============================================================
enum class LinkKind { WIKI, MARKDOWN }

// see LEARN[KJV-001] (data class) and LEARN[KJV-002] (val/var)
// `targetPath` is nullable because an unresolved link has no target.
// see LEARN[KJV-003] (null safety)
data class Link(
    val sourcePath: String,        // vault-relative path of the note containing the link
    val targetPath: String?,       // resolved vault-relative target; null = unresolved
    val rawTarget: String,         // as written in the source
    val kind: LinkKind,
)
