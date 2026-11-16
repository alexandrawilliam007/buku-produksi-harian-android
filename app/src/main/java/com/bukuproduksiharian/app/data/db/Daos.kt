package com.bukuproduksiharian.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductTypeDao {
    @Query("SELECT * FROM product_types ORDER BY sortOrder ASC, code ASC")
    fun observeAll(): Flow<List<ProductTypeEntity>>

    @Query("SELECT * FROM product_types ORDER BY sortOrder ASC, code ASC")
    suspend fun getAll(): List<ProductTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProductTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProductTypeEntity>)

    @Delete
    suspend fun delete(item: ProductTypeEntity)

    @Query("DELETE FROM product_types")
    suspend fun deleteAll()
}

@Dao
interface WorkerDao {
    @Query("SELECT * FROM workers ORDER BY name ASC")
    fun observeAll(): Flow<List<WorkerEntity>>

    @Query("SELECT * FROM workers ORDER BY name ASC")
    suspend fun getAll(): List<WorkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WorkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WorkerEntity>)

    @Delete
    suspend fun delete(item: WorkerEntity)

    @Query("DELETE FROM workers")
    suspend fun deleteAll()
}

@Dao
interface PriceHistoryDao {
    @Query("SELECT * FROM price_history ORDER BY effectiveFrom DESC, createdAt DESC")
    fun observeAll(): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history ORDER BY effectiveFrom DESC, createdAt DESC")
    suspend fun getAll(): List<PriceHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PriceHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PriceHistoryEntity>)

    @Delete
    suspend fun delete(item: PriceHistoryEntity)

    @Query("DELETE FROM price_history")
    suspend fun deleteAll()
}

@Dao
interface WeekPeriodDao {
    @Query("SELECT * FROM week_periods ORDER BY startDate DESC")
    fun observeAll(): Flow<List<WeekPeriodEntity>>

    @Query("SELECT * FROM week_periods ORDER BY startDate DESC")
    suspend fun getAll(): List<WeekPeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WeekPeriodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WeekPeriodEntity>)

    @Update
    suspend fun update(item: WeekPeriodEntity)

    @Delete
    suspend fun delete(item: WeekPeriodEntity)

    @Query("DELETE FROM week_periods")
    suspend fun deleteAll()
}

@Dao
interface ProductionTransactionDao {
    @Query("SELECT * FROM production_transactions ORDER BY date ASC")
    fun observeAll(): Flow<List<ProductionTransactionEntity>>

    @Query("SELECT * FROM production_transactions ORDER BY date ASC")
    suspend fun getAll(): List<ProductionTransactionEntity>

    @Query(
        "SELECT * FROM production_transactions WHERE periodId = :periodId AND date = :date AND typeId = :typeId AND workerKey = :workerKey LIMIT 1"
    )
    suspend fun findCell(
        periodId: String,
        date: String,
        typeId: String,
        workerKey: String
    ): ProductionTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProductionTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProductionTransactionEntity>)

    @Delete
    suspend fun delete(item: ProductionTransactionEntity)

    @Query("DELETE FROM production_transactions WHERE workerId = :workerId AND periodId NOT IN (SELECT id FROM week_periods WHERE isLocked = 1)")
    suspend fun deleteUnlockedByWorker(workerId: String)

    @Query("DELETE FROM production_transactions WHERE workerId = :workerId")
    suspend fun deleteByWorker(workerId: String)

    @Query("DELETE FROM production_transactions WHERE periodId = :periodId")
    suspend fun deleteByPeriod(periodId: String)

    @Query("DELETE FROM production_transactions")
    suspend fun deleteAll()
}

@Dao
interface WeeklyBookSnapshotDao {
    @Query("SELECT * FROM weekly_book_snapshots")
    fun observeAll(): Flow<List<WeeklyBookSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WeeklyBookSnapshotEntity)

    @Query("DELETE FROM weekly_book_snapshots WHERE periodId = :periodId")
    suspend fun deleteByPeriod(periodId: String)

    @Query("DELETE FROM weekly_book_snapshots")
    suspend fun deleteAll()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettingsEntity)
}
