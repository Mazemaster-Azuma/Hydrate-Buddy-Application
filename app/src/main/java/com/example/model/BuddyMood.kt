package com.example.model

enum class BuddyMood(val label: String) {
    HAPPY("Happy"),
    EXCITED("Excited"),
    NEUTRAL("Waiting"),
    THIRSTY("Thirsty"),
    SAD("Sad"),
    CELEBRATING("Celebrating")
}

enum class DrinkType(val label: String, val shortTag: String) {
    WATER("Pure Water", "Water"),
    SPARKLING("Sparkling", "Sparkling"),
    HERBAL_TEA("Herbal Tea", "Tea"),
    INFUSED("Fruit Infused", "Infused"),
    ELECTROLYTE("Electrolyte", "Electrolyte");

    companion object {
        fun fromName(name: String): DrinkType {
            return entries.firstOrNull { it.name == name } ?: WATER
        }
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromName(name: String): ThemeMode {
            return entries.firstOrNull { it.name == name } ?: SYSTEM
        }
    }
}
