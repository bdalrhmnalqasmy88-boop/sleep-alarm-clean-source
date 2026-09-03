package com.sleepcycle.alarm;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.database.Cursor;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AlarmSound")
public class AlarmSoundPlugin extends Plugin {

    private static AlarmSoundPlugin instance;

    @Override
    public void load() {
        instance = this;
    }

    /**
     * Called from the (process-wide) AlarmService when an alarm fires so the
     * web UI can move into its ringing state if the app happens to be alive.
     * Safe to call when no plugin instance exists (app fully closed) — the
     * native service still rings on its own.
     */
    public static void notifyAlarmFired(int id) {
        if (instance != null) {
            JSObject data = new JSObject();
            data.put("id", id);
            instance.notifyListeners("alarmFired", data);
        }
    }

    // ---------------------------------------------------------------------
    // Exact alarm scheduling
    // ---------------------------------------------------------------------

    @PluginMethod
    public void scheduleAlarm(PluginCall call) {
        Integer id = call.getInt("id");
        if (id == null) {
            call.reject("id is required");
            return;
        }
        long at = call.getLong("at", 0L);
        if (at <= 0L) {
            call.reject("at (epoch ms) is required");
            return;
        }
        String title = call.getString("title", "المنبه");
        String body = call.getString("body", "حان الوقت");
        String soundUri = call.getString("soundUri", null);
        float volume = call.getFloat("volume", 1.0f);

        AlarmStore.Alarm alarm = new AlarmStore.Alarm(id, at, title, body, soundUri, volume);
        AlarmScheduler.schedule(getContext(), alarm);

        JSObject ret = new JSObject();
        ret.put("scheduled", true);
        ret.put("exact", AlarmScheduler.canScheduleExact(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void cancelAlarm(PluginCall call) {
        Integer id = call.getInt("id");
        if (id == null) {
            call.reject("id is required");
            return;
        }
        AlarmScheduler.cancel(getContext(), id);
        call.resolve();
    }

    @PluginMethod
    public void stopAlarm(PluginCall call) {
        AlarmService.stop(getContext());
        call.resolve();
    }

    @PluginMethod
    public void isRinging(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("ringing", AlarmService.isRinging());
        call.resolve(ret);
    }

    @PluginMethod
    public void canScheduleExactAlarms(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("value", AlarmScheduler.canScheduleExact(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void requestExactAlarmPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !AlarmScheduler.canScheduleExact(getContext())) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception ignored) {}
        }
        call.resolve();
    }

    // ---------------------------------------------------------------------
    // Custom audio picking (unchanged behaviour)
    // ---------------------------------------------------------------------

    @PluginMethod
    public void pickAudio(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "audioPickerResult");
    }

    @ActivityCallback
    private void audioPickerResult(PluginCall call, androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("Audio selection cancelled");
            return;
        }
        Uri uri = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}

        String name = uri.toString();
        try (Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {}

        JSObject ret = new JSObject();
        ret.put("uri", uri.toString());
        ret.put("name", name);
        call.resolve(ret);
    }

    @PluginMethod
    public void configureChannel(PluginCall call) {
        String channelId = call.getString("channelId", "alarm-channel");
        String soundUri = call.getString("soundUri", null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(channelId, "Sleep alarms", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Sleep Cycle Alarm notifications");
            channel.enableVibration(true);
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            Uri notificationSound = soundUri == null
                    ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    : Uri.parse(soundUri);
            channel.setSound(notificationSound, attributes);
            manager.createNotificationChannel(channel);
        }
        JSObject ret = new JSObject();
        ret.put("channelId", channelId);
        call.resolve(ret);
    }
}
