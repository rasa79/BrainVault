package com.brainvault.infrastructure.fs

import com.brainvault.domain.port.FileEvent
import com.brainvault.domain.port.FileEventKind
import com.brainvault.domain.port.FileEventSource
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit

/**
 * A recursive [FileEventSource] backed by [WatchService]. Events are debounced
 * (300 ms of quiet), duplicated same-path events are coalesced by precedence
 * (CREATED+DELETED cancel out; CREATED wins over MODIFIED), `.brainvault/` is
 * dropped, and OVERFLOW produces a synthetic rescan signal `(CREATED, "")`.
 *
 * v1 design note: this uses a plain daemon thread rather than a coroutine so the
 * watching loop is not tied to any dispatcher; the UI layer wraps the resulting
 * events in coroutines when it calls `IndexingService.onFileEvents`.
 */
class VaultWatcher : FileEventSource {

    private var root: Path? = null
    private var bv: Path? = null
    private var watcher: WatchService? = null
    private var thread: Thread? = null
    private var listener: ((List<FileEvent>) -> Unit)? = null

    @Volatile
    private var running = false

    override fun start(vaultRoot: Path, listener: (List<FileEvent>) -> Unit) {
        this.root = vaultRoot.toAbsolutePath().normalize()
        this.bv = this.root!!.resolve(".brainvault").normalize()
        this.listener = listener
        this.watcher = FileSystems.getDefault().newWatchService()
        registerAll(this.root!!)
        running = true
        thread = Thread({ pollLoop() }, "brainvault-file-watcher").apply {
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        running = false
        try {
            watcher?.close()
        } catch (e: Exception) {
            // already closed
        }
        thread?.interrupt()
        thread?.join(2000)
    }

    private fun pollLoop() {
        while (running) {
            val firstKey = try {
                watcher!!.take()
            } catch (e: Exception) {
                break
            }
            val pending = linkedMapOf<String, MutableSet<FileEventKind>>()
            process(firstKey, pending)
            // Debounce: keep collecting while events keep arriving within DEBOUNCE_MS.
            while (running) {
                val next = try {
                    watcher!!.poll(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    break
                }
                if (next == null) break
                process(next, pending)
            }
            val batch = coalesce(pending)
            if (batch.isNotEmpty()) listener?.invoke(batch)
        }
    }

    private fun process(key: WatchKey, pending: MutableMap<String, MutableSet<FileEventKind>>) {
        if (!key.isValid) {
            key.reset()
            return
        }
        val dir = key.watchable() as Path
        for (event in key.pollEvents()) {
            val kind = event.kind()
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                // Events were lost: request a full rescan via the empty-path signal.
                pending.clear()
                pending[""] = mutableSetOf(FileEventKind.CREATED)
                continue
            }
            val abs = dir.resolve(event.context() as Path).normalize()
            val rel = relOf(abs) ?: continue
            val evKind = when (kind) {
                StandardWatchEventKinds.ENTRY_CREATE -> FileEventKind.CREATED
                StandardWatchEventKinds.ENTRY_MODIFY -> FileEventKind.MODIFIED
                StandardWatchEventKinds.ENTRY_DELETE -> FileEventKind.DELETED
                else -> null
            } ?: continue
            if (evKind == FileEventKind.CREATED && Files.isDirectory(abs)) {
                registerAll(abs)
            }
            pending.getOrPut(rel) { mutableSetOf() }.add(evKind)
        }
        key.reset()
    }

    private fun coalesce(pending: Map<String, MutableSet<FileEventKind>>): List<FileEvent> {
        val batch = mutableListOf<FileEvent>()
        val sentinel = pending[""]
        for ((path, kinds) in pending) {
            if (path.isEmpty()) continue
            val finalKind = finalKind(kinds) ?: continue
            batch += FileEvent(finalKind, path)
        }
        if (sentinel != null) batch += FileEvent(FileEventKind.CREATED, "")
        return batch
    }

    private fun finalKind(kinds: Set<FileEventKind>): FileEventKind? {
        val created = FileEventKind.CREATED in kinds
        val modified = FileEventKind.MODIFIED in kinds
        val deleted = FileEventKind.DELETED in kinds
        return when {
            created && deleted -> null
            created -> FileEventKind.CREATED
            modified -> FileEventKind.MODIFIED
            deleted -> FileEventKind.DELETED
            else -> null
        }
    }

    private fun relOf(abs: Path): String? {
        val r = root ?: return null
        val b = bv
        if (!abs.startsWith(r)) return null
        if (b != null && abs.startsWith(b)) return null
        return r.relativize(abs).toString().replace('\\', '/')
    }

    private fun registerAll(dir: Path) {
        val b = bv ?: return
        val d = dir.normalize()
        if (d.startsWith(b)) return
        if (!Files.isDirectory(d)) return
        Files.walk(d).use { stream ->
            stream.filter { Files.isDirectory(it) }.forEach { register(it) }
        }
    }

    private fun register(dir: Path) {
        val b = bv
        if (b != null && dir.normalize().startsWith(b)) return
        try {
            dir.register(
                watcher!!,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
        } catch (e: Exception) {
            // Directory may have been removed already; ignore.
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
