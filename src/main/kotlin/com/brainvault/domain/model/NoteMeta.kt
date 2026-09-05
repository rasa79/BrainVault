package com.brainvault.domain.model

import java.time.LocalDateTime

// ============================================================
// LEARN[KJV-003] Null safety vs Optional
// Kotlin:
//   `LocalDateTime?` is a *nullable* type — the `?` marks it as possibly null.
//   The compiler forces the caller to handle null before using the value. Here
//   we copy it into another nullable and read `meta.title ?: fallback` (the
//   elvis operator returns the right-hand side when the left is null).
// Java 25 equivalent:
//   Optional<LocalDateTime> created; // + explicit .orElse()/map() chains
// Differences:
//   - Kotlin tracks nullability in the type system; NPEs are largely a thing of
//     the past (no `.get()` that can throw).
//   - `?:` (elvis), `?.` (safe-call), `!!` (not-null assertion) are surface
//     syntax; Java uses Optional, but Optional only guards *reference* nulls
//     and is not orthogonal to the type system.
// ============================================================
data class NoteMeta(
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val created: LocalDateTime? = null,
    val modified: LocalDateTime? = null,
    val extras: Map<String, Any?> = emptyMap(),  // unknown YAML keys, preserved verbatim
) {
    // ============================================================
    // LEARN[KJV-004] Default & named arguments vs Java overloads
    // Kotlin:
    //   The constructor above declares defaults (= null / = emptyList()).
    //   A caller can write NoteMeta(title = "x") and the rest use defaults.
    //   Defaults let one constructor serve many call shapes; named args make
    //   the call self-documenting and immune to parameter-order mistakes.
    // Java 25 equivalent:
    //   Java models this with overloading: NoteMeta(), NoteMeta(title), etc.,
    //   or the builder pattern / @CompactConstructor on a record.
    // Differences:
    //   - Kotlin defaults live in the *declaration*; Java has no default-arg
    //     support natively, so every combination is a separate overload.
    //   - Named arguments let us call in any order; Java only offers builders.
    // ============================================================
    fun effectiveTitle(fallback: String): String = title ?: fallback

    fun withModified(now: LocalDateTime): NoteMeta = copy(modified = now)

    // ============================================================
    // LEARN[KJV-005] if-expression vs Java ternary
    // Kotlin:
    //   `fun mergeInto(...)` uses the *expression* form. In Kotlin, many control
    //   structures are expressions that produce a value, so a method body can be
    //   a single expression. The body below is an `if` with branches, each a
    //   statement — but the whole block is a normal void method.
    // Java 25 equivalent:
    //   Java has the ternary `cond ? a : b` expression and `if` *statements*;
    //   if/else cannot be an expression assigned to a variable directly.
    // Differences:
    //   - Kotlin's `val x = if (a) 1 else 2` is idiomatic; Java needs a ternary
    //     or an assignment in each branch.
    // ============================================================

    /**
     * Writes the *known* front-matter keys into [raw] (which already holds the
     * unknown/extras keys), leaving unknown keys untouched. A known key whose
     * value is null is removed so the YAML output is not cluttered with nulls.
     */
    fun mergeInto(raw: MutableMap<String, Any?>) {
        if (title != null) raw["title"] = title else raw.remove("title")
        raw["tags"] = tags
        if (created != null) raw["created"] = created else raw.remove("created")
        if (modified != null) raw["modified"] = modified else raw.remove("modified")
    }
}
