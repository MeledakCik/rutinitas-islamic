package com.rutinitas.islamic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Builds the daily native Manzil, Sabqi, and Sabaq prescription from saved Hifz progress. */
public final class NativeHifzPlan {
    private static final int[] JUZ_START_PAGE = {1,22,42,62,82,102,121,142,162,182,201,222,242,262,282,302,322,342,362,382,402,422,442,462,482,502,522,542,562,582};
    private static final int[] JUZ_END_PAGE = {21,41,61,81,101,120,141,161,181,200,221,241,261,281,301,321,341,361,381,401,421,441,461,481,501,521,541,561,581,604};

    private NativeHifzPlan() { }

    public static JSONObject build(JSONObject state, boolean sunday) throws JSONException {
        return build(state, sunday, false);
    }

    static JSONObject buildWithManzilList(JSONObject state, boolean sunday) throws JSONException {
        return build(state, sunday, true);
    }

    private static JSONObject build(JSONObject state, boolean sunday, boolean includeManzilList) throws JSONException {
        JSONObject source = state == null ? new JSONObject() : state;
        JSONObject sabaq = source.optJSONObject("sabaq");
        if (sabaq == null) sabaq = new JSONObject();
        int firstPage = clamp(sabaq.optInt("rangeStartPage", 1), 1, 604);
        int lastPage = clamp(sabaq.optInt("rangeEndPage", 604), firstPage, 604);
        int currentPage = clamp(sabaq.optInt("currentPage", firstPage), firstPage, lastPage);
        boolean targetDone = sabaq.optBoolean("sabaqCompleted", false) || sabaq.optBoolean("targetCompleted", false);
        boolean fullMushaf = sabaq.optInt("rangeStartPage", 1) == 1 && sabaq.optInt("rangeEndPage", 604) == 604;
        int frontier = sabaq.optBoolean("sabaqCompleted", false) && fullMushaf ? 604
                : (targetDone ? Math.min(lastPage, currentPage) : Math.min(lastPage, Math.max(firstPage - 1, currentPage - 1)));

        JSONArray history = source.optJSONArray("sabqiHistory");
        int oldestSabqiPage = Integer.MAX_VALUE;
        if (history != null) for (int i = Math.max(0, history.length() - NativeSabqiHistory.MAX_UNITS); i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;
            int page = item.optInt("approxPage", 0);
            if (page >= firstPage && page <= currentPage) oldestSabqiPage = Math.min(oldestSabqiPage, page);
        }
        int graduated = oldestSabqiPage == Integer.MAX_VALUE ? frontier : Math.max(0, Math.min(frontier, oldestSabqiPage - 1));
        JSONObject conserved = source.optJSONObject("conservedPartialSpans");
        int partialPage = 0;
        String partialLabel = "";
        if (conserved != null) {
            for (int page = firstPage; page <= lastPage; page++) {
                if (!conserved.has(String.valueOf(page))) continue;
                graduated = page <= graduated ? Math.min(graduated, page - 1) : graduated;
                if (page <= currentPage && (partialPage == 0 || page < partialPage)) {
                    JSONObject span = conserved.optJSONObject(String.valueOf(page));
                    partialPage = page;
                    partialLabel = span == null ? "hafalan sebagian" : span.optString("label", "hafalan sebagian");
                }
            }
        }

        Set<Integer> prior = parsePriorJuz(source.opt("priorJuzList"));
        TreeSet<Integer> memorized = new TreeSet<>();
        for (int juz : prior) for (int page = JUZ_START_PAGE[juz - 1]; page <= JUZ_END_PAGE[juz - 1]; page++) memorized.add(page);
        for (int page = firstPage; page <= Math.min(lastPage, graduated); page++) memorized.add(page);
        int phase = memorized.size() > 141 ? 4 : 3;
        boolean frozen = source.optBoolean("sabaqFrozen", false);
        int totalSteps = frozen ? 2 : 3;
        JSONObject plan = new JSONObject().put("phase", phase).put("maxGraduatedPage", graduated)
                .put("totalSteps", totalSteps).put("partialPage", partialPage)
                .put("partialLabel", partialLabel).put("steps", new JSONArray());

        String sabqi = sabqiText(history);
        boolean weeklyAudit = sunday && !sabaq.optBoolean("sundayNewSabaq", false) && !targetDone;
        JSONArray steps = plan.getJSONArray("steps");
        JSONArray manzilList = includeManzilList ? new JSONArray() : null;
        String maghribTarget = "";
        String dawnTarget;
        if (phase == 3) {
            List<Integer> pool = new ArrayList<>();
            for (int page : memorized) if (page >= firstPage && page <= lastPage || priorPage(prior, page)) pool.add(page);
            int pace = source.optBoolean("lvMaghribEnabled", false) && source.optInt("lvPace", 10) == 20 ? 20 : 10;
            int cursor = source.optInt("lvManzilPage", pool.isEmpty() ? firstPage : pool.get(0));
            int index = resolvePageIndex(pool, cursor);
            int count = Math.min(pace, pool.size());
            List<Integer> slice = new ArrayList<>();
            for (int i = 0; i < count; i++) slice.add(pool.get((index + i) % pool.size()));
            int cycleDays = Math.max(1, (int) Math.ceil(pool.size() / (double) Math.max(1, count)));
            int pass = pool.isEmpty() ? 1 : Math.min(cycleDays, (int) Math.floor(index * cycleDays / (double) pool.size()) + 1);
            plan.put("manzilCursorNext", pool.isEmpty() ? JSONObject.NULL : pool.get((index + count) % pool.size()))
                    .put("manzilCount", count).put("manzilPoolSize", pool.size())
                    .put("manzilCycleDays", cycleDays).put("manzilPassNo", pass);
            if (pool.isEmpty()) dawnTarget = partialPage > 0
                    ? "Halaman " + partialPage + " · " + partialLabel
                    : "Belum ada Manzil · mulai halaman " + currentPage;
            else if (source.optBoolean("lvMaghribEnabled", false) && slice.size() >= 2) {
                int split = (slice.size() + 1) / 2;
                dawnTarget = pagesTarget(slice.subList(0, split));
                maghribTarget = pagesTarget(slice.subList(split, slice.size()));
            } else dawnTarget = pagesTarget(slice);
            if (partialPage > 0 && pool.contains(graduated)) {
                String suffix = " + halaman " + partialPage + " (" + partialLabel + ")";
                if (!maghribTarget.isEmpty()) maghribTarget += suffix;
                else if (!pool.isEmpty()) dawnTarget += suffix;
            }
            if (manzilList != null) {
                for (int offset = 0; offset < pool.size(); offset += pace) {
                    List<Integer> pages = new ArrayList<>();
                    for (int i = offset; i < Math.min(offset + pace, pool.size()); i++) {
                        pages.add(pool.get((index + i) % pool.size()));
                    }
                    manzilList.put(manzilEntry(pagesTarget(pages), offset == 0));
                }
            }
        } else {
            List<Integer> roster = memorizedJuzRoster(firstPage, lastPage, graduated, prior, partialPage);
            int pace = source.optInt("HighVolumeJuzPerDay", 1) == 2 ? 2 : 1;
            plan.put("manzilCursorFirst", roster.isEmpty() ? JSONObject.NULL : roster.get(0));
            int cursor = source.optInt("hvNextJuz", 0);
            int index = resolveJuzIndex(roster, cursor);
            if (cursor < 1 && !roster.isEmpty()) {
                int cycle = Math.max(1, (int) Math.ceil(roster.size() / (double) pace));
                index = ((Math.max(1, source.optInt("phaseDay", 1)) - 1) % cycle) * pace;
                if (index >= roster.size()) index = 0;
            }
            List<Integer> picked = new ArrayList<>();
            if (!roster.isEmpty()) {
                picked.add(roster.get(index));
                if (pace == 2 && roster.size() > 1) picked.add(roster.get((index + 1) % roster.size()));
            }
            int nextJuz = picked.isEmpty() ? 0 : picked.get(picked.size() - 1) % 30 + 1;
            plan.put("manzilCursorNext", picked.isEmpty() ? JSONObject.NULL : nextJuz)
                    .put("manzilCount", picked.size()).put("manzilPoolSize", roster.size())
                    .put("manzilCycleDays", Math.max(1, (int) Math.ceil(roster.size() / (double) pace)))
                    .put("manzilPassNo", roster.isEmpty() ? 1 : index / pace + 1);
            if (picked.isEmpty()) dawnTarget = "Belum ada Juz di Manzil · isi Juz hafalan lama";
            else if (pace == 2 && picked.size() == 2) {
                dawnTarget = juzTarget(picked.get(0), firstPage, lastPage, graduated, prior, partialPage, partialLabel);
                maghribTarget = juzTarget(picked.get(1), firstPage, lastPage, graduated, prior, partialPage, partialLabel);
            } else {
                int juz = picked.get(0);
                boolean hasMaghrib = source.optBoolean("HighVolumeMaghribOn", false);
                if (hasMaghrib || pace == 2) {
                    int start = Math.max(JUZ_START_PAGE[juz - 1], firstPage);
                    int end = Math.min(Math.min(JUZ_END_PAGE[juz - 1], lastPage), graduated);
                    if (start > end) { start = JUZ_START_PAGE[juz - 1]; end = JUZ_END_PAGE[juz - 1]; }
                    int mid = start + (end - start + 1) / 2 - 1;
                    dawnTarget = "Juz " + juz + " · halaman " + pageRange(start, mid);
                    maghribTarget = "Juz " + juz + " · halaman " + pageRange(mid + 1, end);
                    if (partialPage >= start && partialPage <= end) maghribTarget += " + halaman " + partialPage + " (" + partialLabel + ")";
                } else dawnTarget = juzTarget(juz, firstPage, lastPage, graduated, prior, partialPage, partialLabel);
            }
            if (manzilList != null) {
                for (int offset = 0; offset < roster.size(); offset++) {
                    int juz = roster.get((index + offset) % roster.size());
                    String label = juzTarget(juz, firstPage, lastPage, graduated, prior, partialPage, partialLabel);
                    manzilList.put(manzilEntry(label, picked.contains(juz)));
                }
            }
        }

        boolean maghribEnabled = !maghribTarget.isEmpty() && phase == 3 && source.optBoolean("lvMaghribEnabled", false)
                || !maghribTarget.isEmpty() && phase == 4
                && (source.optBoolean("HighVolumeMaghribOn", false) || source.optInt("HighVolumeJuzPerDay", 1) == 2);
        if (!maghribEnabled) maghribTarget = "";
        int[] durations = stepDurations(source, phase, maghribEnabled);
        plan.put("dawnTarget", dawnTarget).put("maghribTarget", maghribTarget)
                .put("sabqiTarget", sabqi).put("sabaqTarget", sabaqTarget(sabaq))
                .put("weeklyAudit", weeklyAudit).put("sabaqFrozen", frozen);
        if (manzilList != null) plan.put("manzilList", manzilList);
        steps.put(new JSONObject().put("label", "1. MANZIL").put("durationMins", durations[0]).put("target", dawnTarget));
        steps.put(new JSONObject().put("label", "2. SABQI (1–7 HARI BERGULIR)").put("durationMins", durations[1]).put("target", sabqi));
        if (!frozen) steps.put(new JSONObject().put("label", weeklyAudit ? "3. AUDIT PEKANAN MINGGU (0 SABAQ BARU)" : "3. SABAQ")
                .put("durationMins", durations[2]).put("target", weeklyAudit ? "Audit Sabqi 7 Hari · Berikutnya: " + sabaqTarget(sabaq) : sabaqTarget(sabaq)));
        return plan;
    }

    private static JSONObject manzilEntry(String label, boolean today) throws JSONException {
        return new JSONObject().put("label", label).put("today", today);
    }

    static int juzForPage(int page) {
        for (int i = 0; i < JUZ_END_PAGE.length; i++) {
            if (page <= JUZ_END_PAGE[i]) return i + 1;
        }
        return 30;
    }

    private static int[] stepDurations(JSONObject state, int phase, boolean maghrib) {
        boolean frozen = state.optBoolean("sabaqFrozen", false);
        if (frozen) return phase == 4 && !maghrib ? new int[]{55,35,0} : new int[]{45,45,0};
        if (phase == 4) return maghrib ? new int[]{40,20,30} : new int[]{45,20,25};
        return new int[]{30,30,30};
    }

    private static List<Integer> memorizedJuzRoster(int first, int last, int graduated, Set<Integer> prior, int partialPage) {
        List<Integer> result = new ArrayList<>();
        for (int juz = 1; juz <= 30; juz++) {
            if (prior.contains(juz)) { result.add(juz); continue; }
            int learnedStart = Math.max(JUZ_START_PAGE[juz - 1], first);
            int learnedEnd = Math.min(Math.min(JUZ_END_PAGE[juz - 1], last), graduated);
            if (learnedEnd >= learnedStart || (partialPage >= Math.max(JUZ_START_PAGE[juz - 1], first)
                    && partialPage <= Math.min(JUZ_END_PAGE[juz - 1], last))) result.add(juz);
        }
        return result;
    }

    private static String juzTarget(int juz, int first, int last, int graduated, Set<Integer> prior,
                                    int partialPage, String partialLabel) {
        if (prior.contains(juz)) return "Juz " + juz + " (halaman " + JUZ_START_PAGE[juz - 1] + "–" + JUZ_END_PAGE[juz - 1] + ")";
        int start = Math.max(JUZ_START_PAGE[juz - 1], first);
        int end = Math.min(Math.min(JUZ_END_PAGE[juz - 1], last), graduated);
        boolean onlyPartial = end < start;
        if (onlyPartial) { start = partialPage; end = partialPage; }
        String result = "Juz " + juz + " (halaman " + pageRange(start, end) + ")";
        if (onlyPartial && partialPage > 0) result = "Juz " + juz + " (halaman " + partialPage + " · " + partialLabel + ")";
        if (!onlyPartial && partialPage > 0 && partialPage >= JUZ_START_PAGE[juz - 1]
                && partialPage <= JUZ_END_PAGE[juz - 1]) result += " + halaman " + partialPage + " (" + partialLabel + ")";
        return result;
    }

    private static String pagesTarget(List<Integer> pages) {
        return "Halaman " + pageRuns(pages) + " (" + pages.size() + " halaman)";
    }

    private static String pageRuns(List<Integer> pages) {
        if (pages.isEmpty()) return "—";
        List<String> runs = new ArrayList<>();
        int start = pages.get(0), end = start;
        for (int i = 1; i < pages.size(); i++) {
            int page = pages.get(i);
            if (page == end + 1) end = page;
            else { runs.add(pageRange(start, end)); start = end = page; }
        }
        runs.add(pageRange(start, end));
        return String.join(" & ", runs);
    }

    private static String pageRange(int start, int end) { return start == end ? String.valueOf(start) : start + "–" + end; }

    private static int resolvePageIndex(List<Integer> pages, int cursor) {
        if (pages.isEmpty()) return 0;
        for (int i = 0; i < pages.size(); i++) if (pages.get(i) >= cursor) return i;
        return 0;
    }

    private static int resolveJuzIndex(List<Integer> juzs, int cursor) {
        if (juzs.isEmpty()) return 0;
        if (cursor >= 1) for (int i = 0; i < juzs.size(); i++) if (juzs.get(i) >= cursor) return i;
        return 0;
    }

    private static Set<Integer> parsePriorJuz(Object raw) {
        Set<Integer> result = new TreeSet<>();
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                try { int juz = Integer.parseInt(String.valueOf(item)); if (juz >= 1 && juz <= 30) result.add(juz); }
                catch (NumberFormatException ignored) { }
            }
        } else if (raw instanceof String) {
            for (String token : ((String) raw).split("[,;\\s]+")) {
                try { int juz = Integer.parseInt(token); if (juz >= 1 && juz <= 30) result.add(juz); }
                catch (NumberFormatException ignored) { }
            }
        }
        return result;
    }

    private static boolean priorPage(Set<Integer> prior, int page) {
        for (int juz : prior) if (page >= JUZ_START_PAGE[juz - 1] && page <= JUZ_END_PAGE[juz - 1]) return true;
        return false;
    }

    private static String sabqiText(JSONArray history) {
        if (history == null || history.length() == 0) return "Belum ada riwayat Sabqi";
        List<String> items = new ArrayList<>();
        int start = Math.max(0, history.length() - NativeSabqiHistory.MAX_UNITS);
        for (int i = start; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item != null && !item.optString("unitShort", "").trim().isEmpty()) items.add(item.optString("unitShort"));
        }
        return items.isEmpty() ? "Belum ada riwayat Sabqi" : String.join(" · ", items);
    }

    private static String sabaqTarget(JSONObject sabaq) {
        if (sabaq.optBoolean("sabaqCompleted", false)) return "Khatam 30 Juz selesai · mode pemeliharaan";
        if (sabaq.optBoolean("targetCompleted", false)) return "Target pilihan halaman "
                + sabaq.optInt("rangeStartPage", 1) + "–" + sabaq.optInt("rangeEndPage", 604) + " selesai";
        int page = sabaq.optInt("currentPage", 1), slot = sabaq.optInt("subSlot", 0);
        String prefix = sabaq.optInt("targetJuz", 0) > 0 ? "Juz " + sabaq.optInt("targetJuz") + " · "
                : ((sabaq.optInt("rangeStartPage", 1) != 1 || sabaq.optInt("rangeEndPage", 604) != 604)
                ? "Target halaman " + sabaq.optInt("rangeStartPage", 1) + "–" + sabaq.optInt("rangeEndPage", 604) + " · " : "");
        switch (sabaq.optString("mode", "half_page")) {
            case "quarter_page": return prefix + "Halaman " + page + " · " + new String[]{"Baris 1–4","Baris 5–8","Baris 9–12","Baris 13–15"}[clamp(slot,0,3)];
            case "half_page": return prefix + "Halaman " + page + " · " + (slot == 0 ? "Atas (baris 1–8)" : "Bawah (baris 9–15)");
            case "multi_page": return prefix + "Halaman " + page + "–" + Math.min(sabaq.optInt("rangeEndPage",604), page + Math.max(1,sabaq.optInt("pagesPerDay",2)) - 1);
            case "ayah": return prefix + "Mulai " + QuranSurahNames.reference(sabaq.optInt("surahNum",1), sabaq.optInt("startAyah",1)) + " · " + sabaq.optInt("ayahsPerDay",10) + " ayat";
            default: return prefix + "Halaman " + page;
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
