package com.example.utils

import com.example.model.MeasurementUnit
import kotlin.math.roundToInt

object UnitConverter {
    private const val ML_PER_OZ = 29.5735f

    fun mlToOz(ml: Int): Float = ml / ML_PER_OZ

    fun ozToMl(oz: Float): Int = (oz * ML_PER_OZ).roundToInt()

    fun formatVolume(ml: Int, unit: MeasurementUnit): String {
        return when (unit) {
            MeasurementUnit.ML -> "$ml ml"
            MeasurementUnit.OZ -> {
                val oz = mlToOz(ml)
                val rounded = (oz * 10f).roundToInt() / 10f
                val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
                "$text oz"
            }
        }
    }

    fun formatNumericValue(ml: Int, unit: MeasurementUnit): String {
        return when (unit) {
            MeasurementUnit.ML -> ml.toString()
            MeasurementUnit.OZ -> mlToOz(ml).roundToInt().toString()
        }
    }

    fun displayInputToMl(rawValue: Float, unit: MeasurementUnit): Int {
        return when (unit) {
            MeasurementUnit.ML -> rawValue.roundToInt()
            MeasurementUnit.OZ -> ozToMl(rawValue)
        }
    }

    fun quickAddAmountsMl(unit: MeasurementUnit): List<Int> {
        return when (unit) {
            MeasurementUnit.ML -> listOf(100, 150, 250, 500)
            // 4 oz (~118 ml), 6 oz (~177 ml), 8 oz (~237 ml), 16 oz (~473 ml)
            MeasurementUnit.OZ -> listOf(118, 177, 237, 473)
        }
    }

    fun quickAddButtonLabel(amountMl: Int, unit: MeasurementUnit): String {
        return when (unit) {
            MeasurementUnit.ML -> "$amountMl ml"
            MeasurementUnit.OZ -> "${mlToOz(amountMl).roundToInt()} oz"
        }
    }

    fun suggestedDailyGoalsMl(unit: MeasurementUnit): List<Int> {
        return when (unit) {
            MeasurementUnit.ML -> listOf(1500, 2000, 2500, 3000)
            // 50 oz (~1479 ml), 64 oz (~1893 ml), 80 oz (~2366 ml), 100 oz (~2957 ml)
            MeasurementUnit.OZ -> listOf(1479, 1893, 2366, 2957)
        }
    }

    fun minGoalMl(unit: MeasurementUnit): Int = if (unit == MeasurementUnit.ML) 500 else 473
    fun maxGoalMl(unit: MeasurementUnit): Int = if (unit == MeasurementUnit.ML) 6000 else 5915
}
