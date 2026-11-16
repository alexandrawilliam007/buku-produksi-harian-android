package com.bukuproduksiharian.app

import com.bukuproduksiharian.app.data.db.PriceHistoryEntity
import com.bukuproduksiharian.app.data.db.ProductTypeEntity
import com.bukuproduksiharian.app.data.db.ProductionTransactionEntity
import com.bukuproduksiharian.app.data.db.WeekPeriodEntity
import com.bukuproduksiharian.app.data.db.WorkerEntity
import com.bukuproduksiharian.app.domain.LedgerCalculator
import com.bukuproduksiharian.app.domain.ReportRangeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCalculatorTest {
    private val now = "2026-11-09T00:00:00Z"

    private fun baseTypes() = listOf(
        ProductTypeEntity("t1", "400 LD", "Plastik 400 LD", "Unit", 1, now),
        ProductTypeEntity("t2", "600 HD", "Plastik 600 HD", "Unit", 2, now),
        ProductTypeEntity("t3", "800 HD", "Plastik 800 HD", "Unit", 3, now)
    )

    private fun basePrices() = listOf(
        PriceHistoryEntity("p1", "t1", 33250L, "2025-01-01", "Harga awal", now),
        PriceHistoryEntity("p2", "t2", 68950L, "2025-01-01", "Harga awal", now),
        PriceHistoryEntity("p3", "t3", 96950L, "2025-01-01", "Harga awal", now)
    )

    private fun tx(
        id: String,
        periodId: String,
        date: String,
        typeId: String,
        workerId: String?,
        qty: Long,
        price: Long,
        priceHistoryId: String?,
        workerNameSnapshot: String? = null
    ) = ProductionTransactionEntity(
        id = id,
        periodId = periodId,
        date = date,
        typeId = typeId,
        workerId = workerId,
        workerKey = workerId ?: "__UNALLOCATED__",
        workerNameSnapshot = workerNameSnapshot,
        quantity = qty,
        unitPriceSnapshot = price,
        priceHistoryId = priceHistoryId,
        updatedAt = now
    )

    @Test
    fun excelBenchmarkVerification() {
        val period = WeekPeriodEntity("prd1", "9 Nov - 15 Nov", "2026-11-09", "2026-11-15", false, null, now)
        val monToFri = listOf("2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13")
        val txs = buildList {
            monToFri.forEachIndexed { i, d ->
                add(tx("a$i", "prd1", d, "t1", null, 25L, 33250L, "p1"))
                add(tx("b$i", "prd1", d, "t2", null, 18L, 68950L, "p2"))
                add(tx("c$i", "prd1", d, "t3", null, 10L, 96950L, "p3"))
            }
        }

        val recap = LedgerCalculator.computeWeeklyRecap(period, baseTypes(), basePrices(), txs)
        assertEquals(265L, recap.grandTotalQty)
        assertEquals(15209250L, recap.grandTotalRevenue)
        for (dayIdx in 0..4) {
            assertEquals(3041850L, recap.dailyTotalRevenue[dayIdx])
        }
        assertEquals(0L, recap.dailyTotalRevenue[5])
        assertEquals(0L, recap.dailyTotalRevenue[6])
        assertEquals(0L, recap.allocatedRevenue)
        assertEquals(15209250L, recap.unallocatedRevenue)
    }

    @Test
    fun individualSyncAndArchivedWorkerReconciliation() {
        val period = WeekPeriodEntity("prd1", "9 Nov - 15 Nov", "2026-11-09", "2026-11-15", true, now, now)
        val workers = listOf(
            WorkerEntity("w1", "Andi", "", isActive = true, createdAt = now),
            WorkerEntity("w2", "Budi", "Pindah bagian", isActive = false, createdAt = now)
        )
        val txs = listOf(
            tx("1", "prd1", "2026-11-09", "t1", "w1", 30L, 33250L, "p1", "Andi"),
            tx("2", "prd1", "2026-11-10", "t1", "w2", 45L, 33250L, "p1", "Budi"),
            tx("3", "prd1", "2026-11-11", "t1", null, 5L, 33250L, "p1", null)
        )

        val recap = LedgerCalculator.computeWeeklyRecap(period, baseTypes(), basePrices(), txs)
        val workerSummaries = LedgerCalculator.computeWorkerSummaries(period, workers, baseTypes(), basePrices(), txs)

        assertEquals(80L, recap.grandTotalQty)
        assertEquals(80L * 33250L, recap.grandTotalRevenue)
        assertEquals(75L * 33250L, recap.allocatedRevenue)
        assertEquals(5L * 33250L, recap.unallocatedRevenue)
        assertEquals(recap.grandTotalRevenue, recap.allocatedRevenue + recap.unallocatedRevenue)

        assertEquals(2, workerSummaries.size)
        val budi = workerSummaries.first { it.worker.id == "w2" }
        assertEquals(45L, budi.totalQty)
        assertEquals(45L * 33250L, budi.totalRevenue)
        assertEquals(recap.allocatedRevenue, workerSummaries.sumOf { it.totalRevenue })
    }

    @Test
    fun midWeekVariedPriceAndMonthlyRecap() {
        val period = WeekPeriodEntity("prd1", "9 Nov - 15 Nov", "2026-11-09", "2026-11-15", false, null, now)
        val prices = basePrices() + PriceHistoryEntity("p1b", "t1", 35000L, "2026-11-12", "Naik", now)
        val effMon = LedgerCalculator.getEffectivePriceEntry(prices, "t1", "2026-11-09")
        val effThu = LedgerCalculator.getEffectivePriceEntry(prices, "t1", "2026-11-12")
        assertEquals(33250L, effMon?.price)
        assertEquals(35000L, effThu?.price)

        val txs = listOf(
            tx("m1", "prd1", "2026-11-09", "t1", "w1", 10L, 33250L, "p1", "Andi"),
            tx("m2", "prd1", "2026-11-12", "t1", "w1", 10L, 35000L, "p1b", "Andi")
        )
        val recap = LedgerCalculator.computeWeeklyRecap(period, baseTypes(), prices, txs)
        val row400 = recap.rows.first { it.type.code == "400 LD" }
        assertTrue(row400.isVariedPrice)
        assertNull(row400.displayPrice)
        assertEquals(20L, row400.totalQty)
        assertEquals(682500L, row400.totalRevenue)
        assertEquals(2, row400.priceBreakdown.size)

        val monthly = LedgerCalculator.computeMonthlyRecap(
            monthKey = "2026-11",
            types = baseTypes(),
            workers = listOf(WorkerEntity("w1", "Andi", "", true, now)),
            periods = listOf(period),
            transactions = txs
        )
        assertEquals(20L, monthly.totalQty)
        assertEquals(682500L, monthly.totalRevenue)
        assertEquals(monthly.totalRevenue, monthly.allocatedRevenue + monthly.unallocatedRevenue)

        val dailyFiltered = LedgerCalculator.filterTransactionsByRange(txs, ReportRangeFilter.DAILY, "2026-11-12")
        assertEquals(1, dailyFiltered.size)
        assertEquals(35000L, dailyFiltered.first().unitPriceSnapshot)
    }

    @Test
    fun threeWorkersExactRevenueSyncAndDetailSummary() {
        val period1 = WeekPeriodEntity("prd1", "9 Nov - 15 Nov", "2026-11-09", "2026-11-15", false, null, now)
        val period2 = WeekPeriodEntity("prd2", "16 Nov - 22 Nov", "2026-11-16", "2026-11-22", false, null, now)
        val decemberPeriod = WeekPeriodEntity("prd3", "7 Des - 13 Des", "2026-12-07", "2026-12-13", false, null, now)
        val workers = listOf(
            WorkerEntity("w1", "Andi", "Line A", true, now),
            WorkerEntity("w2", "Budi", "Line B", true, now),
            WorkerEntity("w3", "Candra", "Line C", true, now)
        )
        val txs = listOf(
            // Andi: 400 LD = 30 (997.500), 600 HD = 10 (689.500) -> 1.687.000
            tx("a1", "prd2", "2026-11-16", "t1", "w1", 10L, 33250L, "p1", "Andi"),
            tx("a2", "prd2", "2026-11-17", "t1", "w1", 12L, 33250L, "p1", "Andi"),
            tx("a3", "prd2", "2026-11-18", "t1", "w1", 8L, 33250L, "p1", "Andi"),
            tx("a4", "prd2", "2026-11-16", "t2", "w1", 6L, 68950L, "p2", "Andi"),
            tx("a5", "prd2", "2026-11-19", "t2", "w1", 4L, 68950L, "p2", "Andi"),
            // Budi: 400 LD = 45 (1.496.250), 600 HD = 20 (1.379.000) -> 2.875.250
            tx("b1", "prd2", "2026-11-16", "t1", "w2", 15L, 33250L, "p1", "Budi"),
            tx("b2", "prd2", "2026-11-17", "t1", "w2", 15L, 33250L, "p1", "Budi"),
            tx("b3", "prd2", "2026-11-18", "t1", "w2", 15L, 33250L, "p1", "Budi"),
            tx("b4", "prd2", "2026-11-17", "t2", "w2", 10L, 68950L, "p2", "Budi"),
            tx("b5", "prd2", "2026-11-19", "t2", "w2", 10L, 68950L, "p2", "Budi"),
            // Candra: 400 LD = 25 (831.250), 800 HD = 15 (1.454.250) -> 2.285.500
            tx("c1", "prd2", "2026-11-16", "t1", "w3", 10L, 33250L, "p1", "Candra"),
            tx("c2", "prd2", "2026-11-18", "t1", "w3", 15L, 33250L, "p1", "Candra"),
            tx("c3", "prd2", "2026-11-17", "t3", "w3", 8L, 96950L, "p3", "Candra"),
            tx("c4", "prd2", "2026-11-20", "t3", "w3", 7L, 96950L, "p3", "Candra"),
            // December history for Andi to test multi-month history
            tx("d1", "prd3", "2026-12-07", "t1", "w1", 20L, 33250L, "p1", "Andi")
        )

        val recap = LedgerCalculator.computeWeeklyRecap(period2, baseTypes(), basePrices(), txs)
        val summaries = LedgerCalculator.computeWorkerSummaries(period2, workers, baseTypes(), basePrices(), txs)

        assertEquals(145L, recap.grandTotalQty)
        assertEquals(6847750L, recap.grandTotalRevenue)
        assertEquals(6847750L, recap.allocatedRevenue)
        assertEquals(0L, recap.unallocatedRevenue)

        val andi = summaries.first { it.worker.id == "w1" }
        val budi = summaries.first { it.worker.id == "w2" }
        val candra = summaries.first { it.worker.id == "w3" }
        assertEquals(40L, andi.totalQty)
        assertEquals(1687000L, andi.totalRevenue)
        assertEquals(65L, budi.totalQty)
        assertEquals(2875250L, budi.totalRevenue)
        assertEquals(40L, candra.totalQty)
        assertEquals(2285500L, candra.totalRevenue)
        assertEquals(6847750L, summaries.sumOf { it.totalRevenue })

        val detail = LedgerCalculator.computeWorkerDetailSummary(
            workerId = "w1",
            period = period2,
            workers = workers,
            types = baseTypes(),
            priceHistory = basePrices(),
            periods = listOf(period1, period2, decemberPeriod),
            transactions = txs
        )!!
        assertEquals("Andi", detail.worker.name)
        assertEquals(40L, detail.weeklyTotalQty)
        assertEquals(1687000L, detail.weeklyTotalRevenue)
        assertEquals(60L, detail.allTimeTotalQty)
        assertEquals(1687000L + 20L * 33250L, detail.allTimeTotalRevenue)
        assertEquals(7, detail.byDay.size)
        assertEquals(16L, detail.byDay[0].totalQty) // Senin: 10 + 6
        assertEquals(12L, detail.byDay[1].totalQty) // Selasa: 12
        assertEquals(2, detail.monthlyHistory.size)
        assertEquals("2026-12", detail.monthlyHistory[0].monthKey)
        assertEquals("2026-11", detail.monthlyHistory[1].monthKey)
    }
}
