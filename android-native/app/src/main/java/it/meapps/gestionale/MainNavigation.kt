@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScaffold(vm: AppViewModel, snackbar: SnackbarHostState) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun select(tab: MainTab) {
        vm.selectTab(tab)
        scope.launch { drawer.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    Text(
                        "Gestionale",
                        style = MaterialTheme.typography.headlineSmall,
                        color = AppNavy,
                    )
                    Text(
                        "Il tuo lavoro, sotto controllo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Attività")
                    listOf(
                            Triple(
                                MainTab.HOME,
                                stringResource(R.string.dashboard),
                                Icons.Default.SpaceDashboard,
                            ),
                            Triple(
                                MainTab.ARTICLES,
                                stringResource(R.string.articles),
                                Icons.Default.Inventory2,
                            ),
                            Triple(
                                MainTab.CLIENTS,
                                stringResource(R.string.customers),
                                Icons.Default.People,
                            ),
                            Triple(
                                MainTab.ORDERS,
                                stringResource(R.string.orders),
                                Icons.Default.ReceiptLong,
                            ),
                        )
                        .forEach { (tab, label, icon) ->
                            NavigationDrawerItem(
                                label = { Text(label) },
                                selected = vm.selectedTab == tab,
                                icon = { Icon(icon, null) },
                                onClick = { select(tab) },
                            )
                        }
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Organizzazione")
                    listOf(
                            EntityKind.BRAND to stringResource(R.string.brands),
                            EntityKind.SUPPLIER to stringResource(R.string.suppliers),
                            EntityKind.CATEGORY to stringResource(R.string.categories),
                        )
                        .forEach { (kind, label) ->
                            NavigationDrawerItem(
                                label = { Text(label) },
                                selected =
                                    vm.selectedTab == MainTab.ARCHIVES && vm.archiveKind == kind,
                                icon = { Icon(Icons.Default.FolderOpen, null) },
                                onClick = {
                                    vm.archiveKind = kind
                                    select(MainTab.ARCHIVES)
                                },
                            )
                        }
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("App e dati")
                    NavigationDrawerItem(
                        label = { Text("Impostazioni e backup") },
                        selected = vm.selectedTab == MainTab.SETTINGS,
                        icon = { Icon(Icons.Default.Settings, null) },
                        onClick = { select(MainTab.SETTINGS) },
                    )
                    NavigationDrawerItem(
                        label = { Text("Sincronizza") },
                        selected = false,
                        icon = { Icon(Icons.Default.Sync, null) },
                        onClick = {
                            vm.loadAll()
                            scope.launch { drawer.close() }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Surface(color = BrandNavy) {
                    Row(
                        Modifier.fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                "Gestionale",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                            )
                            Text(
                                DateFormat.getDateInstance(
                                        DateFormat.MEDIUM,
                                        LocalConfiguration.current.locales[0],
                                    )
                                    .format(Date()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCAD8E6),
                            )
                        }
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                stringResource(R.string.menu),
                                tint = Color.White,
                            )
                        }
                    }
                }
            },
            bottomBar = { AppBottomNavigation(vm.selectedTab, vm::selectTab) },
            floatingActionButton = {
                if (vm.selectedTab == MainTab.ARTICLES || vm.selectedTab == MainTab.ARCHIVES)
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (vm.selectedTab == MainTab.ARTICLES) vm.openProduct()
                            else vm.openEntity(vm.archiveKind)
                        },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = {
                            Text(
                                if (vm.selectedTab == MainTab.ARTICLES)
                                    stringResource(R.string.product)
                                else stringResource(R.string.new_label)
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                androidx.compose.animation.Crossfade(
                    vm.selectedTab,
                    animationSpec = androidx.compose.animation.core.tween(150),
                    label = "Sezione",
                ) { tab ->
                    when (tab) {
                        MainTab.HOME -> HomeScreen(vm)
                        MainTab.ARTICLES -> ProductsScreen(vm)
                        MainTab.CLIENTS -> CustomersScreen(vm)
                        MainTab.ORDERS -> OrdersScreen(vm)
                        MainTab.ARCHIVES -> ArchivesScreen(vm)
                        MainTab.SETTINGS -> SettingsScreen(vm)
                    }
                }
                if (vm.loading)
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
internal fun DetailScaffold(
    vm: AppViewModel,
    subtitle: String,
    selectedTab: MainTab,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            Surface(color = BrandNavy) {
                Row(
                    Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::closeDetail) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back),
                            tint = Color.White,
                        )
                    }
                    Text(
                        subtitle,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        },
        bottomBar = {
            AppBottomNavigation(selectedTab) {
                vm.closeDetail()
                vm.selectTab(it)
            }
        },
        content = content,
    )
}
