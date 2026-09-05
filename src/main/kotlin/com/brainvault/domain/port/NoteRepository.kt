package com.brainvault.domain.port

import com.brainvault.domain.model.Note

// ============================================================
// LEARN[KJV-008] Interfaces as ports (dependency-inversion)
// Kotlin:
//   A Kotlin `interface` declares a contract with no implementation, exactly
//   like Java. Here it is the *domain port* that the application layer depends
//   on, so that infrastructure (SQLite) can provide it without the domain
//   knowing anything about persistence. This is the classic hexagonal/DIP port.
// Java 25 equivalent:
//   public interface NoteRepository { long upsert(Note n); ... }
// Differences:
//   - Kotlin interfaces may contain default method implementations (like Java
//     default methods), though the plan keeps ports purely abstract.
//   - Kotlin mnemonic: no `public` keyword is needed; everything is public by
//     default (the inverse of Java's package-private default).
// ============================================================
interface NoteRepository {
    fun upsert(note: Note): Long                       // insert or update by path; returns rowid
    fun deleteByPath(path: String)                     // no-op if absent
    fun findByPath(path: String): Note?
    fun findByTitle(title: String): Note?              // case-insensitive; also matches filename w/o .md
    fun allPaths(): List<String>
    fun count(): Int
}
