package com.example.calorietracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_logs")
data class FoodLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val foodName: String,
    val calories: Int,
    val protein: Int = 0,
    val imagePath: String,
    val dateTime: String,
    val analysisDetails: String = ""
)
