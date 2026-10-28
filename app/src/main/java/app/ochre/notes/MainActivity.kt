package app.ochre.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.ochre.notes.domain.UserSettings
import app.ochre.notes.ui.LocalAppContainer
import app.ochre.notes.ui.OchreApp
import app.ochre.notes.ui.lock.LockGate
import app.ochre.notes.ui.onboarding.OnboardingScreen
import app.ochre.notes.ui.theme.OchreTheme
import app.ochre.notes.widget.OchreWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val container: AppContainer
        get() = (application as OchreApplication).container

    private var deepLinkUid by mutableStateOf<String?>(null)
    private var deepLinkRoute by mutableStateOf<String?>(null)
    private var draftRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())
            LaunchedEffect(settings.hideRecentPreview) {
                if (settings.hideRecentPreview) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            OchreTheme(themeMode = settings.themeMode, fontScale = settings.fontScale) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    if (!settings.onboardingComplete) {
                        OnboardingScreen(
                            onDone = {
                                lifecycleScope.launch {
                                    container.settingsRepository.completeOnboarding()
                                }
                            },
                        )
                    } else {
                        LockGate(
                            enabled = settings.appLockEnabled,
                            timeoutMillis = settings.lockTimeoutMillis,
                        ) {
                            OchreApp(
                                openNoteUid = deepLinkUid,
                                onDeepLinkConsumed = { deepLinkUid = null },
                                draftRequested = draftRequested,
                                onDraftOpened = { draftRequested = false },
                                initialRoute = deepLinkRoute,
                                onInitialRouteConsumed = { deepLinkRoute = null },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        OchreWidgetProvider.refreshAll(this)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data?.scheme == "ochre" && data.host == "note") {
            val uid = data.lastPathSegment
            if (!uid.isNullOrBlank()) deepLinkUid = uid
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                container.draftInbox.offer(text)
                draftRequested = true
            }
            return
        }
        if (intent.action != Intent.ACTION_VIEW || data == null || data.scheme != "ochre") return
        when (data.host) {
            "new" -> draftRequested = true
            "search" -> deepLinkRoute = "search?tag="
            "echo" -> deepLinkRoute = "echo"
        }
    }
}