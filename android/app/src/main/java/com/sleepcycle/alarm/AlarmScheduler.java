package com.sleepcycle.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Schedules exact alarms with AlarmManager so they fire even when the app is
 * fully closed and the device is in Doze. Uses setAlarmClock() which is the
 * highest-priority, Doze-exempt scheduling API on modern Android, and is the
 * correct choice for a user-facing alarm clock.
 */
public final class AlarmScheduler {
    public static final String ACTION_ALARM_FIRE = "com.sleepcycle.alarm.ACTION_ALARM_FIRE";
    public static final String EXTRA_ALARM_ID = "alarm_id";

    private AlarmScheduler() {}

    private static AlarmManager am(Context ctx) {
        return (AlarmManager) ctx.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent firePendingIntent(Context ctx, int id) {
        Intent intent = new Intent(ctx.getApplicationContext(), AlarmReceiver.class);
        intent.setAction(ACTION_ALARM_FIRE);
        intent.putExtra(EXTRA_ALARM_ID, id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(ctx.getApplicationContext(), id, intent, flags);
    }

    /** Returns true if the OS currently allows this app to schedule exact alarms. */
    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager manager = am(ctx);
            return manager != null && manager.canScheduleExactAlarms();
        }
        return true;
    }

    /**
     * Persist + schedule an exact alarm. If exact alarms are not permitted the
     * alarm is still persisted and scheduled inexactly as a fallback so it is
     * never silently lost.
     */
    public static void schedule(Context ctx, AlarmStore.Alarm alarm) {
        AlarmStore.put(ctx, alarm);
        AlarmManager manager = am(ctx);
        if (manager == null) return;

        PendingIntent fire = firePendingIntent(ctx, alarm.id);

        // Intent used by the AlarmClock UI info (launches the app when tapped).
        Intent showIntent = new Intent(ctx.getApplicationContext(), MainActivity.class);
        int showFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            showFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent show = PendingIntent.getActivity(ctx.getApplicationContext(), alarm.id, showIntent, showFlags);

        try {
            if (canScheduleExact(ctx)) {
                // setAlarmClock is Doze-exempt and the correct API for alarm clocks.
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(alarm.triggerAt, show);
                manager.setAlarmClock(info, fire);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAt, fire);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, alarm.triggerAt, fire);
            }
        } catch (SecurityException e) {
            // Exact permission revoked between check and call — fall back gracefully.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAt, fire);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, alarm.triggerAt, fire);
            }
        }
    }

    public static void cancel(Context ctx, int id) {
        AlarmManager manager = am(ctx);
        if (manager != null) {
            manager.cancel(firePendingIntent(ctx, id));
        }
        AlarmStore.remove(ctx, id);
    }
}
