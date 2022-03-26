package io.github.toyota32k.boodroid

import android.content.Context
import androidx.annotation.MainThread
import androidx.work.*
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.SuspendableEvent
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

class KeepAliveWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    companion object {
        val logger = UtLog("KAW", BooApplication.logger)
//        private var alive:Boolean = false
        private val alive = AtomicBoolean(false)
//        private val event = SuspendableEvent(signal = false, autoReset = false)

        private val working = MutableStateFlow(false)

        fun begin(context: Context) {
            if(alive.get()) {
                logger.debug("already running")
                return
            }
            alive.set(true)
            working.value = true
            val workManager = WorkManager.getInstance(context)
            val request = OneTimeWorkRequestBuilder<KeepAliveWorker>().build()
            workManager.enqueueUniqueWork(KeepAliveWorker::class.java.name, ExistingWorkPolicy.KEEP, request)
        }
        fun end() {
            working.value = false
        }
    }
    override fun doWork(): Result {
        logger.debug("background work started.")
        // Do the work here--in this case, upload the images.
        runBlocking {
            working.first { it==false }
        }

        logger.debug("background work completed.")
        alive.set(false)
        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }
}