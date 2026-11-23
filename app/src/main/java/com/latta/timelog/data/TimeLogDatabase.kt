package com.latta.timelog.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "daily_app_usage", primaryKeys = ["dateKey", "packageName"])
data class DailyAppUsageEntity(
    val dateKey: String,
    val packageName: String,
    val appName: String,
    val usageMillis: Long,
    val launchCount: Int,
    val firstOpenedAt: Long,
    val lastOpenedAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "hourly_usage", primaryKeys = ["hourStart", "packageName"])
data class HourlyUsageEntity(
    val hourStart: Long,
    val packageName: String,
    val appName: String,
    val usageMillis: Long,
    val launchCount: Int
)

@Entity(tableName = "sleep_records", indices = [Index(value = ["sleepStart"], unique = true)])
data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sleepStart: Long,
    val awakenAt: Long,
    val sleepWindowMillis: Long,
    val interruptionCount: Int,
    val confidence: String,
    val createdAt: Long
)

@Dao
interface TimeLogDao {
    @Query("SELECT * FROM daily_app_usage WHERE dateKey = :dateKey ORDER BY usageMillis DESC")
    suspend fun dailyUsage(dateKey: String): List<DailyAppUsageEntity>

    @Query("SELECT * FROM daily_app_usage ORDER BY dateKey DESC, usageMillis DESC")
    suspend fun allUsage(): List<DailyAppUsageEntity>

    @Query("SELECT * FROM hourly_usage WHERE hourStart >= :from AND hourStart < :to ORDER BY hourStart ASC")
    suspend fun hourlyUsage(from: Long, to: Long): List<HourlyUsageEntity>

    @Query("SELECT * FROM hourly_usage ORDER BY hourStart ASC")
    suspend fun allHourlyUsage(): List<HourlyUsageEntity>

    @Query("SELECT * FROM sleep_records ORDER BY sleepStart DESC")
    suspend fun allSleepRecords(): List<SleepRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(items: List<DailyAppUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHourly(items: List<HourlyUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleep(item: SleepRecordEntity): Long

    @Query("DELETE FROM daily_app_usage")
    suspend fun clearDaily()

    @Query("DELETE FROM hourly_usage")
    suspend fun clearHourly()

    @Query("DELETE FROM sleep_records")
    suspend fun clearSleep()

    @Transaction
    suspend fun replaceAll(daily: List<DailyAppUsageEntity>, hourly: List<HourlyUsageEntity>, sleep: List<SleepRecordEntity>) {
        clearDaily()
        clearHourly()
        clearSleep()
        upsertDaily(daily)
        upsertHourly(hourly)
        sleep.forEach { insertSleep(it.copy(id = 0)) }
    }
}

@Database(
    entities = [DailyAppUsageEntity::class, HourlyUsageEntity::class, SleepRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TimeLogDatabase : RoomDatabase() {
    abstract fun dao(): TimeLogDao

    companion object {
        @Volatile private var INSTANCE: TimeLogDatabase? = null
        fun get(context: Context): TimeLogDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeLogDatabase::class.java,
                    "timelog.db"
                ).build().also { INSTANCE = it }
            }
    }
}
