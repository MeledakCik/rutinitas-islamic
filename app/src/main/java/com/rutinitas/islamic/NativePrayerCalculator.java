package com.rutinitas.islamic;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Perhitungan waktu shalat di perangkat.
 *
 * Kemenag RI: Subuh -20°, Isya -18°, Ashar Syafi'i (bayangan = 1x), Maghrib memakai koreksi ketinggian
 * tempat. Ihtiyath per waktu dan pembulatan ke atas dipilih agar hasil TIDAK PERNAH lebih awal dari
 * jadwal Bimas Islam Kemenag pada data uji (Jakarta, Bandung, Makassar; lihat NativePrayerCalculatorTest).
 * Lebih lambat 1–2 menit lebih aman daripada lebih awal.
 */
public final class NativePrayerCalculator {
    public static final class PrayerTimes {
        public final String subuh;
        public final String dzuhur;
        public final String ashar;
        public final String maghrib;
        public final String isya;
        /** Terbit matahari (akhir waktu Subuh); kosong untuk jadwal yang tidak dihitung. */
        public final String terbit;

        PrayerTimes(String subuh, String dzuhur, String ashar, String maghrib, String isya) {
            this(subuh, dzuhur, ashar, maghrib, isya, "");
        }

        PrayerTimes(String subuh, String dzuhur, String ashar, String maghrib, String isya, String terbit) {
            this.subuh = subuh;
            this.dzuhur = dzuhur;
            this.ashar = ashar;
            this.maghrib = maghrib;
            this.isya = isya;
            this.terbit = terbit;
        }

        public Map<String, String> asMap() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("subuh", subuh);
            values.put("dzuhur", dzuhur);
            values.put("ashar", ashar);
            values.put("maghrib", maghrib);
            values.put("isya", isya);
            return values;
        }
    }

    private static final class Method {
        final double fajrAngle;
        final double ishaAngle;
        final int ishaMinutes;
        /** Ihtiyath (menit) untuk Subuh, Dzuhur, Ashar, Maghrib, Isya. */
        final double[] precaution;

        Method(double fajrAngle, double ishaAngle, int ishaMinutes, double[] precaution) {
            this.fajrAngle = fajrAngle;
            this.ishaAngle = ishaAngle;
            this.ishaMinutes = ishaMinutes;
            this.precaution = precaution;
        }
    }

    private static final double[] KEMENAG_PRECAUTION = {2.0, 3.5, 2.5, 3.5, 2.5};
    private static final double[] NO_PRECAUTION = {0, 0, 0, 0, 0};
    private static final Map<String, Method> METHODS = new HashMap<>();

    static {
        METHODS.put("kemenag", new Method(20, 18, 0, KEMENAG_PRECAUTION));
        METHODS.put("mwl", new Method(18, 17, 0, NO_PRECAUTION));
        METHODS.put("isna", new Method(15, 15, 0, NO_PRECAUTION));
        METHODS.put("makkah", new Method(18.5, 0, 90, NO_PRECAUTION));
        METHODS.put("egypt", new Method(19.5, 17.5, 0, NO_PRECAUTION));
    }

    private NativePrayerCalculator() { }

    /** Jadwal contoh lama; TIDAK sesuai lokasi mana pun dan tidak dipakai untuk pengingat. */
    public static PrayerTimes fixed() {
        return new PrayerTimes("04:45", "12:00", "15:15", "18:00", "19:15");
    }

    public static PrayerTimes calculate(Date date, double latitude, double longitude,
                                        String methodKey, Double timezoneHours) {
        return calculate(date, latitude, longitude, methodKey, timezoneHours, 0);
    }

    public static PrayerTimes calculate(Date date, double latitude, double longitude,
                                        String methodKey, Double timezoneHours, double elevationMeters) {
        if (date == null || !Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Tanggal atau koordinat tidak valid.");
        }
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(java.util.Calendar.YEAR);
        int month = calendar.get(java.util.Calendar.MONTH) + 1;
        int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);
        double tzHours = timezoneHours != null && Double.isFinite(timezoneHours)
                ? timezoneHours
                : TimeZone.getDefault().getOffset(date.getTime()) / 3600000.0;
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        int a = (int) Math.floor(year / 100.0);
        int b = 2 - a + (int) Math.floor(a / 4.0);
        double jd = Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5 - 2451545.0;

        double g = fixAngle(357.529 + 0.98560028 * jd);
        double q = fixAngle(280.459 + 0.98564736 * jd);
        double l = fixAngle(q + 1.915 * Math.sin(radians(g)) + 0.020 * Math.sin(radians(2 * g)));
        double e = 23.439 - 0.00000036 * jd;
        double rightAscension = degrees(Math.atan2(Math.cos(radians(e)) * Math.sin(radians(l)), Math.cos(radians(l)))) / 15.0;
        double declination = degrees(Math.asin(Math.sin(radians(e)) * Math.sin(radians(l))));
        double equation = q / 15.0 - fixHour(rightAscension);
        double noon = tzHours + 12 - longitude / 15.0 - (fixHour(equation + 12) - 12);

        Method method = METHODS.getOrDefault(methodKey, METHODS.get("kemenag"));
        double[] p = method.precaution;
        double horizon = 0.833 + 0.0347 * Math.sqrt(Math.max(0, Math.min(elevationMeters, 5000)));

        double dzuhur = noon + p[1] / 60.0;
        double subuh = sunAngleHour(method.fajrAngle, true, latitude, declination, noon) + p[0] / 60.0;
        double terbit = sunAngleHour(horizon, true, latitude, declination, noon);
        double maghrib = Math.min(23.25, Math.max(dzuhur + 0.25,
                sunAngleHour(horizon, false, latitude, declination, noon) + p[3] / 60.0));
        double asrAltitude = degrees(Math.atan(1.0 / (Math.tan(radians(Math.abs(latitude - declination))) + 1.0)));
        double ashar = Math.max(dzuhur + 0.1, Math.min(maghrib - 0.1,
                noon + hourAngle(asrAltitude, latitude, declination) + p[2] / 60.0));
        double isya = method.ishaMinutes > 0
                ? maghrib + method.ishaMinutes / 60.0
                : sunAngleHour(method.ishaAngle, false, latitude, declination, noon) + p[4] / 60.0;
        isya = Math.min(23.75, Math.max(maghrib + 0.5, isya));
        subuh = Math.max(0.25, Math.min(dzuhur - 0.5, subuh));

        return new PrayerTimes(formatUp(subuh), formatUp(dzuhur), formatUp(ashar), formatUp(maghrib), formatUp(isya),
                formatMinutes((long) Math.floor(terbit * 60.0 + 1e-9)));
    }

    /** Dibulatkan ke menit berikutnya agar tidak pernah lebih awal dari waktu sebenarnya. */
    private static String formatUp(double hours) {
        return formatMinutes((long) Math.ceil(hours * 60.0 - 1e-9));
    }

    public static int toMinutes(String value) {
        if (value == null) return 0;
        String[] parts = value.trim().replace('.', ':').split(":");
        if (parts.length != 2) return 0;
        try {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    public static String formatMinutes(long totalMinutes) {
        int minutes = (int) (((totalMinutes % 1440) + 1440) % 1440);
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static double hourAngle(double altitude, double latitude, double declination) {
        return degrees(Math.acos(clamp((Math.sin(radians(altitude)) - Math.sin(radians(declination)) * Math.sin(radians(latitude)))
                / (Math.cos(radians(declination)) * Math.cos(radians(latitude)))))) / 15.0;
    }

    private static double sunAngleHour(double angle, boolean morning, double latitude,
                                       double declination, double noon) {
        double t = hourAngle(-angle, latitude, declination);
        return morning ? noon - t : noon + t;
    }

    private static double radians(double degrees) { return degrees * Math.PI / 180.0; }
    private static double degrees(double radians) { return radians * 180.0 / Math.PI; }
    private static double fixAngle(double angle) { return angle - 360 * Math.floor(angle / 360.0); }
    private static double fixHour(double hours) { return hours - 24 * Math.floor(hours / 24.0); }
    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
}
