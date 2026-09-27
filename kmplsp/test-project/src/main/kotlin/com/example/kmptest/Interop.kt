package com.example.kmptest

/**
 * Bridges into the Java sample.
 *
 * The `@JvmStatic` / `@JvmOverloads` annotations are what makes [describe] and [retry] callable
 * from `LegacyClient.java`; jumping from the Java file into these declarations is the Java/Kotlin
 * half of the interop test.
 */
object LegacyBridge {

    @JvmStatic
    fun describe(task: Task, prefix: String = "task"): String = prefix + ": " + task.title

    @JvmOverloads
    fun retry(times: Int = 3, delayMillis: Long = 100): Long = times * delayMillis
}
