# Rutinitas Android

Proyek Android Studio untuk aplikasi Rutinitas. Beranda, jadwal, hafalan, shalat, setelan, dan pembaca Al-Qur'an memakai tampilan Android native. Pembaca memakai teks Uthmani Tanzil dan metadata mushaf yang disimpan di APK, sehingga daftar surah, ayat, dan penanda tersedia offline.

Target Sabaq dapat dipilih per Juz, seluruh mushaf, atau rentang halaman/ayat; progres berhenti tepat pada batas target. Android menyediakan pilihan notifikasi/layar kunci dan tiga widget layar utama (Rutinitas Hafalan, Waktu Shalat, Tasbih Dzikir). Tab Shalat juga memuat tasbih digital dan pengingat beberapa menit sebelum waktu shalat. Widget perlu ditambahkan dari pemilih widget launcher; layar kunci menampilkan aktivitas melalui notifikasi sesuai izin dan pengaturan privasi perangkat.

## Buka dan jalankan

1. Buka folder proyek ini dari Android Studio.
2. Gunakan JDK 17 atau lebih baru dan pasang Android SDK Platform 36.
3. Sinkronkan Gradle, pilih perangkat Android, lalu jalankan konfigurasi `app`.

Minimum Android yang didukung: Android 7.0 (API 24). Efek blur di belakang bilah navigasi tersedia mulai Android 12. Jadwal, shalat, hafalan, dan teks Arab berfungsi offline.

Koneksi internet dipakai saat pengguna memutar atau mengunduh murattal dari [MP3Quran.net](https://mp3quran.net/eng/api), atau mengunduh terjemahan Indonesia dari [EQuran.id](https://equran.id/apidev/overview). Unduhan per surah disimpan di penyimpanan privat. Audio dapat dihapus dari panel pemutar. Pilihan putar surah berikutnya tersedia di setelan audio. Pemutar menjeda saat pengguna meninggalkan tab Qur'an atau aplikasi.

Perangkat dengan RAM rendah memakai tampilan tanpa animasi dan blur untuk mengurangi beban render. Aplikasi hanya meminta lokasi perkiraan saat diperlukan, tidak meminta lokasi latar belakang, tidak meminta akses jaringan, dan tidak mencadangkan data aplikasi otomatis. Lokasi, jadwal, dan progres disimpan secara lokal; pemindahan data dilakukan lewat cadangan JSON.

## Uji regresi mesin rutinitas

Jalankan `npm run test:legacy` atau `node tests/test_harness.js app/src/main/assets/Rutinitas.html` untuk memeriksa aturan jadwal, siklus hafalan, lokasi, penyimpanan, impor/ekspor, dan regresi. Jalankan `npm run test:quran` untuk memeriksa hash teks Uthmani, urutan 114 surah dan 6.236 ayat, pemisahan tampilan basmala tanpa kehilangan karakter sumber, metadata 604 halaman/30 juz, atribusi, dan sintaks UI. Jalankan `npm run test:names` untuk memastikan 114 nama Latin surah (transliterasi Kemenag, `QuranSurahNames.java`) cocok dengan jumlah ayat teks Tanzil dan urutan nama Arab.

Teks Al-Qur'an disalin verbatim dari Tanzil Project Uthmani v1.1. Pemberitahuan dan ketentuan penggunaan disertakan di `app/src/main/assets/TANZIL-LICENSE.txt`; teks sumber tidak diedit. Pada tampilan, basmala yang menjadi prefiks baris pertama surah (selain Al-Fatihah dan At-Tawbah) dipisah secara visual, dan fragmen yang disatukan tetap sama dengan baris sumber.

## Siapkan rilis Google Play

- Target API 36 dan Android App Bundle (AAB) sudah dikonfigurasi. Di Windows, jalankan `..\gradlew.bat :app:bundleRelease` dari folder `app`, atau `.\gradlew.bat :app:bundleRelease` dari root proyek.
- Google Play mewajibkan bundle ditandatangani dengan upload key. Tanpa `keystore.properties`, Gradle menghasilkan AAB tanpa tanda tangan untuk pemeriksaan lokal.
- Buat upload key menggunakan Android Studio **Build → Generate Signed Bundle / APK**, lalu jaga file keystore dan kata sandinya di tempat aman. Atau salin `keystore.properties.example` ke `keystore.properties`, isi `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, lalu letakkan keystore sesuai `storeFile`. File properti aktual dan keystore dikecualikan dari Git. Setelah itu `bundleRelease` akan menandatangani otomatis.
- Pada checkout ini, upload key sudah dibuat di `upload-key.keystore` dan `keystore.properties` lokal. Cadangkan keduanya secara aman sebelum unggah dan gunakan upload key yang sama untuk pembaruan berikutnya; jangan commit atau bagikan kedua file ini.
- Jangan mengubah `applicationId` setelah aplikasi pertama kali dibuat di Play Console. Pastikan `com.rutinitas.islamic` tersedia sebelum membuat listing.
- Siapkan kebijakan privasi sebagai halaman HTTPS publik dari `play-store/privacy-policy-id.html`; isi email kontak yang tampil pada listing Play Store dan gunakan kontak yang sama di Play Console.
- Materi listing Indonesia ada di `play-store/store-listing-id.md`. Play Console juga meminta ikon 512 × 512, gambar fitur 1024 × 500, screenshot ponsel, rating konten, target audiens, alamat kontak, dan formulir Data safety.
- Jika memakai akun developer personal yang dibuat setelah 13 November 2023, Play Console meminta closed test dengan 12 penguji yang opt-in terus-menerus selama 14 hari sebelum meminta akses produksi.

## Lock screen

Saat ada aktivitas aktif, Android menampilkan notifikasi Rutinitas yang dapat terlihat di layar kunci. Timer yang sedang berjalan memakai hitung mundur sistem. Visibilitas akhirnya mengikuti izin notifikasi dan pengaturan privasi lock screen perangkat.

## Sumber konten dan lisensi

Daftar lengkap ada di `app/src/main/assets/CREDITS.txt` dan ditampilkan di Setelan › Sumber & lisensi. Suara adzan bawaan (`app/src/main/res/raw/adzan_mekkah.mp3`) adalah rekaman adzan Masjidil Haram dari islamiques.net tanpa keterangan lisensi; hak cipta tetap milik pemegang hak. Ganti file ini bila ada klaim hak cipta.
