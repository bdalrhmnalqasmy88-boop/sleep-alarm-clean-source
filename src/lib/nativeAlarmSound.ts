import { registerPlugin, type PluginListenerHandle } from '@capacitor/core';

export type PickedAudio = {
  uri: string;
  name: string;
};

export type ScheduleAlarmOptions = {
  id: number;
  /** Epoch milliseconds when the alarm should fire. */
  at: number;
  title: string;
  body: string;
  /** Content URI for a custom sound; omit for the system alarm ringtone. */
  soundUri?: string;
  /** 0..1 */
  volume?: number;
};

export type AlarmFiredEvent = { id: number };

export interface AlarmSoundPlugin {
  pickAudio(): Promise<PickedAudio>;
  configureChannel(options: { soundUri?: string; channelId: string }): Promise<{ channelId: string }>;

  /** Schedule an exact, Doze-exempt native alarm via AlarmManager. */
  scheduleAlarm(options: ScheduleAlarmOptions): Promise<{ scheduled: boolean; exact: boolean }>;
  /** Cancel a previously scheduled alarm by id. */
  cancelAlarm(options: { id: number }): Promise<void>;
  /** Stop the currently ringing alarm (foreground service + sound). */
  stopAlarm(): Promise<void>;
  /** Whether an alarm is ringing right now. */
  isRinging(): Promise<{ ringing: boolean }>;
  /** Whether the OS currently permits exact alarms (Android 12+). */
  canScheduleExactAlarms(): Promise<{ value: boolean }>;
  /** Open the system screen to grant the exact-alarm permission (Android 12+). */
  requestExactAlarmPermission(): Promise<void>;

  addListener(
    eventName: 'alarmFired',
    listenerFunc: (event: AlarmFiredEvent) => void,
  ): Promise<PluginListenerHandle>;
}

export const NativeAlarmSound = registerPlugin<AlarmSoundPlugin>('AlarmSound');

export const isNativePlatform = (): boolean =>
  typeof window !== 'undefined' &&
  typeof (window as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor !== 'undefined' &&
  ((window as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor?.isNativePlatform?.() ?? false);
