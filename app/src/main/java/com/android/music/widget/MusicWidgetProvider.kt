package com.android.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.android.music.R
import com.android.music.data.model.Song
import com.android.music.service.MusicService
import com.android.music.ui.activity.MainActivity

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val songTitle = prefs.getString(KEY_SONG_TITLE, "No song")
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)

        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, songTitle, isPlaying)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)

                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = if (isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_RESUME
                }
                startService(context, serviceIntent)
            }
            ACTION_NEXT -> {
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = MusicService.ACTION_NEXT
                }
                startService(context, serviceIntent)
            }
        }
    }

    private fun startService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    companion object {
        const val PREFS_NAME = "music_widget_prefs"
        const val KEY_SONG_TITLE = "song_title"
        const val KEY_SONG_ID = "song_id"
        const val KEY_IS_PLAYING = "is_playing"

        const val ACTION_PLAY_PAUSE = "com.android.music.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.android.music.widget.NEXT"

        fun updateWidgetState(context: Context, song: Song?, isPlaying: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_SONG_TITLE, song?.title ?: "No song")
                putLong(KEY_SONG_ID, song?.id ?: 0L)
                putBoolean(KEY_IS_PLAYING, isPlaying)
                apply()
            }

            // Update all widgets
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, song?.title ?: "No song", isPlaying)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            songTitle: String?,
            isPlaying: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            // Set song title (truncated for compact display)
            val title = songTitle ?: "No song"
            val displayTitle = if (title.length > 10) title.take(8) + "..." else title
            views.setTextViewText(R.id.tvWidgetTitle, displayTitle)

            // Set play/pause icon
            views.setImageViewResource(
                R.id.btnWidgetPlayPause,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )

            // Play/Pause click
            val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetPlayPause, playPausePending)

            // Next click
            val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 1,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetNext, nextPending)

            // Open app on widget click
            val appIntent = Intent(context, MainActivity::class.java)
            val appPending = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 2,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetContainer, appPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
