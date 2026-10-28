package app.ochre.notes.ui

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ochre.notes.ui.capsules.CapsulesScreen
import app.ochre.notes.ui.echo.EchoScreen
import app.ochre.notes.ui.editor.EditorScreen
import app.ochre.notes.ui.search.SearchScreen
import app.ochre.notes.ui.settings.SettingsScreen
import app.ochre.notes.ui.theme.ochrePaper
import app.ochre.notes.ui.timeline.TimelineScreen
import app.ochre.notes.ui.trash.TrashScreen
import kotlinx.coroutines.launch

private object Routes {
    const val NOTES = "notes"
    const val SEARCH = "search?tag={tag}"
    const val ECHO = "echo"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{uid}"
    const val CAPSULES = "capsules"
    const val TRASH = "trash"

    fun search(tag: String? = null): String =
        if (tag.isNullOrBlank()) "search?tag=" else "search?tag=${Uri.encode(tag)}"

    fun editor(uid: String?): String = "editor/${uid ?: "new"}"
}

@Composable
fun OchreApp(
    openNoteUid: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    draftRequested: Boolean = false,
    onDraftOpened: () -> Unit = {},
    initialRoute: String? = null,
    onInitialRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()
    val showChrome = !route.startsWith(Routes.EDITOR)

    LaunchedEffect(openNoteUid) {
        if (!openNoteUid.isNullOrBlank()) {
            navController.navigate(Routes.editor(openNoteUid)) { launchSingleTop = true }
            onDeepLinkConsumed()
        }
    }
    LaunchedEffect(draftRequested) {
        if (draftRequested) {
            navController.navigate(Routes.editor(null)) { launchSingleTop = true }
            onDraftOpened()
        }
    }
    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            navController.navigate(initialRoute) { launchSingleTop = true }
            onInitialRouteConsumed()
        }
    }

    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .ochrePaper(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showChrome) {
                OchreBottomBar(
                    currentRoute = route,
                    onNavigate = { destination -> navController.navigateTopLevel(destination) },
                )
            }
        },
        floatingActionButton = {
            if (route == Routes.NOTES) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.editor(null)) },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "New note")
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.NOTES,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.NOTES) {
                TimelineScreen(
                    onOpenNote = { uid -> navController.navigate(Routes.editor(uid)) },
                    onNewNote = { navController.navigate(Routes.editor(null)) },
                    onOpenSearch = { navController.navigateTopLevel(Routes.search()) },
                    onOpenCapsules = { navController.navigate(Routes.CAPSULES) },
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                    onOpenTag = { tag -> navController.navigate(Routes.search(tag)) },
                    showMessage = showMessage,
                    contentPadding = innerPadding,
                )
            }
            composable(
                route = Routes.SEARCH,
                arguments = listOf(navArgument("tag") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val tag = entry.arguments?.getString("tag").orEmpty()
                SearchScreen(
                    initialQuery = if (tag.isBlank()) "" else "tag:$tag",
                    onOpenNote = { uid -> navController.navigate(Routes.editor(uid)) },
                    onOpenTag = { next -> navController.navigate(Routes.search(next)) { launchSingleTop = true } },
                    contentPadding = innerPadding,
                )
            }
            composable(Routes.ECHO) {
                EchoScreen(
                    onOpenNote = { uid -> navController.navigate(Routes.editor(uid)) },
                    showMessage = showMessage,
                    contentPadding = innerPadding,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                    onOpenCapsules = { navController.navigate(Routes.CAPSULES) },
                    onOpenSearch = { navController.navigateTopLevel(Routes.search()) },
                    showMessage = showMessage,
                    contentPadding = innerPadding,
                )
            }
            composable(Routes.CAPSULES) {
                CapsulesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { uid -> navController.navigate(Routes.editor(uid)) },
                    showMessage = showMessage,
                )
            }
            composable(Routes.TRASH) {
                TrashScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { uid -> navController.navigate(Routes.editor(uid)) },
                    showMessage = showMessage,
                )
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument("uid") { type = NavType.StringType }),
            ) { entry ->
                val rawUid = entry.arguments?.getString("uid")
                EditorScreen(
                    initialUid = rawUid?.takeUnless { it == "new" },
                    onBack = { navController.popBackStack() },
                    onOpenNote = { uid -> navController.navigate(Routes.editor(uid)) { launchSingleTop = true } },
                    onOpenTag = { tag -> navController.navigate(Routes.search(tag)) },
                    showMessage = showMessage,
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(routeName: String) {
    navigate(routeName) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private data class TabItem(
    val routeBase: String,
    val destination: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun OchreBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val tabs = listOf(
        TabItem("notes", Routes.NOTES, "Notes", Icons.Outlined.StickyNote2),
        TabItem("search", Routes.search(), "Search", Icons.Outlined.Search),
        TabItem("echo", Routes.ECHO, "Echo", Icons.Outlined.Waves),
        TabItem("settings", Routes.SETTINGS, "Settings", Icons.Outlined.Tune),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute.startsWith(tab.routeBase)
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabTint",
                )
                val dotAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "tabDot")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onNavigate(tab.destination) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 54.dp, height = 28.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f) else Color.Transparent,
                                RoundedCornerShape(50),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(width = 4.dp, height = 2.dp)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = dotAlpha), RoundedCornerShape(50)),
                    )
                }
            }
        }
    }
}