package com.rutinitas.islamic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Native, offline state storage with import/export compatibility for schema v10 backups. */
public final class NativeStateStore {
    private static final String PREFS_NAME = "routine_native_state";
    private static final String STATE_KEY = "state_v10";
    private static final int SCHEMA_VERSION = 10;

    private final SharedPreferences preferences;

    public NativeStateStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized JSONObject load() {
        String stored = preferences.getString(STATE_KEY, null);
        if (stored == null) return createDefaultState();
        try {
            JSONObject state = new JSONObject(stored);
            if (state.optInt("schemaVersion", -1) != SCHEMA_VERSION) return createDefaultState();
            if (rolloverIfNewDay(state)) save(state);
            try {
                int currentPhase = NativeHifzPlan.build(state, false).optInt("phase", 3);
                if (state.optInt("hifzPhase", 3) != currentPhase) {
                    state.put("hifzPhase", currentPhase);
                    save(state);
                }
            } catch (JSONException ignored) { }
            return state;
        } catch (JSONException exception) {
            return createDefaultState();
        }
    }

    public synchronized boolean hasStoredState() {
        return preferences.contains(STATE_KEY);
    }

    public synchronized void save(JSONObject state) {
        if (state == null) return;
        try {
            state.put("schemaVersion", SCHEMA_VERSION);
            preferences.edit().putString(STATE_KEY, state.toString()).apply();
        } catch (JSONException ignored) {
            // JSONObject.put can only fail for a null key; the fixed key above cannot fail.
        }
    }

    public synchronized JSONObject importBackup(JSONObject bundle) throws JSONException {
        if (bundle == null || bundle.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw new JSONException("Versi cadangan tidak didukung. Aplikasi memerlukan skema v10.");
        }

        JSONObject routines = bundle.optJSONObject("routineProfiles");
        JSONArray activeRoutines = bundle.optJSONArray("customRoutines");
        if (routines == null && activeRoutines == null && bundle.optJSONObject("hifzProgress") == null
                && bundle.optJSONObject("prayerSettings") == null) {
            throw new JSONException("Cadangan tidak memuat data rutinitas, hafalan, atau shalat.");
        }
        validateRoutineArray(activeRoutines, "customRoutines");
        if (routines != null) {
            for (String profileId : new String[]{"profile_1", "profile_2", "profile_3"}) {
                JSONObject profile = routines.optJSONObject(profileId);
                if (profile != null) validateRoutineArray(profile.optJSONArray("customRoutines"), profileId);
            }
        }

        JSONObject state = createDefaultState();
        String activeProfileId = bundle.optString("activeRoutineProfileId", "profile_1");
        if (!activeProfileId.matches("profile_[1-3]")) activeProfileId = "profile_1";
        state.put("activeRoutineProfileId", activeProfileId);
        if (routines != null) state.put("routineProfiles", routines);
        if (activeRoutines != null) state.put("customRoutines", activeRoutines);
        if (routines != null) {
            JSONObject activeProfile = routines.optJSONObject(activeProfileId);
            if (activeProfile != null && activeProfile.optJSONArray("customRoutines") != null) {
                state.put("customRoutines", activeProfile.getJSONArray("customRoutines"));
            }
        }

        JSONObject hifz = bundle.optJSONObject("hifzProgress");
        if (hifz != null) {
            copyIfPresent(hifz, state, "phaseDay");
            copyIfPresent(hifz, state, "sabaq");
            copyIfPresent(hifz, state, "sabqiHistory");
            copyIfPresent(hifz, state, "conservedPartialSpans");
            copyIfPresent(hifz, state, "priorJuzList");
            copyIfPresent(hifz, state, "sabaqFrozen");
            copyIfPresent(hifz, state, "lvPace");
            copyIfPresent(hifz, state, "lvMaghribEnabled");
            copyIfPresent(hifz, state, "HighVolumeJuzPerDay");
            copyIfPresent(hifz, state, "HighVolumeMaghribOn");
            copyIfPresent(hifz, state, "lvManzilPage");
            copyIfPresent(hifz, state, "hvNextJuz");
        }

        JSONObject prayer = bundle.optJSONObject("prayerSettings");
        if (prayer != null) {
            copyIfPresent(prayer, state, "prayerSource");
            copyIfPresent(prayer, state, "manualPrayerTimes");
            copyIfPresent(prayer, state, "gpsLocation");
            copyIfPresent(prayer, state, "gpsMethod");
            copyIfPresent(prayer, state, "subuhOffsetMins");
        }

        JSONObject appearance = bundle.optJSONObject("appearanceSettings");
        if (appearance != null) {
            String theme = appearance.optString("themePreference", "system");
            if (theme.equals("system") || theme.equals("light") || theme.equals("dark")) {
                state.put("themePreference", theme);
            }
        }

        JSONObject runtime = bundle.optJSONObject("nativeRuntime");
        if (runtime != null) {
            for (String key : new String[]{"completedDate", "completedBlocksToday", "primarySubStep",
                    "gateResultToday", "gateAdvancedDate", "blockTimers", "undoStack",
                    "sabaqFrozenForNextDay", "timerAlarmEnabled"}) copyIfPresent(runtime, state, key);
        }
        JSONObject reader = bundle.optJSONObject("quranReader");
        if (reader != null) state.put("quranReader", reader);

        state.put("hifzPhase", resolveImportedPhase(state));
        if (!state.has("completedDate") || state.isNull("completedDate")) state.put("completedDate", todayString());
        rolloverIfNewDay(state);
        state.put("updatedAt", System.currentTimeMillis());
        save(state);
        return state;
    }

    /** Imports the complete canonical state from the previous WebView version without dropping runtime fields. */
    public synchronized JSONObject importLegacyState(JSONObject legacy) throws JSONException {
        if (legacy == null || legacy.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw new JSONException("Data versi lama tidak memakai skema v10.");
        }
        JSONArray routines = legacy.optJSONArray("customRoutines");
        if (routines == null || legacy.optJSONObject("sabaq") == null
                || legacy.optJSONObject("routineProfiles") == null) {
            throw new JSONException("Data versi lama tidak lengkap.");
        }
        validateRoutineArray(routines, "customRoutines");
        JSONObject profiles = legacy.optJSONObject("routineProfiles");
        for (String profileId : new String[]{"profile_1", "profile_2", "profile_3"}) {
            JSONObject profile = profiles.optJSONObject(profileId);
            if (profile != null) validateRoutineArray(profile.optJSONArray("customRoutines"), profileId);
        }
        JSONObject state = new JSONObject(legacy.toString());
        if (!state.optString("activeRoutineProfileId", "profile_1").matches("profile_[1-3]")) {
            state.put("activeRoutineProfileId", "profile_1");
        }
        state.put("hifzPhase", resolveImportedPhase(state));
        rolloverIfNewDay(state);
        state.put("updatedAt", System.currentTimeMillis());
        save(state);
        return state;
    }

    public JSONObject exportBackup(JSONObject state) throws JSONException {
        JSONObject source = state == null ? load() : state;
        JSONObject bundle = new JSONObject();
        bundle.put("schemaVersion", SCHEMA_VERSION);
        bundle.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).format(new Date()));
        bundle.put("appearanceSettings", new JSONObject().put(
                "themePreference", source.optString("themePreference", "system")));
        bundle.put("activeRoutineProfileId", source.optString("activeRoutineProfileId", "profile_1"));
        bundle.put("routineProfiles", source.optJSONObject("routineProfiles") == null
                ? new JSONObject() : source.optJSONObject("routineProfiles"));
        bundle.put("customRoutines", source.optJSONArray("customRoutines") == null
                ? new JSONArray() : source.optJSONArray("customRoutines"));

        JSONObject hifz = new JSONObject();
        for (String key : new String[]{"phaseDay", "sabaq", "sabqiHistory", "conservedPartialSpans",
                "priorJuzList", "sabaqFrozen", "lvPace", "lvMaghribEnabled", "HighVolumeJuzPerDay",
                "HighVolumeMaghribOn", "lvManzilPage", "hvNextJuz"}) {
            copyIfPresent(source, hifz, key);
        }
        bundle.put("hifzProgress", hifz);

        JSONObject prayer = new JSONObject();
        for (String key : new String[]{"prayerSource", "manualPrayerTimes", "gpsLocation", "gpsMethod", "subuhOffsetMins"}) {
            copyIfPresent(source, prayer, key);
        }
        bundle.put("prayerSettings", prayer);

        JSONObject runtime = new JSONObject();
        for (String key : new String[]{"completedDate", "completedBlocksToday", "primarySubStep",
                "gateResultToday", "gateAdvancedDate", "blockTimers", "undoStack",
                "sabaqFrozenForNextDay", "timerAlarmEnabled"}) copyIfPresent(source, runtime, key);
        bundle.put("nativeRuntime", runtime);
        if (source.optJSONObject("quranReader") != null) bundle.put("quranReader", source.optJSONObject("quranReader"));
        return bundle;
    }

    private static int resolveImportedPhase(JSONObject state) {
        try { return NativeHifzPlan.build(state, false).optInt("phase", 3); }
        catch (JSONException ignored) { return state.optInt("hifzPhase", 3) == 4 ? 4 : 3; }
    }

    private static String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    static boolean rolloverIfNewDay(JSONObject state) {
        String today = todayString();
        if (today.equals(state.optString("completedDate", ""))) {
            reconcileGateLogged(state, today);
            return false;
        }
        JSONArray completed = state.optJSONArray("completedBlocksToday");
        JSONObject timers = state.optJSONObject("blockTimers");
        boolean hadStarted = (completed != null && completed.length() > 0)
                || state.optInt("primarySubStep", 0) > 0 || (timers != null && timers.length() > 0);
        String previousGate = state.isNull("gateResultToday") ? null : state.optString("gateResultToday", null);
        boolean failedYesterday = "failed".equals(previousGate);
        boolean missedGate = hadStarted && (previousGate == null || "unlogged_prev".equals(previousGate)
                || "failed_prev".equals(previousGate));
        String carriedWarning = !hadStarted && ("unlogged_prev".equals(previousGate)
                || "failed_prev".equals(previousGate)) ? previousGate : null;
        try {
            state.put("completedDate", today);
            state.put("completedBlocksToday", new JSONArray());
            state.put("blockTimers", new JSONObject());
            state.put("primarySubStep", 0);
            if (state.optBoolean("sabaqFrozen", false) && !state.optBoolean("sabaqFrozenForNextDay", false)) {
                state.put("sabaqFrozen", false);
            }
            state.put("sabaqFrozenForNextDay", false);
            state.put("gateResultToday", failedYesterday ? "failed_prev" : (missedGate ? "unlogged_prev" : carriedWarning));
            state.put("undoStack", new JSONArray());
            reconcileGateLogged(state, today);
        } catch (JSONException ignored) { }
        return true;
    }

    private static void reconcileGateLogged(JSONObject state, String today) {
        if (!today.equals(state.optString("gateAdvancedDate", ""))) return;
        try {
            JSONArray completed = state.optJSONArray("completedBlocksToday");
            if (completed == null) completed = new JSONArray();
            boolean hasGate = false;
            for (int i = 0; i < completed.length(); i++) if ("presleep_gate".equals(completed.optString(i))) hasGate = true;
            if (!hasGate) completed.put("presleep_gate");
            state.put("completedBlocksToday", completed);
            if (state.isNull("gateResultToday") || !state.has("gateResultToday")) state.put("gateResultToday", "passed");
        } catch (JSONException ignored) { }
    }

    public void clear() {
        preferences.edit().remove(STATE_KEY).apply();
    }

    private static JSONObject createDefaultState() {
        JSONObject state = new JSONObject();
        try {
            state.put("schemaVersion", SCHEMA_VERSION);
            state.put("hifzPhase", 3);
            state.put("phaseDay", 1);
            state.put("sabaq", new JSONObject()
                    .put("mode", "half_page")
                    .put("currentPage", 1)
                    .put("subSlot", 0)
                    .put("pagesPerDay", 2)
                    .put("surahNum", 1)
                    .put("startAyah", 1)
                    .put("targetStartSurahNum", 1)
                    .put("targetStartAyah", 1)
                    .put("targetEndSurahNum", 114)
                    .put("targetEndAyah", 6)
                    .put("targetJuz", 0)
                    .put("rangeStartPage", 1)
                    .put("rangeEndPage", 604)
                    .put("ayahsPerDay", 10)
                    .put("sundayNewSabaq", false)
                    .put("sabaqCompleted", false)
                    .put("targetCompleted", false));
            state.put("sabqiHistory", new JSONArray());
            state.put("conservedPartialSpans", new JSONObject());
            state.put("priorJuzList", new JSONArray());
            state.put("sabaqFrozen", false);
            state.put("sabaqFrozenForNextDay", false);
            state.put("lvPace", 10);
            state.put("lvMaghribEnabled", false);
            state.put("HighVolumeJuzPerDay", 1);
            state.put("HighVolumeMaghribOn", false);
            state.put("lvManzilPage", JSONObject.NULL);
            state.put("hvNextJuz", JSONObject.NULL);
            state.put("activeRoutineProfileId", "profile_1");
            state.put("routineProfiles", new JSONObject()
                    .put("profile_1", new JSONObject().put("name", "Profil 1").put("customRoutines", defaultRoutines()))
                    .put("profile_2", new JSONObject().put("name", "Profil 2").put("customRoutines", new JSONArray()))
                    .put("profile_3", new JSONObject().put("name", "Profil 3").put("customRoutines", new JSONArray())));
            state.put("customRoutines", defaultRoutines());
            state.put("prayerSource", "fixed");
            state.put("subuhOffsetMins", 15);
            state.put("manualPrayerTimes", new JSONObject().put("subuh", "04:30")
                    .put("dzuhur", "11:50").put("ashar", "15:10").put("maghrib", "17:55").put("isya", "19:08"));
            state.put("gpsLocation", new JSONObject().put("lat", -6.9175).put("lon", 107.6191)
                    .put("label", "Default Bandung").put("tzOffsetHours", 7).put("hasFix", false));
            state.put("gpsMethod", "kemenag");
            state.put("completedDate", todayString());
            state.put("completedBlocksToday", new JSONArray());
            state.put("primarySubStep", 0);
            state.put("gateResultToday", JSONObject.NULL);
            state.put("gateAdvancedDate", JSONObject.NULL);
            state.put("blockTimers", new JSONObject());
            state.put("undoStack", new JSONArray());
            state.put("timerAlarmEnabled", true);
            state.put("themePreference", "system");
            state.put("updatedAt", 0);
        } catch (JSONException ignored) { }
        return state;
    }

    private static JSONArray defaultRoutines() throws JSONException {
        JSONArray routines = new JSONArray();
        routines.put(new JSONObject()
                .put("id", "daily_dzuhur_hifz")
                .put("title", "Dzuhur + Penguatan Hafalan Siang")
                .put("protocol", "Shalat Dzuhur, 15 menit cek hafalan.")
                .put("anchor", new JSONObject().put("type", "prayer").put("prayer", "dzuhur").put("offsetMins", 15))
                .put("windowMins", 30)
                .put("durationMins", 15)
                .put("isFlexible", true)
                .put("includeHifzTarget", true)
                .put("isCommute", false)
                .put("conflictPolicy", "shift")
                .put("daysOfWeek", new JSONArray().put(0).put(1).put(2).put(3).put(4).put(5).put(6))
                .put("enabled", true));
        return routines;
    }

    private static void copyIfPresent(JSONObject from, JSONObject to, String key) throws JSONException {
        if (from.has(key)) to.put(key, from.get(key));
    }

    private static void validateRoutineArray(JSONArray routines, String label) throws JSONException {
        if (routines == null) return;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < routines.length(); i++) {
            JSONObject routine = routines.optJSONObject(i);
            if (routine == null || routine.optString("id").trim().isEmpty()
                    || routine.optString("title").trim().isEmpty()
                    || routine.optJSONObject("anchor") == null) {
                throw new JSONException(label + "[" + i + "]: data aktivitas tidak lengkap.");
            }
            String id = routine.optString("id");
            if (!ids.add(id)) throw new JSONException(label + ": ID aktivitas duplikat.");
        }
    }
}
