import { useCallback, useEffect, useRef, useState } from 'react';
import { startAlarmSound, startCustomAlarmSound, type AlarmSoundType } from '@/lib/alarmSound';
import { NativeAlarmSound, isNativePlatform } from '@/lib/nativeAlarmSound';

export type AlarmState = 'idle' | 'armed' | 'ringing' | 'slept' | 'final-ringing' | 'done';

type Options = {
  firstAlarmAt: Date | null;
  finalAlarmAt: Date | null;
  sound: AlarmSoundType;
  volume: number;
  customSoundUri?: string;
  onFirstFired?: () => void;
  onFinalFired?: () => void;
};

export function useAlarm({ firstAlarmAt, finalAlarmAt, sound, volume, customSoundUri, onFirstFired, onFinalFired }: Options) {
  const [state, setState] = useState<AlarmState>('idle');
  const [now, setNow] = useState(() => new Date());
  const intervalRef = useRef<number | null>(null);
  const stopSoundRef = useRef<(() => void) | null>(null);
  const firedFirstRef = useRef(false);
  const firedFinalRef = useRef(false);

  const native = isNativePlatform();

  // keep latest callbacks in refs so the tick effect doesn't restart on every render
  const onFirstFiredRef = useRef(onFirstFired);
  const onFinalFiredRef = useRef(onFinalFired);
  useEffect(() => {
    onFirstFiredRef.current = onFirstFired;
  }, [onFirstFired]);
  useEffect(() => {
    onFinalFiredRef.current = onFinalFired;
  }, [onFinalFired]);

  const stopSound = useCallback(() => {
    // On native, the foreground AlarmService owns the sound — stop it there.
    if (native) {
      void NativeAlarmSound.stopAlarm().catch(() => undefined);
    }
    if (stopSoundRef.current) {
      stopSoundRef.current();
      stopSoundRef.current = null;
    }
  }, [native]);

  const startSound = useCallback(() => {
    // On native the alarm sound is played by the native foreground service
    // (which keeps ringing even when the app is closed), so we must NOT also
    // start the in-WebView Web Audio sound or we'd double up.
    if (native) return;
    stopSound();
    stopSoundRef.current =
      sound === 'custom' && customSoundUri ? startCustomAlarmSound(customSoundUri, volume) : startAlarmSound(sound, volume);
  }, [native, sound, volume, customSoundUri, stopSound]);

  // main tick — drives the in-app UI; the actual background alarm is native.
  useEffect(() => {
    if (state === 'idle' || state === 'done') {
      if (intervalRef.current) {
        window.clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      stopSound();
      return;
    }

    intervalRef.current = window.setInterval(() => {
      const t = new Date();
      setNow(t);

      if (state === 'armed' && firstAlarmAt && !firedFirstRef.current && t.getTime() >= firstAlarmAt.getTime()) {
        firedFirstRef.current = true;
        setState('ringing');
        startSound();
        onFirstFiredRef.current?.();
      }

      if (state === 'slept' && finalAlarmAt && !firedFinalRef.current && t.getTime() >= finalAlarmAt.getTime()) {
        firedFinalRef.current = true;
        setState('final-ringing');
        startSound();
        onFinalFiredRef.current?.();
      }
    }, 250);

    return () => {
      if (intervalRef.current) {
        window.clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [state, firstAlarmAt, finalAlarmAt, startSound, stopSound]);

  // Native: when the OS-level alarm fires while the app is open, move the UI
  // into the ringing state immediately (the sound is already playing natively).
  useEffect(() => {
    if (!native) return;
    let handle: { remove: () => void } | undefined;
    let cancelled = false;

    void NativeAlarmSound.addListener('alarmFired', () => {
      const t = new Date();
      if (finalAlarmAt && t.getTime() >= finalAlarmAt.getTime() - 1000) {
        firedFinalRef.current = true;
        setState('final-ringing');
        onFinalFiredRef.current?.();
      } else {
        firedFirstRef.current = true;
        setState('ringing');
        onFirstFiredRef.current?.();
      }
    }).then((h) => {
      if (cancelled) h.remove();
      else handle = h;
    });

    return () => {
      cancelled = true;
      handle?.remove();
    };
  }, [native, firstAlarmAt, finalAlarmAt]);

  const arm = useCallback(() => {
    firedFirstRef.current = false;
    firedFinalRef.current = false;
    setState('armed');
  }, []);

  const dismissFirst = useCallback(() => {
    stopSound();
    setState('slept');
  }, [stopSound]);

  const dismissFinal = useCallback(() => {
    stopSound();
    setState('done');
  }, [stopSound]);

  const reset = useCallback(() => {
    stopSound();
    firedFirstRef.current = false;
    firedFinalRef.current = false;
    setState('idle');
  }, [stopSound]);

  // cleanup on unmount
  useEffect(() => {
    return () => {
      if (intervalRef.current) window.clearInterval(intervalRef.current);
      if (stopSoundRef.current) {
        stopSoundRef.current();
        stopSoundRef.current = null;
      }
    };
  }, []);

  return { state, now, arm, dismissFirst, dismissFinal, reset };
}
