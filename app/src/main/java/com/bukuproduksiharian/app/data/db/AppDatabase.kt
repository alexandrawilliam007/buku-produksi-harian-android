package com.bukuproduksiharian.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bukuproduksiharian.app.domain.LedgerCalculator
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Instant

@Database(
    entities = [
        ProductTypeEntity::class,
        WorkerEntity::class,
        PriceHistoryEntity::class,
        WeekPeriodEntity::class,
        ProductionTransactionEntity::class,
        WeeklyBookSnapshotEntity::class,
        AppSettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productTypeDao(): ProductTypeDao
    abstract fun workerDao(): WorkerDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun weekPeriodDao(): WeekPeriodDao
    abstract fun transactionDao(): ProductionTransactionDao
    abstract fun snapshotDao(): WeeklyBookSnapshotDao
    abstract fun settingsDao(): AppSettingsDao

    suspend fun ensureSeeded() {
        if (productTypeDao().getAll().isNotEmpty()) return
        val now = Instant.now().toString()
        val monday = LedgerCalculator.mondayOf(LocalDate.now())
        val sunday = monday.plusDays(6)

        withTransaction {
            val types = listOf(
                ProductTypeEntity("type_400ld", "400 LD", "Plastik 400 LD", "Unit", 1, now),
                ProductTypeEntity("type_600hd", "600 HD", "Plastik 600 HD", "Unit", 2, now),
                ProductTypeEntity("type_800hd", "800 HD", "Plastik 800 HD", "Unit", 3, now)
            )
            val prices = listOf(
                PriceHistoryEntity("prc_400ld_init", "type_400ld", 33250L, "2025-01-01", "Harga awal 400 LD", now),
                PriceHistoryEntity("prc_600hd_init", "type_600hd", 68950L, "2025-01-01", "Harga awal 600 HD", now),
                PriceHistoryEntity("prc_800hd_init", "type_800hd", 96950L, "2025-01-01", "Harga awal 800 HD", now)
            )
            val period = WeekPeriodEntity(
                id = "prd_current",
                label = "${LedgerCalculator.formatShortDate(monday)} - ${LedgerCalculator.formatShortDate(sunday)}",
                startDate = monday.toString(),
                endDate = sunday.toString(),
                isLocked = false,
                lockedAt = null,
                createdAt = now
            )
            productTypeDao().upsertAll(types)
            priceHistoryDao().upsertAll(prices)
            weekPeriodDao().upsert(period)
            settingsDao().upsert(AppSettingsEntity(activePeriodId = period.id))
            snapshotDao().upsert(WeeklyBookSnapshotEntity(period.id, 0L, 0L, now))
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE production_transactions ADD COLUMN workerKey TEXT NOT NULL DEFAULT '__UNALLOCATED__'"
                )
                db.execSQL(
                    "ALTER TABLE production_transactions ADD COLUMN workerNameSnapshot TEXT"
                )
                db.execSQL(
                    "UPDATE production_transactions SET workerKey = COALESCE(workerId, '__UNALLOCATED__')"
                )
                db.execSQL(
                    "DROP INDEX IF EXISTS index_production_transactions_periodId_date_typeId_workerId"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_production_transactions_periodId_date_typeId_workerKey ON production_transactions(periodId, date, typeId, workerKey)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "buku_produksi_harian.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: String,
    val productTypes: List<ProductTypeEntity>,
    val workers: List<WorkerEntity>,
    val priceHistory: List<PriceHistoryEntity>,
    val periods: List<WeekPeriodEntity>,
    val transactions: List<ProductionTransactionEntity>,
    val settings: AppSettingsEntity
)
