package utility

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry

@UseExperimental(ExperimentalCoroutinesApi::class)
suspend fun <T> withRetry(attempts: Long = 30, backoff: Long = 3000, block: suspend () -> T): T =
    flow { emit(block()) }
        .retry(attempts) {
            it.printStackTrace()
            delay(backoff)
            println("Retrying...")
            true
        }
        .first()
