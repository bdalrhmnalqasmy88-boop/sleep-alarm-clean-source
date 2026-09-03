package com.sleepcycle.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists scheduled alarms in SharedPreferences so they can be:
 *  - re-scheduled after a device reboot (BootReceiver)
 *  - looked up when firing / cancelling
 *
 * Independent of the WebView / JS layer so alarms survive the app being
 * fully closed or killed.
 */
public final class AlarmStore {
    private static final String PREFS = "sleep_alarm_store";
    private static final String KEY_ALARMS = "alarms";

    public static final class Alarm {
        public int id;
        public long triggerAt;
        public String title;
        public String body;
        public String soundUri; // null => default alarm ringtone
        public float volume;    // 0..1

        public Alarm(int id, long triggerAt, String title, String body, String soundUri, float volume) {
            this.id = id;
            this.triggerAt = triggerAt;
            this.title = title;
            this.body = body;
            this.soundUri = soundUri;
            this.volume = volume;
        }
    }

    private AlarmStore() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void put(Context ctx, Alarm alarm) {
        List<Alarm> all = getAll(ctx);
        List<Alarm> next = new ArrayList<>();
        for (Alarm a : all) {
            if (a.id != alarm.id) next.add(a);
        }
        next.add(alarm);
        save(ctx, next);
    }

    public static synchronized void remove(Context ctx, int id) {
        List<Alarm> all = getAll(ctx);
        List<Alarm> next = new ArrayList<>();
        for (Alarm a : all) {
            if (a.id != id) next.add(a);
        }
        save(ctx, next);
    }

    public static synchronized Alarm get(Context ctx, int id) {
        for (Alarm a : getAll(ctx)) {
            if (a.id == id) return a;
        }
        return null;
    }

    public static synchronized List<Alarm> getAll(Context ctx) {
        List<Alarm> out = new ArrayList<>();
        String raw = prefs(ctx).getString(KEY_ALARMS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Alarm(
                        o.getInt("id"),
                        o.getLong("triggerAt"),
                        o.optString("title", "المنبه"),
                        o.optString("body", ""),
                        o.has("soundUri") && !o.isNull("soundUri") ? o.optString("soundUri", null) : null,
                        (float) o.optDouble("volume", 1.0)
                ));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void save(Context ctx, List<Alarm> alarms) {
        JSONArray arr = new JSONArray();
        for (Alarm a : alarms) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("triggerAt", a.triggerAt);
                o.put("title", a.title);
                o.put("body", a.body);
                o.put("soundUri", a.soundUri == null ? JSONObject.NULL : a.soundUri);
                o.put("volume", a.volume);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        prefs(ctx).edit().putString(KEY_ALARMS, arr.toString()).apply();
    }
}
