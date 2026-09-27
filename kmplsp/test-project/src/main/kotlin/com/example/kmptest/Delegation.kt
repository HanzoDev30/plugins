package com.example.kmptest

import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Caches the first result of an expensive lookup. */
class TaskCache(private val repository: Repository<Task>) {

    /** Computed once, on first read. */
    val size: Int by lazy { repository.all().size }

    /** Fires [onChange] on every write. */
    var lastError: String? by Delegates.observable<String?>(null) { _, _, new ->
        Log.d("cache", "error=$new")
    }

    /** Hand-written delegate, to check that `getValue`/`setValue` resolve. */
    var hits: Int by Counting()
}

/** Minimal custom delegate. */
class Counting : ReadWriteProperty<Any?, Int> {
    private var value = 0

    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        this.value = value
    }
}

/**
 * Interface delegation: every member is forwarded to [store] without repeating it.
 *
 * [findById] is overridden, so this class is a good test for `super` / delegate resolution.
 */
class CachingRepository(private val store: Repository<Task>) : Repository<Task> by store {

    override fun findById(id: Long): Task? =
        store.findById(id)?.also { Log.d("cache", "hit $id") }
}
