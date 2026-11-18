package com.pace.app.auth

internal fun normalizeEmail(email: String): String? {
    val normalized = email.trim()
    if (normalized.length !in 3..254) return null
    val separator = normalized.lastIndexOf('@')
    if (separator < 1 || separator == normalized.lastIndex || normalized.any(Char::isWhitespace)) return null
    return normalized
}

internal fun isValidNewPassword(password: String): Boolean = password.length >= MINIMUM_PASSWORD_LENGTH

internal const val MINIMUM_PASSWORD_LENGTH = 8
