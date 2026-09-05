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
//   - Kotlin enums can hold richer behavior, and a `when` over an enum used as an
//     *expression* is exhaustively checked (a missed case is a compile error and
//     no `else` is needed). Java switch *expressions* over enums/sealed types are
//     also exhaustiveness-checked since Java 14/21 (switch statements never
//     required a default), so this is not a Java limitation when using a switch
//     expression — see LEARN[KJV-022].
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
