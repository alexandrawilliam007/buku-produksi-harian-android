package com.bukuproduksiharian.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.bukuproduksiharian.app.data.db.*
import com.bukuproduksiharian.app.data.export.ReportExporter
import com.bukuproduksiharian.app.domain.LedgerCalculator
import com.bukuproduksiharian.app.domain.MonthlyRecapSummary
import com.bukuproduksiharian.app.domain.WeeklyRecapSummary
import com.bukuproduksiharian.app.domain.WorkerTotalSummary
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class MainUiState(
    val types: List<ProductTypeEntity> = emptyList(),
    val workers: List<WorkerEntity> = emptyList(),
    val priceHistory: List<PriceHistoryEntity> = emptyList(),
    val periods: List<WeekPeriodEntity> = emptyList(),
    val transactions: List<ProductionTransactionEntity> = emptyList(),
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val activePeriod: WeekPeriodEntity? = null,
    val weeklyRecap: WeeklyRecapSummary? = null,
    val workerSummaries: List<WorkerTotalSummary> = emptyList(),
    val selectedMonthKey: String = YearMonth.now().toString(),
    val monthlyRecap: MonthlyRecapSummary? = null,
    val typeSearch: String = "",
    val statusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val searchQuery = MutableStateFlow("")
    private val statusMsg = MutableStateFlow<String?>(null)
    private val selectedMonth = MutableStateFlow(YearMonth.now().toString())

    init {
        viewModelScope.launch {
            db.ensureSeeded()
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        db.productTypeDao().observeAll(),
        db.workerDao().observeAll(),
        db.priceHistoryDao().observeAll(),
        db.weekPeriodDao().observeAll(),
        db.transactionDao().observeAll(),
        db.settingsDao().observe(),
        searchQuery,
        statusMsg,
        selectedMonth
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val types = arr[0] as List<ProductTypeEntity>
        @Suppress("UNCHECKED_CAST")
        val workers = arr[1] as List<WorkerEntity>
        @Suppress("UNCHECKED_CAST")
        val prices = arr[2] as List<PriceHistoryEntity>
        @Suppress("UNCHECKED_CAST")
        val periods = arr[3] as List<WeekPeriodEntity>
        @Suppress("UNCHECKED_CAST")
        val tx = arr[4] as List<ProductionTransactionEntity>
        val settings = (arr[5] as AppSettingsEntity?) ?: AppSettingsEntity()
        val query = arr[6] as String
        val msg = arr[7] as String?
        val monthKey = arr[8] as String

        val activePeriod = periods.firstOrNull { it.id == settings.activePeriodId } ?: periods.firstOrNull()
        val recap = activePeriod?.let {
            LedgerCalculator.computeWeeklyRecap(it, types, prices, tx, query)
        }
        val workerSummaries = activePeriod?.let {
            LedgerCalculator.computeWorkerSummaries(it, workers, types, prices, tx)
        } ?: emptyList()
        val monthly = LedgerCalculator.computeMonthlyRecap(monthKey, types, workers, periods, tx)

        MainUiState(
            types = types,
            workers = workers,
            priceHistory = prices,
            periods = periods,
            transactions = tx,
            settings = settings,
            activePeriod = activePeriod,
            weeklyRecap = recap,
            workerSummaries = workerSummaries,
            selectedMonthKey = monthKey,
            monthlyRecap = monthly,
            typeSearch = query,
            statusMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    fun clearMessage() { statusMsg.value = null }
    fun setTypeSearch(q: String) { searchQuery.value = q }
    fun setMonthKey(monthKey: String) { selectedMonth.value = monthKey }

    fun shiftMonth(deltaMonths: Long) {
        val next = runCatching {
            YearMonth.parse(selectedMonth.value).plusMonths(deltaMonths).toString()
        }.getOrDefault(selectedMonth.value)
        selectedMonth.value = next
    }

    private suspend fun refreshSnapshot(periodId: String) {
        val tx = db.transactionDao().getAll().filter { it.periodId == periodId && it.quantity > 0L }
        val totalQty = tx.sumOf { it.quantity }
        val totalRev = tx.sumOf { it.quantity * it.unitPriceSnapshot }
        db.snapshotDao().upsert(
            WeeklyBookSnapshotEntity(
                periodId = periodId,
                totalQuantity = totalQty,
                totalRevenue = totalRev,
                updatedAt = Instant.now().toString()
            )
        )
    }

    fun setWorkerProduction(date: String, typeId: String, workerId: String, qty: Long) {
        viewModelScope.launch {
            val period = uiState.value.activePeriod ?: return@launch
            if (period.isLocked) {
                statusMsg.value = "Minggu ini terkunci. Buka kunci terlebih dahulu."
                return@launch
            }
            if (qty < 0L) return@launch

            val workerName = uiState.value.workers.firstOrNull { it.id == workerId }?.name
            db.withTransaction {
                val existing = db.transactionDao().findCell(period.id, date, typeId, workerId)
                if (qty == 0L) {
                    if (existing != null) db.transactionDao().delete(existing)
                } else {
                    val priceEntry = LedgerCalculator.getEffectivePriceEntry(uiState.value.priceHistory, typeId, date)
                    val entity = ProductionTransactionEntity(
                        id = existing?.id ?: "tx_${UUID.randomUUID().toString().take(8)}",
                        periodId = period.id,
                        date = date,
                        typeId = typeId,
                        workerId = workerId,
                        workerKey = workerId,
                        workerNameSnapshot = workerName ?: existing?.workerNameSnapshot,
                        quantity = qty,
                        unitPriceSnapshot = priceEntry?.price ?: 0L,
                        priceHistoryId = priceEntry?.id,
                        updatedAt = Instant.now().toString()
                    )
                    db.transactionDao().upsert(entity)
                }
                refreshSnapshot(period.id)
            }
        }
    }

    fun setWeeklyRecapTotal(date: String, typeId: String, targetTotalQty: Long) {
        viewModelScope.launch {
            val period = uiState.value.activePeriod ?: return@launch
            if (period.isLocked) {
                statusMsg.value = "Minggu ini terkunci."
                return@launch
            }
            if (targetTotalQty < 0L) return@launch
            val allocated = uiState.value.transactions
                .filter { it.periodId == period.id && it.date == date && it.typeId == typeId && it.workerId != null }
                .sumOf { it.quantity }
            if (targetTotalQty < allocated) {
                statusMsg.value = "Total tidak boleh kurang dari produksi perorangan ($allocated unit)."
                return@launch
            }
            val unallocQty = targetTotalQty - allocated
            db.withTransaction {
                val existing = db.transactionDao().findCell(period.id, date, typeId, "__UNALLOCATED__")
                if (unallocQty == 0L) {
                    if (existing != null) db.transactionDao().delete(existing)
                } else {
                    val priceEntry = LedgerCalculator.getEffectivePriceEntry(uiState.value.priceHistory, typeId, date)
                    db.transactionDao().upsert(
                        ProductionTransactionEntity(
                            id = existing?.id ?: "tx_${UUID.randomUUID().toString().take(8)}",
                            periodId = period.id,
                            date = date,
                            typeId = typeId,
                            workerId = null,
                            workerKey = "__UNALLOCATED__",
                            workerNameSnapshot = null,
                            quantity = unallocQty,
                            unitPriceSnapshot = priceEntry?.price ?: 0L,
                            priceHistoryId = priceEntry?.id,
                            updatedAt = Instant.now().toString()
                        )
                    )
                }
                refreshSnapshot(period.id)
            }
        }
    }

    fun addProductType(code: String, name: String, initialPrice: Long) {
        viewModelScope.launch {
            if (code.isBlank() || initialPrice < 0L) return@launch
            val now = Instant.now().toString()
            val id = "type_${UUID.randomUUID().toString().take(8)}"
            db.withTransaction {
                db.productTypeDao().upsert(
                    ProductTypeEntity(id, code.trim(), name.ifBlank { code }.trim(), "Unit", uiState.value.types.size + 1, now)
                )
                db.priceHistoryDao().upsert(
                    PriceHistoryEntity("prc_${UUID.randomUUID().toString().take(8)}", id, initialPrice, "2025-01-01", "Harga awal", now)
                )
            }
            statusMsg.value = "TYPE ${code.trim()} ditambahkan."
        }
    }

    fun updateProductType(type: ProductTypeEntity, newCode: String, newName: String) {
        viewModelScope.launch {
            if (newCode.isBlank()) return@launch
            db.productTypeDao().upsert(
                type.copy(code = newCode.trim(), name = newName.ifBlank { newCode }.trim())
            )
            statusMsg.value = "Data TYPE diperbarui."
        }
    }

    fun deleteProductType(type: ProductTypeEntity) {
        viewModelScope.launch {
            val used = db.transactionDao().getAll().any { it.typeId == type.id && it.quantity > 0L }
            if (used) {
                statusMsg.value = "TYPE ${type.code} memiliki transaksi produksi dan tidak boleh dihapus."
                return@launch
            }
            db.withTransaction {
                val prices = db.priceHistoryDao().getAll().filter { it.typeId == type.id }
                prices.forEach { db.priceHistoryDao().delete(it) }
                db.productTypeDao().delete(type)
            }
            statusMsg.value = "TYPE ${type.code} dihapus."
        }
    }

    fun addWorker(name: String, note: String = "") {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            db.workerDao().upsert(
                WorkerEntity("w_${UUID.randomUUID().toString().take(8)}", name.trim(), note.trim(), true, Instant.now().toString())
            )
            statusMsg.value = "Pekerja ${name.trim()} ditambahkan."
        }
    }

    fun updateWorker(worker: WorkerEntity, newName: String, newNote: String) {
        viewModelScope.launch {
            if (newName.isBlank()) return@launch
            val trimmedName = newName.trim()
            db.withTransaction {
                db.workerDao().upsert(worker.copy(name = trimmedName, note = newNote.trim()))
                val affected = db.transactionDao().getAll().filter { it.workerId == worker.id }
                if (affected.isNotEmpty()) {
                    db.transactionDao().upsertAll(affected.map { it.copy(workerNameSnapshot = trimmedName) })
                }
            }
            statusMsg.value = "Data pekerja diperbarui."
        }
    }

    fun deleteOrArchiveWorker(worker: WorkerEntity) {
        viewModelScope.launch {
            val lockedPeriodIds = db.weekPeriodDao().getAll().filter { it.isLocked }.map { it.id }.toSet()
            val workerTx = db.transactionDao().getAll().filter { it.workerId == worker.id && it.quantity > 0L }
            val hasLocked = workerTx.any { it.periodId in lockedPeriodIds }

            db.withTransaction {
                if (hasLocked) {
                    db.workerDao().upsert(worker.copy(isActive = false))
                    db.transactionDao().deleteUnlockedByWorker(worker.id)
                    val lockedTx = workerTx.filter { it.periodId in lockedPeriodIds }
                    if (lockedTx.isNotEmpty()) {
                        db.transactionDao().upsertAll(lockedTx.map { it.copy(workerNameSnapshot = worker.name) })
                    }
                } else {
                    db.transactionDao().deleteByWorker(worker.id)
                    db.workerDao().delete(worker)
                }
                workerTx.map { it.periodId }.distinct().forEach { refreshSnapshot(it) }
            }
            statusMsg.value = if (hasLocked) {
                "${worker.name} diarsipkan untuk menjaga laporan periode terkunci."
            } else {
                "${worker.name} dihapus."
            }
        }
    }

    fun reactivateWorker(worker: WorkerEntity) {
        viewModelScope.launch {
            db.workerDao().upsert(worker.copy(isActive = true))
            statusMsg.value = "${worker.name} diaktifkan kembali."
        }
    }

    fun addPriceChange(
        typeId: String,
        newPrice: Long,
        effectiveFrom: String,
        note: String,
        recalculateUnlockedOnOrAfter: Boolean = true
    ) {
        viewModelScope.launch {
            if (newPrice < 0L || effectiveFrom.isBlank()) return@launch
            val entry = PriceHistoryEntity(
                id = "prc_${UUID.randomUUID().toString().take(8)}",
                typeId = typeId,
                price = newPrice,
                effectiveFrom = effectiveFrom.trim(),
                note = note.ifBlank { "Perubahan harga satuan" },
                createdAt = Instant.now().toString()
            )
            db.withTransaction {
                db.priceHistoryDao().upsert(entry)
                if (recalculateUnlockedOnOrAfter) {
                    val allPrices = db.priceHistoryDao().getAll()
                    val lockedPeriods = db.weekPeriodDao().getAll().filter { it.isLocked }.map { it.id }.toSet()
                    val allTx = db.transactionDao().getAll()
                    val updatedTx = allTx.filter {
                        it.typeId == typeId && it.date >= entry.effectiveFrom && it.periodId !in lockedPeriods
                    }.map { tx ->
                        val eff = LedgerCalculator.getEffectivePriceEntry(allPrices, tx.typeId, tx.date)
                        tx.copy(
                            unitPriceSnapshot = eff?.price ?: newPrice,
                            priceHistoryId = eff?.id ?: entry.id
                        )
                    }
                    if (updatedTx.isNotEmpty()) {
                        db.transactionDao().upsertAll(updatedTx)
                        updatedTx.map { it.periodId }.distinct().forEach { refreshSnapshot(it) }
                    }
                }
            }
            statusMsg.value = "Riwayat harga disimpan."
        }
    }

    fun deletePriceHistory(entry: PriceHistoryEntity) {
        viewModelScope.launch {
            val used = db.transactionDao().getAll().any { it.priceHistoryId == entry.id }
            if (used) {
                statusMsg.value = "Riwayat harga yang sudah dipakai transaksi tidak boleh dihapus."
                return@launch
            }
            db.priceHistoryDao().delete(entry)
            statusMsg.value = "Riwayat harga dihapus."
        }
    }

    fun selectPeriod(periodId: String) {
        viewModelScope.launch {
            db.settingsDao().upsert(uiState.value.settings.copy(activePeriodId = periodId))
        }
    }

    fun shiftWeek(deltaDays: Long) {
        val current = uiState.value.activePeriod ?: return
        val targetDate = LocalDate.parse(current.startDate).plusDays(deltaDays)
        selectOrCreateWeek(targetDate.toString())
    }

    fun togglePeriodLock(period: WeekPeriodEntity) {
        viewModelScope.launch {
            db.weekPeriodDao().update(
                period.copy(
                    isLocked = !period.isLocked,
                    lockedAt = if (!period.isLocked) Instant.now().toString() else null
                )
            )
            statusMsg.value = if (!period.isLocked) "Periode ${period.label} dikunci." else "Kunci periode ${period.label} dibuka."
        }
    }

    fun selectOrCreateWeek(dateIso: String) {
        viewModelScope.launch {
            val monday = LedgerCalculator.mondayOf(LocalDate.parse(dateIso))
            val existing = uiState.value.periods.firstOrNull { it.startDate == monday.toString() }
            val targetId = if (existing != null) {
                existing.id
            } else {
                val sunday = monday.plusDays(6)
                val newPeriod = WeekPeriodEntity(
                    id = "prd_${UUID.randomUUID().toString().take(8)}",
                    label = "${LedgerCalculator.formatShortDate(monday)} - ${LedgerCalculator.formatShortDate(sunday)}",
                    startDate = monday.toString(),
                    endDate = sunday.toString(),
                    isLocked = false,
                    lockedAt = null,
                    createdAt = Instant.now().toString()
                )
                db.withTransaction {
                    db.weekPeriodDao().upsert(newPeriod)
                    db.snapshotDao().upsert(WeeklyBookSnapshotEntity(newPeriod.id, 0L, 0L, Instant.now().toString()))
                }
                newPeriod.id
            }
            db.settingsDao().upsert(uiState.value.settings.copy(activePeriodId = targetId))
        }
    }

    fun deleteWeekPeriod(period: WeekPeriodEntity) {
        viewModelScope.launch {
            if (uiState.value.periods.size <= 1) {
                statusMsg.value = "Minimal harus tersisa satu periode pembukuan."
                return@launch
            }
            if (period.isLocked) {
                statusMsg.value = "Periode terkunci tidak boleh dihapus."
                return@launch
            }
            db.withTransaction {
                db.transactionDao().deleteByPeriod(period.id)
                db.snapshotDao().deleteByPeriod(period.id)
                db.weekPeriodDao().delete(period)
                val remaining = db.weekPeriodDao().getAll().firstOrNull()
                if (remaining != null && uiState.value.settings.activePeriodId == period.id) {
                    db.settingsDao().upsert(uiState.value.settings.copy(activePeriodId = remaining.id))
                }
            }
            statusMsg.value = "Periode ${period.label} dihapus."
        }
    }

    fun saveSettings(businessName: String, ownerName: String, confirmUnlock: Boolean, confirmDelete: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.settings
            db.settingsDao().upsert(
                current.copy(
                    businessName = businessName.ifBlank { "Buku Produksi Harian" }.trim(),
                    ownerName = ownerName.ifBlank { "Admin Produksi" }.trim(),
                    confirmBeforeUnlock = confirmUnlock,
                    confirmBeforeDelete = confirmDelete
                )
            )
            statusMsg.value = "Pengaturan disimpan."
        }
    }

    fun loadBenchmarkData() {
        viewModelScope.launch {
            val monday = "2026-11-09"
            val sunday = "2026-11-15"
            val now = Instant.now().toString()
            db.withTransaction {
                val existingTypes = db.productTypeDao().getAll()
                val typeDefs = listOf(
                    Triple("type_400ld", "400 LD", 33250L),
                    Triple("type_600hd", "600 HD", 68950L),
                    Triple("type_800hd", "800 HD", 96950L)
                )
                val resolvedIds = mutableMapOf<String, String>()
                typeDefs.forEachIndexed { idx, (defId, code, price) ->
                    val found = existingTypes.firstOrNull { it.id == defId || it.code.equals(code, ignoreCase = true) }
                    if (found != null) {
                        resolvedIds[defId] = found.id
                    } else {
                        resolvedIds[defId] = defId
                        db.productTypeDao().upsert(ProductTypeEntity(defId, code, "Plastik $code", "Unit", existingTypes.size + idx + 1, now))
                        db.priceHistoryDao().upsert(PriceHistoryEntity("prc_${defId}_bench", defId, price, "2025-01-01", "Harga awal $code", now))
                    }
                }

                val existingPeriod = db.weekPeriodDao().getAll().firstOrNull { it.startDate == monday }
                if (existingPeriod?.isLocked == true) {
                    statusMsg.value = "Periode uji 9 Nov - 15 Nov sedang terkunci."
                    return@withTransaction
                }
                val periodId = existingPeriod?.id ?: "prd_bench_20261109"
                if (existingPeriod == null) {
                    db.weekPeriodDao().upsert(
                        WeekPeriodEntity(periodId, "9 Nov - 15 Nov", monday, sunday, false, null, now)
                    )
                }

                val prices = db.priceHistoryDao().getAll()
                val monDate = LocalDate.parse(monday)
                val qtyByDefId = mapOf("type_400ld" to 25L, "type_600hd" to 18L, "type_800hd" to 10L)
                for (offset in 0L..4L) {
                    val d = monDate.plusDays(offset).toString()
                    for ((defId, qty) in qtyByDefId) {
                        val tid = resolvedIds[defId] ?: continue
                        val existingCell = db.transactionDao().findCell(periodId, d, tid, "__UNALLOCATED__")
                        val eff = LedgerCalculator.getEffectivePriceEntry(prices, tid, d)
                        db.transactionDao().upsert(
                            ProductionTransactionEntity(
                                id = existingCell?.id ?: "tx_${UUID.randomUUID().toString().take(8)}",
                                periodId = periodId,
                                date = d,
                                typeId = tid,
                                workerId = null,
                                workerKey = "__UNALLOCATED__",
                                workerNameSnapshot = null,
                                quantity = qty,
                                unitPriceSnapshot = eff?.price ?: 0L,
                                priceHistoryId = eff?.id,
                                updatedAt = now
                            )
                        )
                    }
                }
                refreshSnapshot(periodId)
                db.settingsDao().upsert(uiState.value.settings.copy(activePeriodId = periodId))
                selectedMonth.value = "2026-11"
                statusMsg.value = "Data uji Excel Bagian 16 dimuat (265 unit · Rp15.209.250)."
            }
        }
    }

    fun loadIndividualTestScenario() {
        viewModelScope.launch {
            val monday = "2026-11-16"
            val sunday = "2026-11-22"
            val now = Instant.now().toString()
            db.withTransaction {
                val existingTypes = db.productTypeDao().getAll()
                val typeDefs = listOf(
                    Triple("type_400ld", "400 LD", 33250L),
                    Triple("type_600hd", "600 HD", 68950L),
                    Triple("type_800hd", "800 HD", 96950L)
                )
                val resolvedTypeIds = mutableMapOf<String, String>()
                typeDefs.forEachIndexed { idx, (defId, code, price) ->
                    val found = existingTypes.firstOrNull { it.id == defId || it.code.equals(code, ignoreCase = true) }
                    if (found != null) {
                        resolvedTypeIds[defId] = found.id
                    } else {
                        resolvedTypeIds[defId] = defId
                        db.productTypeDao().upsert(ProductTypeEntity(defId, code, "Plastik $code", "Unit", existingTypes.size + idx + 1, now))
                        db.priceHistoryDao().upsert(PriceHistoryEntity("prc_${defId}_ind", defId, price, "2025-01-01", "Harga awal $code", now))
                    }
                }

                val existingWorkers = db.workerDao().getAll()
                val workerDefs = listOf(
                    Triple("w_andi", "Andi", "Operator Line A"),
                    Triple("w_budi", "Budi", "Operator Line B"),
                    Triple("w_candra", "Candra", "Operator Line C")
                )
                val resolvedWorkerIds = mutableMapOf<String, String>()
                workerDefs.forEach { (defId, name, note) ->
                    val found = existingWorkers.firstOrNull { it.id == defId || it.name.equals(name, ignoreCase = true) }
                    if (found != null) {
                        resolvedWorkerIds[defId] = found.id
                        if (!found.isActive) {
                            db.workerDao().upsert(found.copy(isActive = true))
                        }
                    } else {
                        resolvedWorkerIds[defId] = defId
                        db.workerDao().upsert(WorkerEntity(defId, name, note, true, now))
                    }
                }

                val existingPeriod = db.weekPeriodDao().getAll().firstOrNull { it.startDate == monday }
                if (existingPeriod?.isLocked == true) {
                    statusMsg.value = "Periode uji 16 Nov - 22 Nov sedang terkunci."
                    return@withTransaction
                }
                val periodId = existingPeriod?.id ?: "prd_ind_20261116"
                if (existingPeriod == null) {
                    db.weekPeriodDao().upsert(
                        WeekPeriodEntity(periodId, "16 Nov - 22 Nov", monday, sunday, false, null, now)
                    )
                }

                val prices = db.priceHistoryDao().getAll()
                val monDate = LocalDate.parse(monday)
                val entries = listOf(
                    listOf("w_andi", "type_400ld", 0L, 10L),
                    listOf("w_andi", "type_400ld", 1L, 12L),
                    listOf("w_andi", "type_400ld", 2L, 8L),
                    listOf("w_andi", "type_600hd", 0L, 6L),
                    listOf("w_andi", "type_600hd", 3L, 4L),
                    listOf("w_budi", "type_400ld", 0L, 15L),
                    listOf("w_budi", "type_400ld", 1L, 15L),
                    listOf("w_budi", "type_400ld", 2L, 15L),
                    listOf("w_budi", "type_600hd", 1L, 10L),
                    listOf("w_budi", "type_600hd", 3L, 10L),
                    listOf("w_candra", "type_400ld", 0L, 10L),
                    listOf("w_candra", "type_400ld", 2L, 15L),
                    listOf("w_candra", "type_800hd", 1L, 8L),
                    listOf("w_candra", "type_800hd", 4L, 7L)
                )

                for (entry in entries) {
                    val wKey = entry[0] as String
                    val tKey = entry[1] as String
                    val offset = entry[2] as Long
                    val qty = entry[3] as Long
                    val wid = resolvedWorkerIds[wKey] ?: continue
                    val tid = resolvedTypeIds[tKey] ?: continue
                    val date = monDate.plusDays(offset).toString()
                    val existingCell = db.transactionDao().findCell(periodId, date, tid, wid)
                    val eff = LedgerCalculator.getEffectivePriceEntry(prices, tid, date)
                    val workerName = workerDefs.first { it.first == wKey }.second
                    db.transactionDao().upsert(
                        ProductionTransactionEntity(
                            id = existingCell?.id ?: "tx_${UUID.randomUUID().toString().take(8)}",
                            periodId = periodId,
                            date = date,
                            typeId = tid,
                            workerId = wid,
                            workerKey = wid,
                            workerNameSnapshot = workerName,
                            quantity = qty,
                            unitPriceSnapshot = eff?.price ?: 0L,
                            priceHistoryId = eff?.id,
                            updatedAt = now
                        )
                    )
                }
                refreshSnapshot(periodId)
                db.settingsDao().upsert(uiState.value.settings.copy(activePeriodId = periodId))
                selectedMonth.value = "2026-11"
                statusMsg.value = "Data uji Perorangan (Andi, Budi, Candra = 145 unit · Rp6.847.750) dimuat."
            }
        }
    }

    fun exportExcel(uri: Uri) {
        viewModelScope.launch {
            val state = uiState.value
            val recap = state.weeklyRecap ?: return@launch
            val monthly = state.monthlyRecap ?: return@launch
            ReportExporter.exportExcelXlsx(
                getApplication(),
                uri,
                state.settings.businessName,
                recap,
                state.workerSummaries,
                state.priceHistory,
                state.types,
                monthly
            )
            statusMsg.value = "Ekspor Excel XLSX (5 sheet) selesai."
        }
    }

    fun exportPdf(uri: Uri) {
        viewModelScope.launch {
            val state = uiState.value
            val recap = state.weeklyRecap ?: return@launch
            ReportExporter.exportPdf(
                getApplication(),
                uri,
                state.settings.businessName,
                recap,
                state.workerSummaries
            )
            statusMsg.value = "Ekspor PDF Mingguan selesai."
        }
    }

    fun exportMonthlyPdf(uri: Uri) {
        viewModelScope.launch {
            val state = uiState.value
            val monthly = state.monthlyRecap ?: return@launch
            ReportExporter.exportMonthlyPdf(
                getApplication(),
                uri,
                state.settings.businessName,
                monthly
            )
            statusMsg.value = "Ekspor PDF Bulanan selesai."
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val state = uiState.value
            val recap = state.weeklyRecap ?: return@launch
            ReportExporter.exportWeeklyCsv(getApplication(), uri, recap)
            statusMsg.value = "Ekspor CSV Mingguan selesai."
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val state = uiState.value
            val payload = BackupPayload(
                exportedAt = Instant.now().toString(),
                productTypes = state.types,
                workers = state.workers,
                priceHistory = state.priceHistory,
                periods = state.periods,
                transactions = state.transactions,
                settings = state.settings
            )
            ReportExporter.exportJsonBackup(getApplication(), uri, payload)
            statusMsg.value = "Backup JSON berhasil disimpan."
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val payload = ReportExporter.importJsonBackup(getApplication(), uri)
                val normalizedTx = payload.transactions.map {
                    it.copy(workerKey = it.workerId ?: "__UNALLOCATED__")
                }
                db.withTransaction {
                    db.snapshotDao().deleteAll()
                    db.transactionDao().deleteAll()
                    db.priceHistoryDao().deleteAll()
                    db.workerDao().deleteAll()
                    db.weekPeriodDao().deleteAll()
                    db.productTypeDao().deleteAll()

                    db.productTypeDao().upsertAll(payload.productTypes)
                    db.workerDao().upsertAll(payload.workers)
                    db.priceHistoryDao().upsertAll(payload.priceHistory)
                    db.weekPeriodDao().upsertAll(payload.periods)
                    db.transactionDao().upsertAll(normalizedTx)
                    db.settingsDao().upsert(payload.settings)
                    payload.periods.forEach { refreshSnapshot(it.id) }
                }
            }.onSuccess {
                statusMsg.value = "Restore backup berhasil."
            }.onFailure { e ->
                statusMsg.value = "Gagal restore: ${e.message}"
            }
        }
    }
}
