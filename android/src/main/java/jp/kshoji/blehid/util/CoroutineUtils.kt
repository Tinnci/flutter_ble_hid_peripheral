package jp.kshoji.blehid.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CoroutineUtils {

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Executes a task after a specified delay.
     *
     * @param delayMillis The delay in milliseconds.
     * @param task The function to execute after the delay.
     */
    fun runAfterDelay(delayMillis: Long, task: () -> Unit) {
        scope.launch {
            delay(delayMillis)
            task()
        }
    }
} 