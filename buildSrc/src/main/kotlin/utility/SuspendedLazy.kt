package utility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class SuspendedLazy<T> private constructor(
    private val value: AtomicReference<Deferred<T>>
) {
    constructor(initializer: suspend CoroutineScope.() -> T) : this(kotlin.run {
        val reference = AtomicReference<Deferred<T>>()
        suspend fun initialize(scope: CoroutineScope): T =
            try {
                scope.initializer()
            } catch (t: Throwable) {
                // Avoid caching failures
                reference.set(GlobalScope.async(Unconfined, LAZY, ::initialize))
                throw t
            }

        reference.set(GlobalScope.async(Unconfined, LAZY, ::initialize))
        reference
    })

    suspend operator fun invoke() =
        value.get().await()
}


fun <K, V> cache(valueFunction: suspend CoroutineScope.(K) -> V): suspend (K) -> V =
    ConcurrentHashMap<K, SuspendedLazy<V>>().let { map ->
        { k -> map.computeIfAbsent(k) { SuspendedLazy { valueFunction(it) } }() }
    }
