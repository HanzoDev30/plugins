package com.example.kmptest.util

/**
 * Uppercases the first character and leaves the rest untouched.
 *
 * Lives in a second package so that `import com.example.kmptest.util.*` (a star import) has
 * something to resolve.
 */
fun String.titleCase(): String = replaceFirstChar { it.uppercaseChar() }

/** Joins non-blank parts with a single space. */
fun List<String>.joinNonBlank(separator: String = " "): String =
    filter { it.isNotBlank() }.joinToString(separator)
