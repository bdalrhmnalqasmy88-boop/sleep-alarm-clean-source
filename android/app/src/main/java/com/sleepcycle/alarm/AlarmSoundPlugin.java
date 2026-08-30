package com.sleepcycle.alarm;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.database.Cursor;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AlarmSound")
public class AlarmSoundPlugin extends Plugin {
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