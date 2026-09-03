package com.sleepcycle.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Receives the exact-alarm broadcast from AlarmManager at the scheduled time
 * (even when the app process is dead) and immediately starts the foreground
 * AlarmService which plays the sound and shows the full-screen alarm.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1);

        Intent serviceIntent = new Intent(context.getApplicationContext(), AlarmService.class);
        serviceIntent.setAction(AlarmService.ACTION_START);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, id);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getApplicationContext().startForegroundService(serviceIntent);
        } else {
            context.getApplicationContext().startService(serviceIntent);
        }
    }
}
