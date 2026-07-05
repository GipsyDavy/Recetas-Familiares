package org.gipsybuho.recetasfamiliares.core

import kotlin.math.abs
import kotlin.math.round

fun Double.formatOneDecimal(): String {
    val scaled = round(this * 10.0).toLong()
    val decimal = abs(scaled % 10)
    return "${scaled / 10}.$decimal"
}

fun formatTimerMinutesSeconds(minutes: Int, seconds: Int): String =
    "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
