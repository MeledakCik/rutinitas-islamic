package com.rutinitas.islamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class NativeQuranPageMapTest {
    @Test public void juzPageLandmarksMatchMushafPages() {
        Map<Integer, Integer> counts = NativeQuranPageMap.canonicalAyahCounts();
        assertEquals(22, NativeQuranPageMap.estimatePage(2, 142, counts));
        assertEquals(23, NativeQuranPageMap.estimatePage(2, 146, counts));
        assertEquals(582, NativeQuranPageMap.estimatePage(78, 1, counts));
    }

    @Test public void pageEdgesCoverTheEntireMushaf() {
        Map<Integer, Integer> counts = NativeQuranPageMap.canonicalAyahCounts();
        NativeQuranPageMap.AyahRef first = NativeQuranPageMap.pageEdge(1, false, counts);
        NativeQuranPageMap.AyahRef last = NativeQuranPageMap.pageEdge(604, true, counts);
        assertEquals(1, first.surah);
        assertEquals(1, first.ayah);
        assertEquals(114, last.surah);
        assertEquals(6, last.ayah);
    }

    @Test public void eachPageRangeProducesOrderedAyahBounds() {
        Map<Integer, Integer> counts = NativeQuranPageMap.canonicalAyahCounts();
        NativeQuranPageMap.AyahRef start = NativeQuranPageMap.pageEdge(300, false, counts);
        NativeQuranPageMap.AyahRef end = NativeQuranPageMap.pageEdge(307, true, counts);
        int startIndex = cumulative(start.surah, start.ayah, counts);
        int endIndex = cumulative(end.surah, end.ayah, counts);
        assertTrue(endIndex >= startIndex);
    }

    private static int cumulative(int surah, int ayah, Map<Integer, Integer> counts) {
        int index = Math.max(0, ayah - 1);
        for (int i = 1; i < surah; i++) index += counts.get(i);
        return index;
    }
}
