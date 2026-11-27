package com.rutinitas.islamic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NativeQuranTimingTest {
    // Data nyata MP3Quran ayat_timing surah 1, read 123 (Mishary Alafasy), satuan milidetik.
    private static final String FATIHAH = "[{\"ayah\":1,\"start_time\":6420,\"end_time\":11120},"
            + "{\"ayah\":2,\"start_time\":11120,\"end_time\":17640},{\"ayah\":3,\"start_time\":17640,\"end_time\":21280},"
            + "{\"ayah\":4,\"start_time\":21280,\"end_time\":26800},{\"ayah\":5,\"start_time\":26800,\"end_time\":33280},"
            + "{\"ayah\":6,\"start_time\":33280,\"end_time\":38960},{\"ayah\":7,\"start_time\":38960,\"end_time\":51200}]";

    @Test public void parsesCompleteTiming() throws Exception {
        int[] starts = NativeQuranTiming.parse(FATIHAH, 7);
        assertArrayEquals(new int[]{0, 6420, 11120, 17640, 21280, 26800, 33280, 38960}, starts);
    }

    @Test public void findsAyahAtPosition() throws Exception {
        int[] starts = NativeQuranTiming.parse(FATIHAH, 7);
        assertEquals(0, NativeQuranTiming.ayahAt(starts, 1000));   // isti'adzah sebelum ayat 1
        assertEquals(1, NativeQuranTiming.ayahAt(starts, 6420));
        assertEquals(4, NativeQuranTiming.ayahAt(starts, 25000));
        assertEquals(7, NativeQuranTiming.ayahAt(starts, 50000));
    }

    @Test public void rejectsIncompleteOrMismatchedTiming() throws Exception {
        assertNull("Jumlah ayat beda: jangan dipakai agar sorotan tidak salah ayat", NativeQuranTiming.parse(FATIHAH, 8));
        assertNull(NativeQuranTiming.parse("[{\"ayah\":1,\"start_time\":0}]", 7));
    }

    @Test public void surahReferenceUsesKemenagName() {
        assertEquals("Al-Baqarah 2:255", QuranSurahNames.reference(2, 255));
        assertEquals("An-Nas", QuranSurahNames.latin(114));
        assertEquals(6236, java.util.Arrays.stream(QuranSurahNames.AYAH_COUNTS).sum());
    }
}
