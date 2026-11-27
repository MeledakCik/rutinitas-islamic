package com.rutinitas.islamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NativeHifzPlanTest {
    private static JSONObject sabaq(int page) throws Exception {
        return new JSONObject().put("rangeStartPage", 1).put("rangeEndPage", 604)
                .put("currentPage", page).put("mode", "half_page").put("subSlot", 0);
    }

    @Test public void newMemorizerStartsWithoutInventedManzilPages() throws Exception {
        JSONObject plan = NativeHifzPlan.build(new JSONObject().put("sabaq", sabaq(1)), false);
        assertEquals(3, plan.getInt("phase"));
        assertEquals(0, plan.getInt("manzilPoolSize"));
        assertTrue(plan.getString("dawnTarget").contains("Belum ada Manzil"));
        assertEquals(3, plan.getInt("totalSteps"));
        assertEquals("", plan.getString("maghribTarget"));
    }

    @Test public void sabqiHistoryKeepsRecentPagesOutOfManzil() throws Exception {
        JSONArray history = new JSONArray().put(new JSONObject().put("approxPage", 5).put("unitShort", "Halaman 5 Atas"));
        JSONObject state = new JSONObject().put("sabaq", sabaq(11)).put("sabqiHistory", history);
        JSONObject plan = NativeHifzPlan.build(state, false);
        assertEquals(4, plan.getInt("maxGraduatedPage"));
        assertEquals(4, plan.getInt("manzilPoolSize"));
        assertEquals("Halaman 1–4 (4 halaman)", plan.getString("dawnTarget"));
    }

    @Test public void priorJuzAndHighVolumePaceCreateSeparateMaghribTarget() throws Exception {
        JSONArray prior = new JSONArray();
        for (int juz = 1; juz <= 8; juz++) prior.put(juz);
        JSONObject state = new JSONObject().put("sabaq", sabaq(201)).put("priorJuzList", prior)
                .put("HighVolumeJuzPerDay", 2).put("HighVolumeMaghribOn", false);
        JSONObject plan = NativeHifzPlan.build(state, false);
        assertEquals(4, plan.getInt("phase"));
        assertTrue(plan.getString("dawnTarget").startsWith("Juz 1"));
        assertTrue(plan.getString("maghribTarget").startsWith("Juz 2"));
        assertEquals(40, plan.getJSONArray("steps").getJSONObject(0).getInt("durationMins"));
    }

    @Test public void freezingSabaqReducesTheSessionToManzilAndSabqi() throws Exception {
        JSONObject state = new JSONObject().put("sabaq", sabaq(11)).put("sabaqFrozen", true);
        JSONObject plan = NativeHifzPlan.build(state, false);
        assertEquals(2, plan.getInt("totalSteps"));
        assertEquals(2, plan.getJSONArray("steps").length());
        assertEquals(45, plan.getJSONArray("steps").getJSONObject(0).getInt("durationMins"));
        assertFalse(plan.getBoolean("weeklyAudit"));
    }

    @Test public void manzilPageListUsesTheCurrentCursorAndKeepsGapsBetweenPriorJuz() throws Exception {
        JSONObject state = new JSONObject().put("sabaq", sabaq(1)).put("lvManzilPage", 19)
                .put("priorJuzList", new JSONArray().put(1).put(3));
        JSONObject plan = NativeHifzPlan.buildWithManzilList(state, false);
        JSONArray list = plan.getJSONArray("manzilList");
        assertEquals(plan.getString("dawnTarget"), list.getJSONObject(0).getString("label"));
        assertEquals("Halaman 19–21 & 42–48 (10 halaman)", list.getJSONObject(0).getString("label"));
        assertTrue(list.getJSONObject(0).getBoolean("today"));
        assertFalse(list.getJSONObject(1).getBoolean("today"));
    }

    @Test public void manzilJuzListMarksBothActiveJuzWhenTheCursorWraps() throws Exception {
        JSONArray prior = new JSONArray();
        for (int juz = 1; juz <= 8; juz++) prior.put(juz);
        JSONObject state = new JSONObject().put("sabaq", sabaq(1)).put("priorJuzList", prior)
                .put("HighVolumeJuzPerDay", 2).put("hvNextJuz", 8);
        JSONArray list = NativeHifzPlan.buildWithManzilList(state, false).getJSONArray("manzilList");
        assertEquals(8, list.length());
        assertTrue(list.getJSONObject(0).getString("label").startsWith("Juz 8 "));
        assertTrue(list.getJSONObject(1).getString("label").startsWith("Juz 1 "));
        assertTrue(list.getJSONObject(0).getBoolean("today"));
        assertTrue(list.getJSONObject(1).getBoolean("today"));
        assertFalse(list.getJSONObject(2).getBoolean("today"));
    }

    @Test public void conservedPartialPageIsKeptAsASeparateManzilNote() throws Exception {
        JSONObject partial = new JSONObject().put("page", 5).put("label", "Baris 1–8");
        JSONObject state = new JSONObject().put("sabaq", sabaq(11))
                .put("conservedPartialSpans", new JSONObject().put("5", partial));
        JSONObject plan = NativeHifzPlan.build(state, false);
        assertEquals(4, plan.getInt("maxGraduatedPage"));
        assertEquals(5, plan.getInt("partialPage"));
        assertTrue(plan.getString("dawnTarget").contains("halaman 5 (Baris 1–8)"));
    }
}
