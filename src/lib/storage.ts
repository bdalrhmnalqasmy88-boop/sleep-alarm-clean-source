export type SleepSession = {
  id: string;
  wake_time: string;
  bedtime: string;
  final_wake: string;
  cycles: number;
  duration_min: number;
  completed: boolean;
  created_at: string;
};

const STORAGE_KEY = 'sleep_sessions';

export function loadHistory(): SleepSession[] {
  try {
    const data = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
    return data.slice(0, 5);
  } catch {
    return [];
  }
}

export function saveSession(session: SleepSession): void {
  let list: SleepSession[] = [];
  try {
    list = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
  } catch {
    list = [];
  }
  list.unshift(session);
  list = list.slice(0, 20);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

export function updateSession(id: string, updates: Partial<SleepSession>): void {
  let list: SleepSession[] = [];
  try {
    list = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
  } catch {
    list = [];
  }
  for (const s of list) {
    if (s.id === id) {
      Object.assign(s, updates);
      break;
    }
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

export function genId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 7);
}
