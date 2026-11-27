package com.rutinitas.islamic;

/**
 * Kota di Indonesia untuk jadwal shalat offline: koordinat pusat kota (perkiraan), zona waktu, dan
 * ketinggian (m) yang memengaruhi waktu Maghrib. Dipakai juga untuk memperkirakan ketinggian lokasi GPS.
 */
final class IndonesianCities {
    static final class City {
        final String name;
        final double lat;
        final double lon;
        final double tz;
        final double elevation;
        City(String name, double lat, double lon, double tz, double elevation) {
            this.name = name; this.lat = lat; this.lon = lon; this.tz = tz; this.elevation = elevation;
        }
    }

    static final City[] ALL = {
            new City("Banda Aceh", 5.5483, 95.3238, 7, 10),
            new City("Medan", 3.5952, 98.6722, 7, 25),
            new City("Padang", -0.9471, 100.4172, 7, 5),
            new City("Bukittinggi", -0.3055, 100.3692, 7, 920),
            new City("Pekanbaru", 0.5071, 101.4478, 7, 15),
            new City("Batam", 1.0456, 104.0305, 7, 20),
            new City("Jambi", -1.6101, 103.6131, 7, 30),
            new City("Palembang", -2.9761, 104.7754, 7, 8),
            new City("Bengkulu", -3.7928, 102.2608, 7, 10),
            new City("Bandar Lampung", -5.3971, 105.2668, 7, 100),
            new City("Pangkalpinang", -2.1316, 106.1169, 7, 10),
            new City("Serang", -6.1200, 106.1503, 7, 30),
            new City("Tangerang", -6.1783, 106.6319, 7, 15),
            new City("Jakarta", -6.1754, 106.8272, 7, 8),
            new City("Depok", -6.4025, 106.7942, 7, 100),
            new City("Bekasi", -6.2383, 106.9756, 7, 20),
            new City("Bogor", -6.5971, 106.8060, 7, 265),
            new City("Sukabumi", -6.9277, 106.9300, 7, 584),
            new City("Bandung", -6.9175, 107.6191, 7, 768),
            new City("Garut", -7.2279, 107.9087, 7, 717),
            new City("Tasikmalaya", -7.3274, 108.2207, 7, 351),
            new City("Cirebon", -6.7063, 108.5570, 7, 5),
            new City("Purwokerto", -7.4245, 109.2302, 7, 75),
            new City("Semarang", -6.9667, 110.4167, 7, 5),
            new City("Magelang", -7.4797, 110.2177, 7, 380),
            new City("Yogyakarta", -7.7956, 110.3695, 7, 114),
            new City("Surakarta (Solo)", -7.5755, 110.8243, 7, 105),
            new City("Madiun", -7.6298, 111.5239, 7, 63),
            new City("Kediri", -7.8480, 112.0178, 7, 67),
            new City("Malang", -7.9666, 112.6326, 7, 440),
            new City("Surabaya", -7.2575, 112.7521, 7, 5),
            new City("Jember", -8.1724, 113.7000, 7, 89),
            new City("Denpasar", -8.6705, 115.2126, 8, 10),
            new City("Mataram", -8.5833, 116.1167, 8, 20),
            new City("Kupang", -10.1772, 123.6070, 8, 50),
            new City("Pontianak", -0.0263, 109.3425, 7, 1),
            new City("Palangka Raya", -2.2161, 113.9135, 7, 20),
            new City("Banjarmasin", -3.3186, 114.5944, 8, 3),
            new City("Balikpapan", -1.2379, 116.8529, 8, 15),
            new City("Samarinda", -0.5022, 117.1536, 8, 10),
            new City("Makassar", -5.1477, 119.4327, 8, 10),
            new City("Palu", -0.8917, 119.8707, 8, 5),
            new City("Kendari", -3.9985, 122.5130, 8, 20),
            new City("Gorontalo", 0.5435, 123.0568, 8, 10),
            new City("Manado", 1.4748, 124.8421, 8, 5),
            new City("Ternate", 0.7893, 127.3752, 9, 20),
            new City("Ambon", -3.6954, 128.1814, 9, 10),
            new City("Sorong", -0.8762, 131.2558, 9, 10),
            new City("Manokwari", -0.8615, 134.0620, 9, 10),
            new City("Jayapura", -2.5337, 140.7181, 9, 10),
            new City("Merauke", -8.4932, 140.4018, 9, 5),
    };

    private IndonesianCities() { }

    static City find(String name) {
        if (name == null) return null;
        for (City city : ALL) if (city.name.equalsIgnoreCase(name)) return city;
        return null;
    }

    /** Kota terdekat (km), untuk memperkirakan ketinggian lokasi GPS bila perangkat tidak memberi altitude. */
    static City nearest(double lat, double lon, double maxKm) {
        City best = null;
        double bestKm = Double.MAX_VALUE;
        for (City city : ALL) {
            double dLat = Math.toRadians(city.lat - lat);
            double dLon = Math.toRadians(city.lon - lon);
            double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat))
                    * Math.cos(Math.toRadians(city.lat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double km = 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
            if (km < bestKm) { bestKm = km; best = city; }
        }
        return bestKm <= maxKm ? best : null;
    }
}
