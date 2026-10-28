package app.ochre.notes.domain

enum class ThemeMode { System, Light, Dark }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.Light,
    val fontScale: Float = 1f,
    val onboardingComplete: Boolean = false,
    val welcomeSeeded: Boolean = false,
    val lastSeenVersion: String = "",
    val appLockEnabled: Boolean = false,
    val lockTimeoutMillis: Long = 30_000L,
    val hideRecentPreview: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val syncUrl: String = "",
    val syncUsername: String = "",
    val syncEnabled: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    val lastSyncAt: Long? = null,
    val lastSyncMessage: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupTreeUri: String? = null,
    val lastBackupAt: Long? = null,
    val vaultPassphraseSet: Boolean = false,
)