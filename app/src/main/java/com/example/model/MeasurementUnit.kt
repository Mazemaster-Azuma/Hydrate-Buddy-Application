package com.example.model

enum class MeasurementUnit(val suffix: String, val fullName: String) {
    ML("ml", "Milliliters (ml)"),
    OZ("oz", "Fluid Ounces (oz)");

    companion object {
        fun fromName(name: String): MeasurementUnit {
            return entries.firstOrNull { it.name == name } ?: ML
        }
    }
}
