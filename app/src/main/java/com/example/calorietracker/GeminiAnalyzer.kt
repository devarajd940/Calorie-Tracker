package com.example.calorietracker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import java.io.File

data class FoodAnalysisResult(
    val foodName: String,
    val calories: Int,
    val protein: Int,
    val details: String
)

class GeminiAnalyzer {

    private val apiKey = "enter your Gemini key here"

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun analyzeFood(imagePath: String): FoodAnalysisResult {
        try {
            val imageFile = File(imagePath)
            val bitmap: Bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

            val prompt = """
                Look at this food image and tell me:
                1. What food items do you see?
                2. Estimate the total calories
                3. Estimate the total protein in grams
                Respond in this exact format:
                Food: [name of the food]
                Calories: [number only]
                Protein: [number only]
                Details: [brief description]
                For example:
                Food: Grilled chicken salad
                Calories: 350
                Protein: 30
                Details: Mixed greens with grilled chicken breast, tomatoes, and light dressing
            """.trimIndent()

            val inputContent = content {
                image(bitmap)
                text(prompt)
            }

            val response = generativeModel.generateContent(inputContent)
            val responseText = response.text ?: throw Exception("No response from AI")
            return parseResponse(responseText)
        } catch (e: Exception) {
            throw Exception("Failed to analyze image: ${e.message}")
        }
    }

    private fun parseResponse(response: String): FoodAnalysisResult {
        try {
            val lines = response.split("\n")
            var foodName = "Unknown Food"
            var calories = 0
            var protein = 0
            var details = "Analysis completed"
            for (line in lines) {
                when {
                    line.startsWith("Food:", ignoreCase = true) -> {
                        foodName = line.substringAfter(":").trim()
                    }
                    line.startsWith("Calories:", ignoreCase = true) -> {
                        val calorieStr = line.substringAfter(":").trim().replace("[^0-9]".toRegex(), "")
                        calories = calorieStr.toIntOrNull() ?: 0
                    }
                    line.startsWith("Protein:", ignoreCase = true) -> {
                        val proteinStr = line.substringAfter(":").trim().replace("[^0-9]".toRegex(), "")
                        protein = proteinStr.toIntOrNull() ?: 0
                    }
                    line.startsWith("Details:", ignoreCase = true) -> {
                        details = line.substringAfter(":").trim()
                    }
                }
            }
            return FoodAnalysisResult(foodName, calories, protein, details)
        } catch (e: Exception) {
            return FoodAnalysisResult("Unknown Food", 300, 0, "Could not parse AI response")
        }
    }
}
