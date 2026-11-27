package com.rutinitas.islamic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class NativeSabqiHistory {
    static final int MAX_UNITS = 7;

    private NativeSabqiHistory() { }

    static boolean isOutsideTarget(JSONArray history, JSONObject sabaq) {
        if (history == null || history.length() == 0 || sabaq == null
                || sabaq.optBoolean("targetCompleted") || sabaq.optBoolean("sabaqCompleted")) return false;
        int first = sabaq.optInt("rangeStartPage", 1);
        int last = sabaq.optInt("rangeEndPage", 604);
        for (int i = 0; i < history.length(); i++) {
            JSONObject entry = history.optJSONObject(i);
            int page = entry == null ? 0 : entry.optInt("approxPage");
            if (page < 1 || page > 604 || (page >= first && page <= last)) return false;
        }
        return true;
    }

    static JSONArray backfill(JSONObject sabaq, Map<Integer, Integer> ayahCounts,
                              Function<JSONObject, String> unitLabel) throws JSONException {
        JSONObject cursor = new JSONObject(sabaq.toString());
        String mode = cursor.optString("mode", "half_page");
        NativeQuranPageMap.AyahRef ayahStart = "ayah".equals(mode) ? targetAyahStart(sabaq, ayahCounts) : null;
        List<JSONObject> recent = new ArrayList<>(MAX_UNITS);
        for (int i = 0; i < MAX_UNITS; i++) {
            JSONObject previous = ayahStart != null ? previousAyahUnit(cursor, ayahStart, ayahCounts)
                    : previousPageUnit(cursor, mode);
            if (previous == null) break;
            recent.add(new JSONObject().put("unitShort", unitLabel.apply(previous))
                    .put("approxPage", previous.getInt("currentPage")).put("mode", mode));
            cursor = previous;
        }
        JSONArray history = new JSONArray();
        for (int i = recent.size() - 1; i >= 0; i--) history.put(recent.get(i));
        return history;
    }

    private static JSONObject previousPageUnit(JSONObject cursor, String mode) throws JSONException {
        int first = cursor.optInt("rangeStartPage", 1);
        int page = cursor.optInt("currentPage", first);
        int lastSlot = "quarter_page".equals(mode) ? 3 : ("half_page".equals(mode) ? 1 : 0);
        int slot = Math.max(0, Math.min(lastSlot, cursor.optInt("subSlot")));
        if (slot == 0 && page <= first) return null;

        JSONObject previous = new JSONObject(cursor.toString());
        if ("multi_page".equals(mode)) {
            int start = Math.max(first, page - Math.max(1, cursor.optInt("pagesPerDay", 2)));
            return previous.put("currentPage", start).put("rangeEndPage", page - 1);
        }
        return previous.put("currentPage", slot > 0 ? page : page - 1)
                .put("subSlot", slot > 0 ? slot - 1 : lastSlot);
    }

    private static NativeQuranPageMap.AyahRef targetAyahStart(JSONObject sabaq, Map<Integer, Integer> counts) {
        int surah = sabaq.optInt("targetStartSurahNum");
        int ayah = sabaq.optInt("targetStartAyah");
        if (surah >= 1 && surah <= 114 && ayah >= 1 && ayah <= counts.getOrDefault(surah, 1)) {
            return new NativeQuranPageMap.AyahRef(surah, ayah);
        }
        return NativeQuranPageMap.pageEdge(sabaq.optInt("rangeStartPage", 1), false, counts);
    }

    private static JSONObject previousAyahUnit(JSONObject cursor, NativeQuranPageMap.AyahRef first,
                                               Map<Integer, Integer> counts) throws JSONException {
        int firstSurah = first.surah;
        int firstAyah = first.ayah;
        int surah = cursor.optInt("surahNum", firstSurah);
        int ayah = cursor.optInt("startAyah", firstAyah);
        int remaining = Math.max(1, cursor.optInt("ayahsPerDay", 10));
        int taken = 0;
        while (remaining > 0 && surah >= firstSurah) {
            int floor = surah == firstSurah ? firstAyah : 1;
            int move = Math.min(remaining, Math.max(0, ayah - floor));
            ayah -= move;
            remaining -= move;
            taken += move;
            if (remaining == 0 || surah == firstSurah) break;
            ayah = counts.getOrDefault(--surah, 1);
            remaining--;
            taken++;
        }
        if (taken == 0) return null;
        int page = Math.max(cursor.optInt("rangeStartPage", 1), NativeQuranPageMap.estimatePage(surah, ayah, counts));
        return new JSONObject(cursor.toString()).put("surahNum", surah).put("startAyah", ayah)
                .put("currentPage", page).put("ayahsPerDay", taken);
    }
}
