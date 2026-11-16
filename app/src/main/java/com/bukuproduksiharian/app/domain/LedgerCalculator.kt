package com.bukuproduksiharian.app.domain

import com.bukuproduksiharian.app.data.db.PriceHistoryEntity
import com.bukuproduksiharian.app.data.db.ProductTypeEntity
import com.bukuproduksiharian.app.data.db.ProductionTransactionEntity
import com.bukuproduksiharian.app.data.db.WeekPeriodEntity
import com.bukuproduksiharian.app.data.db.WorkerEntity
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class ReportRangeFilter(val label: String) {
    DAILY("Harian"),
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan"),
    YEARLY("Tahunan")
}

data class PriceBreakdownItem(
    val price: Long,
    val qty: Long,
    val revenue: Long
)

data class DailyCellSummary(
    val date: String,
    val totalQty: Long,
    val allocatedQty: Long,
    val unallocatedQty: Long,
    val totalRevenue: Long
)

data class WeeklyTypeRowSummary(
    val type: ProductTypeEntity,
    val days: List<DailyCellSummary>,
    val totalQty: Long,
    val allocatedQty: Long,
    val unallocatedQty: Long,
    val displayPrice: Long?,
    val isVariedPrice: Boolean,
    val priceBreakdown: List<PriceBreakdownItem>,
    val totalRevenue: Long
)

data class WeeklyRecapSummary(
    val period: WeekPeriodEntity,
    val dates: List<String>,
    val rows: List<WeeklyTypeRowSummary>,
    val dailyTotalQty: List<Long>,
    val dailyTotalRevenue: List<Long>,
    val grandTotalQty: Long,
    val grandTotalRevenue: Long,
    val allocatedRevenue: Long,
    val unallocatedRevenue: Long
)

data class WorkerTypeRowSummary(
    val worker: WorkerEntity?,
    val workerName: String,
    val type: ProductTypeEntity,
    val dailyQty: List<Long>,
    val totalQty: Long,
    val unitPrice: Long?,
    val isVariedPrice: Boolean,
    val totalRevenue: Long
)

data class WorkerTotalSummary(
    val worker: WorkerEntity,
    val rows: List<WorkerTypeRowSummary>,
    val totalQty: Long,
    val totalRevenue: Long
)

data class MonthlyTypeAggregate(
    val type: ProductTypeEntity,
    val totalQty: Long,
    val totalRevenue: Long,
    val avgPrice: Long
)

data class MonthlyWorkerAggregate(
    val workerId: String?,
    val workerName: String,
    val totalQty: Long,
    val totalRevenue: Long
)

data class MonthlyWeekAggregate(
    val periodId: String,
    val label: String,
    val startDate: String,
    val totalQty: Long,
    val totalRevenue: Long
)

data class MonthlyRecapSummary(
    val monthKey: String,
    val totalQty: Long,
    val totalRevenue: Long,
    val allocatedRevenue: Long,
    val unallocatedRevenue: Long,
    val byType: List<MonthlyTypeAggregate>,
    val byWorker: List<MonthlyWorkerAggregate>,
    val byWeek: List<MonthlyWeekAggregate>
)

data class WorkerDailyDetailItem(
    val type: ProductTypeEntity,
    val quantity: Long,
    val unitPrice: Long,
    val revenue: Long
)

data class WorkerDailyDetail(
    val date: String,
    val dayLabel: String,
    val totalQty: Long,
    val totalRevenue: Long,
    val items: List<WorkerDailyDetailItem>
)

data class WorkerTypeDetailRow(
    val type: ProductTypeEntity,
    val dailyQty: List<Long>,
    val totalQty: Long,
    val unitPrice: Long?,
    val isVariedPrice: Boolean,
    val priceBreakdown: List<PriceBreakdownItem>,
    val totalRevenue: Long
)

data class WorkerMonthlyHistoryEntry(
    val monthKey: String,
    val totalQty: Long,
    val totalRevenue: Long,
    val byType: List<MonthlyTypeAggregate>,
    val byWeek: List<MonthlyWeekAggregate>
)

data class WorkerDetailSummary(
    val worker: WorkerEntity,
    val period: WeekPeriodEntity,
    val dates: List<String>,
    val byType: List<WorkerTypeDetailRow>,
    val byDay: List<WorkerDailyDetail>,
    val weeklyTotalQty: Long,
    val weeklyTotalRevenue: Long,
    val allTimeTotalQty: Long,
    val allTimeTotalRevenue: Long,
    val monthlyHistory: List<WorkerMonthlyHistoryEntry>
)

object LedgerCalculator {
    private val idLocale = Locale("id", "ID")
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", idLocale)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", idLocale)
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", idLocale)

    fun formatRupiah(amount: Long): String {
        val fmt = NumberFormat.getCurrencyInstance(idLocale)
        fmt.maximumFractionDigits = 0
        return fmt.format(amount).replace("\\s+".toRegex(), "")
    }

    fun formatNumber(value: Long): String {
        return NumberFormat.getIntegerInstance(idLocale).format(value)
    }

    fun mondayOf(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun formatShortDate(date: LocalDate): String = date.format(shortDateFormatter)

    fun formatFullDate(dateIso: String): String = runCatching {
        LocalDate.parse(dateIso).format(fullDateFormatter)
    }.getOrDefault(dateIso)

    fun formatMonthYear(monthKey: String): String = runCatching {
        YearMonth.parse(monthKey).atDay(1).format(monthYearFormatter)
    }.getOrDefault(monthKey)

    fun weekDates(startDateIso: String): List<String> {
        val start = LocalDate.parse(startDateIso)
        return (0L..6L).map { start.plusDays(it).toString() }
    }

    fun getEffectivePriceEntry(
        priceHistory: List<PriceHistoryEntity>,
        typeId: String,
        date: String
    ): PriceHistoryEntity? {
        val sorted = priceHistory
            .filter { it.typeId == typeId }
            .sortedWith(
                compareByDescending<PriceHistoryEntity> { it.effectiveFrom }
                    .thenByDescending { it.createdAt }
            )
        if (sorted.isEmpty()) return null
        return sorted.firstOrNull { it.effectiveFrom <= date } ?: sorted.last()
    }

    fun computeWeeklyRecap(
        period: WeekPeriodEntity,
        types: List<ProductTypeEntity>,
        priceHistory: List<PriceHistoryEntity>,
        transactions: List<ProductionTransactionEntity>,
        typeSearch: String = ""
    ): WeeklyRecapSummary {
        val dates = weekDates(period.startDate)
        val periodTx = transactions.filter { it.periodId == period.id && it.quantity > 0L }
        val query = typeSearch.trim().lowercase()
        val filteredTypes = types
            .sortedWith(compareBy<ProductTypeEntity> { it.sortOrder }.thenBy { it.code })
            .filter { query.isEmpty() || it.code.lowercase().contains(query) || it.name.lowercase().contains(query) }

        val dailyQty = MutableList(7) { 0L }
        val dailyRev = MutableList(7) { 0L }
        var grandQty = 0L
        var grandRev = 0L
        var allocatedRev = 0L
        var unallocatedRev = 0L

        val rows = filteredTypes.map { type ->
            val typeTx = periodTx.filter { it.typeId == type.id }
            val priceQtyMap = linkedMapOf<Long, Pair<Long, Long>>()

            val days = dates.mapIndexed { idx, date ->
                val dayTx = typeTx.filter { it.date == date }
                var dQty = 0L
                var dAlloc = 0L
                var dUnalloc = 0L
                var dRev = 0L

                for (tx in dayTx) {
                    val rev = tx.quantity * tx.unitPriceSnapshot
                    dQty += tx.quantity
                    dRev += rev
                    if (tx.workerId == null) {
                        dUnalloc += tx.quantity
                        unallocatedRev += rev
                    } else {
                        dAlloc += tx.quantity
                        allocatedRev += rev
                    }
                    val prev = priceQtyMap[tx.unitPriceSnapshot] ?: (0L to 0L)
                    priceQtyMap[tx.unitPriceSnapshot] = (prev.first + tx.quantity) to (prev.second + rev)
                }

                dailyQty[idx] += dQty
                dailyRev[idx] += dRev

                DailyCellSummary(date, dQty, dAlloc, dUnalloc, dRev)
            }

            val rowQty = days.sumOf { it.totalQty }
            val rowAlloc = days.sumOf { it.allocatedQty }
            val rowUnalloc = days.sumOf { it.unallocatedQty }
            val rowRev = days.sumOf { it.totalRevenue }
            grandQty += rowQty
            grandRev += rowRev

            val distinctPrices = priceQtyMap.keys
            val isVaried = distinctPrices.size > 1
            val displayPrice = when {
                distinctPrices.size == 1 -> distinctPrices.first()
                isVaried -> null
                else -> getEffectivePriceEntry(priceHistory, type.id, dates.first())?.price ?: 0L
            }
            val breakdown = priceQtyMap.entries
                .map { (p, pair) -> PriceBreakdownItem(p, pair.first, pair.second) }
                .sortedBy { it.price }

            WeeklyTypeRowSummary(
                type = type,
                days = days,
                totalQty = rowQty,
                allocatedQty = rowAlloc,
                unallocatedQty = rowUnalloc,
                displayPrice = displayPrice,
                isVariedPrice = isVaried,
                priceBreakdown = breakdown,
                totalRevenue = rowRev
            )
        }

        return WeeklyRecapSummary(
            period = period,
            dates = dates,
            rows = rows,
            dailyTotalQty = dailyQty,
            dailyTotalRevenue = dailyRev,
            grandTotalQty = grandQty,
            grandTotalRevenue = grandRev,
            allocatedRevenue = allocatedRev,
            unallocatedRevenue = unallocatedRev
        )
    }

    fun computeWorkerSummaries(
        period: WeekPeriodEntity,
        workers: List<WorkerEntity>,
        types: List<ProductTypeEntity>,
        priceHistory: List<PriceHistoryEntity>,
        transactions: List<ProductionTransactionEntity>
    ): List<WorkerTotalSummary> {
        val dates = weekDates(period.startDate)
        val periodTx = transactions.filter { it.periodId == period.id && it.quantity > 0L }
        val typeMap = types.associateBy { it.id }
        val workerMap = workers.associateBy { it.id }

        val targetWorkerIds = linkedSetOf<String>()
        workers.forEach { w ->
            if (w.isActive || periodTx.any { it.workerId == w.id }) {
                targetWorkerIds.add(w.id)
            }
        }
        periodTx.forEach { tx ->
            if (tx.workerId != null) targetWorkerIds.add(tx.workerId)
        }

        return targetWorkerIds.mapNotNull { wid ->
            val wTx = periodTx.filter { it.workerId == wid }
            val worker = workerMap[wid] ?: WorkerEntity(
                id = wid,
                name = wTx.firstOrNull { !it.workerNameSnapshot.isNullOrBlank() }?.workerNameSnapshot ?: "Pekerja Historis",
                note = "Arsip / Terhapus",
                isActive = false,
                createdAt = ""
            )
            if (!worker.isActive && wTx.isEmpty()) return@mapNotNull null

            val typeIds = wTx.map { it.typeId }.distinct()
            val rows = typeIds.mapNotNull { tid ->
                val type = typeMap[tid] ?: return@mapNotNull null
                val list = wTx.filter { it.typeId == tid }
                val daily = MutableList(7) { 0L }
                var totalRev = 0L
                val prices = mutableSetOf<Long>()

                for (tx in list) {
                    val idx = dates.indexOf(tx.date)
                    if (idx >= 0) {
                        daily[idx] += tx.quantity
                        totalRev += tx.quantity * tx.unitPriceSnapshot
                        prices.add(tx.unitPriceSnapshot)
                    }
                }
                val totalQty = daily.sum()
                val varied = prices.size > 1
                val unitPrice = when {
                    prices.size == 1 -> prices.first()
                    varied -> null
                    else -> getEffectivePriceEntry(priceHistory, tid, dates.first())?.price ?: 0L
                }

                WorkerTypeRowSummary(
                    worker = worker,
                    workerName = worker.name,
                    type = type,
                    dailyQty = daily,
                    totalQty = totalQty,
                    unitPrice = unitPrice,
                    isVariedPrice = varied,
                    totalRevenue = totalRev
                )
            }.sortedBy { it.type.sortOrder }

            WorkerTotalSummary(
                worker = worker,
                rows = rows,
                totalQty = rows.sumOf { it.totalQty },
                totalRevenue = rows.sumOf { it.totalRevenue }
            )
        }.sortedByDescending { it.totalRevenue }
    }

    fun filterTransactionsByRange(
        transactions: List<ProductionTransactionEntity>,
        filter: ReportRangeFilter,
        anchorDate: String,
        periodId: String? = null
    ): List<ProductionTransactionEntity> {
        return transactions.filter { tx ->
            if (tx.quantity <= 0L) return@filter false
            when (filter) {
                ReportRangeFilter.DAILY -> tx.date == anchorDate
                ReportRangeFilter.WEEKLY -> {
                    if (periodId != null) tx.periodId == periodId
                    else {
                        val dates = weekDates(mondayOf(LocalDate.parse(anchorDate)).toString())
                        tx.date >= dates.first() && tx.date <= dates.last()
                    }
                }
                ReportRangeFilter.MONTHLY -> tx.date.take(7) == anchorDate.take(7)
                ReportRangeFilter.YEARLY -> tx.date.take(4) == anchorDate.take(4)
            }
        }
    }

    fun computeMonthlyRecap(
        monthKey: String,
        types: List<ProductTypeEntity>,
        workers: List<WorkerEntity>,
        periods: List<WeekPeriodEntity>,
        transactions: List<ProductionTransactionEntity>
    ): MonthlyRecapSummary {
        val monthTx = transactions.filter { it.quantity > 0L && it.date.take(7) == monthKey }
        val workerMap = workers.associateBy { it.id }
        val periodMap = periods.associateBy { it.id }

        var totalQty = 0L
        var totalRev = 0L
        var allocRev = 0L
        var unallocRev = 0L

        val typeAgg = mutableMapOf<String, Pair<Long, Long>>()
        val workerAgg = mutableMapOf<String, Triple<String, Long, Long>>()
        val weekAgg = mutableMapOf<String, Pair<Long, Long>>()

        for (tx in monthTx) {
            val rev = tx.quantity * tx.unitPriceSnapshot
            totalQty += tx.quantity
            totalRev += rev
            if (tx.workerId == null) unallocRev += rev else allocRev += rev

            val tPrev = typeAgg[tx.typeId] ?: (0L to 0L)
            typeAgg[tx.typeId] = (tPrev.first + tx.quantity) to (tPrev.second + rev)

            val wKey = tx.workerId ?: "__UNALLOCATED__"
            val wName = if (tx.workerId != null) {
                workerMap[tx.workerId]?.name ?: tx.workerNameSnapshot ?: "Pekerja Historis"
            } else {
                "Belum Dialokasikan"
            }
            val wPrev = workerAgg[wKey] ?: Triple(wName, 0L, 0L)
            workerAgg[wKey] = Triple(wPrev.first, wPrev.second + tx.quantity, wPrev.third + rev)

            val pPrev = weekAgg[tx.periodId] ?: (0L to 0L)
            weekAgg[tx.periodId] = (pPrev.first + tx.quantity) to (pPrev.second + rev)
        }

        val byType = types.sortedWith(compareBy<ProductTypeEntity> { it.sortOrder }.thenBy { it.code })
            .mapNotNull { t ->
                val agg = typeAgg[t.id] ?: return@mapNotNull null
                if (agg.first <= 0L) return@mapNotNull null
                MonthlyTypeAggregate(
                    type = t,
                    totalQty = agg.first,
                    totalRevenue = agg.second,
                    avgPrice = agg.second / agg.first
                )
            }

        val byWorker = workerAgg.entries.map { (key, triple) ->
            MonthlyWorkerAggregate(
                workerId = if (key == "__UNALLOCATED__") null else key,
                workerName = triple.first,
                totalQty = triple.second,
                totalRevenue = triple.third
            )
        }.sortedByDescending { it.totalRevenue }

        val byWeek = weekAgg.entries.map { (pid, pair) ->
            val p = periodMap[pid]
            MonthlyWeekAggregate(
                periodId = pid,
                label = p?.label ?: pid,
                startDate = p?.startDate ?: "",
                totalQty = pair.first,
                totalRevenue = pair.second
            )
        }.sortedBy { it.startDate }

        return MonthlyRecapSummary(
            monthKey = monthKey,
            totalQty = totalQty,
            totalRevenue = totalRev,
            allocatedRevenue = allocRev,
            unallocatedRevenue = unallocRev,
            byType = byType,
            byWorker = byWorker,
            byWeek = byWeek
        )
    }

    fun computeWorkerDetailSummary(
        workerId: String,
        period: WeekPeriodEntity,
        workers: List<WorkerEntity>,
        types: List<ProductTypeEntity>,
        priceHistory: List<PriceHistoryEntity>,
        periods: List<WeekPeriodEntity>,
        transactions: List<ProductionTransactionEntity>
    ): WorkerDetailSummary? {
        val workerTx = transactions.filter { it.workerId == workerId && it.quantity > 0L }
        val worker = workers.firstOrNull { it.id == workerId } ?: run {
            if (workerTx.isEmpty()) return null
            WorkerEntity(
                id = workerId,
                name = workerTx.firstOrNull { !it.workerNameSnapshot.isNullOrBlank() }?.workerNameSnapshot ?: "Pekerja Historis",
                note = "Arsip / Terhapus",
                isActive = false,
                createdAt = ""
            )
        }

        val dates = weekDates(period.startDate)
        val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val typeMap = types.associateBy { it.id }
        val periodMap = periods.associateBy { it.id }
        val periodTx = workerTx.filter { it.periodId == period.id }

        val typeIds = periodTx.map { it.typeId }.distinct()
        val byType = typeIds.mapNotNull { tid ->
            val type = typeMap[tid] ?: return@mapNotNull null
            val list = periodTx.filter { it.typeId == tid }
            val daily = MutableList(7) { 0L }
            val priceMap = linkedMapOf<Long, Pair<Long, Long>>()
            var totalRev = 0L

            for (tx in list) {
                val idx = dates.indexOf(tx.date)
                if (idx >= 0) {
                    val rev = tx.quantity * tx.unitPriceSnapshot
                    daily[idx] += tx.quantity
                    totalRev += rev
                    val prev = priceMap[tx.unitPriceSnapshot] ?: (0L to 0L)
                    priceMap[tx.unitPriceSnapshot] = (prev.first + tx.quantity) to (prev.second + rev)
                }
            }
            val totalQty = daily.sum()
            val varied = priceMap.keys.size > 1
            val unitPrice = when {
                priceMap.keys.size == 1 -> priceMap.keys.first()
                varied -> null
                else -> getEffectivePriceEntry(priceHistory, tid, dates.first())?.price ?: 0L
            }
            val breakdown = priceMap.entries
                .map { (p, pair) -> PriceBreakdownItem(p, pair.first, pair.second) }
                .sortedBy { it.price }

            WorkerTypeDetailRow(
                type = type,
                dailyQty = daily,
                totalQty = totalQty,
                unitPrice = unitPrice,
                isVariedPrice = varied,
                priceBreakdown = breakdown,
                totalRevenue = totalRev
            )
        }.sortedWith(compareBy<WorkerTypeDetailRow> { it.type.sortOrder }.thenBy { it.type.code })

        val byDay = dates.mapIndexed { idx, d ->
            val dayTx = periodTx.filter { it.date == d }
            val items = dayTx.mapNotNull { tx ->
                val type = typeMap[tx.typeId] ?: return@mapNotNull null
                WorkerDailyDetailItem(
                    type = type,
                    quantity = tx.quantity,
                    unitPrice = tx.unitPriceSnapshot,
                    revenue = tx.quantity * tx.unitPriceSnapshot
                )
            }.sortedWith(compareBy<WorkerDailyDetailItem> { it.type.sortOrder }.thenBy { it.type.code })
            WorkerDailyDetail(
                date = d,
                dayLabel = dayNames[idx],
                totalQty = items.sumOf { it.quantity },
                totalRevenue = items.sumOf { it.revenue },
                items = items
            )
        }

        val weeklyTotalQty = byType.sumOf { it.totalQty }
        val weeklyTotalRevenue = byType.sumOf { it.totalRevenue }
        val allTimeTotalQty = workerTx.sumOf { it.quantity }
        val allTimeTotalRevenue = workerTx.sumOf { it.quantity * it.unitPriceSnapshot }

        val monthBuckets = linkedMapOf<String, MutableList<ProductionTransactionEntity>>()
        for (tx in workerTx) {
            val mk = tx.date.take(7)
            monthBuckets.getOrPut(mk) { mutableListOf() }.add(tx)
        }

        val monthlyHistory = monthBuckets.entries
            .sortedByDescending { it.key }
            .map { (mk, txs) ->
                val typeAgg = mutableMapOf<String, Pair<Long, Long>>()
                val weekAgg = mutableMapOf<String, Pair<Long, Long>>()
                var mQty = 0L
                var mRev = 0L
                for (tx in txs) {
                    val rev = tx.quantity * tx.unitPriceSnapshot
                    mQty += tx.quantity
                    mRev += rev
                    val tPrev = typeAgg[tx.typeId] ?: (0L to 0L)
                    typeAgg[tx.typeId] = (tPrev.first + tx.quantity) to (tPrev.second + rev)
                    val pPrev = weekAgg[tx.periodId] ?: (0L to 0L)
                    weekAgg[tx.periodId] = (pPrev.first + tx.quantity) to (pPrev.second + rev)
                }
                val mByType = types.sortedWith(compareBy<ProductTypeEntity> { it.sortOrder }.thenBy { it.code })
                    .mapNotNull { t ->
                        val agg = typeAgg[t.id] ?: return@mapNotNull null
                        if (agg.first <= 0L) return@mapNotNull null
                        MonthlyTypeAggregate(
                            type = t,
                            totalQty = agg.first,
                            totalRevenue = agg.second,
                            avgPrice = agg.second / agg.first
                        )
                    }
                val mByWeek = weekAgg.entries.map { (pid, pair) ->
                    val p = periodMap[pid]
                    MonthlyWeekAggregate(
                        periodId = pid,
                        label = p?.label ?: pid,
                        startDate = p?.startDate ?: "",
                        totalQty = pair.first,
                        totalRevenue = pair.second
                    )
                }.sortedBy { it.startDate }

                WorkerMonthlyHistoryEntry(
                    monthKey = mk,
                    totalQty = mQty,
                    totalRevenue = mRev,
                    byType = mByType,
                    byWeek = mByWeek
                )
            }

        return WorkerDetailSummary(
            worker = worker,
            period = period,
            dates = dates,
            byType = byType,
            byDay = byDay,
            weeklyTotalQty = weeklyTotalQty,
            weeklyTotalRevenue = weeklyTotalRevenue,
            allTimeTotalQty = allTimeTotalQty,
            allTimeTotalRevenue = allTimeTotalRevenue,
            monthlyHistory = monthlyHistory
        )
    }
}
