package com.eveningoutpost.dexdrip.UtilityModels;

import java.util.Date;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.eveningoutpost.dexdrip.Services.SnoozeOnNotificationDismissService;
import com.eveningoutpost.dexdrip.SnoozeActivity;
import com.eveningoutpost.dexdrip.Models.ActiveBgAlert;
import com.eveningoutpost.dexdrip.Models.AlertType;
import com.eveningoutpost.dexdrip.R;

public class AlertPlayer {

    static AlertPlayer singletone;

    private final static String TAG = AlertPlayer.class.getSimpleName();
    private MediaPlayer mediaPlayer;
    int volumeBeforeAlert;
    int volumeForThisAlert;
    Context context;
    final static int  MAX_VIBRATING = 2;
    final static int  MAX_ASCENDING = 5; 


    public static AlertPlayer getPlayer() {
        if(singletone == null) {
            Log.e(TAG,"getPlayer: Creating a new AlertPlayer");
            singletone = new AlertPlayer();
        } else {
            Log.i(TAG,"getPlayer: Using existing AlertPlayer");
        }
        return singletone;
    }

    public synchronized  void startAlert(Context ctx, AlertType newAlert, String bgValue )  {
      Log.e(TAG, "start called, Threadid " + Thread.currentThread().getId());
      stopAlert(ctx, true, false);
      ActiveBgAlert.Create(newAlert.uuid, false, new Date().getTime() + newAlert.minutes_between * 60000 );
      Vibrate(ctx, newAlert, bgValue, newAlert.override_silent_mode, newAlert.mp3_file, 0);
    }

    public synchronized void stopAlert(Context ctx, boolean ClearData, boolean clearIfSnoozeFinished) {

        Log.e(TAG, "stopAlert: stop called ClearData " + ClearData + "  ThreadID " + Thread.currentThread().getId());
        if (ClearData) {
            ActiveBgAlert.ClearData();
        }
        if(clearIfSnoozeFinished) {
            ActiveBgAlert.ClearIfSnoozeFinished();
        }
        notificationDismiss(ctx);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public synchronized  void Snooze(Context ctx, int repeatTime) {
        Log.e(TAG, "Snooze called repeatTime = "+ repeatTime);
        stopAlert(ctx, false, false);
        ActiveBgAlert activeBgAlert = ActiveBgAlert.getOnly();
        if (activeBgAlert  == null) {
            Log.e(TAG, "Error, snooze was called but no alert is active. alert was probably removed in ui ");
            return;
        }
        activeBgAlert.snooze(repeatTime);
    }

    public synchronized  void PreSnooze(Context ctx, String uuid, int repeatTime) {
        Log.e(TAG, "PreSnooze called repeatTime = "+ repeatTime);
        stopAlert(ctx, true, false);
        ActiveBgAlert.Create(uuid, true, new Date().getTime() + repeatTime * 60000 );
        ActiveBgAlert activeBgAlert = ActiveBgAlert.getOnly();
        if (activeBgAlert  == null) {
            Log.wtf(TAG, "Just created the allert, where did it go...");
            return;
        }
        activeBgAlert.snooze(repeatTime);
    }

 // Check the state and alrarm if needed
    public void ClockTick(Context ctx, boolean trendingToAlertEnd, String bgValue)
    {
        if (trendingToAlertEnd) {
            Log.e(TAG,"ClockTick: This alert is trending to it's end will not do anything");
            return;
        }
        ActiveBgAlert activeBgAlert = ActiveBgAlert.getOnly();
        if (activeBgAlert  == null) {
            // Nothing to do ...
            return;
        }
        if(activeBgAlert.ready_to_alarm()) {
            stopAlert(ctx, false, false);
            
            int timeFromStartPlaying = activeBgAlert.getUpdatePlayTime();
            AlertType alert = AlertType.get_alert(activeBgAlert.alert_uuid);
            if (alert == null) {
                Log.w(TAG, "ClockTick: The alert was already deleted... will not play");
                ActiveBgAlert.ClearData();
                return;
            }
            Log.e(TAG,"ClockTick: Playing the alert again");
            Vibrate(ctx, alert, bgValue, alert.override_silent_mode, alert.mp3_file, timeFromStartPlaying);
        }

    }

    private void PlayFile(Context ctx, String FileName, float VolumeFrac) {
        Log.e(TAG, "PlayFile: called FileName = " + FileName);
        if(mediaPlayer != null) {
            Log.e(TAG, "ERROR, PlayFile:going to leak a mediaplayer !!!");
        }
        if(FileName != null && FileName.length() > 0) {
            mediaPlayer = MediaPlayer.create(ctx, Uri.parse(FileName), null);
        }
        if(mediaPlayer == null) {
            Log.w(TAG, "PlayFile: Creating mediaplayer with file " + FileName + " failed. using default alarm");
            mediaPlayer = MediaPlayer.create(ctx, R.raw.default_alert);
        }
        if(mediaPlayer != null) {
            AudioManager manager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            int maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            volumeBeforeAlert = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
            volumeForThisAlert = (int)(maxVolume * VolumeFrac);
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeForThisAlert, 0);
            context = ctx;

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.e(TAG, "PlayFile: onCompletion called (finished playing) ");
                    AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    int currentVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if(volumeForThisAlert == currentVolume) {
                        // If the user has changed the volume, don't change it again.
                        manager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeAlert, 0);
                    }
                }
            });
            Log.e(TAG, "PlayFile: calling mediaPlayer.start() ");
            mediaPlayer.start();
        } else {
            // TODO, what should we do here???
            Log.wtf(TAG,"PlayFile: Starting an alert failed, what should we do !!!");
        }
    }

    private PendingIntent notificationIntent(Context ctx, Intent intent){
        return PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

    }
    private PendingIntent snoozeIntent(Context ctx){
        Intent intent = new Intent(ctx, SnoozeOnNotificationDismissService.class);
        return PendingIntent.getService(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

    }
    private void Vibrate(Context ctx, AlertType alert, String bgValue, Boolean overrideSilent, String audioPath, int timeFromStartPlaying) {
        Log.e(TAG, "Vibrate called timeFromStartPlaying = " + timeFromStartPlaying);
        Log.e("ALARM", "setting vibrate alarm");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean ascending_bg_alerts = prefs.getBoolean("ascending_bg_alerts", true);
        if (!ascending_bg_alerts) {
            // We start from the non ascending part...
            timeFromStartPlaying = MAX_ASCENDING;
        }

        String title = bgValue + " " + alert.name;
        String content = "BG LEVEL ALERT: " + bgValue;
        Intent intent = new Intent(ctx, SnoozeActivity.class);

        NotificationCompat.Builder  builder = new NotificationCompat.Builder(ctx)
            .setSmallIcon(R.drawable.ic_action_communication_invert_colors_on)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(notificationIntent(ctx, intent))
            .setDeleteIntent(snoozeIntent(ctx));
        if (timeFromStartPlaying >= MAX_VIBRATING) {
            // Before this, we only vibrate...
            float volumeFrac = (float)(timeFromStartPlaying - MAX_VIBRATING) / (MAX_ASCENDING - MAX_VIBRATING);
            volumeFrac = Math.max(volumeFrac, 1);
            Log.e(TAG, "Vibrate volumeFrac = " + volumeFrac);
            if(overrideSilent) {
                PlayFile(ctx, alert.mp3_file, volumeFrac);
            } else {
                builder.setSound(Uri.parse(audioPath), AudioAttributes.USAGE_ALARM);
            }
        }
        //NotificationCompat.Builder mBuilder = notificationBuilder(title, content, intent);
        builder.setVibrate(Notifications.vibratePattern);
        NotificationManager mNotifyMgr = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyMgr.cancel(Notifications.exportAlertNotificationId);
        mNotifyMgr.notify(Notifications.exportAlertNotificationId, builder.build());

    }

    private void notificationDismiss(Context ctx) {
        NotificationManager mNotifyMgr = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyMgr.cancel(Notifications.exportAlertNotificationId);
    }

}