package com.example.kmptest

/**
 * Storage abstraction for the sample.
 *
 * [count] has a default implementation on purpose: it is inherited, not overridden, which makes
 * it a good test for "go to definition on an inherited member" - the resolution chain has to walk
 * the supertype hierarchy to find it.
 */
interface Repository<T : Any> {
    fun all(): List<T>

    fun findById(id: Long): T?

    fun save(item: T): T

    fun delete(id: Long): Boolean

    /** Inherited default - overridden nowhere. */
    fun count(): Int = all().size
}

/** In-memory [Repository] used by the sample. */
class InMemoryTaskRepository : Repository<Task> {

    private val items = linkedMapOf<Long, Task>()
    private var nextId = 1L

    override fun all(): List<Task> = items.values.toList()

    override fun findById(id: Long): Task? = items[id]

    override fun save(item: Task): Task {
        val stored = if (item.id == 0L) item.copy(id = nextId++) else item
        items[stored.id] = stored
        Log.d("repo", "saved ${stored.id}")
        return stored
    }

    override fun delete(id: Long): Boolean = items.remove(id) != null

    /** Everything at least as urgent as [Priority.HIGH]. */
    fun highPriority(): TaskList = all().filter { it.priority.weight >= Priority.HIGH.weight }
}

/** Second implementation, so "go to implementations" has more than one hit. */
class ReadOnlyTaskRepository(private val delegate: Repository<Task>) : Repository<Task> {

    override fun all(): List<Task> = delegate.all()

    override fun findById(id: Long): Task? = delegate.findById(id)

    override fun save(item: Task): Task = throw UnsupportedOperationException("read-only")

    override fun delete(id: Long): Boolean = false
}

open class Hsi{
    const val USER = 1000
    fun obb() : Int{
    	return 0 ;
    }
}