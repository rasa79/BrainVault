package com.brainvault.application

import com.brainvault.domain.port.SettingsStore
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import java.nio.file.Path

/**
 * Typed access over [SettingsStore]. Each property reads through to the store on
 * get and writes through on set; `persist()` flushes the underlying store to
 * disk. Unknown/future keys in the properties file are left untouched.
 */
class SettingsService(private val store: SettingsStore) {

    // ============================================================
    // LEARN[KJV-018] Delegated properties (custom ReadWriteProperty)
    // Kotlin:
    //   `var dailyFolder: String by stringDelegate(...)` uses the `by` delegation
    //   operator: the value lives in the delegate, not the class field. Every
    //   get reads through to the store, every set writes through immediately.
    //   This is the same mechanism as `by lazy`/`by observable`, but with a
    //   hand-written delegate (ReadWriteProperty) so we control persistence.
    // Java 25 equivalent:
    //   private String dailyFolder() { return store.get(KEY).orElse("daily"); }
    //   private void setDailyFolder(String v) { store.put(KEY, v); ... }
    //   Accessor methods with manual get/set (no language-level delegation).
    // Differences:
    //   - Kotlin delegation reads/writes like a plain property field. Java needs
    //     manual accessors or an external framework.
    //   - A custom ReadWriteProperty is reusable: the same delegate type backs all
    //     four settings below.
    // ============================================================
    var vaultPath: Path by pathDelegate("vault.path", "Documents/BrainVault")
    var dailyFolder: String by stringDelegate("daily.folder", "daily")
    var dailyPattern: String by stringDelegate("daily.pattern", "yyyy-MM-dd")
    var dailyTemplate: String by stringDelegate("daily.template", DEFAULT_TEMPLATE)

    fun persist() = store.save()

    private fun stringDelegate(key: String, default: String): ReadWriteProperty<SettingsService, String> =
        object : ReadWriteProperty<SettingsService, String> {
            override fun getValue(thisRef: SettingsService, property: KProperty<*>): String =
                store.get(key) ?: default

            override fun setValue(thisRef: SettingsService, property: KProperty<*>, value: String) {
                store.put(key, value)
            }
        }

    private fun pathDelegate(key: String, relativeToHome: String): ReadWriteProperty<SettingsService, Path> =
        object : ReadWriteProperty<SettingsService, Path> {
            override fun getValue(thisRef: SettingsService, property: KProperty<*>): Path =
                store.get(key)?.let(Path::of) ?: defaultHomePath(relativeToHome)

            override fun setValue(thisRef: SettingsService, property: KProperty<*>, value: Path) {
                store.put(key, value.toString())
            }
        }

    private fun defaultHomePath(relative: String): Path =
        Path.of(System.getProperty("user.home"), relative)

    private companion object {
        val DEFAULT_TEMPLATE = "---\ntitle: {{title}}\ntags: [daily]\ncreated: {{created}}\n---\n\n# {{date}}\n"
    }
}
