import type { SleepSession } from './storage';
import { NativeAlarmSound, isNativePlatform } from './nativeAlarmSound';

type AlarmConfig = {
  id: number;
  title: string;
  body: string;
  at: Date;
  sound: string;
  volume: number;
  soundUri?: string;
};

const isCapacitor = typeof window !== 'undefined' && typeof window.Capacitor !== 'undefined';

async function getLocalNotifications() {
  if (!isCapacitor) return null;
  try {
    const mod = await import('@capacitor/local-notifications');
    return mod.LocalNotifications;
  } catch {
    return null;
  }
}

async function hasNativeAlarm(): Promise<boolean> {
  if (!isNativePlatform()) return false;
  try {
    // Any resolving call proves the native plugin is registered.
    await NativeAlarmSound.canScheduleExactAlarms();
    return true;
  } catch {
    return false;
  }
}

export async function initializeNotifications(): Promise<void> {
  if (!isCapacitor) return;

  // Ask for the exact-alarm permission up front (Android 12+) so the alarm
  // can fire precisely even in Doze / when the app is closed.
  if (await hasNativeAlarm()) {
    try {
      const { value } = await NativeAlarmSound.canScheduleExactAlarms();
      if (!value) await NativeAlarmSound.requestExactAlarmPermission();
    } catch {
      // ignore — scheduling falls back to inexact automatically
    }
  }

  const LocalNotifications = await getLocalNotifications();
  if (!LocalNotifications) return;
  try {
    await LocalNotifications.requestPermissions();
  } catch {
    // permissions may already be granted
  }
}

export async function scheduleAlarm(config: AlarmConfig): Promise<void> {
  // Preferred path: native AlarmManager exact alarm + foreground ringing service.
  if (await hasNativeAlarm()) {
    try {
      await NativeAlarmSound.scheduleAlarm({
        id: config.id,
        at: config.at.getTime(),
        title: config.title,
        body: config.body,
        soundUri: config.soundUri,
        volume: config.volume,
      });
      return;
    } catch {
      // fall through to local-notification fallback
    }
  }

  // Fallback: local notification (older/other platforms).
  const LocalNotifications = await getLocalNotifications();
  if (!LocalNotifications) return;
  try {
    await LocalNotifications.schedule({
      notifications: [
        {
          id: config.id,
          title: config.title,
          body: config.body,
          schedule: { at: config.at, allowWhileIdle: true },
          smallIcon: 'ic_stat_icon',
          largeIcon: 'ic_launcher',
          channelId: 'alarm-channel',
          ongoing: true,
        },
      ],
    });
  } catch {
    // notification scheduling failed — app still works in foreground
  }
}

export async function cancelAlarm(id: number): Promise<void> {
  if (await hasNativeAlarm()) {
    try {
      await NativeAlarmSound.cancelAlarm({ id });
    } catch {
      // ignore
    }
  }
  const LocalNotifications = await getLocalNotifications();
  if (!LocalNotifications) return;
  try {
    await LocalNotifications.cancel({ notifications: [{ id }] });
  } catch {
    // ignore
  }
}

export async function cancelAllAlarms(): Promise<void> {
  if (await hasNativeAlarm()) {
    try {
      await NativeAlarmSound.stopAlarm();
    } catch {
      // ignore
    }
  }
  const LocalNotifications = await getLocalNotifications();
  if (!LocalNotifications) return;
  try {
    const pending = await LocalNotifications.getPending();
    if (pending.notifications.length > 0) {
      await LocalNotifications.cancel({
        notifications: pending.notifications.map((n) => ({ id: n.id })),
      });
    }
  } catch {
    // ignore
  }
}

/** Stop the currently ringing native alarm (sound + foreground service). */
export async function stopRingingAlarm(): Promise<void> {
  if (await hasNativeAlarm()) {
    try {
      await NativeAlarmSound.stopAlarm();
    } catch {
      // ignore
    }
  }
}

export function makeSessionFromAlarm(
  wakeTime: string,
  bedtime: Date,
  wakeDate: Date,
  cycles: number,
  durationMin: number,
): SleepSession {
  return {
    id: Date.now().toString(36) + Math.random().toString(36).substring(2, 7),
    wake_time: wakeTime,
    bedtime: bedtime.toISOString(),
    final_wake: wakeDate.toISOString(),
    cycles,
    duration_min: durationMin,
    completed: false,
    created_at: new Date().toISOString(),
  };
}
