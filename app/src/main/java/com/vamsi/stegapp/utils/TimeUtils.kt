package com.vamsi.stegapp.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    private val timeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    private val dateTimeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("dd MMM • h:mm a", Locale.getDefault())
    }

    fun formatTime(timestamp: Long): String {
        return timeFormatter.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormatter.get()?.format(Date(timestamp)) ?: ""
    }
}
