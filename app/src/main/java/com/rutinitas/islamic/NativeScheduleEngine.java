package com.rutinitas.islamic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Native port of the daily schedule resolver and its conflict compressor. */
public final class NativeScheduleEngine {
    private static final int EVALUATION_END = 21 * 60 + 15;
    private static final int SLEEP_START = 22 * 60;
    private static final int PLANNED_HIFZ_WINDOW = 90;

    private NativeScheduleEngine() { }

    public static JSONObject build(JSONObject state, Map<String, String> prayerTimes, Date date,
                                   boolean preview) throws JSONException {
        JSONObject source = state == null ? new JSONObject() : state;
        Map<String, String> prayers = prayerTimes == null ? Collections.emptyMap() : prayerTimes;
        Date targetDate = date == null ? new Date() : date;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(targetDate);
        int day = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int nowMins = preview ? 0 : Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                + Calendar.getInstance().get(Calendar.MINUTE);
        String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(targetDate);
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        boolean sameCompletedDate = !preview && (dateKey.equals(source.optString("completedDate", ""))
                || dateKey.equals(todayKey));
        JSONArray completedRaw = sameCompletedDate ? source.optJSONArray("completedBlocksToday") : null;
        Set<String> completed = new HashSet<>();
        if (completedRaw != null) for (int i = 0; i < completedRaw.length(); i++) completed.add(completedRaw.optString(i));

        boolean sunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
        JSONObject hifzPlan = NativeHifzPlan.build(source, sunday);
        int hifzPhase = hifzPlan.optInt("phase", source.optInt("hifzPhase", 3));
        int[] stepDurations = stepDurations(hifzPlan);
        int totalSteps = Math.max(1, hifzPlan.optInt("totalSteps", stepDurations.length));
        int plannedStart = Math.max(0, toMinutes(prayers.getOrDefault("subuh", "04:45"))
                + source.optInt("subuhOffsetMins", 15));
        JSONObject timers = source.optJSONObject("blockTimers");
        int subStep = Math.max(0, Math.min(totalSteps - 1, source.optInt("primarySubStep", 0)));
        boolean hifzDone = sameCompletedDate && completed.contains("primary_hifz");
        JSONObject hifzWindow = timers == null ? null : timers.optJSONObject("primary_hifz_window");
        int liveStart = plannedStart;
        int liveEnd = plannedStart + PLANNED_HIFZ_WINDOW;
        boolean hifzRunning = false;
        if (!preview && !hifzDone) {
            int remaining = 0;
            for (int i = subStep; i < stepDurations.length; i++) remaining += stepDurations[i];
            if (hifzWindow != null && hifzWindow.optLong("startedAtMs", 0) > 0) {
                Calendar started = Calendar.getInstance();
                started.setTimeInMillis(hifzWindow.optLong("startedAtMs"));
                int startedMins = started.get(Calendar.HOUR_OF_DAY) * 60 + started.get(Calendar.MINUTE);
                liveStart = startedMins;
                liveEnd = Math.max(startedMins + PLANNED_HIFZ_WINDOW, nowMins + remaining);
                hifzRunning = true;
            } else {
                liveStart = Math.max(plannedStart, nowMins);
                liveEnd = liveStart + remaining;
            }
        }

        JSONObject primary = new JSONObject()
                .put("id", "primary_hifz").put("layer", "hifz")
                .put("title", "Hafalan Al-Qur’an Subuh")
                .put("protocol", "Selesaikan Manzil, Sabqi, dan Sabaq secara berurutan.")
                .put("isPrimaryHifz", true).put("isFlexible", true).put("conflictPolicy", "strict")
                .put("origStartMins", plannedStart).put("origEndMins", plannedStart + PLANNED_HIFZ_WINDOW)
                .put("startMins", liveStart).put("endMins", liveEnd)
                .put("origWindowMins", PLANNED_HIFZ_WINDOW).put("windowMins", liveEnd - liveStart)
                .put("durationMins", PLANNED_HIFZ_WINDOW).put("effectiveDurationMins", PLANNED_HIFZ_WINDOW)
                .put("shiftMins", Math.max(0, liveStart - plannedStart))
                .put("isRunning", hifzRunning).put("isCompleted", hifzDone).put("isDeferred", false);
        List<JSONObject> conflicts = new ArrayList<>();
        List<JSONObject> skipped = new ArrayList<>();
        List<JSONObject> deferred = new ArrayList<>();
        Map<String, JSONObject> resolved = new HashMap<>();
        resolved.put("primary_hifz", primary);

        JSONObject maghribBlock = null;
        String maghribTarget = hifzPlan.optString("maghribTarget", "").trim();
        if (!maghribTarget.isEmpty()) {
            boolean fixedSource = "fixed".equals(source.optString("prayerSource", "fixed"));
            int magStart = fixedSource ? toMinutes("18:30") : toMinutes(prayers.getOrDefault("maghrib", "18:00")) + 20;
            int magEnd;
            if (fixedSource) magEnd = toMinutes("19:05");
            else {
                int isya = toMinutes(prayers.getOrDefault("isya", "19:15"));
                magEnd = Math.min(magStart + 35, Math.max(magStart + 20, isya - 5));
            }
            int magDuration = Math.max(20, magEnd - magStart);
            String magId = hifzPhase == 4 ? "hv_maghrib_manzil" : "lv_maghrib_manzil";
            maghribBlock = new JSONObject().put("id", magId).put("layer", "hifz")
                    .put("title", "Manzil Maghrib · " + maghribTarget).put("protocol", "Manzil Maghrib.")
                    .put("hifzTarget", maghribTarget).put("isFlexible", true).put("conflictPolicy", "shift")
                    .put("origStartMins", magStart).put("origEndMins", magStart + magDuration)
                    .put("startMins", magStart).put("endMins", magStart + magDuration)
                    .put("origWindowMins", magDuration).put("windowMins", magDuration)
                    .put("origDurationMins", magDuration).put("durationMins", magDuration)
                    .put("effectiveDurationMins", magDuration).put("shiftMins", 0)
                    .put("isDeferred", false).put("isCompleted", sameCompletedDate && completed.contains(magId));
            resolved.put(magId, maghribBlock);
        }

        List<JSONObject> active = activeRoutines(source.optJSONArray("customRoutines"), day);
        List<JSONObject> ordered = topologicalOrder(active, resolved.keySet());
        List<JSONObject> routines = new ArrayList<>();
        for (JSONObject raw : ordered) {
            JSONObject anchor = raw.optJSONObject("anchor");
            int start = resolveAnchor(anchor, prayers, resolved, liveEnd);
            int window = raw.optInt("windowMins", 30);
            JSONObject item = copy(raw)
                    .put("layer", "custom").put("origStartMins", start).put("origEndMins", start + window)
                    .put("startMins", start).put("endMins", start + window)
                    .put("origWindowMins", window).put("origDurationMins", raw.optInt("durationMins", window))
                    .put("effectiveDurationMins", raw.optInt("durationMins", window))
                    .put("shiftMins", 0).put("isDeferred", false)
                    .put("isCompleted", sameCompletedDate && completed.contains(raw.optString("id")));
            if (raw.optBoolean("includeHifzTarget", false)) item.put("hifzTarget", nativeHifzTarget(source, hifzPlan));
            routines.add(item);
            resolved.put(item.optString("id"), item);
        }
        List<JSONObject> candidates = new ArrayList<>();
        if (maghribBlock != null) candidates.add(maghribBlock);
        candidates.addAll(routines);

        if (!hifzDone && !hifzRunning) {
            int originalStart = primary.optInt("startMins");
            int start = originalStart;
            for (int guard = 0; guard < 60; guard++) {
                int nextStart = start;
                for (JSONObject routine : routines) {
                    if (routine.optBoolean("isCompleted") || !"strict".equals(routine.optString("conflictPolicy"))) continue;
                    if (overlaps(start, start + liveEnd - liveStart,
                            routine.optInt("startMins"), routine.optInt("endMins"))) {
                        nextStart = Math.max(nextStart, routine.optInt("endMins"));
                    }
                }
                if (nextStart == start) break;
                start = nextStart;
            }
            int duration = Math.max(1, liveEnd - liveStart);
            if (start > originalStart && start + duration > EVALUATION_END) {
                conflicts.add(new JSONObject().put("message", "Hafalan Subuh berbenturan dengan aktivitas prioritas; tidak digeser melewati evaluasi 21:15."));
                start = originalStart;
            }
            if (start != originalStart) {
                primary.put("startMins", start).put("endMins", start + duration)
                        .put("shiftMins", Math.max(0, start - plannedStart));
                for (JSONObject routine : routines) {
                    JSONObject anchor = routine.optJSONObject("anchor");
                    if (anchor != null && "after_block".equals(anchor.optString("type"))
                            && "primary_hifz".equals(anchor.optString("blockId"))) {
                        int anchored = start + duration + anchor.optInt("offsetMins", 0);
                        routine.put("origStartMins", anchored).put("origEndMins", anchored + routine.optInt("windowMins"))
                                .put("startMins", anchored).put("endMins", anchored + routine.optInt("windowMins"));
                    }
                }
            }
        }
        int hifzEnd = primary.optInt("endMins");
        if (!hifzDone && hifzEnd > EVALUATION_END) {
            conflicts.add(new JSONObject().put("message", "Hafalan Subuh melewati evaluasi 21:15."));
        }

        List<JSONObject> fixed = new ArrayList<>();
        if (!hifzDone) fixed.add(obstacle(primary));
        List<JSONObject> strict = new ArrayList<>(), completedItems = new ArrayList<>(), runningFlex = new ArrayList<>();
        List<JSONObject> shiftPool = new ArrayList<>(), skipPool = new ArrayList<>();
        for (JSONObject routine : candidates) {
            if (routine.optBoolean("isCompleted")) { completedItems.add(routine); continue; }
            String policy = routine.optString("conflictPolicy", "strict");
            if ("skip_if_busy".equals(policy)) { skipPool.add(routine); continue; }
            if ("strict".equals(policy)) {
                JSONObject hit = null;
                for (JSONObject obstacle : fixed) {
                    if ("primary_hifz".equals(obstacle.optString("id")) && !hifzRunning) continue;
                    if (overlaps(routine.optInt("startMins"), routine.optInt("endMins"),
                            obstacle.optInt("startMins"), obstacle.optInt("endMins"))) { hit = obstacle; break; }
                }
                if (hit != null) conflicts.add(new JSONObject().put("message", routine.optString("title") + " berbenturan dengan " + hit.optString("title")));
                fixed.add(obstacle(routine));
                strict.add(routine);
                continue;
            }
            JSONObject run = timers == null ? null : timers.optJSONObject(routine.optString("id"));
            if (sameCompletedDate && run != null && run.optLong("startedAtMs", 0) > 0) {
                Calendar started = Calendar.getInstance();
                started.setTimeInMillis(run.optLong("startedAtMs"));
                int s = started.get(Calendar.HOUR_OF_DAY) * 60 + started.get(Calendar.MINUTE);
                int dur = Math.max(1, (int) Math.ceil(run.optDouble("durSecs", routine.optInt("durationMins") * 60) / 60.0));
                int e = Math.max(nowMins + 1, s + dur);
                routine.put("startMins", s).put("endMins", e).put("windowMins", e - s)
                        .put("shiftMins", Math.max(0, s - routine.optInt("origStartMins")));
                fixed.add(obstacle(routine));
                runningFlex.add(routine);
            } else shiftPool.add(routine);
        }

        Comparator<JSONObject> byStart = Comparator.comparingInt((JSONObject o) -> o.optInt("origStartMins"))
                .thenComparingInt(o -> "hifz".equals(o.optString("layer")) ? 0 : 1);
        shiftPool.sort(byStart);
        List<JSONObject> baseAnchors = new ArrayList<>();
        baseAnchors.add(primary); baseAnchors.addAll(completedItems); baseAnchors.addAll(strict); baseAnchors.addAll(runningFlex);
        double compression = 1.0;
        List<JSONObject> packed = pack(shiftPool, fixed, baseAnchors, nowMins, preview, 1.0);
        if (maxEnd(packed) > EVALUATION_END) {
            List<JSONObject> minimum = pack(shiftPool, fixed, baseAnchors, nowMins, preview, 0.0);
            if (maxEnd(minimum) <= EVALUATION_END) {
                double lo = 0, hi = 1;
                for (int i = 0; i < 24; i++) {
                    double mid = (lo + hi) / 2.0;
                    if (maxEnd(pack(shiftPool, fixed, baseAnchors, nowMins, preview, mid)) <= EVALUATION_END) lo = mid;
                    else hi = mid;
                }
                compression = lo;
                packed = pack(shiftPool, fixed, baseAnchors, nowMins, preview, compression);
            } else { compression = 0; packed = minimum; }
        }
        List<JSONObject> activeDay = new ArrayList<>();
        activeDay.add(primary); activeDay.addAll(completedItems); activeDay.addAll(strict); activeDay.addAll(runningFlex);
        for (JSONObject block : packed) {
            if (block.optBoolean("isDeferred")) {
                deferred.add(display(block));
                conflicts.add(new JSONObject().put("message", block.optString("title") + " melewati evaluasi 21:15 meski durasinya sudah dipangkas."));
            } else activeDay.add(block);
        }
        List<JSONObject> keepSkip = new ArrayList<>();
        for (JSONObject block : skipPool) {
            JSONObject hit = null;
            for (JSONObject placed : activeDay) {
                if (!placed.optBoolean("isCompleted") && overlaps(block.optInt("origStartMins"), block.optInt("origEndMins"),
                        placed.optInt("startMins"), placed.optInt("endMins"))) { hit = placed; break; }
            }
            if (hit != null || block.optInt("origEndMins") > EVALUATION_END) {
                String reason = hit != null ? "Sibuk dengan " + hit.optString("title") : "Melewati Evaluasi 21:15";
                skipped.add(new JSONObject().put("id", block.optString("id")).put("title", block.optString("title"))
                        .put("reason", reason).put("start", time(block.optInt("origStartMins")))
                        .put("end", time(block.optInt("origEndMins"))));
            } else keepSkip.add(block);
        }
        activeDay.addAll(keepSkip);
        activeDay.sort(Comparator.comparingInt(o -> o.optInt("startMins")));
        JSONArray blocks = new JSONArray();
        int earliest = Integer.MAX_VALUE, lastEnd = 0;
        for (JSONObject b : activeDay) {
            earliest = Math.min(earliest, b.optInt("origStartMins", b.optInt("startMins")));
            if (!b.optBoolean("isDeferred")) lastEnd = Math.max(lastEnd, b.optInt("endMins"));
            blocks.put(display(b));
        }
        int wake = Math.min(4 * 60 + 30, earliest == Integer.MAX_VALUE ? 4 * 60 + 30 : earliest);
        int sleepDuration = (24 * 60 - SLEEP_START) + wake;
        blocks.put(new JSONObject().put("id", "presleep_gate").put("layer", "hifz")
                .put("title", "Evaluasi Sebelum Tidur").put("protocol", "Cek hafalan hari ini sebelum pukul 22:00.")
                .put("start", time(EVALUATION_END)).put("end", time(SLEEP_START))
                .put("origStart", time(EVALUATION_END)).put("origEnd", time(SLEEP_START))
                .put("startMins", EVALUATION_END).put("endMins", SLEEP_START).put("origStartMins", EVALUATION_END)
                .put("origEndMins", SLEEP_START).put("durationMins", 45).put("effectiveDurationMins", 45)
                .put("isCompleted", sameCompletedDate && completed.contains("presleep_gate")).put("isDeferred", false));
        blocks.put(new JSONObject().put("id", "sleep_block").put("layer", "hifz")
                .put("title", "Pemulihan & Tidur Terjaga").put("protocol", "Tidur teratur menjaga memori hafalan Al-Qur’an.")
                .put("start", time(SLEEP_START)).put("end", time(wake))
                .put("origStart", time(SLEEP_START)).put("origEnd", time(wake))
                .put("startMins", SLEEP_START).put("endMins", SLEEP_START + sleepDuration)
                .put("origStartMins", SLEEP_START).put("origEndMins", SLEEP_START + sleepDuration)
                .put("durationMins", sleepDuration).put("effectiveDurationMins", sleepDuration).put("isDeferred", false));
        JSONArray conflictJson = new JSONArray(), skippedJson = new JSONArray(), deferredJson = new JSONArray();
        for (JSONObject item : conflicts) conflictJson.put(item);
        for (JSONObject item : skipped) skippedJson.put(item);
        for (JSONObject item : deferred) deferredJson.put(item);
        return new JSONObject().put("date", dateKey).put("wakeMins", wake).put("blocks", blocks)
                .put("conflicts", conflictJson).put("skippedBlocks", skippedJson).put("deferredBlocks", deferredJson)
                .put("slackBeforeGateMins", Math.max(0, EVALUATION_END - lastEnd)).put("compressionRatio", compression);
    }

    private static int[] stepDurations(JSONObject plan) {
        JSONArray steps = plan.optJSONArray("steps");
        if (steps == null || steps.length() == 0) return new int[]{30,30,30};
        int[] result = new int[steps.length()];
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            result[i] = step == null ? 30 : Math.max(1, step.optInt("durationMins", 30));
        }
        return result;
    }

    private static List<JSONObject> activeRoutines(JSONArray raw, int day) throws JSONException {
        List<JSONObject> result = new ArrayList<>();
        if (raw == null) return result;
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.optJSONObject(i);
            if (item == null || !item.optBoolean("enabled", true)) continue;
            JSONArray days = item.optJSONArray("daysOfWeek");
            if (days != null && days.length() > 0) {
                boolean match = false;
                for (int j = 0; j < days.length(); j++) if (days.optInt(j, -1) == day) match = true;
                if (!match) continue;
            }
            JSONObject copy = copy(item);
            if (!copy.has("id") || copy.optString("id").trim().isEmpty()) copy.put("id", "routine_" + i);
            int window = clamp(copy.optInt("windowMins", 30), 5, 480);
            int duration = clamp(copy.optInt("durationMins", window), 1, window);
            boolean commute = copy.optBoolean("isCommute", false);
            boolean flexible = commute ? false : copy.optBoolean("isFlexible", false);
            String policy = commute ? "strict" : copy.optString("conflictPolicy", flexible ? "shift" : "strict");
            if (!"shift".equals(policy) && !"strict".equals(policy) && !"skip_if_busy".equals(policy)) policy = flexible ? "shift" : "strict";
            copy.put("windowMins", window).put("durationMins", duration).put("isFlexible", flexible)
                    .put("includeHifzTarget", !commute && copy.optBoolean("includeHifzTarget", false))
                    .put("conflictPolicy", policy).put("isCommute", commute);
            result.add(copy);
        }
        return result;
    }

    private static List<JSONObject> topologicalOrder(List<JSONObject> source, Set<String> known) {
        List<JSONObject> pending = new ArrayList<>(source), ordered = new ArrayList<>();
        Set<String> ids = new HashSet<>(known);
        for (int guard = 0; guard < source.size() + 1 && !pending.isEmpty(); guard++) {
            int selected = -1;
            for (int i = 0; i < pending.size(); i++) {
                JSONObject anchor = pending.get(i).optJSONObject("anchor");
                if (anchor == null || !"after_block".equals(anchor.optString("type"))
                        || ids.contains(anchor.optString("blockId"))
                        || (("hv_maghrib_manzil".equals(anchor.optString("blockId"))) && ids.contains("lv_maghrib_manzil"))) {
                    selected = i; break;
                }
            }
            if (selected < 0) selected = 0;
            JSONObject item = pending.remove(selected);
            ordered.add(item); ids.add(item.optString("id"));
        }
        return ordered;
    }

    private static int resolveAnchor(JSONObject anchor, Map<String, String> prayers,
                                     Map<String, JSONObject> resolved, int fallbackEnd) {
        if (anchor == null) return toMinutes("09:00");
        String type = anchor.optString("type", "fixed");
        if ("prayer".equals(type)) {
            String prayer = anchor.optString("prayer", "dzuhur");
            return Math.max(0, toMinutes(prayers.getOrDefault(prayer, "12:00")) + clamp(anchor.optInt("offsetMins", 0), -120, 240));
        }
        if ("after_block".equals(type)) {
            String id = anchor.optString("blockId", "primary_hifz");
            JSONObject ref = resolved.get(id);
            if (ref == null && "hv_maghrib_manzil".equals(id)) ref = resolved.get("lv_maghrib_manzil");
            int offset = clamp(anchor.optInt("offsetMins", 10), 0, 240);
            if (ref != null) return ref.optInt("endMins") + offset;
            if (id.contains("maghrib_manzil")) return toMinutes(prayers.getOrDefault("maghrib", "18:00")) + 20 + offset;
            return fallbackEnd + offset;
        }
        String time = anchor.optString("time", "09:00");
        return validTime(time) ? toMinutes(time) : toMinutes("09:00");
    }

    private static List<JSONObject> pack(List<JSONObject> blocks, List<JSONObject> fixed,
                                        List<JSONObject> base, int nowMins, boolean preview, double factor) throws JSONException {
        List<JSONObject> obstacles = new ArrayList<>();
        for (JSONObject item : fixed) obstacles.add(copy(item));
        Map<String, Integer> endById = new HashMap<>();
        for (JSONObject item : base) endById.put(item.optString("id"), item.optInt("endMins"));
        List<JSONObject> ordered = new ArrayList<>();
        for (JSONObject item : blocks) if (!isAfter(item)) ordered.add(item);
        for (JSONObject item : blocks) if (isAfter(item)) ordered.add(item);
        Map<String, JSONObject> result = new HashMap<>();
        for (JSONObject block : ordered) {
            int originalWindow = block.optInt("origWindowMins", block.optInt("windowMins", 30));
            int minFloor = Math.min(originalWindow, Math.max(10, Math.round(originalWindow * 0.25f)));
            int targetWindow = factor >= .9999 ? originalWindow : Math.max(minFloor, Math.round(originalWindow * (float) factor));
            int baseStart = block.optInt("origStartMins");
            JSONObject anchor = block.optJSONObject("anchor");
            if (anchor != null && "after_block".equals(anchor.optString("type"))) {
                Integer refEnd = endById.get(anchor.optString("blockId"));
                if (refEnd == null && "hv_maghrib_manzil".equals(anchor.optString("blockId"))) refEnd = endById.get("lv_maghrib_manzil");
                if (refEnd != null) baseStart = Math.max(baseStart, refEnd + anchor.optInt("offsetMins", 0));
            }
            int start = preview ? baseStart : Math.max(baseStart, nowMins);
            int transition = factor < .9999 ? 0 : 5;
            for (int guard = 0; guard < 60; guard++) {
                int next = start;
                for (JSONObject obstacle : obstacles) if (overlaps(start, start + targetWindow,
                        obstacle.optInt("startMins"), obstacle.optInt("endMins"))) next = Math.max(next, obstacle.optInt("endMins") + transition);
                if (next == start) break;
                start = next;
            }
            int end = start + targetWindow;
            obstacles.add(new JSONObject().put("id", block.optString("id")).put("title", block.optString("title"))
                    .put("startMins", start).put("endMins", end));
            endById.put(block.optString("id"), end);
            double ratio = targetWindow / (double) Math.max(1, originalWindow);
            int originalDuration = block.optInt("origDurationMins", block.optInt("durationMins", originalWindow));
            int scaledDuration = originalDuration >= originalWindow ? targetWindow
                    : Math.max(10, Math.min(targetWindow, (int) Math.round(originalDuration * ratio)));
            JSONObject packed = copy(block).put("startMins", start).put("endMins", end).put("windowMins", targetWindow)
                    .put("effectiveDurationMins", scaledDuration).put("shiftMins", Math.max(0, start - block.optInt("origStartMins")))
                    .put("compressedFromMins", targetWindow < originalWindow ? originalWindow : JSONObject.NULL)
                    .put("isDeferred", end > EVALUATION_END);
            result.put(block.optString("id"), packed);
        }
        List<JSONObject> orderedResult = new ArrayList<>();
        for (JSONObject item : blocks) orderedResult.add(result.get(item.optString("id")));
        return orderedResult;
    }

    private static String nativeHifzTarget(JSONObject state, JSONObject plan) {
        int total = Math.max(1, plan.optInt("totalSteps", 3));
        int step = Math.max(0, Math.min(total - 1, state.optInt("primarySubStep", 0)));
        if (step == 0) return plan.optString("dawnTarget", "Target Manzil");
        if (step == 1) return plan.optString("sabqiTarget", "Riwayat Sabqi");
        JSONArray steps = plan.optJSONArray("steps");
        JSONObject current = steps == null ? null : steps.optJSONObject(step);
        return current == null ? plan.optString("sabaqTarget", "Target Sabaq") : current.optString("target", "Target Sabaq");
    }

    private static JSONObject obstacle(JSONObject source) throws JSONException {
        return new JSONObject().put("id", source.optString("id")).put("title", source.optString("title"))
                .put("startMins", source.optInt("startMins")).put("endMins", source.optInt("endMins"));
    }

    private static JSONObject display(JSONObject source) throws JSONException {
        JSONObject item = copy(source);
        int start = source.optInt("startMins"), end = source.optInt("endMins");
        item.put("start", time(start)).put("end", time(end))
                .put("origStart", time(source.optInt("origStartMins", start)))
                .put("origEnd", time(source.optInt("origEndMins", end)));
        return item;
    }

    private static boolean isAfter(JSONObject block) {
        JSONObject anchor = block.optJSONObject("anchor");
        return anchor != null && "after_block".equals(anchor.optString("type"));
    }

    private static int maxEnd(List<JSONObject> blocks) {
        int max = 0;
        for (JSONObject item : blocks) max = Math.max(max, item.optInt("endMins"));
        return max;
    }

    private static JSONObject copy(JSONObject source) throws JSONException { return new JSONObject(source.toString()); }
    private static boolean overlaps(int aStart, int aEnd, int bStart, int bEnd) { return aStart < bEnd && bStart < aEnd; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static int toMinutes(String value) {
        if (!validTime(value)) return 0;
        String[] parts = value.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
    private static String time(int mins) {
        int wrapped = ((mins % 1440) + 1440) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", wrapped / 60, wrapped % 60);
    }
    private static boolean validTime(String value) { return value != null && value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"); }
}
