package com.example.calorietracker

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
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

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCameraIntent()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        adapter = FoodLogAdapter()
        binding.recyclerViewFoodLogs.adapter = adapter
        binding.recyclerViewFoodLogs.layoutManager = LinearLayoutManager(this)

        updateDateField()
        val databaseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        loadFoodLogsForDate(databaseDateFormat.format(currentSelectedDate.time))

        binding.tfSelectedDate.setOnClickListener {
            val year = currentSelectedDate.get(Calendar.YEAR)
            val month = currentSelectedDate.get(Calendar.MONTH)
            val day = currentSelectedDate.get(Calendar.DAY_OF_MONTH)

            val dialog = DatePickerDialog(this, { _, y, m, d ->
                currentSelectedDate.set(y, m, d)
                updateDateField()
                loadFoodLogsForDate(databaseDateFormat.format(currentSelectedDate.time))
            }, year, month, day)

            dialog.show()
        }

        binding.btnAddFood.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCameraIntent()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
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

    private fun launchCameraIntent() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(cameraIntent)
    }

    private fun saveBitmapAndAnalyze(bitmap: Bitmap) {
        runOnUiThread { binding.progressBarLoading.visibility = android.view.View.VISIBLE }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(filesDir, "food_${System.currentTimeMillis()}.jpg")
                val fos = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                fos.flush()
                fos.close()

                val imagePath = file.absolutePath
                val result = geminiAnalyzer.analyzeFood(imagePath)
                saveFoodLog(result, imagePath)

                withContext(Dispatchers.Main) {
                    binding.progressBarLoading.visibility = android.view.View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "Found: ${result.foodName} (${result.calories} cal)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarLoading.visibility = android.view.View.GONE
                    Toast.makeText(this@MainActivity, "Failed to analyze image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveFoodLog(result: FoodAnalysisResult, imagePath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val foodLog = FoodLog(
                foodName = result.foodName,
                calories = result.calories,
                imagePath = imagePath,
                dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                analysisDetails = result.details
            )
            database.foodLogDao().insertFoodLog(foodLog)
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
                val totalCalories = logs.sumOf { it.calories }
                binding.tvTodaysCalories.text = "$totalCalories cal"
            }
        }
    }
}
