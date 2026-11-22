package com.blazify.buds.core

/** Two hex digits for a byte, as "%02x" writes them; the iPhone build has no String.format. */
fun Byte.hex2(): String = (toInt() and 0xFF).toString(16).padStart(2, '0')

/** A number in hex, at least two digits, as "%02x" writes it. */
fun Int.hex2(): String = toString(16).padStart(2, '0')
