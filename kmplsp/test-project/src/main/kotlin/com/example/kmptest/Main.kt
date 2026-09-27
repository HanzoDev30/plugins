package com.example.kmptest

import kotlin.system.measureTimeMillis

/**
 * Entry point of the sample - the file to open first.
 *
 * Everything here is intentionally spread across the other files in this package so that
 * go-to-definition, references and rename have somewhere to go.
 */
fun main() {
    val repository: Repository<Task> = InMemoryTaskRepository()

    val seeded = listOf(
        Task(title = "Wire the LSP", priority = Priority.HIGH, tags = listOf("ide", "rust")),
        Task(title = "Write the docs", priority = Priority.LOW),
        Task(title = "Ship v1", priority = Priority.URGENT, tags = listOf("release")),
    )
    seeded.forEach { repository.save(it) }
    repository.save(seeded.first().copy(done = true))

    Log.i("main", repository.count().toString() + " tasks tracked")

    // Named argument + default argument: try signature help inside render(...).
    println(repository.all().asReport())
    println(repository.all().map { it.render(showTags = false, prefix = "! ") })

    // `it` should get a type inlay hint here; Task.render is an extension from Extensions.kt.
    repository.all().forEach { println(it.render()) }

    val overdue: List<String> = repository.all().filter { it.isOverdue() }.map { it.title }
    if (overdue.isNotEmpty()) {
        Log.d("main", "overdue: " + overdue.joinToString())
    }

    val elapsed = measureTimeMillis { repository.highPriorityOrEmpty() }
    Log.d("main", "grouped in " + elapsed + "ms")

    val cache = TaskCache(repository)
    Log.d("main", "cache size = " + cache.size)

    val client = LegacyClient("https://example.invalid/hook")
    client.send(seeded.first(), true)
    client.sendAll(repository.all())

    val mirror: Repository<Task> = ReadOnlyTaskRepository(repository)
    mirror.all().forEach { Log.d("main", it.title.quote()) }

    when (val first = repository.all().firstOrNull()) {
        null -> Log.d("main", "empty")
        else -> Log.d("main", "first = " + LegacyBridge.describe(first))
    }
}

/** Extension used from [main] to test cross-file resolution of a private-ish helper. */
fun Repository<Task>.highPriorityOrEmpty(): TaskList =
    if (this is InMemoryTaskRepository) highPriority() else all()
