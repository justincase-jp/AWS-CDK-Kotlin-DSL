package utility

import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers.Unconfined
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
