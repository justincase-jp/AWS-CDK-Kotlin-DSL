
import kotlinx.coroutines.runBlocking
import org.gradle.api.Task

fun Task.doLastBlocking(action: suspend () -> Unit) {
    doLast {
        runBlocking { action() }
    }
}
