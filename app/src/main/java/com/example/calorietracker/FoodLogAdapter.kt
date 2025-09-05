package com.example.calorietracker

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.calorietracker.databinding.ItemFoodLogBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FoodLogAdapter : RecyclerView.Adapter<FoodLogAdapter.FoodLogViewHolder>() {

    private var foodLogs = listOf<FoodLog>()
    var onDeleteClick: ((FoodLog) -> Unit)? = null

    fun updateFoodLogs(newFoodLogs: List<FoodLog>) {
        foodLogs = newFoodLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodLogViewHolder {
        val binding = ItemFoodLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FoodLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FoodLogViewHolder, position: Int) {
        holder.bind(foodLogs[position])
    }

    override fun getItemCount() = foodLogs.size

    inner class FoodLogViewHolder(private val binding: ItemFoodLogBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnDelete.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick?.invoke(foodLogs[position])
                }
            }
        }

        fun bind(foodLog: FoodLog) {
            binding.tvFoodName.text = foodLog.foodName
            binding.tvCalories.text = "${foodLog.calories} calories"
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                val date = inputFormat.parse(foodLog.dateTime)
                binding.tvDateTime.text = outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                binding.tvDateTime.text = foodLog.dateTime
            }
            try {
                val imageFile = File(foodLog.imagePath)
                if (imageFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    binding.ivFoodImage.setImageBitmap(bitmap)
                } else {
                    binding.ivFoodImage.setImageResource(R.drawable.ic_launcher_background)
                }
            } catch (e: Exception) {
                binding.ivFoodImage.setImageResource(R.drawable.ic_launcher_background)
            }
        }
    }
}
