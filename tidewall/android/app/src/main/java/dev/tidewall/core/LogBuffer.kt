package dev.tidewall.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LogLine(val id: Long, val time: Long, val level: String, val message: String)

/** In-memory ring buffer of engine logs shown on the Logs screen. */
object LogBuffer {
    private const val CAPACITY = 1500
    private var nextId = 0L
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    @Synchronized
    fun add(level: String, message: String) {
        val line = LogLine(nextId++, System.currentTimeMillis(), level.lowercase(), message.trimEnd())
        _lines.update { old -> if (old.size >= CAPACITY) old.drop(old.size - CAPACITY + 1) + line else old + line }
    }

    fun clear() = _lines.update { emptyList() }
}
