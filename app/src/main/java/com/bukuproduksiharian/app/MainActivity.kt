package com.bukuproduksiharian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bukuproduksiharian.app.ui.MainViewModel
import com.bukuproduksiharian.app.ui.screens.*
import com.bukuproduksiharian.app.ui.theme.BukuProduksiTheme
import com.bukuproduksiharian.app.ui.theme.DarkGreenPrimary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BukuProduksiTheme {
                BukuProduksiApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BukuProduksiApp(vm: MainViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var activeMenu by remember { mutableStateOf(AppMenu.DASHBOARD) }
    var detailWorkerId by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val createXlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> uri?.let { vm.exportExcel(it) } }

    val createPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { vm.exportPdf(it) } }

    val createMonthlyPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { vm.exportMonthlyPdf(it) } }

    val createCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportCsv(it) } }

    val createJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportBackup(it) } }

    val openJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.restoreBackup(it) } }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "BUKU PRODUKSI HARIAN",
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkGreenPrimary,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                AppMenu.entries.forEach { menu ->
                    NavigationDrawerItem(
                        label = { Text(menu.title) },
                        selected = activeMenu == menu && detailWorkerId == null,
                        onClick = {
                            detailWorkerId = null
                            activeMenu = menu
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(activeMenu.title)
                            state.activePeriod?.let {
                                Text(
                                    text = if (it.isLocked) "${it.label} (Terkunci)" else it.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkGreenPrimary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Buka Menu")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf(
                        AppMenu.DASHBOARD to Icons.Default.Dashboard,
                        AppMenu.WEEKLY_BOOK to Icons.Default.TableChart,
                        AppMenu.INDIVIDUAL to Icons.Default.People,
                        AppMenu.INCOME_REPORT to Icons.Default.Assessment,
                        AppMenu.BACKUP to Icons.Default.SaveAlt
                    ).forEach { (menu, icon) ->
                        NavigationBarItem(
                            selected = activeMenu == menu && detailWorkerId == null,
                            onClick = {
                                detailWorkerId = null
                                activeMenu = menu
                            },
                            icon = { Icon(icon, contentDescription = menu.title) },
                            label = { Text(menu.title.split(" ").first(), maxLines = 1) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val activeWorkerDetailId = detailWorkerId
                if (activeWorkerDetailId != null) {
                    WorkerDetailScreen(
                        state = state,
                        workerId = activeWorkerDetailId,
                        onSelectWorker = { detailWorkerId = it },
                        onBack = { detailWorkerId = null }
                    )
                } else {
                    when (activeMenu) {
                        AppMenu.DASHBOARD -> DashboardScreen(
                            state = state,
                            onNavigate = { activeMenu = it },
                            onLoadBenchmark = vm::loadBenchmarkData
                        )
                        AppMenu.WEEKLY_BOOK -> WeeklyBookScreen(
                            state = state,
                            vm = vm,
                            onOpenPriceHistory = { activeMenu = AppMenu.PRICE_HISTORY }
                        )
                        AppMenu.INDIVIDUAL -> IndividualProductionScreen(
                            state = state,
                            vm = vm,
                            onOpenWorkerDetail = { detailWorkerId = it }
                        )
                        AppMenu.INCOME_REPORT -> IncomeReportScreen(
                            state = state,
                            onExportExcel = { createXlsxLauncher.launch("buku-produksi-mingguan.xlsx") },
                            onExportPdf = { createPdfLauncher.launch("laporan-mingguan.pdf") },
                            onOpenWorkerDetail = { detailWorkerId = it }
                        )
                        AppMenu.MONTHLY_RECAP -> MonthlyRecapScreen(
                            state = state,
                            vm = vm,
                            onExportExcel = { createXlsxLauncher.launch("buku-produksi-bulanan.xlsx") },
                            onExportMonthlyPdf = { createMonthlyPdfLauncher.launch("rekap-bulanan-${state.selectedMonthKey}.pdf") }
                        )
                        AppMenu.SETTINGS -> SettingsScreen(state = state, vm = vm)
                        else -> MasterDataAndToolsScreen(
                            menu = activeMenu,
                            state = state,
                            vm = vm,
                            onExportExcel = { createXlsxLauncher.launch("buku-produksi-mingguan.xlsx") },
                            onExportPdf = { createPdfLauncher.launch("laporan-produksi.pdf") },
                            onExportCsv = { createCsvLauncher.launch("rekap-mingguan.csv") },
                            onExportBackup = { createJsonLauncher.launch("backup-buku-produksi.json") },
                            onRestoreBackup = { openJsonLauncher.launch(arrayOf("application/json")) },
                            onOpenWorkerDetail = { detailWorkerId = it }
                        )
                    }
                }
            }
        }
    }
}
