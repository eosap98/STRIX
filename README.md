# STRIX 🦉

STRIX adalah aplikasi Monitoring tool Android

## Fitur ✨

*   **Pilih Role:** **Host** (Pemantau) atau **Client** (Target yang dipantau).
*   **Mode Penyamaran (Stealth):** icon aplikasinya bisa berubah total jadi:
    *   **Kalkulator:** Bisa dipake ngitung beneran.
*   **Cara Buka Sandi Rahasia:** ketik `8888='
*   **Phantom Reply:** Sebagai Host, bisa langsung balas chat WhatsApp/Telegram yang masuk ke HP target dari jauh tanpa ketahuan!
*   **Data Real-time:** Aplikasi ini otomatis mengirim rute Lokasi, SMS, Log Telpon, Kontak, Daftar Aplikasi, Info WiFi, rekaman ketikan keyboard (Keylogger), sampe isi semua Notifikasi target langsung ke database (Firebase) kamu.

## Cara Setting Private Server (Firebase) ⚙️

1.  Buka web [Firebase Console](https://console.firebase.google.com/) lalu buat *Project* baru.
2.  Nyalakan **Firestore Database** (pilih Test Mode), **Authentication** (nyalain Anonymous Mode)
3.  Daftar aplikasi dengan Package Name: `com.strix.safesync`.
4.  Download file `google-services.json` dan taroh di folder `app/`
5.  setup aplikasi STRIX melalui "Costum Server" di layar awal, masukkan *App-Id* , *Web-Api-Key* dan *Project-Id* dari Firebase.

## Google Play Protect! ⚠️

**Penting:** Karena STRIX ini fungsinya di latar belakang dan izin akses berat (Keylogger dan Lokasi), **Google Play Protect detect aplikasi ini sebagai Spyware/Malware.**

*   Cara install, **WAJIB** ke Play Store -> klik Profil -> **Play Protect** -> klik ikon gir (⚙️) -> **Matiin fitur "Scan apps with Play Protect"**.
*   Proyek ini *HANYA* Untuk **Edukasi** atau pantau HP anak/family yang memang sudah janjian! Kalau dipakai buat hal menyimpang (nyadap pacar, dsb) tanpa ada kesepakatan, risiko ditanggung sendiri!, **DEVELOPER TIDAK BERTANGGUNG JAWAB ATAS PENYALAHGUNAAN APLIKASI INI!**

## Lisensi
MIT License bebas pakai 🍻
