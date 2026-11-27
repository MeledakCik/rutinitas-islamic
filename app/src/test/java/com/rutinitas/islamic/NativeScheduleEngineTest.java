package com.rutinitas.islamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class NativeScheduleEngineTest {
    private static Date previewDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.NOVEMBER, 26, 0, 0);
        return calendar.getTime();
    }

    private static Map<String, String> prayers() {
        Map<String, String> result = new HashMap<>();
        result.put("subuh", "04:45");
        result.put("dzuhur", "12:00");
        result.put("ashar", "15:15");
        result.put("maghrib", "18:00");
        result.put("isya", "19:15");
        return result;
    }

    private static JSONObject routine(String id, String title, String policy, JSONObject anchor,
                                      int windowMins, int durationMins) throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("enabled", true)
                .put("daysOfWeek", new JSONArray().put(0).put(1).put(2).put(3).put(4).put(5).put(6))
                .put("anchor", anchor).put("conflictPolicy", policy).put("isFlexible", "shift".equals(policy))
                .put("windowMins", windowMins).put("durationMins", durationMins);
    }

    @Test public void strictRoutineMovesPrimaryHifzAfterItsWindow() throws Exception {
        JSONObject state = new JSONObject().put("customRoutines", new JSONArray().put(routine(
                "work", "Kerja", "strict", new JSONObject().put("type", "fixed").put("time", "04:45"), 30, 30)));

        JSONObject schedule = NativeScheduleEngine.build(state, prayers(), previewDate(), true);
        JSONObject primary = find(schedule.getJSONArray("blocks"), "primary_hifz");
        JSONObject work = find(schedule.getJSONArray("blocks"), "work");

        assertEquals(5 * 60 + 15, primary.getInt("startMins"));
        assertEquals(4 * 60 + 45, work.getInt("startMins"));
    }

    @Test public void dependentRoutineStartsAfterPackedReferenceAndOffset() throws Exception {
        JSONArray routines = new JSONArray()
                .put(routine("prep", "Persiapan", "shift",
                        new JSONObject().put("type", "fixed").put("time", "09:00"), 30, 30))
                .put(routine("follow", "Lanjutan", "shift",
                        new JSONObject().put("type", "after_block").put("blockId", "prep").put("offsetMins", 10), 30, 30));
        JSONObject schedule = NativeScheduleEngine.build(new JSONObject().put("customRoutines", routines), prayers(), previewDate(), true);

        JSONObject prep = find(schedule.getJSONArray("blocks"), "prep");
        JSONObject follow = find(schedule.getJSONArray("blocks"), "follow");
        assertTrue(follow.getInt("startMins") >= prep.getInt("endMins") + 10);
    }

    @Test public void flexibleWindowsCompressToPresleepBoundaryWhenPossible() throws Exception {
        JSONArray routines = new JSONArray();
        for (int i = 0; i < 3; i++) {
            routines.put(routine("long_" + i, "Aktivitas panjang " + i, "shift",
                    new JSONObject().put("type", "fixed").put("time", "06:15"), 480, 480));
        }
        JSONObject schedule = NativeScheduleEngine.build(new JSONObject().put("customRoutines", routines), prayers(), previewDate(), true);

        assertTrue(schedule.getDouble("compressionRatio") < 1.0);
        for (JSONObject item : objects(schedule.getJSONArray("blocks"))) {
            if ("presleep_gate".equals(item.optString("id")) || "sleep_block".equals(item.optString("id"))) continue;
            assertTrue(item.getInt("endMins") <= 21 * 60 + 15);
        }
    }

    @Test public void manzilMaghribTargetBecomesAResolvableScheduleBlock() throws Exception {
        JSONObject state = new JSONObject().put("priorJuzList", new JSONArray().put(1))
                .put("lvMaghribEnabled", true).put("lvPace", 10);
        JSONObject schedule = NativeScheduleEngine.build(state, prayers(), previewDate(), true);
        JSONObject maghrib = find(schedule.getJSONArray("blocks"), "lv_maghrib_manzil");
        assertEquals("18:30", maghrib.getString("start"));
        assertTrue(maghrib.getString("hifzTarget").contains("Halaman 6–10"));
    }

    private static JSONObject find(JSONArray items, String id) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        throw new AssertionError("Missing schedule block: " + id);
    }

    private static Iterable<JSONObject> objects(JSONArray items) {
        java.util.List<JSONObject> result = new java.util.ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) result.add(item);
        }
        return result;
    }
}
