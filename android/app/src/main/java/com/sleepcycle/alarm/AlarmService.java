package com.sleepcycle.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Foreground service that actually rings the alarm. It:
 *  - acquires a wake lock so the CPU stays awake,
 *  - plays the alarm sound on a loop at ALARM stream volume,
 *  - vibrates,
 *  - posts a high-importance full-screen notification that launches the app
 *    over the lock screen,
 *  - keeps ringing until the user stops it (STOP action or JS stopAlarm()).
 *
 * Because it runs as a foreground service started from a BroadcastReceiver,
 * it works even when the app has been fully closed / swiped away.
 */
public class AlarmService extends Service {
    public static final String ACTION_START = "com.sleepcycle.alarm.ACTION_START";
    public static final String ACTION_STOP = "com.sleepcycle.alarm.ACTION_STOP";

    private static final String CHANNEL_ID = "alarm-fire-channel";
    private static final int NOTIFICATION_ID = 424242;

    private static MediaPlayer mediaPlayer;
    private static Vibrator vibrator;
    private static PowerManager.WakeLock wakeLock;
    private static boolean isRinging = false;

    public static boolean isRinging() {
        return isRinging;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            stopRinging();
            stopSelf();
            return START_NOT_STICKY;
        }

        int id = intent != null ? intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1) : -1;
        AlarmStore.Alarm alarm = id >= 0 ? AlarmStore.get(getApplicationContext(), id) : null;

        String title = alarm != null && alarm.title != null ? alarm.title : "المنبه";
        String body = alarm != null && alarm.body != null ? alarm.body : "حان الوقت";
        String soundUri = alarm != null ? alarm.soundUri : null;
        float volume = alarm != null ? alarm.volume : 1.0f;

        createChannel();
        Notification notification = buildNotification(title, body);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startRinging(soundUri, volume);

        // A fired one-shot alarm is consumed; remove it from the store so it
        // is not re-scheduled on the next reboot.
        if (id >= 0) {
            AlarmStore.remove(getApplicationContext(), id);
        }

        // Let the JS layer (if the app is alive) move its UI into the ringing state.
        AlarmSoundPlugin.notifyAlarmFired(id);

        return START_STICKY;
    }

    private void startRinging(String soundUri, float volume) {
        acquireWakeLock();
        isRinging = true;

        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                int target = Math.max(1, Math.round(max * Math.max(0f, Math.min(1f, volume))));
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
            }
        } catch (Exception ignored) {}

        try {
            releasePlayer();
            mediaPlayer = new MediaPlayer();
            Uri uri;
            if (soundUri != null && soundUri.length() > 0) {
                uri = Uri.parse(soundUri);
            } else {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (uri == null) {
                    uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
            }
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            // If custom uri fails, fall back to the default alarm tone once.
            try {
                releasePlayer();
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mediaPlayer.setDataSource(getApplicationContext(),
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception ignored) {}
        }

        startVibration();
    }

    private void startVibration() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 800, 600};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }

    private void stopRinging() {
        isRinging = false;
        releasePlayer();
        try {
            if (vibrator != null) {
                vibrator.cancel();
                vibrator = null;
            }
        } catch (Exception ignored) {}
        releaseWakeLock();
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {}
    }

    private static void releasePlayer() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "sleepcycle:alarm");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(10 * 60 * 1000L); // safety timeout: 10 min
            }
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = null;
        } catch (Exception ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "المنبه", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("تنبيهات منبه دورات النوم");
            channel.enableVibration(true);
            channel.setBypassDnd(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            // We play the sound ourselves via MediaPlayer, so keep the channel silent.
            channel.setSound(null, null);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String body) {
        // Full-screen intent -> launches the app over the lock screen.
        Intent fullScreenIntent = new Intent(getApplicationContext(), MainActivity.class);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent fullScreenPI = PendingIntent.getActivity(
                getApplicationContext(), 0, fullScreenIntent, piFlags);

        // Stop action.
        Intent stopIntent = new Intent(getApplicationContext(), AlarmService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPI = PendingIntent.getService(
                getApplicationContext(), 1, stopIntent, piFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID);
        } else {
            builder = new Notification.Builder(getApplicationContext());
        }

        builder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(getApplicationInfo().icon)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPI)
                .setFullScreenIntent(fullScreenPI, true)
                .addAction(0, "إيقاف", stopPI);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_ALARM);
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            builder.setPriority(Notification.PRIORITY_MAX);
        }

        return builder.build();
    }

    @Override
    public void onDestroy() {
        stopRinging();
        super.onDestroy();
    }

    /** Stop any active alarm from anywhere in the app. */
    public static void stop(Context ctx) {
        Intent intent = new Intent(ctx.getApplicationContext(), AlarmService.class);
        intent.setAction(ACTION_STOP);
        try {
            ctx.getApplicationContext().startService(intent);
        } catch (Exception ignored) {
            releasePlayer();
        }
    }
}
