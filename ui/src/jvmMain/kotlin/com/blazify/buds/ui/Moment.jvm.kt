package com.blazify.buds.ui

import java.time.LocalDateTime

actual fun localMoment(): LocalMoment {
    val now = LocalDateTime.now()
    return LocalMoment(now.dayOfWeek.value, now.hour * 60 + now.minute, now.toLocalDate().toString())
}
