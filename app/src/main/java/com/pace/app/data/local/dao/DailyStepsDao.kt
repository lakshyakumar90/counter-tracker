package com.pace.app.data.local.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_steps")
data class DailySteps(
    @PrimaryKey val day: String,
    val count: Long,
    val sensorCount: Long,
    val bootCount: Int,
    val updatedAt: Long = 0,
)

@Dao
interface DailyStepsDao {
    @Query("SELECT * FROM daily_steps WHERE day >= :from AND day <= :through ORDER BY day")
    fun observeRange(from: String, through: String): Flow<List<DailySteps>>

    @Query("SELECT * FROM daily_steps ORDER BY updatedAt DESC, day DESC LIMIT 1")
    suspend fun current(): DailySteps?

    @Query("SELECT * FROM daily_steps WHERE day = :day")
    suspend fun forDay(day: String): DailySteps?

    @Upsert
    suspend fun save(value: DailySteps)
}
