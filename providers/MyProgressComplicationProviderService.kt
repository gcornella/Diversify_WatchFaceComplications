package com.yourprojectid

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

// Your storage accessor (you'll have to change these values as the code for DataStorageManager is not provided here)
// This code is just an example of how to get values from a Room database
import com.yourprojectid.DataStorageManager
import com.yourprojectid.PrefsKeys

class MyProgressComplicationProviderService : SuspendingComplicationDataSourceService() {

    /** Called when the complication instance is activated on the watch face (useful for logging/setup). */
    override fun onComplicationActivated(id: Int, type: ComplicationType) {
        Log.d(TAG, "onComplicationActivated(id=$id, type=$type)")
    }

    /** Provides preview data for watch face editors; shows a 10-minute sample progress ring. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) return null
        val previewValue = 10f // 10' preview
        val previewMax   = DEFAULT_GOAL_MINUTES.toFloat()
        return buildRanged(previewValue, previewMax, formatHm(previewValue.toInt()))
    }

    /**
     * Handles live complication requests.
     * Reads week ID to optionally hide, then loads today's progress/goal and returns a ranged ring.
     */
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null

        val prefsDataStorage = applicationContext.getSharedPreferences(PrefsKeys.Data.PREFS, Context.MODE_PRIVATE)
        val weekId = prefsDataStorage.getInt(PrefsKeys.Data.WEEK_ID, 1)

        // TODO - 2nd STUDY (comment next section to always show progress circle)
        // Hide the complication during the first and 6+ weeks
        if (weekId==1 || weekId >= 6) {
            return NoDataComplicationData()
        }

        val today = loadTodayProgressAndGoal()
        val g = max(today.goalMinutes, 1)          // max >= 1
        val p = today.progressMinutes.coerceAtLeast(0)
        val label = if (p <= g) formatHm(p) else "+${p - g}'"  // truthful label past goal
        Log.d(TAG, "onComplicationActivated(g=$g, p=$p, label=$label)")

        val value = p.coerceAtMost(g).toFloat()            // <-- cap for the ring
        val maxV  = g.toFloat()

        Log.d(TAG, "buildRanged(value=$value, maxV=$maxV")

        return buildRanged(value, maxV, label)
    }


    companion object {
        private const val TAG = "ProgressProviderComplication"
        private const val DEFAULT_GOAL_MINUTES = 840 // 14h fallback

        /** Formats minutes as "HhMM'" (e.g., 70 -> "1h10'") for display in the complication. */
        private fun formatHm(mins: Int): String {
            val h = mins / 60
            val m = mins % 60
            return if (m < 10) "${h}h0${m}'" else "${h}h${m}'"
        }

        /**
         * Loads today's cumulative progress and goal from the databases on an IO thread.
         * Falls back to defaults if data isn't available.
         */
        private suspend fun loadTodayProgressAndGoal(): TodaySnapshot =
            withContext(Dispatchers.IO) {
                val dayKey  = DataStorageManager.getDayForDB()
                val mainDb  = DataStorageManager.getMainDatabase()
                val dailyDb = DataStorageManager.getDailyDatabase() // your accessor

                val progress: Int = try {
                    mainDb?.dailyCumulativeDao()?.getLastEntryForDay(dayKey)?.cumulative ?: 0
                } catch (_: Throwable) { 0 }

                val goal: Int = try {
                    dailyDb
                        ?.adjustedDailyGoalDao()
                        ?.getLastGoal()
                        ?.adjustedDailyGoal
                        ?.takeIf { it > 0 }           // or { it != 0 } if you only want to treat 0 as invalid
                        ?: DEFAULT_GOAL_MINUTES
                } catch (_: Throwable) {
                    DEFAULT_GOAL_MINUTES
                }
                Log.w(TAG, "Updating complication to: "+ progress + "; out of: "+ goal)
                TodaySnapshot(progress, goal)
            }

        private data class TodaySnapshot(
            val progressMinutes: Int,
            val goalMinutes: Int
        )

        /** Requests an immediate refresh of all instances of this complication data source. */
        @JvmStatic
        fun requestComplicationUpdate(ctx: android.content.Context) {
            ComplicationDataSourceUpdateRequester
                .create(ctx, ComponentName(ctx, MyProgressComplicationProviderService::class.java))
                .requestUpdateAll()
        }
    }

    /** Builds a RangedValueComplicationData ring with text/label used by the watch face. */
    private fun buildRanged(value: Float, max: Float, label: String): RangedValueComplicationData {
        return RangedValueComplicationData.Builder(
            value = value,
            min = 0f,
            max = max,
            contentDescription = PlainComplicationText.Builder("Watch progress $label today").build()
        )
            // This is the text you'll read in the watch face via [COMPLICATION.TEXT]
            .setText(PlainComplicationText.Builder(label).build())
            .setTitle(PlainComplicationText.Builder(label).build())  // fallback: read as [COMPLICATION.TITLE]
            .build()
    }

    /** Called when the complication instance is deactivated/removed (good for logging/cleanup). */
    override fun onComplicationDeactivated(id: Int) {
        Log.d(TAG, "onComplicationDeactivated(id=$id)")
    }
}
