package com.rutinitas.islamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

public class NativeSabqiHistoryTest {
    private final Map<Integer, Integer> ayahCounts = NativeQuranPageMap.canonicalAyahCounts();

    @Test public void oldPagesAreReplacedWithUnitsInsideTheActiveRange() throws Exception {
        JSONObject target = new JSONObject().put("mode", "one_page").put("rangeStartPage", 62)
                .put("rangeEndPage", 81).put("currentPage", 67);
        JSONArray stale = new JSONArray();
        for (int page = 11; page <= 17; page++) stale.put(new JSONObject().put("approxPage", page));

        assertTrue(NativeSabqiHistory.isOutsideTarget(stale, target));
        JSONArray updated = NativeSabqiHistory.backfill(target, ayahCounts, unit -> "Halaman " + unit.optInt("currentPage"));
        assertEquals(5, updated.length());
        assertEquals("Halaman 62", updated.getJSONObject(0).getString("unitShort"));
        assertEquals("Halaman 66", updated.getJSONObject(4).getString("unitShort"));
        assertFalse(NativeSabqiHistory.isOutsideTarget(updated, target));
        assertEquals(67, target.getInt("currentPage"));
    }

    @Test public void multiPageHistoryStopsBeforeTheCurrentUnitAndAtSevenUnits() throws Exception {
        JSONObject target = new JSONObject().put("mode", "multi_page").put("rangeStartPage", 40)
                .put("rangeEndPage", 81).put("currentPage", 62).put("pagesPerDay", 3);
        JSONArray updated = NativeSabqiHistory.backfill(target, ayahCounts,
                unit -> unit.optInt("currentPage") + "–" + unit.optInt("rangeEndPage"));
        assertEquals(7, updated.length());
        assertEquals("41–43", updated.getJSONObject(0).getString("unitShort"));
        assertEquals("59–61", updated.getJSONObject(6).getString("unitShort"));
    }

    @Test public void ayahHistoryStopsAtTheSelectedFirstAyahAcrossSurahs() throws Exception {
        JSONObject target = new JSONObject().put("mode", "ayah").put("rangeStartPage", 49)
                .put("rangeEndPage", 62).put("currentPage", 50).put("surahNum", 3).put("startAyah", 4)
                .put("targetStartSurahNum", 2).put("targetStartAyah", 285).put("ayahsPerDay", 4);
        JSONArray updated = NativeSabqiHistory.backfill(target, ayahCounts,
                unit -> unit.optInt("surahNum") + ":" + unit.optInt("startAyah") + " +" + unit.optInt("ayahsPerDay"));
        assertEquals(2, updated.length());
        assertEquals("2:285 +1", updated.getJSONObject(0).getString("unitShort"));
        assertEquals("2:286 +4", updated.getJSONObject(1).getString("unitShort"));
        assertEquals(4, target.getInt("ayahsPerDay"));
    }
}
