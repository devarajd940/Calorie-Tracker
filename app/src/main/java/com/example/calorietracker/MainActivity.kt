package com.example.calorietracker

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calorietracker.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FoodLogAdapter
    private lateinit var database: AppDatabase
    private var currentSelectedDate: Calendar = Calendar.getInstance()
    private val geminiAnalyzer = GeminiAnalyzer()

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                saveBitmapAndAnalyze(bitmap)
            } else {
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraIntent()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            val savedTime = savedInstanceState.getLong("selectedDate", -1L)
            if (savedTime != -1L) {
                currentSelectedDate.timeInMillis = savedTime
            }
        }

        database = AppDatabase.getDatabase(this)
        adapter = FoodLogAdapter()

        binding.recyclerViewFoodLogs.adapter = adapter
        binding.recyclerViewFoodLogs.layoutManager = LinearLayoutManager(this)

        val databaseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        updateDateField()
        loadFoodLogsForDate(databaseDateFormat.format(currentSelectedDate.time))

        binding.tfSelectedDate.setOnClickListener {
            val year = currentSelectedDate.get(Calendar.YEAR)
            val month = currentSelectedDate.get(Calendar.MONTH)
            val day = currentSelectedDate.get(Calendar.DAY_OF_MONTH)

            val dialog = DatePickerDialog(
                this,
                { _, y, m, d ->
                    currentSelectedDate.set(y, m, d)
                    updateDateField()
                    loadFoodLogsForDate(databaseDateFormat.format(currentSelectedDate.time))
                },
                year,
                month,
                day
            )
            dialog.show()
        }

        binding.btnAddFood.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                launchCameraIntent()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.fabAddCustomEntry.setOnClickListener {
            showAddCustomEntryDialog()
        }

        adapter.onDeleteClick = { foodLog ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Entry")
                .setMessage("Are you sure you want to delete this entry?")
                .setPositiveButton("Yes") { _, _ ->
                    lifecycleScope.launch {
                        database.foodLogDao().deleteFoodLog(foodLog)
                        loadFoodLogsForDate(databaseDateFormat.format(currentSelectedDate.time))
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("selectedDate", currentSelectedDate.timeInMillis)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val savedTime = savedInstanceState.getLong("selectedDate", -1L)
        if (savedTime != -1L) {
            currentSelectedDate.timeInMillis = savedTime
        }
    }

    override fun onResume() {
        super.onResume()
        val databaseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        loadFoodLogsForDate(databaseDateFormat.format(currentSelectedDate.time))
    }

    private fun launchCameraIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun saveBitmapAndAnalyze(bitmap: Bitmap) {
        runOnUiThread { binding.progressBarLoading.visibility = View.VISIBLE }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(filesDir, "food_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                }
                val imagePath = file.absolutePath

                val result = geminiAnalyzer.analyzeFood(imagePath)
                saveFoodLog(result, imagePath)

                withContext(Dispatchers.Main) {
                    binding.progressBarLoading.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "Found: ${result.foodName} (${result.calories} cal, ${result.protein} g protein)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarLoading.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to analyze image: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveFoodLog(result: FoodAnalysisResult, imagePath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val datePart = dateFormat.format(currentSelectedDate.time)
            val timePart = timeFormat.format(Date())
            val dateTimeString = "$datePart $timePart"

            val foodLog = FoodLog(
                id = 0,
                foodName = result.foodName,
                calories = result.calories,
                protein = result.protein,
                imagePath = imagePath,
                dateTime = dateTimeString,
                analysisDetails = result.details
            )
            database.foodLogDao().insertFoodLog(foodLog)
        }
    }

    private fun showAddCustomEntryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_entry, null)
        val etMealName = dialogView.findViewById<android.widget.EditText>(R.id.etMealName)
        val etCalories = dialogView.findViewById<android.widget.EditText>(R.id.etCalories)
        val etProtein = dialogView.findViewById<android.widget.EditText>(R.id.etProtein)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Custom Meal")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etMealName.text.toString().trim()
                val calories = etCalories.text.toString().trim().toIntOrNull()
                val protein = etProtein.text.toString().trim().toIntOrNull()

                if (name.isEmpty() || calories == null || calories <= 0 || protein == null || protein < 0) {
                    Toast.makeText(this, "Please enter valid data", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                saveCustomMeal(name, calories, protein)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveCustomMeal(name: String, calories: Int, protein: Int) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val datePart = dateFormat.format(currentSelectedDate.time)
        val timePart = timeFormat.format(Date())
        val dateTimeString = "$datePart $timePart"

        val foodLog = FoodLog(
            id = 0,
            foodName = name,
            calories = calories,
            protein = protein,
            imagePath = "",
            dateTime = dateTimeString,
            analysisDetails = ""
        )
        lifecycleScope.launch {
            database.foodLogDao().insertFoodLog(foodLog)
            loadFoodLogsForDate(dateFormat.format(currentSelectedDate.time))
            Toast.makeText(this@MainActivity, "Custom meal added", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDateField() {
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        binding.tfSelectedDate.setText(dateFormat.format(currentSelectedDate.time))
    }

    private fun loadFoodLogsForDate(date: String) {
        lifecycleScope.launch {
            database.foodLogDao().getFoodLogsByDate(date).collect { logs ->
                adapter.updateFoodLogs(logs)
                val totalCalories = logs.sumOf { log -> log.calories }
                val totalProtein = logs.sumOf { log -> log.protein }
                binding.tvTodaysCalories.text = "$totalCalories cal"
                binding.tvTodaysProtein.text = "$totalProtein g protein"
            }
        }
    }
}
