package app.ochre.notes.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ochre.notes.BuildConfig
import app.ochre.notes.data.backup.BackupManager
import app.ochre.notes.data.backup.VaultCrypto
import app.ochre.notes.data.security.SecretStore
import app.ochre.notes.data.settings.SettingsRepository
import app.ochre.notes.data.sync.SyncManager
import app.ochre.notes.data.sync.SyncWorker
import app.ochre.notes.domain.ThemeMode
import app.ochre.notes.domain.UserSettings
import app.ochre.notes.notifications.ReminderScheduler
import app.ochre.notes.ui.components.SparkMark
import app.ochre.notes.ui.ochreViewModel
import app.ochre.notes.ui.util.Formatting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun counted(count: Int, singular: String, plural: String = singular + "s") =
    "$count ${if (count == 1) singular else plural}"

private class SettingsViewModel(
    private val context: Context,
    private val repository: SettingsRepository,
    private val backupManager: BackupManager,
    private val scheduler: ReminderScheduler,
    private val syncManager: SyncManager,
    private val secrets: SecretStore,
) : ViewModel() {
    val settings = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }

    fun setFontScale(scale: Float) = viewModelScope.launch { repository.setFontScale(scale) }

    fun setAppLock(enabled: Boolean) = viewModelScope.launch { repository.setAppLock(enabled) }

    fun setLockTimeout(millis: Long) = viewModelScope.launch { repository.setLockTimeout(millis) }

    fun setHideRecentPreview(hidden: Boolean) = viewModelScope.launch { repository.setHideRecentPreview(hidden) }

    fun createBackup(uri: Uri, passphrase: String) = runTask("Encrypting your vault…") {
        val summary = backupManager.exportEncrypted(uri, passphrase.toCharArray())
        repository.setVaultPassphraseSet(true)
        repository.setLastBackupAt(System.currentTimeMillis())
        "Backed up ${counted(summary.memos, "note")} and ${counted(summary.attachments, "attachment")}."
    }

    fun restoreBackup(uri: Uri, passphrase: String) = runTask("Restoring your notes…") {
        val summary = backupManager.restoreEncrypted(uri, passphrase.toCharArray())
        scheduler.rescheduleAll()
        "Restored ${counted(summary.importedMemos, "new note")} and ${counted(summary.updatedMemos, "updated note")}."
    }

    fun exportMarkdown(uri: Uri) = runTask("Exporting Markdown…") {
        val summary = backupManager.exportMarkdown(uri)
        "Exported ${counted(summary.memos, "note")}. Open the zip to read them anywhere."
    }

    fun saveSync(url: String, username: String, password: String, passphrase: String?, autoSync: Boolean) {
        viewModelScope.launch {
            repository.setSyncConfiguration(url, username, enabled = url.isNotBlank())
            if (password.isNotBlank()) secrets.put(SecretStore.SYNC_PASSWORD, password)
            if (!passphrase.isNullOrBlank()) secrets.put(SecretStore.SYNC_PASSPHRASE, passphrase)
            repository.setAutoSync(autoSync)
            SyncWorker.schedule(context, autoSync && url.isNotBlank())
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSync(enabled)
            SyncWorker.schedule(context, enabled)
        }
    }

    fun disconnectSync() {
        viewModelScope.launch {
            repository.setSyncConfiguration("", "", enabled = false)
            repository.setAutoSync(false)
            secrets.clear(SecretStore.SYNC_PASSWORD)
            secrets.clear(SecretStore.SYNC_PASSPHRASE)
            SyncWorker.schedule(context, false)
        }
    }

    fun syncNow() = runTask("Syncing your notes…") {
        val result = syncManager.syncNow()
        buildString {
            append("Synced. ")
            append("${result.pushed} sent, ${result.pulled} received")
            if (result.media > 0) append(", ${counted(result.media, "media file")}")
            append(".")
        }
    }

    fun hasSecret(name: String) = secrets.has(name)

    private fun runTask(label: String, block: suspend () -> String) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = label
            val outcome = runCatching { block() }
            _busy.value = null
            outcome
                .onSuccess { messages.send(it) }
                .onFailure { error ->
                    messages.send(
                        when (error) {
                            is VaultCrypto.WrongPassphraseException -> "That passphrase did not match this backup."
                            else -> error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
                        },
                    )
                }
        }
    }
}

@Composable
fun SettingsScreen(
    onOpenTrash: () -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenSearch: () -> Unit,
    showMessage: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val viewModel = ochreViewModel { container ->
        SettingsViewModel(
            context = container.appContext,
            repository = container.settingsRepository,
            backupManager = container.backupManager,
            scheduler = container.reminderScheduler,
            syncManager = container.syncManager,
            secrets = container.secretStore,
        )
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCredits by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var vaultDialog by remember { mutableStateOf<VaultDialog?>(null) }
    var showSync by remember { mutableStateOf(false) }
    var pendingPassphrase by remember { mutableStateOf("") }
    val stamp = remember { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }

    LaunchedEffect(Unit) { viewModel.events.collect { showMessage(it) } }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) viewModel.createBackup(uri, pendingPassphrase)
        pendingPassphrase = ""
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.restoreBackup(uri, pendingPassphrase)
        pendingPassphrase = ""
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.exportMarkdown(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = contentPadding.calculateBottomPadding() + 90.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SparkMark(size = 22.dp)
                Spacer(Modifier.width(9.dp))
                Text("SETTINGS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Make it yours.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))
        }

        item { SectionTitle("APPEARANCE") }
        item {
            SettingsCard {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                ThemeSelector(selected = settings.themeMode, onSelect = viewModel::setTheme)
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(20.dp))
                Text("Text size", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                FontScaleSelector(scale = settings.fontScale, onSelect = viewModel::setFontScale)
            }
        }

        item { SectionTitle("LIBRARY", Modifier.padding(top = 14.dp)) }
        item {
            SettingsCard(contentPadding = 6.dp) {
                SettingsRow(Icons.Outlined.LockClock, "Capsules", "Letters to your future self", onOpenCapsules)
                SettingsRow(Icons.Outlined.Delete, "Trash", "Notes kept for 30 days", onOpenTrash)
                SettingsRow(Icons.Outlined.Search, "Browse tags", "Every thread in your library", onOpenSearch)
            }
        }

        item { SectionTitle("SECURITY", Modifier.padding(top = 14.dp)) }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Biometric lock", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Ask for your fingerprint or screen lock when Ochre opens",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.appLockEnabled,
                        onCheckedChange = { wantsLock ->
                            if (wantsLock) {
                                val manager = BiometricManager.from(context)
                                val can = manager.canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                                ) == BiometricManager.BIOMETRIC_SUCCESS
                                if (can) {
                                    viewModel.setAppLock(true)
                                    showMessage("App lock enabled")
                                } else {
                                    showMessage("Set up a screen lock on this device first")
                                }
                            } else {
                                viewModel.setAppLock(false)
                                showMessage("App lock off")
                            }
                        },
                    )
                }
                if (settings.appLockEnabled) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(14.dp))
                    Text("Lock again after", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LockTimeoutSelector(
                        current = settings.lockTimeoutMillis,
                        onSelect = viewModel::setLockTimeout,
                    )
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hide note previews", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Blur Ochre in the app switcher",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.hideRecentPreview,
                        onCheckedChange = viewModel::setHideRecentPreview,
                    )
                }
            }
        }

        item { SectionTitle("YOUR DATA", Modifier.padding(top = 14.dp)) }
        item {
            SettingsCard(contentPadding = 6.dp) {
                SettingsRow(
                    Icons.Outlined.Backup,
                    "Encrypted backup",
                    "A sealed vault file you can keep anywhere",
                ) {
                    if (busy == null) vaultDialog = VaultDialog.CreateBackup
                }
                SettingsRow(
                    Icons.Outlined.Restore,
                    "Restore backup",
                    "Merge a vault file into this device",
                ) {
                    if (busy == null) vaultDialog = VaultDialog.RestoreBackup
                }
                SettingsRow(
                    Icons.Outlined.Description,
                    "Export Markdown",
                    "Plain .md files and media in a zip",
                ) {
                    if (busy == null) exportLauncher.launch("ochre-markdown-$stamp.zip")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Backups are encrypted with AES-256 using your passphrase. There is no recovery if you forget it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            settings.lastBackupAt?.let { last ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "Last encrypted backup ${Formatting.relativeAge(last)} · ${Formatting.dateTime(last)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }

        busy?.let { label ->
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item { SectionTitle("SYNC", Modifier.padding(top = 14.dp)) }
        item {
            SettingsCard(contentPadding = 6.dp) {
                if (settings.syncUrl.isBlank()) {
                    SettingsRow(
                        Icons.Outlined.CloudQueue,
                        "Set up sync",
                        "End-to-end encrypted across your devices",
                    ) { showSync = true }
                } else {
                    SettingsRow(
                        Icons.Outlined.Sync,
                        "Sync now",
                        settings.lastSyncMessage ?: "Not synced yet",
                    ) {
                        if (busy == null) viewModel.syncNow()
                    }
                    SettingsRow(
                        Icons.Outlined.CloudQueue,
                        "Sync settings",
                        settings.syncUrl.removePrefix("https://").removePrefix("http://").take(46),
                    ) { showSync = true }
                }
            }
            if (settings.syncUrl.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sync automatically", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Roughly every 6 hours, on any connection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.autoSyncEnabled, onCheckedChange = viewModel::setAutoSync)
                    }
                    settings.lastSyncAt?.let { last ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Last synced ${Formatting.relativeAge(last)} · ${Formatting.time(last)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { SectionTitle("PRIVACY", Modifier.padding(top = 14.dp)) }
        item {
            SettingsCard(contentPadding = 6.dp) {
                SettingsRow(Icons.Outlined.Shield, "Privacy", "Local-first, no account, no tracking") { showPrivacy = true }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Ochre stores your notes on this device. Nothing is uploaded unless you explicitly set up sync or backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        item { SectionTitle("ABOUT", Modifier.padding(top = 14.dp)) }
        item {
            SettingsCard(contentPadding = 6.dp) {
                SettingsRow(Icons.Outlined.Info, "Ochre ${BuildConfig.VERSION_NAME}", "An offline-first notebook") { showCredits = true }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            Text(
                "Made with care. Inspired by Memos (MIT).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showCredits) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showCredits = false },
            icon = { SparkMark(size = 28.dp) },
            title = { Text("Ochre") },
            text = {
                Column {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}\n\n" +
                            "Conceptually inspired by Memos, the open-source memo hub by usememos, used under the MIT License.\n\n" +
                            "Typography: Fraunces and Inter, licensed under the SIL Open Font License.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { uriHandler.openUri("https://github.com/usememos/memos") }) {
                        Text("usememos/memos")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCredits = false }) { Text("Close") } },
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Your notes stay yours") },
            text = {
                Text(
                    "Ochre works completely offline. There is no account, no analytics and no advertising.\n\n" +
                        "Notes, photos and voice memories live in the app's private storage. Optional backup and sync are end-to-end encrypted and only run when you turn them on.\n\n" +
                        "Biometric data never leaves your device's secure hardware.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Understood") } },
        )
    }

    when (vaultDialog) {
        VaultDialog.CreateBackup -> PassphraseDialog(
            title = "Seal this backup",
            confirmLabel = "Choose location",
            requireConfirmation = true,
            hint = "This passphrase encrypts the vault file. You will need it to restore — it cannot be recovered.",
            onDismiss = { vaultDialog = null },
            onConfirm = { passphrase ->
                pendingPassphrase = passphrase
                vaultDialog = null
                createBackupLauncher.launch("ochre-vault-$stamp.ochre")
            },
        )
        VaultDialog.RestoreBackup -> PassphraseDialog(
            title = "Open a backup",
            confirmLabel = "Choose file",
            requireConfirmation = false,
            hint = "Enter the passphrase you used when this vault was created. Matching notes merge newest-first.",
            onDismiss = { vaultDialog = null },
            onConfirm = { passphrase ->
                pendingPassphrase = passphrase
                vaultDialog = null
                restoreLauncher.launch(arrayOf("*/*"))
            },
        )
        null -> Unit
    }

    if (showSync) {
        SyncSetupDialog(
            initialUrl = settings.syncUrl,
            initialUsername = settings.syncUsername,
            initialAutoSync = settings.autoSyncEnabled,
            hasPassword = viewModel.hasSecret(SecretStore.SYNC_PASSWORD),
            hasPassphrase = viewModel.hasSecret(SecretStore.SYNC_PASSPHRASE),
            onDismiss = { showSync = false },
            onDisconnect = if (settings.syncUrl.isBlank()) {
                null
            } else {
                {
                    showSync = false
                    viewModel.disconnectSync()
                    showMessage("Sync turned off")
                }
            },
            onSave = { url, username, password, passphrase, autoSync ->
                showSync = false
                viewModel.saveSync(url, username, password, passphrase, autoSync)
                showMessage(if (url.isBlank()) "Sync turned off" else "Sync ready")
            },
        )
    }
}

@Composable
private fun SyncSetupDialog(
    initialUrl: String,
    initialUsername: String,
    initialAutoSync: Boolean,
    hasPassword: Boolean,
    hasPassphrase: Boolean,
    onDismiss: () -> Unit,
    onDisconnect: (() -> Unit)?,
    onSave: (url: String, username: String, password: String, passphrase: String?, autoSync: Boolean) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var autoSync by remember { mutableStateOf(initialAutoSync) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypted sync") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Point Ochre at any WebDAV folder — Nextcloud, Synology or your own server. Notes are sealed on this device before they are uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("WebDAV address") },
                    placeholder = { Text("https://dav.example.com/notes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(if (hasPassword) "Password (saved)" else "Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text(if (hasPassphrase) "Sync passphrase (saved)" else "Sync passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Use the same passphrase on every device. It cannot be recovered.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sync automatically",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                onDisconnect?.let { disconnect ->
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = disconnect) {
                        Text("Disconnect this device", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = url.trim()
                    when {
                        trimmed.isBlank() -> onSave("", "", "", null, false)
                        !trimmed.startsWith("https://") && !trimmed.startsWith("http://") ->
                            error = "The address must start with https://"
                        !hasPassword && password.isBlank() -> error = "Enter your WebDAV password."
                        !hasPassphrase && passphrase.length < 8 ->
                            error = "The sync passphrase needs at least 8 characters."
                        else -> onSave(
                            trimmed,
                            username.trim(),
                            password,
                            passphrase.takeIf { it.isNotBlank() },
                            autoSync,
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class VaultDialog { CreateBackup, RestoreBackup }

@Composable
private fun PassphraseDialog(
    title: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirmation) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it; error = null },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        value.length < 8 -> error = "Use at least 8 characters."
                        requireConfirmation && value != confirmation -> error = "The passphrases do not match."
                        else -> onConfirm(value)
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun SettingsCard(
    contentPadding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.width(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.System, "System", Icons.Outlined.SettingsBrightness),
        Triple(ThemeMode.Light, "Light", Icons.Outlined.LightMode),
        Triple(ThemeMode.Dark, "Dark", Icons.Outlined.DarkMode),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (mode, label, icon) ->
            val isSelected = selected == mode
            val background by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "themeBackground",
            )
            Column(
                Modifier
                    .weight(1f)
                    .background(background, RoundedCornerShape(11.dp))
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LockTimeoutSelector(current: Long, onSelect: (Long) -> Unit) {
    val options = listOf(
        "30 sec" to 30_000L,
        "1 min" to 60_000L,
        "5 min" to 300_000L,
        "15 min" to 900_000L,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (label, millis) ->
            val selected = current == millis
            Surface(
                onClick = { onSelect(millis) },
                color = if (selected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FontScaleSelector(scale: Float, onSelect: (Float) -> Unit) {
    val options = listOf(0.9f to 14, 1f to 16, 1.12f to 19, 1.28f to 22)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, size) ->
            val isSelected = kotlin.math.abs(scale - value) < 0.01f
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        RoundedCornerShape(11.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Aa",
                    fontSize = size.sp,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

