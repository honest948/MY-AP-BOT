package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.BotFirestoreRepository
import com.example.data.OpenAiAndMediaRepository
import com.example.data.PolicyEnforcer
import com.example.data.WhatsAppCloudRepository
import com.example.model.AppRoutes
import com.example.security.SessionCryptoManager
import com.example.ui.AdminVaultScreen
import com.example.ui.AuthScreen
import com.example.ui.ConsoleMediaScreen
import com.example.ui.DashboardScreen
import com.example.ui.LinkingScreen
import com.example.ui.PlatformsScreen
import com.example.ui.theme.CyberMint
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.HonestBotProTheme
import com.example.ui.theme.LuminousEmerald
import com.example.ui.theme.MidnightPine
import com.example.ui.theme.SageText
import com.example.viewmodel.CommandCenterViewModel
import com.example.viewmodel.ConsoleMediaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HonestBotProTheme {
                AppNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            HonestBotShell(currentUserId = user.uid)
        }
    }
}

private data class NavTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navTabs = listOf(
    NavTabItem(AppRoutes.ROUTE_DASHBOARD, "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    NavTabItem(AppRoutes.ROUTE_LINKING, "Linking", Icons.Filled.Link, Icons.Outlined.Link),
    NavTabItem(AppRoutes.ROUTE_CONSOLE, "Console", Icons.Filled.Terminal, Icons.Outlined.Terminal),
    NavTabItem(AppRoutes.ROUTE_PLATFORMS, "Platforms", Icons.Filled.Hub, Icons.Outlined.Hub),
    NavTabItem(AppRoutes.ROUTE_VAULT, "Vault", Icons.Filled.Security, Icons.Outlined.Security)
)

@Composable
private fun HonestBotShell(currentUserId: String) {
    val commandCenterVm: CommandCenterViewModel = viewModel(
        key = "cmd_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                val crypto = SessionCryptoManager(app)
                CommandCenterViewModel(
                    firestoreRepo = BotFirestoreRepository(db),
                    openAiMediaRepo = OpenAiAndMediaRepository(app),
                    whatsAppRepo = WhatsAppCloudRepository(crypto),
                    cryptoManager = crypto,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val consoleMediaVm: ConsoleMediaViewModel = viewModel(
        key = "con_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                val crypto = SessionCryptoManager(app)
                ConsoleMediaViewModel(
                    firestoreRepo = BotFirestoreRepository(db),
                    openAiMediaRepo = OpenAiAndMediaRepository(app),
                    whatsAppRepo = WhatsAppCloudRepository(crypto),
                    policyEnforcer = PolicyEnforcer(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.ROUTE_DASHBOARD

    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by commandCenterVm.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            commandCenterVm.clearStatusMessage()
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = DeepObsidian,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MidnightPine
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepObsidian,
                                    selectedTextColor = CyberMint,
                                    indicatorColor = LuminousEmerald,
                                    unselectedIconColor = SageText,
                                    unselectedTextColor = SageText
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MidnightPine) {
                        navTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = DeepObsidian,
                                    selectedTextColor = CyberMint,
                                    indicatorColor = LuminousEmerald,
                                    unselectedIconColor = SageText,
                                    unselectedTextColor = SageText
                                ),
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.ROUTE_DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppRoutes.ROUTE_DASHBOARD) {
                        DashboardScreen(
                            viewModel = commandCenterVm,
                            onNavigateLinking = { navigateTo(AppRoutes.ROUTE_LINKING) },
                            onNavigateConsole = { navigateTo(AppRoutes.ROUTE_CONSOLE) },
                            onNavigateVault = { navigateTo(AppRoutes.ROUTE_VAULT) }
                        )
                    }
                    composable(AppRoutes.ROUTE_LINKING) {
                        LinkingScreen(
                            viewModel = commandCenterVm,
                            onSelectAccountForConsole = { accountId ->
                                consoleMediaVm.selectAccount(accountId)
                                navigateTo(AppRoutes.ROUTE_CONSOLE)
                            }
                        )
                    }
                    composable(AppRoutes.ROUTE_CONSOLE) {
                        ConsoleMediaScreen(
                            commandCenterVm = commandCenterVm,
                            consoleMediaVm = consoleMediaVm
                        )
                    }
                    composable(AppRoutes.ROUTE_PLATFORMS) {
                        PlatformsScreen(viewModel = commandCenterVm)
                    }
                    composable(AppRoutes.ROUTE_VAULT) {
                        AdminVaultScreen(viewModel = commandCenterVm)
                    }
                }
            }
        }
    }
}
