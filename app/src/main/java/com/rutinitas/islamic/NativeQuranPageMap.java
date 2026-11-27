package com.rutinitas.islamic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Mushaf page estimates shared by the offline reader and native Hifz targets. */
public final class NativeQuranPageMap {
    private static final int[] SURAH_START_PAGES = {
            1,2,50,77,106,128,151,177,187,208,221,235,249,255,262,267,282,293,305,312,
            322,332,342,350,359,367,377,385,396,404,411,415,418,428,434,440,446,453,458,467,
            477,483,489,496,499,502,507,511,515,518,520,523,526,528,531,534,537,542,545,549,551,
            553,554,556,558,560,562,564,566,568,570,572,574,575,577,578,580,582,583,585,586,
            587,587,589,590,591,591,592,593,594,595,595,596,596,597,597,598,598,599,599,600,
            600,601,601,601,602,602,602,603,603,603,604,604,604
    };
    private static final int[] CANONICAL_AYAH_COUNTS = {
            7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,
            112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,
            54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,
            14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,
            29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,
            11,8,3,9,5,4,7,3,6,3,5,4,5,6
    };
    private static final int[][] JUZ_LANDMARKS = {
            {2,142,22},{2,146,23},{2,253,42},{3,93,62},{4,24,82},{4,148,102},
            {5,82,121},{6,111,142},{7,88,162},{8,41,182},{9,93,201},{11,6,222},
            {12,53,242},{15,1,262},{17,1,282},{18,75,302},{21,1,322},{23,1,342},
            {25,21,362},{27,56,382},{29,46,402},{33,31,422},{36,28,442},{39,32,462},
            {41,47,482},{46,1,502},{51,31,522},{58,1,542},{67,1,562},{78,1,582}
    };

    private NativeQuranPageMap() { }

    public static final class AyahRef {
        public final int surah;
        public final int ayah;
        public AyahRef(int surah, int ayah) { this.surah = surah; this.ayah = ayah; }
    }

    public static java.util.Map<Integer, Integer> canonicalAyahCounts() {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int i = 0; i < CANONICAL_AYAH_COUNTS.length; i++) counts.put(i + 1, CANONICAL_AYAH_COUNTS[i]);
        return counts;
    }

    private static final class Landmark {
        final int cumulativeAyah;
        final int page;
        Landmark(int cumulativeAyah, int page) { this.cumulativeAyah = cumulativeAyah; this.page = page; }
    }

    public static int estimatePage(int surah, int ayah, Map<Integer, Integer> ayahCounts) {
        return estimatePage(surah, ayah, ayahCounts, landmarks(ayahCounts));
    }

    private static int estimatePage(int surah, int ayah, Map<Integer, Integer> ayahCounts,
                                    List<Landmark> landmarks) {
        int safeSurah = clamp(surah, 1, 114);
        int count = Math.max(1, ayahCounts.getOrDefault(safeSurah, 1));
        int safeAyah = clamp(ayah, 1, count);
        int target = cumulativeIndex(safeSurah, safeAyah, ayahCounts);
        Landmark lower = landmarks.get(0), upper = landmarks.get(landmarks.size() - 1);
        for (int i = 0; i + 1 < landmarks.size(); i++) {
            Landmark current = landmarks.get(i), next = landmarks.get(i + 1);
            if (target >= current.cumulativeAyah && target < next.cumulativeAyah) {
                lower = current; upper = next; break;
            }
        }
        int span = Math.max(1, upper.cumulativeAyah - lower.cumulativeAyah);
        double fraction = Math.min(1.0, Math.max(0.0, (target - lower.cumulativeAyah) / (double) span));
        int estimated = lower.page + (int) Math.floor(fraction * (upper.page - lower.page));
        int firstPage = SURAH_START_PAGES[safeSurah - 1];
        int afterPage = safeSurah < 114 ? SURAH_START_PAGES[safeSurah] : 605;
        int lastPage = Math.max(firstPage, afterPage - 1);
        return clamp(estimated, firstPage, lastPage);
    }

    public static AyahRef pageEdge(int page, boolean end, Map<Integer, Integer> ayahCounts) {
        int wantedPage = clamp(page, 1, 604);
        List<Landmark> pageLandmarks = landmarks(ayahCounts);
        int startSurah = 1;
        for (int i = 0; i < SURAH_START_PAGES.length; i++) {
            if (SURAH_START_PAGES[i] <= wantedPage) startSurah = i + 1;
            else break;
        }
        while (startSurah > 1 && SURAH_START_PAGES[startSurah - 2] == SURAH_START_PAGES[startSurah - 1]) startSurah--;
        int first = Math.max(1, startSurah - 1);
        AyahRef lastMatch = null;
        for (int surah = first; surah <= 114; surah++) {
            int count = Math.max(1, ayahCounts.getOrDefault(surah, 1));
            for (int ayah = 1; ayah <= count; ayah++) {
                int estimated = estimatePage(surah, ayah, ayahCounts, pageLandmarks);
                if (estimated < wantedPage) continue;
                if (estimated > wantedPage) return lastMatch != null ? lastMatch : nearestEdge(wantedPage, end, ayahCounts);
                if (!end) return new AyahRef(surah, ayah);
                lastMatch = new AyahRef(surah, ayah);
            }
        }
        return lastMatch != null ? lastMatch : nearestEdge(wantedPage, end, ayahCounts);
    }

    private static AyahRef nearestEdge(int page, boolean end, Map<Integer, Integer> ayahCounts) {
        int nearest = 1;
        for (int i = 0; i < SURAH_START_PAGES.length; i++) if (SURAH_START_PAGES[i] <= page) nearest = i + 1;
        int count = Math.max(1, ayahCounts.getOrDefault(nearest, 1));
        return new AyahRef(nearest, end ? count : 1);
    }

    private static List<Landmark> landmarks(Map<Integer, Integer> ayahCounts) {
        List<Landmark> points = new ArrayList<>();
        for (int surah = 1; surah <= 114; surah++) points.add(new Landmark(cumulativeIndex(surah, 1, ayahCounts), SURAH_START_PAGES[surah - 1]));
        for (int[] item : JUZ_LANDMARKS) points.add(new Landmark(cumulativeIndex(item[0], item[1], ayahCounts), item[2]));
        points.add(new Landmark(cumulativeTotal(ayahCounts), 605));
        points.sort(Comparator.comparingInt((Landmark item) -> item.cumulativeAyah).thenComparingInt(item -> item.page));
        List<Landmark> unique = new ArrayList<>();
        for (Landmark point : points) {
            if (!unique.isEmpty() && unique.get(unique.size() - 1).cumulativeAyah == point.cumulativeAyah) {
                unique.set(unique.size() - 1, point);
            } else unique.add(point);
        }
        return unique;
    }

    private static int cumulativeIndex(int surah, int ayah, Map<Integer, Integer> ayahCounts) {
        int sum = 0;
        for (int i = 1; i < surah; i++) sum += Math.max(1, ayahCounts.getOrDefault(i, 1));
        return sum + Math.max(0, ayah - 1);
    }

    private static int cumulativeTotal(Map<Integer, Integer> ayahCounts) {
        int sum = 0;
        for (int i = 1; i <= 114; i++) sum += Math.max(1, ayahCounts.getOrDefault(i, 1));
        return sum;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
