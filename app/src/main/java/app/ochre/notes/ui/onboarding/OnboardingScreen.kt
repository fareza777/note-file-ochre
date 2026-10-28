package app.ochre.notes.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ochre.notes.ui.components.SparkMark
import app.ochre.notes.ui.theme.ochrePaper
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val label: String,
    val title: String,
    val body: String,
    val footnote: String,
)

private val pages = listOf(
    OnboardingPage(
        label = "OCHRE",
        title = "A quieter place\nfor your thoughts.",
        body = "Ochre is a notebook without noise. No feeds, no followers, no account — just your words on warm paper.",
        footnote = "Local-first · No tracking",
    ),
    OnboardingPage(
        label = "WRITE FREELY",
        title = "Notes that keep\nup with you.",
        body = "Quick notes become rich pages with Markdown, photos, voice memories and #tags. Everything saves as you type.",
        footnote = "Markdown · Tags · Attachments",
    ),
    OnboardingPage(
        label = "REMEMBER",
        title = "Your thinking,\nreturned to you.",
        body = "Echo brings back older notes at the right interval. Threads connect ideas with [[wiki links]], and Capsules hold letters for your future self.",
        footnote = "Echo · Threads · Capsules",
    ),
    OnboardingPage(
        label = "PRIVATE",
        title = "Yours alone.\nAlways.",
        body = "Your vault lives on this device first, protected by biometrics whenever you want. Optional encrypted backup and sync are yours to switch on.",
        footnote = "Offline-first · Biometric lock",
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .ochrePaper()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SparkMark(size = 26.dp)
            Spacer(Modifier.weight(1f))
            if (!lastPage) {
                TextButton(onClick = onDone) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    page.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    page.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .fillMaxWidth(0.2f)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), RoundedCornerShape(50)),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(26.dp))
                Text(
                    page.footnote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.indices.forEach { index ->
                    val selected = pagerState.currentPage == index
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                        label = "dotColor",
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .background(color, CircleShape),
                    )
                }
            }
            Button(
                onClick = {
                    if (lastPage) {
                        onDone()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (lastPage) "Start writing" else "Continue",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "No account needed. Ever.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}