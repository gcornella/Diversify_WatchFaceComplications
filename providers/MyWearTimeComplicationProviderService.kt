package com.yourprojectid

import android.content.ComponentName
import android.util.Log
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// Your storage accessor. DataStorageManager is not provided but this is just an example setting data from a Room database
import com.yourprojectid.DataStorageManager

class MyWearTimeComplicationProviderService : SuspendingComplicationDataSourceService() {

    /** Called when the complication is activated on the watch face (useful for logging/setup). */
    override fun onComplicationActivated(id: Int, type: ComplicationType) {
        Log.d(TAG, "onComplicationActivated(id=$id, type=$type)")
    }

    /** Provides preview data for editors; shows a 2h30' sample wear-time ring. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) return null
        val previewValue = 150f // 2h30' preview
        return buildRanged(previewValue, GOAL_MINUTES, formatHm(previewValue.toInt()))
    }

    /**
     * Handles live requests for RANGED_VALUE data.
     * Reads today's worn minutes, clamps to goal range, formats label, and returns the ring.
     */
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null

        val wornMins = withContext(Dispatchers.IO) { readTodayWornMinutes() }
        val clamped = min(max(wornMins.toFloat(), 0f), GOAL_MINUTES)
        val label = formatHm(wornMins)

        return buildRanged(clamped, GOAL_MINUTES, label)
    }

    /** Called when the complication instance is deactivated/removed (good for cleanup/logging). */
    override fun onComplicationDeactivated(id: Int) {
        Log.d(TAG, "onComplicationDeactivated(id=$id)")
    }

    // ————— helpers —————

    /** Builds a ranged-value complication ring with the given value/max and a readable label. */
    private fun buildRanged(value: Float, max: Float, label: String): RangedValueComplicationData {
        return RangedValueComplicationData.Builder(
            value = value,
            min = 0f,
            max = max,
            contentDescription = PlainComplicationText.Builder("Watch worn $label today").build()
        )
            // This is the text you'll read in the watch face via [COMPLICATION.TEXT]
            .setText(PlainComplicationText.Builder(label).build())
            .build()
    }

    companion object {
        private const val TAG = "WearTimeComplication"
        private const val GOAL_MINUTES = 840f // 14h goal (change to what you want)

        /** Formats minutes as "HhMM'" (e.g., 70 -> "1h10'") for display in the complication. */
        private fun formatHm(mins: Int): String {
            val h = mins / 60
            val m = mins % 60
            return if (m < 10) "${h}h0${m}'" else "${h}h${m}'"
        }

        /**
         * Reads today's wornMinutes from the database using your manager + DAO.
         * Returns 0 if DB is unavailable or the read fails.
         */
        private suspend fun readTodayWornMinutes(): Int {
            val db = DataStorageManager.getMainDatabase()
            if (db == null) {
                Log.w(TAG, "Main database is null; returning 0")
                return 0
            }
            val dayKey = DataStorageManager.getDayForDB() // your existing helper (e.g., "yyyy-MM-dd")
            return try {
                // Expect: DailyWearTimeDao.getLastEntryForDay(String): DailyWearTimeEntity?
                db.dailyWearTimeDao().getLastEntryForDay(dayKey)?.wornMinutes ?: 0
            } catch (t: Throwable) {
                Log.e(TAG, "DAO read failed", t)
                0
            }
        }

        /** Requests an immediate refresh of all instances of this complication data source. */
        @JvmStatic
        fun requestComplicationUpdate(ctx: android.content.Context) {
            ComplicationDataSourceUpdateRequester
                .create(ctx, ComponentName(ctx, MyWearTimeComplicationProviderService::class.java))
                .requestUpdateAll()
        }
    }
}
