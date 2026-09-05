package com.brainvault.infrastructure.config

import com.brainvault.domain.port.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * [SettingsStore] backed by a `java.util.Properties` file. Launched with a
 * missing file → empty settings; `put`/`remove` mark the store dirty and `save()`
 * writes to disk only when dirty, creating parent directories.
 */
class PropertiesSettingsStore(configFile: Path) : SettingsStore {

    private val file: Path = configFile.toAbsolutePath()
    private val props: Properties = Properties()
    private var dirty = false

    init {
        if (Files.exists(file)) {
            Files.newInputStream(file).use { props.load(it) }
        }
    }

    override fun get(key: String): String? = props.getProperty(key)

    override fun put(key: String, value: String) {
        props.setProperty(key, value)
        dirty = true
    }

    override fun remove(key: String) {
        if (props.remove(key) != null) dirty = true
    }

    override fun save() {
        if (!dirty) return
        file.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(file).use { props.store(it, "BrainVault settings") }
        dirty = false
    }
}
