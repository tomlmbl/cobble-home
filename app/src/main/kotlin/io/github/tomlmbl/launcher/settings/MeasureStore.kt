package io.github.tomlmbl.launcher.settings

import android.content.Context
import android.util.AtomicFile
import io.github.tomlmbl.launcher.context.MeasureCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Counts of the correction measure ([Measure]) in a separate private file, readable through `run-as`: no network
 * or app name, nothing leaves the device. Serial writes, off the main thread.
 */
class MeasureStore(context: Context, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var counts: MeasureCounts? = null

    fun update(transform: (MeasureCounts, day: Long) -> MeasureCounts) {
        scope.launch(io) {
            val current = counts ?: runCatching { MeasureCounts.decode(file.readFully().decodeToString()) }.getOrDefault(MeasureCounts())
            val next = transform(current, LocalDate.now().toEpochDay())
            counts = next
            val out = file.startWrite()
            runCatching { out.write(next.encode().encodeToByteArray()); file.finishWrite(out) }.onFailure { file.failWrite(out) }
        }
    }

    private companion object {
        const val FILE_NAME = "measure.txt"
    }
}
