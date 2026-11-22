package com.blazify.buds.ui

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

actual fun localMoment(): LocalMoment {
    val parts = NSCalendar.currentCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitWeekday or NSCalendarUnitHour or NSCalendarUnitMinute,
        fromDate = NSDate(),
    )
    // The iPhone counts Sunday as 1 and Saturday as 7; the rules count Monday as 1 and Sunday as 7.
    val weekday = parts.weekday.toInt()
    val day = if (weekday == 1) 7 else weekday - 1
    val date = "${parts.year}-${parts.month.toString().padStart(2, '0')}-${parts.day.toString().padStart(2, '0')}"
    return LocalMoment(day, parts.hour.toInt() * 60 + parts.minute.toInt(), date)
}
