package com.rutinitas.islamic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NativeDzikirTest {
    @Test public void countsUpAndSignalsTarget() {
        NativeDzikir.Counter counter = new NativeDzikir.Counter();
        counter.index = 3; // Astaghfirullah
        for (int i = 0; i < 99; i++) assertEquals(NativeDzikir.EVENT_COUNTED, NativeDzikir.tap(counter, 100, "2026-11-27"));
        assertEquals(NativeDzikir.EVENT_TARGET_REACHED, NativeDzikir.tap(counter, 100, "2026-11-27"));
        assertEquals(100, counter.count);
        // Bacaan di luar urutan tasbih tetap bisa dihitung melewati target.
        assertEquals(NativeDzikir.EVENT_COUNTED, NativeDzikir.tap(counter, 100, "2026-11-27"));
        assertEquals(101, counter.count);
        assertEquals(3, counter.index);
        assertEquals(101, counter.today);
    }

    @Test public void tasbihAfterPrayerMovesToNextReadingAfterTarget() {
        NativeDzikir.Counter counter = new NativeDzikir.Counter();
        for (int i = 0; i < 33; i++) NativeDzikir.tap(counter, 33, "2026-11-27");
        assertEquals(0, counter.index);
        assertEquals(33, counter.count);
        assertEquals(NativeDzikir.EVENT_NEXT_PRESET, NativeDzikir.tap(counter, 33, "2026-11-27"));
        assertEquals(1, counter.index);
        assertEquals(1, counter.count);
    }

    @Test public void sequenceWrapsFromAllahuAkbarToSubhanallah() {
        assertEquals(1, NativeDzikir.nextIndex(0));
        assertEquals(2, NativeDzikir.nextIndex(1));
        assertEquals(0, NativeDzikir.nextIndex(2));
        assertEquals(4, NativeDzikir.nextIndex(3));
        assertEquals(0, NativeDzikir.nextIndex(NativeDzikir.PRESETS.length - 1));
    }

    @Test public void dailyTotalResetsOnNewDayButCountStays() {
        NativeDzikir.Counter counter = new NativeDzikir.Counter();
        counter.index = 3;
        NativeDzikir.tap(counter, 100, "2026-11-27");
        NativeDzikir.tap(counter, 100, "2026-11-27");
        NativeDzikir.tap(counter, 100, "2026-11-28");
        assertEquals(1, counter.today);
        assertEquals(3, counter.count);
    }

    @Test public void selectingAReadingStartsFromZero() {
        NativeDzikir.Counter counter = new NativeDzikir.Counter();
        counter.count = 12;
        NativeDzikir.select(counter, 99);
        assertEquals(NativeDzikir.PRESETS.length - 1, counter.index);
        assertEquals(0, counter.count);
    }
}
