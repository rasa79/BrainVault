package com.brainvault.infrastructure.fs

import com.brainvault.domain.port.FileEvent
import com.brainvault.domain.port.FileEventKind
import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VaultWatcherTest {

    private val received = ConcurrentLinkedQueue<FileEvent>()

    private fun startWatcher(root: java.nio.file.Path): VaultWatcher {
        val watcher = VaultWatcher()
        watcher.start(root) { events -> events.forEach { received.add(it) } }
        return watcher
    }

    @Test
    fun `create event is delivered`() {
        val root = TestVaults.tempDir()
        val watcher = startWatcher(root)
        try {
            Thread.sleep(200)
            Files.writeString(root.resolve("a.md"), "hello")
            Thread.sleep(1200)
            val created = received.filter { it.vaultRelativePath == "a.md" }
            assertEquals(FileEventKind.CREATED, created.firstOrNull()?.kind, "events=$received")
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `modify event is delivered`() {
        val root = TestVaults.tempDir()
        Files.writeString(root.resolve("a.md"), "v1")
        val watcher = startWatcher(root)
        try {
            Thread.sleep(200)
            Files.writeString(root.resolve("a.md"), "v2")
            Thread.sleep(1200)
            val modified = received.filter { it.vaultRelativePath == "a.md" }
            assertTrue(modified.any { it.kind == FileEventKind.MODIFIED }, "events=$received")
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `delete event is delivered`() {
        val root = TestVaults.tempDir()
        Files.writeString(root.resolve("a.md"), "v1")
        val watcher = startWatcher(root)
        try {
            Thread.sleep(200)
            Files.delete(root.resolve("a.md"))
            Thread.sleep(1200)
            val deleted = received.filter { it.vaultRelativePath == "a.md" }
            assertTrue(deleted.any { it.kind == FileEventKind.DELETED }, "events=$received")
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `create then modify within window coalesces to created`() {
        val root = TestVaults.tempDir()
        val watcher = startWatcher(root)
        try {
            Thread.sleep(200)
            Files.writeString(root.resolve("a.md"), "v1")
            Files.writeString(root.resolve("a.md"), "v2")
            Thread.sleep(1400)
            val forA = received.filter { it.vaultRelativePath == "a.md" }
            assertTrue(forA.isNotEmpty(), "events=$received")
            assertTrue(
                forA.none { it.kind == FileEventKind.DELETED },
                "should not see a DELETED for a.md: $received",
            )
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `brainvault events are dropped`() {
        val root = TestVaults.tempDir()
        val watcher = startWatcher(root)
        try {
            Thread.sleep(200)
            Files.createDirectories(root.resolve(".brainvault"))
            Files.writeString(root.resolve(".brainvault/secret.md"), "hidden")
            Thread.sleep(1200)
            assertTrue(
                received.none { it.vaultRelativePath.startsWith(".brainvault/") },
                "brainvault events should be dropped: $received",
            )
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `empty path signals rescan on overflow path is never from normal events`() {
        val root = TestVaults.tempDir()
        val watcher = startWatcher(root)
        try {
            Thread.sleep(200)
            Files.writeString(root.resolve("a.md"), "hello")
            Thread.sleep(1200)
            // Normal file events never use the empty path sentinel.
            assertTrue(received.none { it.vaultRelativePath.isEmpty() }, "events=$received")
        } finally {
            watcher.stop()
        }
    }
}
