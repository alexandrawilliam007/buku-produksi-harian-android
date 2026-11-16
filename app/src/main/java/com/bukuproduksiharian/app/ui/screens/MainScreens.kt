package com.bukuproduksiharian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bukuproduksiharian.app.data.db.PriceHistoryEntity
import com.bukuproduksiharian.app.data.db.ProductTypeEntity
import com.bukuproduksiharian.app.data.db.WorkerEntity
import com.bukuproduksiharian.app.domain.LedgerCalculator
import com.bukuproduksiharian.app.domain.WeeklyTypeRowSummary
import com.bukuproduksiharian.app.ui.MainUiState
import com.bukuproduksiharian.app.ui.MainViewModel
import com.bukuproduksiharian.app.ui.theme.DarkGreenPrimary
import com.bukuproduksiharian.app.ui.theme.NeutralBorder
import com.bukuproduksiharian.app.ui.theme.SoftGreenSurface
import java.time.LocalDate

enum class AppMenu(val title: String) {
    DASHBOARD("Dashboard"),
    WEEKLY_BOOK("Pembukuan Mingguan"),
    INDIVIDUAL("Produksi Perorangan"),
    TYPES("Data TYPE Barang"),
    WORKERS("Data Nama Orang"),
    PRICE_HISTORY("Riwayat Harga"),
    INCOME_REPORT("Laporan Pendapatan"),
    MONTHLY_RECAP("Rekap Bulanan"),
    BACKUP("Backup & Restore"),
    SETTINGS("Pengaturan")
}

@Composable
fun DashboardScreen(
    state: MainUiState,
    onNavigate: (AppMenu) -> Unit,
    onLoadBenchmark: () -> Unit
) {
    val recap = state.weeklyRecap
    val todayIso = LocalDate.now().toString()
    val todayTx = state.transactions.filter { it.date == todayIso && it.quantity > 0L }
    val todayQty = todayTx.sumOf { it.quantity }
    val todayRev = todayTx.sumOf { it.quantity * it.unitPriceSnapshot }

    val topByQty = state.workerSummaries.maxByOrNull { it.totalQty }
    val topByRev = state.workerSummaries.maxByOrNull { it.totalRevenue }
    val dayShort = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Ringkasan Pembukuan Mingguan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkGreenPrimary
                    )
                    Text(
                        state.activePeriod?.label ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF475569)
                    )
                }
                if (state.transactions.isEmpty()) {
                    OutlinedButton(onClick = onLoadBenchmark) {
                        Text("Data Uji")
                    }
                }
            }
        }

        // 8 KPI Cards (2 columns x 4 rows)
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Produksi Minggu Ini", "${LedgerCalculator.formatNumber(recap?.grandTotalQty ?: 0L)} Unit", Modifier.weight(1f), highlight = true)
                KpiCard("Pendapatan Minggu Ini", LedgerCalculator.formatRupiah(recap?.grandTotalRevenue ?: 0L), Modifier.weight(1f), highlight = true)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Jumlah Orang", "${state.workers.count { it.isActive }} Orang", Modifier.weight(1f))
                KpiCard("Jumlah TYPE", "${state.types.size} TYPE", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Produksi Hari Ini", "${LedgerCalculator.formatNumber(todayQty)} Unit", Modifier.weight(1f))
                KpiCard("Pendapatan Hari Ini", LedgerCalculator.formatRupiah(todayRev), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    "Produksi Tertinggi",
                    if (topByQty != null && topByQty.totalQty > 0L) "${topByQty.worker.name} (${topByQty.totalQty})" else "-",
                    Modifier.weight(1f)
                )
                KpiCard(
                    "Pendapatan Tertinggi",
                    if (topByRev != null && topByRev.totalRevenue > 0L) topByRev.worker.name else "-",
                    Modifier.weight(1f)
                )
            }
        }

        // Quick Actions
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onNavigate(AppMenu.WEEKLY_BOOK) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tabel Mingguan")
                }
                OutlinedButton(
                    onClick = { onNavigate(AppMenu.INDIVIDUAL) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Input Perorangan")
                }
            }
        }

        // Daily Production Distribution Bars
        if (recap != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Grafik Produksi & Pendapatan Harian", fontWeight = FontWeight.Bold)
                        val maxRev = (recap.dailyTotalRevenue.maxOrNull() ?: 1L).coerceAtLeast(1L)
                        recap.dailyTotalRevenue.forEachIndexed { idx, rev ->
                            val qty = recap.dailyTotalQty[idx]
                            val ratio = (rev.toFloat() / maxRev.toFloat()).coerceIn(0f, 1f)
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${dayShort[idx]} (${recap.dates[idx].takeLast(5)})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text("$qty unit · ${LedgerCalculator.formatRupiah(rev)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = DarkGreenPrimary)
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = DarkGreenPrimary,
                                    trackColor = SoftGreenSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Workers Breakdown
        items(state.workerSummaries.filter { it.totalQty > 0L }) { ws ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(ws.worker.name, fontWeight = FontWeight.SemiBold)
                        Text("${LedgerCalculator.formatNumber(ws.totalQty)} unit · ${ws.rows.size} TYPE", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        LedgerCalculator.formatRupiah(ws.totalRevenue),
                        fontWeight = FontWeight.Bold,
                        color = DarkGreenPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (highlight) SoftGreenSurface else Color.White),
        modifier = modifier.border(1.dp, NeutralBorder)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (highlight) DarkGreenPrimary else Color(0xFF0F172A)
            )
        }
    }
}

@Composable
fun WeeklyBookScreen(
    state: MainUiState,
    vm: MainViewModel,
    onOpenPriceHistory: () -> Unit
) {
    val recap = state.weeklyRecap ?: return
    val scrollH = rememberScrollState()
    var editCell by remember { mutableStateOf<Triple<String, String, Long>?>(null) }
    var editPriceRow by remember { mutableStateOf<WeeklyTypeRowSummary?>(null) }
    var variedRow by remember { mutableStateOf<WeeklyTypeRowSummary?>(null) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var showPeriodsDialog by remember { mutableStateOf(false) }
    var showUnlockConfirm by remember { mutableStateOf(false) }
    var deleteTypeTarget by remember { mutableStateOf<ProductTypeEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Period Navigation Bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftWeek(-7L) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Minggu Sebelumnya")
                }
                OutlinedButton(onClick = { showPeriodsDialog = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(recap.period.label)
                }
                IconButton(onClick = { vm.shiftWeek(7L) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Minggu Berikutnya")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showAddTypeDialog = true },
                    enabled = !recap.period.isLocked
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah TYPE", tint = DarkGreenPrimary)
                }
                IconButton(onClick = {
                    if (recap.period.isLocked && state.settings.confirmBeforeUnlock) {
                        showUnlockConfirm = true
                    } else {
                        vm.togglePeriodLock(recap.period)
                    }
                }) {
                    Icon(
                        if (recap.period.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Kunci Periode",
                        tint = if (recap.period.isLocked) Color(0xFFB45309) else DarkGreenPrimary
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.typeSearch,
            onValueChange = vm::setTypeSearch,
            placeholder = { Text("Cari TYPE barang...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        val totalAllocatedQty = recap.rows.sumOf { it.allocatedQty }
        val totalUnallocatedQty = recap.rows.sumOf { it.unallocatedQty }
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (totalUnallocatedQty > 0L) Color(0xFFFFFBEB) else SoftGreenSurface
            ),
            modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Sinkronisasi: Rekap ${LedgerCalculator.formatNumber(recap.grandTotalQty)} unit · Perorangan ${LedgerCalculator.formatNumber(totalAllocatedQty)} unit",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkGreenPrimary
                )
                Text(
                    text = if (totalUnallocatedQty > 0L) {
                        "Selisih belum dialokasikan: ${LedgerCalculator.formatNumber(totalUnallocatedQty)} unit (${LedgerCalculator.formatRupiah(recap.unallocatedRevenue)})"
                    } else {
                        "Seluruh produksi teralokasi penuh ke pekerja (Selisih 0)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (totalUnallocatedQty > 0L) Color(0xFFB45309) else DarkGreenPrimary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Excel-Style Horizontal Scroll Table
        Box(Modifier.fillMaxWidth().horizontalScroll(scrollH).border(1.dp, NeutralBorder)) {
            Column {
                Row(Modifier.background(DarkGreenPrimary).padding(vertical = 10.dp)) {
                    TableCell("TYPE", 104.dp, Color.White, FontWeight.Bold, TextAlign.Start)
                    listOf("SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN").forEachIndexed { idx, d ->
                        TableCell("$d\n${recap.dates[idx].takeLast(5)}", 72.dp, Color.White, FontWeight.Bold, TextAlign.End)
                    }
                    TableCell("TOTAL", 76.dp, Color.White, FontWeight.Bold, TextAlign.End)
                    TableCell("HARGA SATUAN", 125.dp, Color.White, FontWeight.Bold, TextAlign.End)
                    TableCell("HARGA TOTAL", 130.dp, Color.White, FontWeight.Bold, TextAlign.End)
                    TableCell("AKSI", 64.dp, Color.White, FontWeight.Bold, TextAlign.Center)
                }

                recap.rows.forEach { row ->
                    Row(
                        Modifier.background(Color.White).border(0.5.dp, NeutralBorder),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(row.type.code, 104.dp, Color.Black, FontWeight.SemiBold, TextAlign.Start)
                        row.days.forEach { day ->
                            Box(
                                Modifier
                                    .width(72.dp)
                                    .clickable(enabled = !recap.period.isLocked) {
                                        editCell = Triple(day.date, row.type.id, day.totalQty)
                                    }
                                    .padding(10.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(LedgerCalculator.formatNumber(day.totalQty), textAlign = TextAlign.End)
                            }
                        }
                        TableCell(LedgerCalculator.formatNumber(row.totalQty), 76.dp, Color.Black, FontWeight.Bold, TextAlign.End)

                        Box(
                            Modifier
                                .width(125.dp)
                                .clickable {
                                    if (row.isVariedPrice) variedRow = row
                                    else if (!recap.period.isLocked) editPriceRow = row
                                }
                                .padding(10.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = if (row.isVariedPrice) "HARGA BERVARIASI" else LedgerCalculator.formatRupiah(row.displayPrice ?: 0L),
                                color = if (row.isVariedPrice) Color(0xFFB45309) else Color.Black,
                                fontWeight = if (row.isVariedPrice) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End
                            )
                        }

                        TableCell(LedgerCalculator.formatRupiah(row.totalRevenue), 130.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)

                        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                            IconButton(
                                enabled = !recap.period.isLocked,
                                onClick = {
                                    if (state.settings.confirmBeforeDelete) deleteTypeTarget = row.type
                                    else vm.deleteProductType(row.type)
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus TYPE", tint = Color(0xFFB91C1C))
                            }
                        }
                    }
                }

                // Bottom Total Rows
                Row(Modifier.background(SoftGreenSurface).border(0.5.dp, NeutralBorder)) {
                    TableCell("JUMLAH TOTAL", 104.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.Start)
                    recap.dailyTotalQty.forEach { q ->
                        TableCell(LedgerCalculator.formatNumber(q), 72.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                    }
                    TableCell(LedgerCalculator.formatNumber(recap.grandTotalQty), 76.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                    TableCell("", 125.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                    TableCell("", 130.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                    TableCell("", 64.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.Center)
                }

                Row(Modifier.background(SoftGreenSurface).border(0.5.dp, NeutralBorder)) {
                    TableCell("HARGA HARIAN", 104.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.Start)
                    recap.dailyTotalRevenue.forEach { rev ->
                        TableCell(LedgerCalculator.formatRupiah(rev), 72.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                    }
                    TableCell("", 76.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                    TableCell("TOTAL MINGGUAN", 125.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                    TableCell(LedgerCalculator.formatRupiah(recap.grandTotalRevenue), 130.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                    TableCell("", 64.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.Center)
                }
            }
        }
    }

    // Edit Qty Cell Dialog
    editCell?.let { (date, typeId, currentQty) ->
        var inputText by remember(date, typeId) { mutableStateOf(currentQty.toString()) }
        AlertDialog(
            onDismissRequest = { editCell = null },
            title = { Text("Ubah Produksi ($date)") },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it.filter(Char::isDigit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Jumlah Total Unit") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.setWeeklyRecapTotal(date, typeId, inputText.toLongOrNull() ?: 0L)
                    editCell = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editCell = null }) { Text("Batal") } }
        )
    }

    // Edit Price Dialog
    editPriceRow?.let { row ->
        var priceText by remember(row.type.id) { mutableStateOf((row.displayPrice ?: 0L).toString()) }
        AlertDialog(
            onDismissRequest = { editPriceRow = null },
            title = { Text("Ubah Harga ${row.type.code}") },
            text = {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Harga Satuan (Rp)") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val price = priceText.toLongOrNull() ?: 0L
                    vm.addPriceChange(
                        row.type.id,
                        price,
                        recap.period.startDate,
                        "Perubahan dari tabel mingguan",
                        true
                    )
                    editPriceRow = null
                }) { Text("Simpan Harga") }
            },
            dismissButton = { TextButton(onClick = { editPriceRow = null }) { Text("Batal") } }
        )
    }

    // Varied Price Breakdown Dialog
    variedRow?.let { row ->
        AlertDialog(
            onDismissRequest = { variedRow = null },
            title = { Text("Rincian Harga Bervariasi — ${row.type.code}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.priceBreakdown.forEach { b ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(LedgerCalculator.formatRupiah(b.price), fontWeight = FontWeight.SemiBold)
                            Text("${b.qty} unit = ${LedgerCalculator.formatRupiah(b.revenue)}")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    variedRow = null
                    onOpenPriceHistory()
                }) { Text("Riwayat Harga") }
            },
            dismissButton = { TextButton(onClick = { variedRow = null }) { Text("Tutup") } }
        )
    }

    // Add TYPE Dialog
    if (showAddTypeDialog) {
        var code by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTypeDialog = false },
            title = { Text("Tambah TYPE Barang Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Kode TYPE") })
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Barang") })
                    OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit) }, label = { Text("Harga Satuan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addProductType(code, name, price.toLongOrNull() ?: 0L)
                    showAddTypeDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddTypeDialog = false }) { Text("Batal") } }
        )
    }

    // Periods List Dialog
    if (showPeriodsDialog) {
        var customDate by remember { mutableStateOf(LocalDate.now().toString()) }
        AlertDialog(
            onDismissRequest = { showPeriodsDialog = false },
            title = { Text("Daftar Periode Mingguan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customDate,
                            onValueChange = { customDate = it },
                            label = { Text("Tanggal (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            runCatching { LocalDate.parse(customDate) }.onSuccess {
                                vm.selectOrCreateWeek(customDate)
                                showPeriodsDialog = false
                            }
                        }) { Text("Buka") }
                    }
                    HorizontalDivider()
                    state.periods.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.selectPeriod(p.id)
                                showPeriodsDialog = false
                            }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(p.label, fontWeight = if (p.id == recap.period.id) FontWeight.Bold else FontWeight.Normal)
                                Text(if (p.isLocked) "Terkunci" else "Aktif", style = MaterialTheme.typography.labelSmall)
                            }
                            if (!p.isLocked && state.periods.size > 1) {
                                IconButton(onClick = { vm.deleteWeekPeriod(p) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Periode")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPeriodsDialog = false }) { Text("Tutup") } }
        )
    }

    if (showUnlockConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlockConfirm = false },
            title = { Text("Buka Kunci Periode?") },
            text = { Text("Membuka kunci ${recap.period.label} mengizinkan perubahan data produksi pada minggu tersebut.") },
            confirmButton = {
                Button(onClick = {
                    vm.togglePeriodLock(recap.period)
                    showUnlockConfirm = false
                }) { Text("Ya, Buka Kunci") }
            },
            dismissButton = { TextButton(onClick = { showUnlockConfirm = false }) { Text("Batal") } }
        )
    }

    deleteTypeTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTypeTarget = null },
            title = { Text("Hapus TYPE ${t.code}?") },
            text = { Text("TYPE yang sudah memiliki transaksi produksi tidak akan dihapus.") },
            confirmButton = {
                Button(onClick = {
                    vm.deleteProductType(t)
                    deleteTypeTarget = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteTypeTarget = null }) { Text("Batal") } }
        )
    }
}

@Composable
internal fun TableCell(
    text: String,
    width: Dp,
    color: Color,
    weight: FontWeight,
    align: TextAlign
) {
    Text(
        text = text,
        color = color,
        fontWeight = weight,
        textAlign = align,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.width(width).padding(10.dp)
    )
}

@Composable
fun IndividualProductionScreen(
    state: MainUiState,
    vm: MainViewModel,
    onOpenWorkerDetail: (String) -> Unit = {}
) {
    val recap = state.weeklyRecap ?: return
    val activeWorkers = state.workers.filter { it.isActive }
    var selectedWorkerId by remember { mutableStateOf(activeWorkers.firstOrNull()?.id ?: "") }
    var selectedTypeId by remember { mutableStateOf(state.types.firstOrNull()?.id ?: "") }
    var selectedDate by remember { mutableStateOf(recap.dates.first()) }
    var qtyInput by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showAddWorkerDialog by remember { mutableStateOf(false) }
    var inlineEdit by remember { mutableStateOf<Triple<String, String, String>?>(null) } // workerId, typeId, date
    var inlineQty by remember { mutableStateOf("") }

    val visibleSummaries = state.workerSummaries.filter { ws ->
        val q = search.trim().lowercase()
        q.isEmpty() || ws.worker.name.lowercase().contains(q) || ws.rows.any { it.type.code.lowercase().contains(q) }
    }

    val totalAllocatedQty = recap.rows.sumOf { it.allocatedQty }
    val totalUnallocatedQty = recap.rows.sumOf { it.unallocatedQty }

    LazyColumn(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (totalUnallocatedQty > 0L) Color(0xFFFFFBEB) else SoftGreenSurface
                ),
                modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rekonsiliasi & Selisih", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                    Text(
                        "Total Rekap Utama: ${LedgerCalculator.formatNumber(recap.grandTotalQty)} unit (${LedgerCalculator.formatRupiah(recap.grandTotalRevenue)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Total Produksi Perorangan: ${LedgerCalculator.formatNumber(totalAllocatedQty)} unit (${LedgerCalculator.formatRupiah(recap.allocatedRevenue)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (totalUnallocatedQty > 0L) {
                            "Selisih Belum Dialokasikan: ${LedgerCalculator.formatNumber(totalUnallocatedQty)} unit (${LedgerCalculator.formatRupiah(recap.unallocatedRevenue)})"
                        } else {
                            "Selisih: 0 unit (Rekap Utama & Perorangan Seimbang)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (totalUnallocatedQty > 0L) Color(0xFFB45309) else DarkGreenPrimary
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Produksi Perorangan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                OutlinedButton(onClick = { showAddWorkerDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Orang Baru")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (activeWorkers.isEmpty()) {
                Text("Belum ada data orang aktif. Tambahkan nama orang terlebih dahulu.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeWorkers.forEach { w ->
                            FilterChip(
                                selected = (selectedWorkerId.ifEmpty { activeWorkers.first().id }) == w.id,
                                onClick = { selectedWorkerId = w.id },
                                label = { Text(w.name) }
                            )
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.types.forEach { t ->
                            FilterChip(
                                selected = (selectedTypeId.ifEmpty { state.types.firstOrNull()?.id ?: "" }) == t.id,
                                onClick = { selectedTypeId = t.id },
                                label = { Text(t.code) }
                            )
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recap.dates.forEach { d ->
                            FilterChip(
                                selected = (if (recap.dates.contains(selectedDate)) selectedDate else recap.dates.first()) == d,
                                onClick = { selectedDate = d },
                                label = { Text(d.takeLast(5)) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = it.filter(Char::isDigit) },
                            label = { Text("Jumlah Unit") },
                            enabled = !recap.period.isLocked,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = !recap.period.isLocked,
                            onClick = {
                                val wid = selectedWorkerId.ifEmpty { activeWorkers.first().id }
                                val tid = selectedTypeId.ifEmpty { state.types.firstOrNull()?.id ?: "" }
                                val targetDate = if (recap.dates.contains(selectedDate)) selectedDate else recap.dates.first()
                                if (tid.isNotEmpty()) {
                                    vm.setWorkerProduction(targetDate, tid, wid, qtyInput.toLongOrNull() ?: 0L)
                                    qtyInput = ""
                                }
                            }
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Cari nama pekerja atau TYPE...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Full Matrix Table: NAMA | TYPE | SEN..MIN | TOTAL PRODUKSI | HARGA SATUAN | TOTAL PENDAPATAN
        item {
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).border(1.dp, NeutralBorder)) {
                Column {
                    Row(Modifier.background(DarkGreenPrimary).padding(vertical = 10.dp)) {
                        TableCell("NAMA", 110.dp, Color.White, FontWeight.Bold, TextAlign.Start)
                        TableCell("TYPE", 84.dp, Color.White, FontWeight.Bold, TextAlign.Start)
                        listOf("SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN").forEachIndexed { idx, d ->
                            TableCell("$d\n${recap.dates[idx].takeLast(5)}", 64.dp, Color.White, FontWeight.Bold, TextAlign.End)
                        }
                        TableCell("TOTAL", 74.dp, Color.White, FontWeight.Bold, TextAlign.End)
                        TableCell("HARGA", 105.dp, Color.White, FontWeight.Bold, TextAlign.End)
                        TableCell("PENDAPATAN", 125.dp, Color.White, FontWeight.Bold, TextAlign.End)
                    }

                    visibleSummaries.forEach { ws ->
                        ws.rows.forEach { row ->
                            Row(Modifier.background(Color.White).border(0.5.dp, NeutralBorder)) {
                                Box(
                                    Modifier
                                        .width(110.dp)
                                        .clickable { onOpenWorkerDetail(ws.worker.id) }
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "${ws.worker.name} ↗",
                                        color = DarkGreenPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TableCell(row.type.code, 84.dp, Color.Black, FontWeight.Medium, TextAlign.Start)
                                row.dailyQty.forEachIndexed { idx, q ->
                                    val d = recap.dates[idx]
                                    Box(
                                        Modifier
                                            .width(64.dp)
                                            .clickable(enabled = !recap.period.isLocked) {
                                                inlineQty = q.toString()
                                                inlineEdit = Triple(ws.worker.id, row.type.id, d)
                                            }
                                            .padding(10.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(LedgerCalculator.formatNumber(q), textAlign = TextAlign.End)
                                    }
                                }
                                TableCell(LedgerCalculator.formatNumber(row.totalQty), 74.dp, Color.Black, FontWeight.Bold, TextAlign.End)
                                val priceStr = if (row.isVariedPrice) "BERVARIASI" else LedgerCalculator.formatRupiah(row.unitPrice ?: 0L)
                                TableCell(priceStr, 105.dp, Color.Black, FontWeight.Normal, TextAlign.End)
                                TableCell(LedgerCalculator.formatRupiah(row.totalRevenue), 125.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                            }
                        }
                        if (ws.rows.size > 1) {
                            Row(Modifier.background(SoftGreenSurface).border(0.5.dp, NeutralBorder)) {
                                TableCell("TOTAL ${ws.worker.name.uppercase()}", 194.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.Start)
                                Spacer(Modifier.width(448.dp))
                                TableCell(LedgerCalculator.formatNumber(ws.totalQty), 74.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                                TableCell("${ws.rows.size} TYPE", 105.dp, DarkGreenPrimary, FontWeight.Normal, TextAlign.End)
                                TableCell(LedgerCalculator.formatRupiah(ws.totalRevenue), 125.dp, DarkGreenPrimary, FontWeight.Bold, TextAlign.End)
                            }
                        }
                    }
                }
            }
        }

        if (visibleSummaries.isNotEmpty()) {
            item {
                Text("Buka Halaman Detail Pekerja", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleSummaries.forEach { ws ->
                        OutlinedButton(onClick = { onOpenWorkerDetail(ws.worker.id) }) {
                            Text("Detail ${ws.worker.name} (${LedgerCalculator.formatRupiah(ws.totalRevenue)})")
                        }
                    }
                }
            }
        }
    }

    inlineEdit?.let { (wid, tid, date) ->
        AlertDialog(
            onDismissRequest = { inlineEdit = null },
            title = { Text("Ubah Produksi ($date)") },
            text = {
                OutlinedTextField(
                    value = inlineQty,
                    onValueChange = { inlineQty = it.filter(Char::isDigit) },
                    label = { Text("Jumlah Unit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.setWorkerProduction(date, tid, wid, inlineQty.toLongOrNull() ?: 0L)
                    inlineEdit = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { inlineEdit = null }) { Text("Batal") } }
        )
    }

    if (showAddWorkerDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddWorkerDialog = false },
            title = { Text("Tambah Nama Orang") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nama Pekerja") }) },
            confirmButton = {
                Button(onClick = {
                    vm.addWorker(newName)
                    showAddWorkerDialog = false
                }) { Text("Tambah") }
            },
            dismissButton = { TextButton(onClick = { showAddWorkerDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun MasterDataAndToolsScreen(
    menu: AppMenu,
    state: MainUiState,
    vm: MainViewModel,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onOpenWorkerDetail: (String) -> Unit = {}
) {
    var nameInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var dateInput by remember { mutableStateOf(LocalDate.now().toString()) }
    var recalcUnlocked by remember { mutableStateOf(true) }
    var selectedTypeId by remember { mutableStateOf(state.types.firstOrNull()?.id ?: "") }

    var editTypeTarget by remember { mutableStateOf<ProductTypeEntity?>(null) }
    var deleteTypeTarget by remember { mutableStateOf<ProductTypeEntity?>(null) }
    var editWorkerTarget by remember { mutableStateOf<WorkerEntity?>(null) }
    var deleteWorkerTarget by remember { mutableStateOf<WorkerEntity?>(null) }
    var deletePriceTarget by remember { mutableStateOf<PriceHistoryEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(menu.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
        }
        when (menu) {
            AppMenu.TYPES -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Kode TYPE") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = priceInput, onValueChange = { priceInput = it.filter(Char::isDigit) }, label = { Text("Harga Rp") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        vm.addProductType(nameInput, nameInput, priceInput.toLongOrNull() ?: 0L)
                        nameInput = ""; priceInput = ""
                    }) { Text("Tambah TYPE") }
                }
                items(state.types) { t ->
                    val price = LedgerCalculator.getEffectivePriceEntry(state.priceHistory, t.id, LocalDate.now().toString())?.price ?: 0L
                    ListItem(
                        headlineContent = { Text(t.code, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("${t.name} · ${LedgerCalculator.formatRupiah(price)}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editTypeTarget = t }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit TYPE")
                                }
                                IconButton(onClick = {
                                    if (state.settings.confirmBeforeDelete) deleteTypeTarget = t
                                    else vm.deleteProductType(t)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus TYPE")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
            AppMenu.WORKERS -> {
                item {
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Nama Pekerja") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = noteInput, onValueChange = { noteInput = it }, label = { Text("Catatan / Bagian") }, modifier = Modifier.weight(1f))
                        Button(onClick = { vm.addWorker(nameInput, noteInput); nameInput = ""; noteInput = "" }) { Text("Tambah") }
                    }
                }
                items(state.workers) { w ->
                    ListItem(
                        headlineContent = { Text(if (w.isActive) w.name else "${w.name} (Diarsipkan)", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(w.note.ifBlank { "-" }) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onOpenWorkerDetail(w.id) }) { Text("Detail") }
                                if (!w.isActive) {
                                    TextButton(onClick = { vm.reactivateWorker(w) }) { Text("Aktifkan") }
                                }
                                IconButton(onClick = { editWorkerTarget = w }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Orang")
                                }
                                IconButton(onClick = {
                                    if (state.settings.confirmBeforeDelete) deleteWorkerTarget = w
                                    else vm.deleteOrArchiveWorker(w)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Orang")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
            AppMenu.PRICE_HISTORY -> {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.types.forEach { t ->
                            FilterChip(
                                selected = (selectedTypeId.ifEmpty { state.types.firstOrNull()?.id ?: "" }) == t.id,
                                onClick = { selectedTypeId = t.id },
                                label = { Text(t.code) }
                            )
                        }
                    }
                    OutlinedTextField(value = priceInput, onValueChange = { priceInput = it.filter(Char::isDigit) }, label = { Text("Harga Baru (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = dateInput, onValueChange = { dateInput = it }, label = { Text("Berlaku Mulai (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = noteInput, onValueChange = { noteInput = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = recalcUnlocked, onCheckedChange = { recalcUnlocked = it })
                        Text("Terapkan ke transaksi belum terkunci >= tanggal ini", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        val tid = selectedTypeId.ifEmpty { state.types.firstOrNull()?.id ?: "" }
                        if (tid.isNotEmpty()) {
                            vm.addPriceChange(tid, priceInput.toLongOrNull() ?: 0L, dateInput, noteInput, recalcUnlocked)
                            priceInput = ""; noteInput = ""
                        }
                    }) { Text("Simpan Harga Baru") }
                }
                items(state.priceHistory) { p ->
                    val code = state.types.firstOrNull { it.id == p.typeId }?.code ?: p.typeId
                    val isUsed = state.transactions.any { it.priceHistoryId == p.id }
                    ListItem(
                        headlineContent = { Text("$code — ${LedgerCalculator.formatRupiah(p.price)}") },
                        supportingContent = { Text("Mulai berlaku ${p.effectiveFrom} · ${p.note}") },
                        trailingContent = {
                            if (isUsed) {
                                Text("Dipakai", style = MaterialTheme.typography.labelSmall, color = DarkGreenPrimary)
                            } else {
                                IconButton(onClick = { deletePriceTarget = p }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Harga")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
            AppMenu.BACKUP -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onExportExcel, modifier = Modifier.fillMaxWidth()) { Text("Ekspor Excel (.xlsx 5 Sheet)") }
                        Button(onClick = onExportPdf, modifier = Modifier.fillMaxWidth()) { Text("Ekspor Laporan Mingguan (.pdf)") }
                        OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) { Text("Ekspor CSV Mingguan (.csv)") }
                        HorizontalDivider()
                        OutlinedButton(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Text("Backup Database (.json)") }
                        OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) { Text("Restore Database (.json)") }
                    }
                }
            }
            else -> Unit
        }
    }

    editTypeTarget?.let { t ->
        var code by remember(t.id) { mutableStateOf(t.code) }
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { editTypeTarget = null },
            title = { Text("Edit TYPE ${t.code}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Kode TYPE") })
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Barang") })
                }
            },
            confirmButton = { Button(onClick = { vm.updateProductType(t, code, name); editTypeTarget = null }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { editTypeTarget = null }) { Text("Batal") } }
        )
    }

    deleteTypeTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTypeTarget = null },
            title = { Text("Hapus TYPE ${t.code}?") },
            text = { Text("TYPE yang sudah memiliki transaksi produksi tidak akan dihapus.") },
            confirmButton = { Button(onClick = { vm.deleteProductType(t); deleteTypeTarget = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { deleteTypeTarget = null }) { Text("Batal") } }
        )
    }

    editWorkerTarget?.let { w ->
        var wName by remember(w.id) { mutableStateOf(w.name) }
        var wNote by remember(w.id) { mutableStateOf(w.note) }
        AlertDialog(
            onDismissRequest = { editWorkerTarget = null },
            title = { Text("Edit Pekerja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = wName, onValueChange = { wName = it }, label = { Text("Nama Pekerja") })
                    OutlinedTextField(value = wNote, onValueChange = { wNote = it }, label = { Text("Catatan") })
                }
            },
            confirmButton = { Button(onClick = { vm.updateWorker(w, wName, wNote); editWorkerTarget = null }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { editWorkerTarget = null }) { Text("Batal") } }
        )
    }

    deleteWorkerTarget?.let { w ->
        AlertDialog(
            onDismissRequest = { deleteWorkerTarget = null },
            title = { Text("Hapus / Arsipkan ${w.name}?") },
            text = { Text("Jika pekerja memiliki catatan pada periode terkunci, pekerja akan diarsipkan agar laporan historis tetap utuh.") },
            confirmButton = { Button(onClick = { vm.deleteOrArchiveWorker(w); deleteWorkerTarget = null }) { Text("Lanjutkan") } },
            dismissButton = { TextButton(onClick = { deleteWorkerTarget = null }) { Text("Batal") } }
        )
    }

    deletePriceTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deletePriceTarget = null },
            title = { Text("Hapus Riwayat Harga?") },
            text = { Text("Riwayat tarif ${LedgerCalculator.formatRupiah(p.price)} (${p.effectiveFrom}) akan dihapus.") },
            confirmButton = { Button(onClick = { vm.deletePriceHistory(p); deletePriceTarget = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { deletePriceTarget = null }) { Text("Batal") } }
        )
    }
}
