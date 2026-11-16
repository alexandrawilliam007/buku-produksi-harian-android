package com.bukuproduksiharian.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "product_types")
data class ProductTypeEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val unitLabel: String = "Unit",
    val sortOrder: Int = 0,
    val createdAt: String
)

@Serializable
@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String = "",
    val isActive: Boolean = true,
    val createdAt: String
)

@Serializable
@Entity(
    tableName = "price_history",
    foreignKeys = [
        ForeignKey(
            entity = ProductTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("typeId"), Index("effectiveFrom")]
)
data class PriceHistoryEntity(
    @PrimaryKey val id: String,
    val typeId: String,
    val price: Long, // Integer Rupiah >= 0
    val effectiveFrom: String, // YYYY-MM-DD
    val note: String = "",
    val createdAt: String
)

@Serializable
@Entity(tableName = "week_periods", indices = [Index(value = ["startDate"], unique = true)])
data class WeekPeriodEntity(
    @PrimaryKey val id: String,
    val label: String,
    val startDate: String, // Monday YYYY-MM-DD
    val endDate: String, // Sunday YYYY-MM-DD
    val isLocked: Boolean = false,
    val lockedAt: String? = null,
    val createdAt: String
)

@Serializable
@Entity(
    tableName = "production_transactions",
    foreignKeys = [
        ForeignKey(
            entity = WeekPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["periodId", "date", "typeId", "workerKey"], unique = true),
        Index("date"),
        Index("workerId")
    ]
)
data class ProductionTransactionEntity(
    @PrimaryKey val id: String,
    val periodId: String,
    val date: String, // YYYY-MM-DD
    val typeId: String,
    val workerId: String?, // null = Belum Dialokasikan
    val workerKey: String = workerId ?: "__UNALLOCATED__",
    val workerNameSnapshot: String? = null,
    val quantity: Long, // >= 0
    val unitPriceSnapshot: Long, // Harga satuan yang berlaku saat transaksi
    val priceHistoryId: String?,
    val updatedAt: String
)

@Serializable
@Entity(tableName = "weekly_book_snapshots")
data class WeeklyBookSnapshotEntity(
    @PrimaryKey val periodId: String,
    val totalQuantity: Long,
    val totalRevenue: Long,
    val updatedAt: String
)

@Serializable
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val businessName: String = "Buku Produksi Harian",
    val ownerName: String = "Admin Produksi",
    val defaultUnitLabel: String = "Unit",
    val confirmBeforeUnlock: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val activePeriodId: String = "prd_current"
)
