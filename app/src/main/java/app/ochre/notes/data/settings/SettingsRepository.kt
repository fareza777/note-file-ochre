package app.ochre.notes.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ochre.notes.domain.ThemeMode
import app.ochre.notes.domain.UserSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.ochreSettings: DataStore<Preferences> by preferencesDataStore(name = "ochre_settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.ochreSettings

    val settings: Flow<UserSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { preferences ->
            preferences.write(transform(preferences.toSettings()))
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    suspend fun setFontScale(scale: Float) = update { it.copy(fontScale = scale.coerceIn(0.85f, 1.4f)) }

    suspend fun completeOnboarding() = update { it.copy(onboardingComplete = true) }

    suspend fun markWelcomeSeeded() = update { it.copy(welcomeSeeded = true) }

    suspend fun markVersionSeen(version: String) = update { it.copy(lastSeenVersion = version) }

    suspend fun setAppLock(enabled: Boolean) = update { it.copy(appLockEnabled = enabled) }

    suspend fun setLockTimeout(millis: Long) = update { it.copy(lockTimeoutMillis = millis.coerceIn(0L, 3_600_000L)) }

    suspend fun setHideRecentPreview(hidden: Boolean) = update { it.copy(hideRecentPreview = hidden) }

    suspend fun pushRecentSearch(raw: String) {
        val cleaned = raw.trim().take(120)
        if (cleaned.isBlank()) return
        update { current ->
            val next = (listOf(cleaned) + current.recentSearches.filterNot { it.equals(cleaned, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            current.copy(recentSearches = next)
        }
    }

    suspend fun clearRecentSearches() = update { it.copy(recentSearches = emptyList()) }

    suspend fun setSyncConfiguration(url: String, username: String, enabled: Boolean) =
        update { it.copy(syncUrl = url.trim(), syncUsername = username.trim(), syncEnabled = enabled) }

    suspend fun setAutoSync(enabled: Boolean) = update { it.copy(autoSyncEnabled = enabled) }

    suspend fun setSyncResult(at: Long?, message: String?) =
        update { it.copy(lastSyncAt = at, lastSyncMessage = message) }

    suspend fun setAutoBackup(enabled: Boolean, treeUri: String?) =
        update { it.copy(autoBackupEnabled = enabled, autoBackupTreeUri = treeUri ?: it.autoBackupTreeUri) }

    suspend fun setLastBackupAt(at: Long) = update { it.copy(lastBackupAt = at) }

    suspend fun setVaultPassphraseSet(set: Boolean) = update { it.copy(vaultPassphraseSet = set) }

    private fun Preferences.toSettings(): UserSettings = UserSettings(
        themeMode = runCatching { ThemeMode.valueOf(this[Keys.THEME] ?: ThemeMode.Light.name) }
            .getOrDefault(ThemeMode.Light),
        fontScale = this[Keys.FONT_SCALE] ?: 1f,
        onboardingComplete = this[Keys.ONBOARDING] ?: false,
        welcomeSeeded = this[Keys.WELCOME_SEEDED] ?: false,
        lastSeenVersion = this[Keys.LAST_VERSION] ?: "",
        appLockEnabled = this[Keys.APP_LOCK] ?: false,
        lockTimeoutMillis = this[Keys.LOCK_TIMEOUT] ?: 30_000L,
        hideRecentPreview = this[Keys.HIDE_PREVIEW] ?: false,
        recentSearches = this[Keys.RECENT_SEARCHES]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList(),
        syncUrl = this[Keys.SYNC_URL] ?: "",
        syncUsername = this[Keys.SYNC_USER] ?: "",
        syncEnabled = this[Keys.SYNC_ENABLED] ?: false,
        autoSyncEnabled = this[Keys.AUTO_SYNC] ?: false,
        lastSyncAt = this[Keys.LAST_SYNC_AT],
        lastSyncMessage = this[Keys.LAST_SYNC_MESSAGE],
        autoBackupEnabled = this[Keys.AUTO_BACKUP] ?: false,
        autoBackupTreeUri = this[Keys.AUTO_BACKUP_URI],
        lastBackupAt = this[Keys.LAST_BACKUP_AT],
        vaultPassphraseSet = this[Keys.VAULT_PASSPHRASE] ?: false,
    )

    private fun MutablePreferences.write(settings: UserSettings) {
        this[Keys.THEME] = settings.themeMode.name
        this[Keys.FONT_SCALE] = settings.fontScale
        this[Keys.ONBOARDING] = settings.onboardingComplete
        this[Keys.WELCOME_SEEDED] = settings.welcomeSeeded
        this[Keys.LAST_VERSION] = settings.lastSeenVersion
        this[Keys.APP_LOCK] = settings.appLockEnabled
        this[Keys.LOCK_TIMEOUT] = settings.lockTimeoutMillis
        this[Keys.HIDE_PREVIEW] = settings.hideRecentPreview
        this[Keys.RECENT_SEARCHES] = settings.recentSearches.joinToString("\n")
        this[Keys.SYNC_URL] = settings.syncUrl
        this[Keys.SYNC_USER] = settings.syncUsername
        this[Keys.SYNC_ENABLED] = settings.syncEnabled
        this[Keys.AUTO_SYNC] = settings.autoSyncEnabled
        settings.lastSyncAt?.let { this[Keys.LAST_SYNC_AT] = it } ?: remove(Keys.LAST_SYNC_AT)
        settings.lastSyncMessage?.let { this[Keys.LAST_SYNC_MESSAGE] = it } ?: remove(Keys.LAST_SYNC_MESSAGE)
        this[Keys.AUTO_BACKUP] = settings.autoBackupEnabled
        settings.autoBackupTreeUri?.let { this[Keys.AUTO_BACKUP_URI] = it } ?: remove(Keys.AUTO_BACKUP_URI)
        settings.lastBackupAt?.let { this[Keys.LAST_BACKUP_AT] = it } ?: remove(Keys.LAST_BACKUP_AT)
        this[Keys.VAULT_PASSPHRASE] = settings.vaultPassphraseSet
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val WELCOME_SEEDED = booleanPreferencesKey("welcome_seeded")
        val LAST_VERSION = stringPreferencesKey("last_seen_version")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val LOCK_TIMEOUT = longPreferencesKey("lock_timeout")
        val HIDE_PREVIEW = booleanPreferencesKey("hide_recent_preview")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val SYNC_URL = stringPreferencesKey("sync_url")
        val SYNC_USER = stringPreferencesKey("sync_username")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync_enabled")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_tree_uri")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val VAULT_PASSPHRASE = booleanPreferencesKey("vault_passphrase_set")
    }

    private companion object {
        const val MAX_RECENT_SEARCHES = 8
    }
}