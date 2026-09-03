package com.sleepcycle.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Re-schedules any still-pending alarms after the device reboots (or the app
 * is updated). Without this, exact alarms set with AlarmManager are cleared by
 * the OS on reboot and the user would miss their alarm.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!isBoot) return;

        long now = System.currentTimeMillis();
        List<AlarmStore.Alarm> alarms = AlarmStore.getAll(context.getApplicationContext());
        for (AlarmStore.Alarm alarm : alarms) {
            if (alarm.triggerAt > now) {
                AlarmScheduler.schedule(context.getApplicationContext(), alarm);
            } else {
                // Missed while powered off — drop it so it doesn't fire late.
                AlarmStore.remove(context.getApplicationContext(), alarm.id);
            }
        }
    }
}
