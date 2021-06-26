package com.xclydes.finance.longboard.util

import java.time.format.DateTimeFormatter

class DatesUtil {

    companion object {
        val dateFormatHuman: DateTimeFormatter by lazyOf(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        val dateFormatSQL: DateTimeFormatter by lazyOf(DateTimeFormatter.ISO_DATE)
        val dateFormatReport: DateTimeFormatter by lazyOf(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val dateFormatDescription: DateTimeFormatter by lazyOf(DateTimeFormatter.ofPattern("MM/dd/yyyy"))

    }
}
