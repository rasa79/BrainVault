package com.brainvault.domain.port

// see LEARN[KJV-008] (ports as interfaces)
interface SettingsStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun save()                            // persist to disk
}
