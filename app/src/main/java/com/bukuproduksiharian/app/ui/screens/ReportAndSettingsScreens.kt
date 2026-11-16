package com.bukuproduksiharian.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bukuproduksiharian.app.domain.LedgerCalculator
import com.bukuproduksiharian.app.domain.ReportRangeFilter
import com.bukuproduksiharian.app.ui.MainUiState
import com.bukuproduksiharian.app.ui.MainViewModel
import com.bukuproduksiharian.app.ui.theme.DarkGreenPrimary
import com.bukuproduksiharian.app.ui.theme.NeutralBorder
import com.bukuproduksiharian.app.ui.theme.SoftGreenSurface
import java.time.LocalDate

@Composable
fun IncomeReportScreen(
    state: MainUiState,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenWorkerDetail: (String) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(ReportRangeFilter.WEEKLY) }
    var anchorDate by remember(state.activePeriod?.id) {
        mutableStateOf(state.activePeriod?.startDate ?: LocalDate.now().toString())
    }
    var selectedWorkerId by remember { mutableStateOf("ALL") }

    val filteredTx = LedgerCalculator.filterTransactionsByRange(
        transactions = state.transactions,
        filter = selectedFilter,
        anchorDate = anchorDate,
        periodId = if (selectedFilter == ReportRangeFilter.WEEKLY) state.activePeriod?.id else null
    )

    val typeMap = state.types.associateBy { it.id }
    val workerMap = state.workers.associateBy { it.id }

    var totalQty = 0L
    var totalRev = 0L
    var allocRev = 0L
    var unallocRev = 0L

    data class TypeAgg(val code: String, var qty: Long, var rev: Long, val prices: MutableSet<Long>)
    data class WorkerAgg(val name: String, var qty: Long, var rev: Long, val types: MutableMap<String, TypeAgg>)

    val grouped = linkedMapOf<String, WorkerAgg>()
    for (tx in filteredTx) {
        val rev = tx.quantity * tx.unitPriceSnapshot
        totalQty += tx.quantity
        totalRev += rev
        if (tx.workerId == null) unallocRev += rev else allocRev += rev

        val key = tx.workerId ?: "__UNALLOCATED__"
        val wName = if (tx.workerId != null) {
            workerMap[tx.workerId]?.name ?: tx.workerNameSnapshot ?: "Pekerja Historis"
        } else {
            "Produksi Belum Dialokasikan"
        }
        val wAgg = grouped.getOrPut(key) { WorkerAgg(wName, 0L, 0L, linkedMapOf()) }
        wAgg.qty += tx.quantity
        wAgg.rev += rev

        val code = typeMap[tx.typeId]?.code ?: tx.typeId
        val tAgg = wAgg.types.getOrPut(tx.typeId) { TypeAgg(code, 0L, 0L, mutableSetOf()) }
        tAgg.qty += tx.quantity
        tAgg.rev += rev
        tAgg.prices.add(tx.unitPriceSnapshot)
    }

    val visibleWorkers = grouped.entries
        .filter { selectedWorkerId == "ALL" || it.key == selectedWorkerId }
        .sortedByDescending { it.value.rev }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Laporan Pendapatan Perorangan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DarkGreenPrimary
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportRangeFilter.entries.forEach { f ->
                    FilterChip(
                        selected = selectedFilter == f,
                        onClick = { selectedFilter = f },
                        label = { Text(f.label) }
                    )
                }
            }
            if (selectedFilter != ReportRangeFilter.WEEKLY) {
                OutlinedTextField(
                    value = anchorDate,
                    onValueChange = { anchorDate = it },
                    label = { Text("Tanggal Acuan (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedWorkerId == "ALL",
                    onClick = { selectedWorkerId = "ALL" },
                    label = { Text("Semua Orang") }
                )
                state.workers.forEach { w ->
                    FilterChip(
                        selected = selectedWorkerId == w.id,
                        onClick = { selectedWorkerId = w.id },
                        label = { Text(if (w.isActive) w.name else "${w.name} (Arsip)") }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExportExcel, modifier = Modifier.weight(1f)) { Text("Ekspor Excel") }
                OutlinedButton(onClick = onExportPdf, modifier = Modifier.weight(1f)) { Text("Ekspor PDF") }
            }
        }

        // Reconciliation Strip
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SoftGreenSurface),
                modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rekonsiliasi Pendapatan", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                    Text("Total Produksi: ${LedgerCalculator.formatNumber(totalQty)} Unit")
                    Text("Pendapatan Perorangan: ${LedgerCalculator.formatRupiah(allocRev)}")
                    Text("Belum Dialokasikan: ${LedgerCalculator.formatRupiah(unallocRev)}")
                    Text("Total Keseluruhan: ${LedgerCalculator.formatRupiah(totalRev)}", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                }
            }
        }

        items(visibleWorkers) { (key, info) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(info.name, fontWeight = FontWeight.Bold)
                        Text(LedgerCalculator.formatRupiah(info.rev), fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total ${LedgerCalculator.formatNumber(info.qty)} unit", style = MaterialTheme.typography.bodySmall)
                        if (key != "__UNALLOCATED__") {
                            TextButton(onClick = { onOpenWorkerDetail(key) }) {
                                Text("Detail Pekerja")
                            }
                        }
                    }
                    HorizontalDivider()
                    info.types.values.forEach { t ->
                        val priceLabel = if (t.prices.size > 1) "BERVARIASI" else LedgerCalculator.formatRupiah(t.prices.firstOrNull() ?: 0L)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${t.code} (${t.qty} × $priceLabel)", style = MaterialTheme.typography.bodySmall)
                            Text(LedgerCalculator.formatRupiah(t.rev), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyRecapScreen(
    state: MainUiState,
    vm: MainViewModel,
    onExportExcel: () -> Unit,
    onExportMonthlyPdf: () -> Unit
) {
    val monthly = state.monthlyRecap ?: return
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.shiftMonth(-1L) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Bulan Sebelumnya")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Rekap Bulanan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                    Text(LedgerCalculator.formatMonthYear(monthly.monthKey), style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { vm.shiftMonth(1L) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Bulan Berikutnya")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExportExcel, modifier = Modifier.weight(1f)) { Text("Ekspor XLSX") }
                OutlinedButton(onClick = onExportMonthlyPdf, modifier = Modifier.weight(1f)) { Text("Ekspor PDF") }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SoftGreenSurface),
                modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Total Produksi Bulanan: ${LedgerCalculator.formatNumber(monthly.totalQty)} Unit", fontWeight = FontWeight.Bold)
                    Text("Total Pendapatan Bulanan: ${LedgerCalculator.formatRupiah(monthly.totalRevenue)}", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                    Text("Teralokasi: ${LedgerCalculator.formatRupiah(monthly.allocatedRevenue)} · Umum: ${LedgerCalculator.formatRupiah(monthly.unallocatedRevenue)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Text("Rekap Per TYPE", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
        }
        items(monthly.byType) { item ->
            ListItem(
                headlineContent = { Text(item.type.code, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("${LedgerCalculator.formatNumber(item.totalQty)} unit · Rata-rata ${LedgerCalculator.formatRupiah(item.avgPrice)}") },
                trailingContent = { Text(LedgerCalculator.formatRupiah(item.totalRevenue), fontWeight = FontWeight.Bold, color = DarkGreenPrimary) }
            )
            HorizontalDivider()
        }

        item {
            Text("Rekap Per Pekerja", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
        }
        items(monthly.byWorker) { w ->
            ListItem(
                headlineContent = { Text(w.workerName, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("${LedgerCalculator.formatNumber(w.totalQty)} unit") },
                trailingContent = { Text(LedgerCalculator.formatRupiah(w.totalRevenue), fontWeight = FontWeight.Bold, color = DarkGreenPrimary) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun SettingsScreen(state: MainUiState, vm: MainViewModel) {
    var businessName by remember(state.settings.businessName) { mutableStateOf(state.settings.businessName) }
    var ownerName by remember(state.settings.ownerName) { mutableStateOf(state.settings.ownerName) }
    var confirmUnlock by remember(state.settings.confirmBeforeUnlock) { mutableStateOf(state.settings.confirmBeforeUnlock) }
    var confirmDelete by remember(state.settings.confirmBeforeDelete) { mutableStateOf(state.settings.confirmBeforeDelete) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Pengaturan Aplikasi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
        }
        item {
            OutlinedTextField(
                value = businessName,
                onValueChange = { businessName = it },
                label = { Text("Nama Usaha / Buku Produksi") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text("Nama Admin / Pemilik") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = confirmUnlock, onCheckedChange = { confirmUnlock = it })
                Text("Konfirmasi sebelum membuka kunci periode mingguan")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = confirmDelete, onCheckedChange = { confirmDelete = it })
                Text("Konfirmasi sebelum menghapus TYPE atau nama orang")
            }
        }
        item {
            Button(
                onClick = { vm.saveSettings(businessName, ownerName, confirmUnlock, confirmDelete) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan Pengaturan")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = vm::loadBenchmarkData,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Muat Data Uji Excel (Bagian 16)")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = vm::loadIndividualTestScenario,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Muat Data Uji Perorangan (Andi, Budi, Candra)")
            }
        }
    }
}

@Composable
fun WorkerDetailScreen(
    state: MainUiState,
    workerId: String,
    onSelectWorker: (String) -> Unit,
    onBack: () -> Unit
) {
    val activePeriod = state.activePeriod ?: return
    val detail = LedgerCalculator.computeWorkerDetailSummary(
        workerId = workerId,
        period = activePeriod,
        workers = state.workers,
        types = state.types,
        priceHistory = state.priceHistory,
        periods = state.periods,
        transactions = state.transactions
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kembali")
                }
                if (state.workers.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.workers.forEach { w ->
                            FilterChip(
                                selected = w.id == workerId,
                                onClick = { onSelectWorker(w.id) },
                                label = { Text(w.name) }
                            )
                        }
                    }
                }
            }
        }

        if (detail == null) {
            item {
                Text("Data pekerja tidak ditemukan.")
            }
            return@LazyColumn
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SoftGreenSurface),
                modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(detail.worker.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                    Text("Periode: ${detail.period.label} · ${if (detail.worker.isActive) "Aktif" else "Diarsipkan"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Total Barang Mingguan: ${LedgerCalculator.formatNumber(detail.weeklyTotalQty)} Unit", fontWeight = FontWeight.SemiBold)
                    Text("Total Pendapatan Mingguan: ${LedgerCalculator.formatRupiah(detail.weeklyTotalRevenue)}", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                    Text("Total Sepanjang Waktu: ${LedgerCalculator.formatNumber(detail.allTimeTotalQty)} Unit · ${LedgerCalculator.formatRupiah(detail.allTimeTotalRevenue)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Text("Produksi & Pendapatan per TYPE", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
        }
        if (detail.byType.isEmpty()) {
            item { Text("Belum ada produksi pada minggu ini.", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(detail.byType) { row ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.type.code, fontWeight = FontWeight.Bold)
                            Text(LedgerCalculator.formatRupiah(row.totalRevenue), fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                        }
                        val priceDesc = if (row.isVariedPrice) {
                            "HARGA BERVARIASI (" + row.priceBreakdown.joinToString(" + ") {
                                "${it.qty}×${LedgerCalculator.formatRupiah(it.price)}"
                            } + ")"
                        } else {
                            LedgerCalculator.formatRupiah(row.unitPrice ?: 0L)
                        }
                        Text("${LedgerCalculator.formatNumber(row.totalQty)} unit · Harga satuan: $priceDesc", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text("Produksi Setiap Hari (Senin – Minggu)", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
        }
        items(detail.byDay) { day ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${day.dayLabel} (${LedgerCalculator.formatFullDate(day.date)})", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (day.totalQty > 0L) "${LedgerCalculator.formatNumber(day.totalQty)} unit · ${LedgerCalculator.formatRupiah(day.totalRevenue)}" else "-",
                            fontWeight = FontWeight.Bold,
                            color = DarkGreenPrimary
                        )
                    }
                    if (day.items.isNotEmpty()) {
                        day.items.forEach { it ->
                            Text(
                                "· ${it.type.code}: ${LedgerCalculator.formatNumber(it.quantity)} × ${LedgerCalculator.formatRupiah(it.unitPrice)} = ${LedgerCalculator.formatRupiah(it.revenue)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Riwayat Pendapatan Bulanan", fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
        }
        if (detail.monthlyHistory.isEmpty()) {
            item { Text("Belum ada riwayat pendapatan bulanan.", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(detail.monthlyHistory) { m ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().border(1.dp, NeutralBorder)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(LedgerCalculator.formatMonthYear(m.monthKey), fontWeight = FontWeight.Bold)
                            Text(LedgerCalculator.formatRupiah(m.totalRevenue), fontWeight = FontWeight.Bold, color = DarkGreenPrimary)
                        }
                        Text("Total ${LedgerCalculator.formatNumber(m.totalQty)} unit", style = MaterialTheme.typography.bodySmall)
                        m.byType.forEach { t ->
                            Text(
                                "· ${t.type.code}: ${LedgerCalculator.formatNumber(t.totalQty)} unit = ${LedgerCalculator.formatRupiah(t.totalRevenue)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
