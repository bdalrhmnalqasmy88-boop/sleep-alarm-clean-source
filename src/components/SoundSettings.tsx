import { useRef, useState } from 'react';
import { ChevronDown, Music2, Play, Square, Volume1, Volume2 } from 'lucide-react';
import { ALARM_SOUNDS, previewSound, type AlarmSoundType } from '@/lib/alarmSound';
import { NativeAlarmSound } from '@/lib/nativeAlarmSound';

type Props = {
  sound: AlarmSoundType;
  setSound: (s: AlarmSoundType) => void;
  volume: number;
  setVolume: (v: number) => void;
  customSoundUri?: string;
  customSoundName?: string;
  setCustomSound: (value: { uri: string; name: string }) => void;
};

export default function SoundSettings({ sound, setSound, volume, setVolume, customSoundUri, customSoundName, setCustomSound }: Props) {
  const [open, setOpen] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const stopPreviewRef = useRef<(() => void) | null>(null);

  const selected = sound === 'custom'
    ? { id: 'custom' as const, label: customSoundName || 'صوت من الهاتف', description: 'ملف صوتي مخصص' }
    : ALARM_SOUNDS.find((s) => s.id === sound);

  const stopPreview = () => {
    if (stopPreviewRef.current) {
      stopPreviewRef.current();
      stopPreviewRef.current = null;
    }
    setPreviewing(false);
  };

  const handlePreview = () => {
    if (previewing) {
      stopPreview();
      return;
    }
    stopPreviewRef.current = sound === 'custom' && customSoundUri
      ? (() => {
          const audio = new Audio(customSoundUri);
          audio.volume = volume;
          void audio.play().catch(() => undefined);
          return () => { audio.pause(); audio.currentTime = 0; };
        })()
      : previewSound(sound, volume);
    setPreviewing(true);
    // auto-stop after 1.6s
    setTimeout(() => {
      stopPreview();
    }, 1600);
  };

  const volumePct = Math.round(volume * 100);

  return (
    <div className="rounded-2xl border border-night-700/50 bg-night-800/40 p-5 backdrop-blur-md">
      <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-moon-200">
        <Music2 className="h-4 w-4 text-accent-400" />
        إعدادات نغمة المنبه
      </div>

      {/* Sound dropdown */}
      <label className="mb-1.5 block text-xs text-moon-300/70">نوع النغمة</label>
      <div className="relative">
        <button
          onClick={() => setOpen((o) => !o)}
          className="flex w-full items-center justify-between rounded-xl border border-night-600 bg-night-900/60 px-4 py-3 text-right text-sm text-moon-50 outline-none transition focus:border-moon-500"
        >
          <span>{selected?.label ?? 'اختر نغمة'}</span>
          <ChevronDown className={`h-4 w-4 text-moon-300 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
        {open && (
          <>
            <div className="fixed inset-0 z-20" onClick={() => setOpen(false)} />
            <div className="absolute z-30 mt-1 w-full overflow-hidden rounded-xl border border-night-600 bg-night-900 shadow-glow">
              {ALARM_SOUNDS.map((s) => (
                <button
                  key={s.id}
                  onClick={() => {
                    setSound(s.id);
                    setOpen(false);
                    stopPreview();
                  }}
                  className={`flex w-full flex-col items-start px-4 py-3 text-right transition hover:bg-night-700/60 ${
                    s.id === sound ? 'bg-accent-500/10' : ''
                  }`}
                >
                  <span className={`text-sm font-medium ${s.id === sound ? 'text-accent-400' : 'text-moon-100'}`}>
                    {s.label}
                  </span>
                  <span className="text-[11px] text-moon-300/60">{s.description}</span>
                </button>
              ))}
              {customSoundUri && (
                <button
                  onClick={() => { setSound('custom'); setOpen(false); stopPreview(); }}
                  className={`flex w-full flex-col items-start px-4 py-3 text-right transition hover:bg-night-700/60 ${sound === 'custom' ? 'bg-accent-500/10' : ''}`}
                >
                  <span className={`text-sm font-medium ${sound === 'custom' ? 'text-accent-400' : 'text-moon-100'}`}>
                    {customSoundName || 'صوت من الهاتف'}
                  </span>
                  <span className="text-[11px] text-moon-300/60">ملف صوتي مخصص</span>
                </button>
              )}
            </div>
          </>
        )}
      </div>

      <button
        onClick={async () => {
          try {
            const picked = await NativeAlarmSound.pickAudio();
            setCustomSound(picked);
            setSound('custom');
          } catch {
            // User cancelled the picker or the web preview is being used.
          }
        }}
        className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl border border-accent-500/40 bg-accent-500/10 px-4 py-2.5 text-sm font-medium text-accent-300 transition hover:bg-accent-500/20"
      >
        <Music2 className="h-4 w-4" /> اختيار نغمة من ملفات الهاتف
      </button>

      {/* Preview button */}
      <button
        onClick={handlePreview}
        className={`mt-3 flex w-full items-center justify-center gap-2 rounded-xl border px-4 py-2.5 text-sm font-medium transition ${
          previewing
            ? 'border-accent-400 bg-accent-500/15 text-accent-400'
            : 'border-night-600 bg-night-900/40 text-moon-200 hover:bg-night-700/40'
        }`}
      >
        {previewing ? (
          <>
            <Square className="h-4 w-4 fill-current" /> إيقاف المعاينة
          </>
        ) : (
          <>
            <Play className="h-4 w-4 fill-current" /> تجربة الصوت
          </>
        )}
      </button>

      {/* Volume slider */}
      <div className="mt-5">
        <div className="mb-2 flex items-center justify-between text-xs text-moon-300/70">
          <span className="flex items-center gap-1.5">
            {volumePct >= 66 ? (
              <Volume2 className="h-4 w-4" />
            ) : (
              <Volume1 className="h-4 w-4" />
            )}
            مستوى الصوت
          </span>
          <span className="font-mono font-semibold text-moon-100">{volumePct}%</span>
        </div>
        <input
          type="range"
          min={0}
          max={100}
          value={volumePct}
          onChange={(e) => setVolume(Number(e.target.value) / 100)}
          className="alarm-range w-full"
        />
        <div className="mt-1 flex justify-between text-[10px] text-moon-300/40">
          <span>صامت</span>
          <span>أعلى حد</span>
        </div>
      </div>
    </div>
  );
}
