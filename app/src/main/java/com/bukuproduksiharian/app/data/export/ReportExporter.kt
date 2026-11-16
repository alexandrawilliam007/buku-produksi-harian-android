package com.bukuproduksiharian.app.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.bukuproduksiharian.app.data.db.BackupPayload
import com.bukuproduksiharian.app.data.db.PriceHistoryEntity
import com.bukuproduksiharian.app.data.db.ProductTypeEntity
import com.bukuproduksiharian.app.domain.LedgerCalculator
import com.bukuproduksiharian.app.domain.MonthlyRecapSummary
import com.bukuproduksiharian.app.domain.ReportRangeFilter
import com.bukuproduksiharian.app.domain.WeeklyRecapSummary
import com.bukuproduksiharian.app.domain.WorkerTotalSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedReader
import java.io.InputStreamReader

object ReportExporter {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dayNames = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

    suspend fun exportExcelXlsx(
        context: Context,
        uri: Uri,
        businessName: String,
        recap: WeeklyRecapSummary,
        workerSummaries: List<WorkerTotalSummary>,
        priceHistory: List<PriceHistoryEntity>,
        types: List<ProductTypeEntity>,
        monthly: MonthlyRecapSummary
    ) = withContext(Dispatchers.IO) {
        val workbook = XSSFWorkbook()

        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.index
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(headerFont)
            alignment = HorizontalAlignment.CENTER
        }
        val boldStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply { bold = true })
        }

        // Sheet 1: Rekap Mingguan
        val s1 = workbook.createSheet("Rekap Mingguan")
        s1.createRow(0).createCell(0).setCellValue("${businessName.uppercase()} — REKAP MINGGUAN (${recap.period.label})")
        val h1 = s1.createRow(1)
        val headers1 = listOf("TYPE") + dayNames + listOf("TOTAL", "HARGA SATUAN", "HARGA TOTAL MINGGUAN")
        headers1.forEachIndexed { idx, title ->
            h1.createCell(idx).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }
        recap.rows.forEachIndexed { rIdx, row ->
            val r = s1.createRow(rIdx + 2)
            r.createCell(0).setCellValue(row.type.code)
            row.days.forEachIndexed { dIdx, d -> r.createCell(dIdx + 1).setCellValue(d.totalQty.toDouble()) }
            r.createCell(8).setCellValue(row.totalQty.toDouble())
            if (row.isVariedPrice) r.createCell(9).setCellValue("HARGA BERVARIASI")
            else r.createCell(9).setCellValue((row.displayPrice ?: 0L).toDouble())
            r.createCell(10).setCellValue(row.totalRevenue.toDouble())
        }
        val totalQtyRow = s1.createRow(recap.rows.size + 2)
        totalQtyRow.createCell(0).apply { setCellValue("JUMLAH TOTAL"); cellStyle = boldStyle }
        recap.dailyTotalQty.forEachIndexed { i, q ->
            totalQtyRow.createCell(i + 1).apply { setCellValue(q.toDouble()); cellStyle = boldStyle }
        }
        totalQtyRow.createCell(8).apply { setCellValue(recap.grandTotalQty.toDouble()); cellStyle = boldStyle }

        val totalRevRow = s1.createRow(recap.rows.size + 3)
        totalRevRow.createCell(0).apply { setCellValue("HARGA TOTAL HARIAN"); cellStyle = boldStyle }
        recap.dailyTotalRevenue.forEachIndexed { i, v ->
            totalRevRow.createCell(i + 1).apply { setCellValue(v.toDouble()); cellStyle = boldStyle }
        }
        totalRevRow.createCell(9).apply { setCellValue("TOTAL PENDAPATAN MINGGUAN"); cellStyle = boldStyle }
        totalRevRow.createCell(10).apply { setCellValue(recap.grandTotalRevenue.toDouble()); cellStyle = boldStyle }

        // Sheet 2: Produksi Perorangan
        val s2 = workbook.createSheet("Produksi Perorangan")
        val h2 = s2.createRow(0)
        (listOf("NAMA", "TYPE") + dayNames + listOf("TOTAL PRODUKSI", "HARGA SATUAN", "TOTAL PENDAPATAN")).forEachIndexed { i, t ->
            h2.createCell(i).apply { setCellValue(t); cellStyle = headerStyle }
        }
        var rowCursor = 1
        workerSummaries.forEach { ws ->
            ws.rows.forEach { r ->
                val row = s2.createRow(rowCursor++)
                row.createCell(0).setCellValue(r.workerName)
                row.createCell(1).setCellValue(r.type.code)
                r.dailyQty.forEachIndexed { i, q -> row.createCell(i + 2).setCellValue(q.toDouble()) }
                row.createCell(9).setCellValue(r.totalQty.toDouble())
                if (r.isVariedPrice) row.createCell(10).setCellValue("HARGA BERVARIASI")
                else row.createCell(10).setCellValue((r.unitPrice ?: 0L).toDouble())
                row.createCell(11).setCellValue(r.totalRevenue.toDouble())
            }
        }

        // Sheet 3: Pendapatan Perorangan
        val s3 = workbook.createSheet("Pendapatan Perorangan")
        val h3 = s3.createRow(0)
        listOf("NAMA", "TOTAL PRODUKSI", "TOTAL PENDAPATAN").forEachIndexed { i, t ->
            h3.createCell(i).apply { setCellValue(t); cellStyle = headerStyle }
        }
        workerSummaries.forEachIndexed { idx, ws ->
            val r = s3.createRow(idx + 1)
            r.createCell(0).setCellValue(ws.worker.name)
            r.createCell(1).setCellValue(ws.totalQty.toDouble())
            r.createCell(2).setCellValue(ws.totalRevenue.toDouble())
        }

        // Sheet 4: Riwayat Harga
        val s4 = workbook.createSheet("Riwayat Harga")
        val h4 = s4.createRow(0)
        listOf("TYPE", "HARGA SATUAN", "MULAI BERLAKU", "CATATAN").forEachIndexed { i, t ->
            h4.createCell(i).apply { setCellValue(t); cellStyle = headerStyle }
        }
        val typeMap = types.associateBy { it.id }
        priceHistory.forEachIndexed { idx, p ->
            val r = s4.createRow(idx + 1)
            r.createCell(0).setCellValue(typeMap[p.typeId]?.code ?: p.typeId)
            r.createCell(1).setCellValue(p.price.toDouble())
            r.createCell(2).setCellValue(p.effectiveFrom)
            r.createCell(3).setCellValue(p.note)
        }

        // Sheet 5: Rekap Bulanan (Real Monthly Aggregation)
        val s5 = workbook.createSheet("Rekap Bulanan")
        s5.createRow(0).createCell(0).apply {
            setCellValue("REKAP BULANAN — ${LedgerCalculator.formatMonthYear(monthly.monthKey)}")
            cellStyle = boldStyle
        }
        val h5Type = s5.createRow(2)
        listOf("TYPE", "TOTAL PRODUKSI", "RATA-RATA HARGA", "TOTAL PENDAPATAN").forEachIndexed { i, t ->
            h5Type.createCell(i).apply { setCellValue(t); cellStyle = headerStyle }
        }
        var r5 = 3
        monthly.byType.forEach { item ->
            val row = s5.createRow(r5++)
            row.createCell(0).setCellValue(item.type.code)
            row.createCell(1).setCellValue(item.totalQty.toDouble())
            row.createCell(2).setCellValue(item.avgPrice.toDouble())
            row.createCell(3).setCellValue(item.totalRevenue.toDouble())
        }
        val mTotalRow = s5.createRow(r5++)
        mTotalRow.createCell(0).apply { setCellValue("TOTAL BULANAN"); cellStyle = boldStyle }
        mTotalRow.createCell(1).apply { setCellValue(monthly.totalQty.toDouble()); cellStyle = boldStyle }
        mTotalRow.createCell(3).apply { setCellValue(monthly.totalRevenue.toDouble()); cellStyle = boldStyle }

        r5++
        val h5Worker = s5.createRow(r5++)
        listOf("NAMA PEKERJA / KATEGORI", "TOTAL PRODUKSI", "TOTAL PENDAPATAN").forEachIndexed { i, t ->
            h5Worker.createCell(i).apply { setCellValue(t); cellStyle = headerStyle }
        }
        monthly.byWorker.forEach { w ->
            val row = s5.createRow(r5++)
            row.createCell(0).setCellValue(w.workerName)
            row.createCell(1).setCellValue(w.totalQty.toDouble())
            row.createCell(2).setCellValue(w.totalRevenue.toDouble())
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            workbook.write(out)
        }
        workbook.close()
    }

    suspend fun exportWeeklyCsv(
        context: Context,
        uri: Uri,
        recap: WeeklyRecapSummary
    ) = withContext(Dispatchers.IO) {
        val sb = StringBuilder("\uFEFF")
        val headers = listOf("TYPE") + dayNames + listOf("TOTAL", "HARGA SATUAN", "HARGA TOTAL MINGGUAN")
        sb.append(headers.joinToString(",") { "\"$it\"" }).append("\n")
        for (row in recap.rows) {
            val cols = listOf(row.type.code) +
                row.days.map { it.totalQty.toString() } +
                listOf(
                    row.totalQty.toString(),
                    if (row.isVariedPrice) "HARGA BERVARIASI" else (row.displayPrice ?: 0L).toString(),
                    row.totalRevenue.toString()
                )
            sb.append(cols.joinToString(",") { "\"$it\"" }).append("\n")
        }
        sb.append(
            (listOf("JUMLAH TOTAL") + recap.dailyTotalQty.map { it.toString() } + listOf(recap.grandTotalQty.toString(), "", ""))
                .joinToString(",") { "\"$it\"" }
        ).append("\n")
        sb.append(
            (listOf("HARGA TOTAL HARIAN") + recap.dailyTotalRevenue.map { it.toString() } + listOf("", "TOTAL PENDAPATAN MINGGUAN", recap.grandTotalRevenue.toString()))
                .joinToString(",") { "\"$it\"" }
        ).append("\n")

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun exportPdf(
        context: Context,
        uri: Uri,
        title: String,
        recap: WeeklyRecapSummary,
        workerSummaries: List<WorkerTotalSummary> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595

        val titlePaint = Paint().apply {
            color = Color.rgb(20, 83, 45)
            textSize = 15f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9.5f
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9.5f
            isFakeBoldText = true
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(20, 83, 45)
            style = Paint.Style.FILL
        }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            isFakeBoldText = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        fun drawWeeklyHeader() {
            canvas.drawText("${title.uppercase()} — PEMBUKUAN MINGGUAN", 28f, 34f, titlePaint)
            canvas.drawText(
                "Periode: ${recap.period.label} (${recap.period.startDate} s/d ${recap.period.endDate})",
                28f,
                50f,
                textPaint
            )
            canvas.drawRect(28f, 62f, 814f, 82f, headerBgPaint)
            canvas.drawText("TYPE", 34f, 75f, headerTextPaint)
            dayNames.forEachIndexed { idx, d ->
                canvas.drawText(d.take(3), 130f + idx * 54f, 75f, headerTextPaint)
            }
            canvas.drawText("TOTAL", 512f, 75f, headerTextPaint)
            canvas.drawText("HARGA SATUAN", 576f, 75f, headerTextPaint)
            canvas.drawText("HARGA TOTAL", 696f, 75f, headerTextPaint)
        }

        drawWeeklyHeader()
        var y = 98f

        recap.rows.forEach { row ->
            if (y > 530f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawWeeklyHeader()
                y = 98f
            }
            canvas.drawText(row.type.code, 34f, y, boldPaint)
            row.days.forEachIndexed { idx, d ->
                canvas.drawText(LedgerCalculator.formatNumber(d.totalQty), 130f + idx * 54f, y, textPaint)
            }
            canvas.drawText(LedgerCalculator.formatNumber(row.totalQty), 512f, y, boldPaint)
            val priceStr = if (row.isVariedPrice) "BERVARIASI" else LedgerCalculator.formatRupiah(row.displayPrice ?: 0L)
            canvas.drawText(priceStr, 576f, y, textPaint)
            canvas.drawText(LedgerCalculator.formatRupiah(row.totalRevenue), 696f, y, boldPaint)
            canvas.drawLine(28f, y + 5f, 814f, y + 5f, linePaint)
            y += 19f
        }

        if (y > 515f) {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 48f
        }

        canvas.drawText("JUMLAH TOTAL", 34f, y, boldPaint)
        recap.dailyTotalQty.forEachIndexed { idx, q ->
            canvas.drawText(LedgerCalculator.formatNumber(q), 130f + idx * 54f, y, boldPaint)
        }
        canvas.drawText(LedgerCalculator.formatNumber(recap.grandTotalQty), 512f, y, boldPaint)
        y += 20f

        canvas.drawText("TOTAL HARIAN", 34f, y, boldPaint)
        recap.dailyTotalRevenue.forEachIndexed { idx, rev ->
            canvas.drawText(LedgerCalculator.formatNumber(rev), 130f + idx * 54f, y, boldPaint)
        }
        canvas.drawText("TOTAL MINGGUAN:", 566f, y, boldPaint)
        canvas.drawText(LedgerCalculator.formatRupiah(recap.grandTotalRevenue), 696f, y, titlePaint)
        doc.finishPage(page)

        // Page 2+: Individual Worker Income Summary
        if (workerSummaries.isNotEmpty()) {
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            var wy = 36f
            canvas.drawText("${title.uppercase()} — RINGKASAN PENDAPATAN PERORANGAN", 28f, wy, titlePaint)
            wy += 18f
            canvas.drawText("Periode: ${recap.period.label}", 28f, wy, textPaint)
            wy += 16f
            canvas.drawRect(28f, wy, 814f, wy + 20f, headerBgPaint)
            canvas.drawText("NAMA PEKERJA", 34f, wy + 13f, headerTextPaint)
            canvas.drawText("RINCIAN TYPE", 210f, wy + 13f, headerTextPaint)
            canvas.drawText("TOTAL UNIT", 580f, wy + 13f, headerTextPaint)
            canvas.drawText("TOTAL PENDAPATAN", 690f, wy + 13f, headerTextPaint)
            wy += 34f

            workerSummaries.filter { it.totalQty > 0 }.forEach { ws ->
                if (wy > 540f) {
                    doc.finishPage(page)
                    pageNumber++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    wy = 44f
                }
                val detail = ws.rows.joinToString(" · ") { "${it.type.code}: ${it.totalQty}" }
                canvas.drawText(ws.worker.name, 34f, wy, boldPaint)
                canvas.drawText(detail.take(58), 210f, wy, textPaint)
                canvas.drawText("${LedgerCalculator.formatNumber(ws.totalQty)} unit", 580f, wy, textPaint)
                canvas.drawText(LedgerCalculator.formatRupiah(ws.totalRevenue), 690f, wy, boldPaint)
                canvas.drawLine(28f, wy + 5f, 814f, wy + 5f, linePaint)
                wy += 20f
            }
            doc.finishPage(page)
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            doc.writeTo(out)
        }
        doc.close()
    }

    suspend fun exportMonthlyPdf(
        context: Context,
        uri: Uri,
        title: String,
        monthly: MonthlyRecapSummary
    ) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()) // A4 Portrait
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(20, 83, 45)
            textSize = 15f
            isFakeBoldText = true
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
        }
        val headerBg = Paint().apply { color = Color.rgb(20, 83, 45) }
        val headerText = Paint().apply { color = Color.WHITE; textSize = 9.5f; isFakeBoldText = true }

        var y = 38f
        canvas.drawText("${title.uppercase()} — REKAP BULANAN", 32f, y, titlePaint)
        y += 18f
        canvas.drawText("Bulan: ${LedgerCalculator.formatMonthYear(monthly.monthKey)}", 32f, y, textPaint)
        y += 16f
        canvas.drawText(
            "Total Produksi: ${LedgerCalculator.formatNumber(monthly.totalQty)} Unit   |   Total Pendapatan: ${LedgerCalculator.formatRupiah(monthly.totalRevenue)}",
            32f,
            y,
            boldPaint
        )
        y += 20f

        canvas.drawRect(32f, y, 563f, y + 20f, headerBg)
        canvas.drawText("TYPE BARANG", 38f, y + 14f, headerText)
        canvas.drawText("TOTAL UNIT", 280f, y + 14f, headerText)
        canvas.drawText("TOTAL PENDAPATAN", 420f, y + 14f, headerText)
        y += 34f

        monthly.byType.forEach { item ->
            if (y < 780f) {
                canvas.drawText(item.type.code, 38f, y, boldPaint)
                canvas.drawText(LedgerCalculator.formatNumber(item.totalQty), 280f, y, textPaint)
                canvas.drawText(LedgerCalculator.formatRupiah(item.totalRevenue), 420f, y, boldPaint)
                y += 18f
            }
        }

        y += 18f
        canvas.drawRect(32f, y, 563f, y + 20f, headerBg)
        canvas.drawText("NAMA PEKERJA / KATEGORI", 38f, y + 14f, headerText)
        canvas.drawText("TOTAL UNIT", 280f, y + 14f, headerText)
        canvas.drawText("TOTAL PENDAPATAN", 420f, y + 14f, headerText)
        y += 34f

        monthly.byWorker.forEach { w ->
            if (y < 800f) {
                canvas.drawText(w.workerName, 38f, y, boldPaint)
                canvas.drawText(LedgerCalculator.formatNumber(w.totalQty), 280f, y, textPaint)
                canvas.drawText(LedgerCalculator.formatRupiah(w.totalRevenue), 420f, y, boldPaint)
                y += 18f
            }
        }

        doc.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            doc.writeTo(out)
        }
        doc.close()
    }

    suspend fun exportJsonBackup(context: Context, uri: Uri, payload: BackupPayload) =
        withContext(Dispatchers.IO) {
            val serialized = json.encodeToString(payload)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(serialized.toByteArray(Charsets.UTF_8))
            }
        }

    suspend fun importJsonBackup(context: Context, uri: Uri): BackupPayload =
        withContext(Dispatchers.IO) {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: error("Tidak dapat membaca file backup.")
            json.decodeFromString<BackupPayload>(content)
        }
}
