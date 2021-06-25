package com.xclydes.finance.longboard.util

import java.text.DateFormat
import java.text.SimpleDateFormat

class DatesUtil {

    companion object {
        val dateFormatHuman: DateFormat by lazyOf( SimpleDateFormat("MMM dd, yyyy"))
        val dateFormatSQL: DateFormat by lazyOf(SimpleDateFormat("yyyy-MM-dd"))
        val dateFormatReport: DateFormat by lazyOf(SimpleDateFormat("yyyyMMdd"))
        val dateFormatDescription: DateFormat by lazyOf(SimpleDateFormat("MM/dd/yyyy"))

    }
}
