package com.example.kmptest

/**
 * How urgent a task is.
 *
 * The [weight] is what the sample UI sorts by, so it is deliberately not the declaration order.
 */
enum class Priority(val weight: Int) {
    LOW(1),
    NORMAL(5),
    HIGH(10),
    URGENT(20),
}

/**
 * A single unit of work tracked by a [Repository].
 *
 * @param id zero means "not stored yet"; the repository assigns a real id on the first save.
 */
data class Task(
    val id: Long,
    val title: String,
    val priority: Priority = Priority.NORMAL,
    val tags: List<String> = emptyList(),
    val done: Boolean = false,
)

/** Outcome of a [Repository] operation. */
sealed interface TaskResult {
    data class Success(val task: Task) : TaskResult

    data class Failure(val reason: String) : TaskResult
}

/** Read-only view over a batch of tasks, used all over the sample. */
typealias TaskList = List<Task>

/** Stateless logging helpers. */
object Log {
    fun d(tag: String, message: String) {
        println("[$tag] $message")
    }

    fun i(tag: String, message: String) = d("I/$tag", message)
}

/** An urgent task that is still open. */
fun Task.isOverdue(): Boolean = priority == Priority.URGENT && !done
