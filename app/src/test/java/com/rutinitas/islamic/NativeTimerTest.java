package com.rutinitas.islamic;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public class NativeTimerTest {
    @Test public void pausedTimerRetainsItsRemainingSeconds() throws Exception {
        JSONObject paused = new JSONObject().put("startedAtMs", 0).put("durSecs", 87);
        assertEquals(87, NativeMainActivity.remainingTimerSeconds(paused, 1800, 1700000000000L));
    }

    @Test public void runningTimerCountsDownAndStopsAtZero() throws Exception {
        JSONObject running = new JSONObject().put("startedAtMs", 1700000000000L).put("durSecs", 45);
        assertEquals(32, NativeMainActivity.remainingTimerSeconds(running, 1800, 1700000013000L));
        assertEquals(0, NativeMainActivity.remainingTimerSeconds(running, 1800, 1700000046000L));
    }
}
