package com.example.kmptest

import com.example.kmptest.util.* // star import: titleCase / joinNonBlank come from here

/**
 * One-line rendering of a task.
 *
 * @param showTags appends the tags as `#a,b`; handy when testing named arguments.
 * @param prefix put in front of the whole line, e.g. `"! "`.
 */
fun Task.render(showTags: Boolean = true, prefix: String = ""): String {
    val marker = if (done) "x" else " "
    val tagPart = if (showTags && tags.isNotEmpty()) " #" + tags.joinToString(",") else ""
    return prefix + "[" + marker + "] #" + id + " " + title + " (" + priority.name.lowercase() + ")" + tagPart
}

/** Groups tasks by priority, heaviest first. */
fun TaskList.byPriority(): Map<Priority, List<Task>> =
    groupBy { it.priority }.toSortedMap(compareByDescending { it.weight })

/** Full report; chains [byPriority], [render] and the star-imported [titleCase]. */
fun TaskList.asReport(header: String = "Tasks"): String = buildString {
    appendLine(titleCase(header))
    byPriority().forEach { (priority, tasks) ->
        appendLine("== " + priority.name + " ==")
        tasks.forEach { appendLine("  " + it.render()) }
    }
}

/** Extension on a stdlib type: used to check that stdlib extensions rank after project ones. */
fun String.quote(): String = "\"" + this + "\""
