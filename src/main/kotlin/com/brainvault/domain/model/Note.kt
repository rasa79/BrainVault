package com.brainvault.domain.model

// ============================================================
// LEARN[KJV-001] Data classes vs Java records
// Kotlin:
//   `data class Note(...)` is a value holder. The compiler derives
//   equals(), hashCode(), toString(), copy(), and componentN() from the
//   primary constructor parameters declared in the class header. The only
//   body here is documentation; there are no hand-written accessors.
// Java 25 equivalent:
//   public record Note(long id, String path, String title, NoteMeta meta, String body) {}
//   A Java record also derives equals/hashCode/toString/accessors, but has
//   no copy() by default (you write a wither) and no componentN() destructuring.
// Differences:
//   - Kotlin data class copy() gives a shallow copy with any subset of fields
//     changed; Java records need an explicit `withX` wither.
//   - Kotlin exposes componentN() so you can destructure `val (a, b) = note`.
//   - Kotlin data class properties are `val` (read-only) by default; fields
//     are backed by a private field + public getter.
// ============================================================

// ============================================================
// LEARN[KJV-002] val/var vs Java final fields
// Kotlin:
//   `val` declares a read-only (immutable) reference; `var` is mutable.
//   The constructor params here are `val`, so `note.path` reads but cannot be
//   reassigned after construction. Kotlin generates the private field and the
//   public getter for you.
// Java 25 equivalent:
//   private final String path;  // + public String path() accessor on a record
// Differences:
//   - `val` is a compile-time immutability contract, not `final` on a ref only.
//   - There is no `val` in Java; `final` on a field is the closest, but it also
//     prevents reassignment only, not mutation of the referenced object.
// ============================================================
data class Note(
    val id: Long,            // SQLite rowid; 0 or negative = not yet persisted
    val path: String,        // vault-relative, forward slashes, e.g. "projects/idea.md"
    val title: String,
    val meta: NoteMeta,
    val body: String,        // full Markdown body WITHOUT the front-matter block
)

// Contract: `path` is the identity used by the UI and services; `id` is the
// database identity. `body` never contains the front-matter block — codecs
// strip it on read and prepend it on write.
