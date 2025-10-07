package com.yourprojectid

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

// ExerciseActivity is not provided, but it's just a Java activity.
// PrefsKeys is a file storing strings
import com.yourprojectid.ExerciseActivity
import com.yourprojectid.PrefsKeys

class MyComplicationProviderService : SuspendingComplicationDataSourceService() {

    /** Called when a complication instance is activated on the watch face (useful for logging/setup). */
    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        Log.d(TAG, "onComplicationActivated(): $complicationInstanceId")
    }

    /** Supplies preview data shown in editors; here we return a simple 💪🏽 short-text preview. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("💪🏽").build(),
                contentDescription = PlainComplicationText.Builder("emoji").build()
            ).build()
            else -> null
        }
    }

    /**
     * Handles live complication requests from the watch face.
     * - Hides the complication for weekId 1 or >= 6.
     * - Otherwise returns a tappable 💪🏽 that opens ExerciseActivity.
     */
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(TAG, "req type=${request.complicationType}")
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null

        val prefsDataStorage = applicationContext.getSharedPreferences(PrefsKeys.Data.PREFS, Context.MODE_PRIVATE)
        val weekId = prefsDataStorage.getInt(PrefsKeys.Data.WEEK_ID, 1)

        // Hide the complication when weekID is those weeks
        if (weekId==1 || weekId >=6) {
            return NoDataComplicationData()
        }

        val intent = Intent(applicationContext, ExerciseActivity::class.java).apply {
            action = "open_from_complication_${request.complicationInstanceId}" // ensure uniqueness
            putExtra(ExerciseActivity.EXTRA_LAUNCH_SOURCE, ExerciseActivity.SOURCE_COMPLICATION)
        }

        val tap = PendingIntent.getActivity(
            applicationContext,
            request.complicationInstanceId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("💪🏽").build(),
            contentDescription = PlainComplicationText.Builder("Open exercise").build()
        ).setTapAction(tap).build()
    }

    /** Called when a complication instance is removed/deactivated (good place to clean up or log). */
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(TAG, "onComplicationDeactivated(): $complicationInstanceId")
    }

    companion object {
        private const val TAG = "EmojiComplication"

        /** Triggers an immediate complication refresh for all instances of this data source. */
        @JvmStatic
        fun requestComplicationUpdate(ctx: android.content.Context) {
            ComplicationDataSourceUpdateRequester
                .create(ctx, ComponentName(ctx, MyComplicationProviderService::class.java))
                .requestUpdateAll()
        }
    }
}
