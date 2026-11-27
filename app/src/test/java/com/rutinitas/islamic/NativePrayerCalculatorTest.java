package com.rutinitas.islamic;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Map;

/**
 * Pembanding: jadwal Bimas Islam Kemenag RI (dikutip Liputan6.com, 2026).
 * Syarat keselamatan: hasil aplikasi tidak boleh lebih awal, dan paling lambat 2 menit.
 */
public class NativePrayerCalculatorTest {
    private static final String[] KEYS = {"subuh", "dzuhur", "ashar", "maghrib", "isya"};
    // kota, lintang, bujur, zona, ketinggian (m), tahun, bulan, tanggal, jadwal resmi
    static final Object[][] OFFICIAL = {
            {"Jakarta", -6.1754, 106.8272, 7.0, 8.0, 2026, 11, 27, "04:06 11:44 15:08 17:57 19:11"},
            {"Jakarta", -6.1754, 106.8272, 7.0, 8.0, 2026, 12, 1, "04:06 11:45 15:10 17:58 19:13"},
            {"Jakarta", -6.1754, 106.8272, 7.0, 8.0, 2026, 8, 7, "04:45 12:02 15:23 17:58 19:09"},
            {"Jakarta", -6.1754, 106.8272, 7.0, 8.0, 2026, 1, 27, "04:34 12:09 15:30 18:20 19:33"},
            {"Jakarta", -6.1754, 106.8272, 7.0, 8.0, 2026, 4, 27, "04:36 11:54 15:14 17:51 19:01"},
            {"Bandung", -6.9175, 107.6191, 7.0, 768.0, 2026, 11, 1, "04:05 11:37 14:51 17:51 18:58"},
            {"Bandung", -6.9175, 107.6191, 7.0, 768.0, 2026, 11, 15, "04:01 11:38 14:58 17:55 19:03"},
            {"Bandung", -6.9175, 107.6191, 7.0, 768.0, 2026, 11, 27, "04:01 11:41 15:04 17:59 19:09"},
            {"Makassar", -5.1477, 119.4327, 8.0, 10.0, 2026, 11, 1, "04:20 11:49 15:05 17:58 19:09"},
            {"Makassar", -5.1477, 119.4327, 8.0, 10.0, 2026, 11, 15, "04:17 11:50 15:11 18:01 19:13"},
            {"Makassar", -5.1477, 119.4327, 8.0, 10.0, 2026, 11, 27, "04:17 11:53 15:17 18:05 19:19"},
    };

    @Test public void kemenagNeverEarlierThanOfficialAndAtMostTwoMinutesLate() {
        for (Object[] row : OFFICIAL) {
            Calendar day = Calendar.getInstance();
            day.set((Integer) row[5], (Integer) row[6] - 1, (Integer) row[7], 12, 0, 0);
            Map<String, String> got = NativePrayerCalculator.calculate(day.getTime(), (Double) row[1], (Double) row[2],
                    "kemenag", (Double) row[3], (Double) row[4]).asMap();
            String[] expected = ((String) row[8]).split(" ");
            for (int i = 0; i < KEYS.length; i++) {
                int diff = NativePrayerCalculator.toMinutes(got.get(KEYS[i])) - NativePrayerCalculator.toMinutes(expected[i]);
                String where = row[0] + " " + row[7] + "/" + row[6] + " " + KEYS[i] + ": app " + got.get(KEYS[i]) + ", resmi " + expected[i];
                assertTrue("Lebih awal dari jadwal resmi — " + where, diff >= 0);
                assertTrue("Terlalu lambat — " + where, diff <= 2);
            }
        }
    }
}
