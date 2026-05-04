package com.denversc.impri.app

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

data class Label(
    val id: String,
    val text: String,
    val timestamp: Long
)

class LabelRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("labels_history", Context.MODE_PRIVATE)

    fun getLabels(): List<Label> {
        val labels = mutableListOf<Label>()
        prefs.all.forEach { (key, value) ->
            if (value is String) {
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    val timestamp = parts[0].toLongOrNull() ?: 0L
                    labels.add(Label(key, parts[1], timestamp))
                }
            }
        }
        return labels.sortedByDescending { it.timestamp }
    }

    fun saveLabel(text: String) {
        // Prevent saving exactly empty labels
        if (text.isBlank()) return
        
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val valueToSave = "$timestamp|$text"
        
        prefs.edit().putString(id, valueToSave).apply()
    }

    fun deleteLabel(id: String) {
        prefs.edit().remove(id).apply()
    }
}
