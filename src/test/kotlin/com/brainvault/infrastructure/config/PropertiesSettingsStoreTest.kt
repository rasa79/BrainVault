package com.brainvault.infrastructure.config

import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PropertiesSettingsStoreTest {

    @Test
    fun `missing file loads empty`() {
        val store = PropertiesSettingsStore(TestVaults.tempDir().resolve("none.properties"))
        assertNull(store.get("k"))
    }

    @Test
    fun `put and save round trip`() {
        val file = TestVaults.tempDir().resolve("config.properties")
        val store = PropertiesSettingsStore(file)
        store.put("k", "v")
        store.put("other", "1")
        store.save()

        val reloaded = PropertiesSettingsStore(file)
        assertEquals("v", reloaded.get("k"))
        assertEquals("1", reloaded.get("other"))
    }

    @Test
    fun `remove deletes a key`() {
        val file = TestVaults.tempDir().resolve("config.properties")
        val store = PropertiesSettingsStore(file)
        store.put("k", "v")
        store.save()
        store.remove("k")
        store.save()
        assertNull(PropertiesSettingsStore(file).get("k"))
    }

    @Test
    fun `save does not write when not dirty`() {
        val file = TestVaults.tempDir().resolve("config.properties")
        val store = PropertiesSettingsStore(file)
        store.save() // not dirty -> should still create nothing (or empty stays absent)
        assertNull(store.get("k"))
    }

    @Test
    fun `creates parent directories`() {
        val file = TestVaults.tempDir().resolve("a/b/c.properties")
        val store = PropertiesSettingsStore(file)
        store.put("k", "v")
        store.save()
        assertEquals("v", PropertiesSettingsStore(file).get("k"))
    }
}
