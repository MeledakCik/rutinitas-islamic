# Google Play listing: Rutinitas

## Short description

Jadwal ibadah, baca Al-Qur'an offline, hafalan, dan rutinitas harian.

## Full description

Atur rutinitas ibadah dan target hafalan harian dalam satu aplikasi yang ringan dan dapat digunakan tanpa internet.

Rutinitas membantu Anda:

- Menyusun kegiatan harian dan melihat aktivitas yang sedang berjalan.
- Membaca teks Al-Qur'an Uthmani secara offline, memilih surah, melompat ke ayat, dan menyimpan bacaan terakhir serta penanda.
- Mengatur progres hafalan Sabaq, Sabqi, dan Manzil.
- Menggunakan hitung mundur aktivitas dan melihat statusnya di layar kunci.
- Menambahkan widget layar utama untuk melihat aktivitas dan target terbaru.
- Menghitung jadwal waktu salat secara offline memakai lokasi perkiraan, koordinat manual, atau jadwal yang Anda masukkan sendiri.
- Mencadangkan dan memulihkan jadwal melalui file JSON pilihan Anda.

Lokasi hanya digunakan setelah Anda memilih sumber GPS dan memberikan izin. Jadwal, koordinat, dan progres tersimpan di perangkat. Rutinitas tidak memakai akun, iklan, analitik, atau server.

Jadwal, hafalan, dan teks Arab tersedia offline. Gunakan internet untuk streaming murattal atau mengunduh murattal dan terjemahan Indonesia per surah. Penentuan lokasi otomatis mengikuti layanan lokasi Android, yang dapat memakai GPS, Wi-Fi, atau jaringan seluler sesuai setelan perangkat. Jika lokasi tidak tersedia, Anda dapat memasukkan koordinat manual atau memakai jadwal tetap.

Waktu salat dihitung menurut metode yang dipilih di aplikasi. Cocokkan jadwal dengan otoritas setempat yang Anda ikuti.

## Play Console fields to complete

- Category: Lifestyle (confirm in Play Console)
- Ads: No
- App access: No login required
- Privacy policy URL: host `privacy-policy-id.html` from this folder on a public HTTPS page
- Data safety (jawaban yang disarankan, cek ulang dengan manifest rilis):
  - Mengumpulkan data? **Tidak.** Lokasi perkiraan hanya diproses di perangkat dan tidak dikirim (data yang tidak meninggalkan perangkat tidak dihitung "dikumpulkan").
  - Membagikan data? **Tidak.** Permintaan murattal, waktu ayat (MP3Quran.net) dan terjemahan (EQuran.id) dimulai pengguna dan hanya berisi nomor surah/qari.
  - Data dienkripsi saat transit? **Ya** (semua koneksi HTTPS, cleartext dimatikan).
  - Pengguna dapat meminta penghapusan? Data lokal: hapus data aplikasi / copot.
- Izin yang perlu dijelaskan: `SCHEDULE_EXACT_ALARM` (pengingat adzan tepat waktu, diminta lewat setelan; tidak memakai `USE_EXACT_ALARM`), `RECEIVE_BOOT_COMPLETED` (menjadwalkan ulang pengingat), `POST_NOTIFICATIONS`, `ACCESS_COARSE_LOCATION`.
- Kebijakan privasi: kontak `depupdev@gmail.com` sudah tercantum di `privacy-policy-id.html`; publikasikan di URL HTTPS publik (mis. GitHub Pages) sebelum mengisi Play Console. Gunakan email yang sama sebagai email developer di Play Console.
- Content rating, target audience, contact email, screenshots, 512 × 512 app icon, and 1024 × 500 feature graphic
- Complete developer identity verification in Play Console and any required Android app registration checks for the account
- Verify that `com.rutinitas.islamic` is available before creating the Play Console app; package name cannot be changed after first upload
