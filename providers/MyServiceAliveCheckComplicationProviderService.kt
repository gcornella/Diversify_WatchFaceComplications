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

// MainActivity is not provided, but it's just a Java activity.
// PrefsKeys is a file storing strings
import com.yourprojectid.MainActivity
import com.yourprojectid.PrefsKeys

class MyServiceAliveCheckComplicationProviderService : SuspendingComplicationDataSourceService() {

    /** Called when a complication instance is activated on the watch face (good for setup/logging). */
    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        Log.d(TAG, "onComplicationActivated(): $complicationInstanceId, type=$type")
    }

    /** Supplies preview data for editors; shows a simple "App!" short-text sample. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("App!").build(),
                contentDescription = PlainComplicationText.Builder("Resume foreground service").build()
            ).build()
            else -> null
        }

    /**
     * Handles live requests.
     * Reads last heartbeat; hides if fresh, otherwise shows a tappable "App!" to resume service.
     */
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null

        val prefs = applicationContext.getSharedPreferences(PrefsKeys.HeartBeat.HEARTBEAT_PREFS, Context.MODE_PRIVATE)
        val lastBeat = prefs.getLong(PrefsKeys.HeartBeat.HEARTBEAT_TIME, 0L)

        // Healthy if last heartbeat < FRESH_MS ago (elapsedRealtime-based)
        val isAlive = lastBeat > 0 && (System.currentTimeMillis() - lastBeat) < FRESH_MS

        if (isAlive) {
            // Hide the complication when service looks healthy
            return NoDataComplicationData()
        }

        // Stale → show clear "Restart" with tap-to-start-foreground-service
        val tap = PendingIntent.getActivity(
            applicationContext,
            request.complicationInstanceId,
            Intent(applicationContext, MainActivity::class.java)
                .setAction("RESUME_FROM_COMPLICATION"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("App!").build(),
            contentDescription = PlainComplicationText.Builder("Resume foreground service").build()
        ).setTapAction(tap).build()
    }

    /** Called when a complication instance is deactivated/removed (useful for cleanup/logging). */
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(TAG, "onComplicationDeactivated(): $complicationInstanceId")
    }

    companion object {
        private const val TAG = "RestartComplication"

        // Heartbeat keys & freshness window (must be > your HB_MS; you use 60s)
        private const val FRESH_MS = 120_000L

        /** Triggers an immediate refresh of all instances of this complication data source. */
        @JvmStatic
        fun requestComplicationUpdate(ctx: android.content.Context) {
            ComplicationDataSourceUpdateRequester
                .create(ctx, ComponentName(ctx, MyServiceAliveCheckComplicationProviderService::class.java))
                .requestUpdateAll()
        }
    }
}
