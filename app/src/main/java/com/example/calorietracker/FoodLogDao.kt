package com.example.calorietracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_logs ORDER BY dateTime DESC")
    fun getAllFoodLogs(): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_logs WHERE DATE(dateTime) = :date ORDER BY dateTime DESC")
    fun getFoodLogsByDate(date: String): Flow<List<FoodLog>>

    @Insert
    suspend fun insertFoodLog(foodLog: FoodLog): Long

    @Delete
    suspend fun deleteFoodLog(foodLog: FoodLog)

    @Query("SELECT SUM(calories) FROM food_logs WHERE DATE(dateTime) = :date")
    suspend fun getCaloriesByDate(date: String): Int?
}
