# STRIX 🦉

STRIX adalah aplikasi Monitoring tool Android

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
1. Di halaman utama Firebase Console (dalam project yang baru dibuat), klik logo **Android** `< / >` atau ikon gear ⚙️ **Project settings** (Pengaturan Proyek).
2. Tambahkan aplikasi Android. Masukkan **Package Name** `com.strix.safesync`
3. Setelah aplikasi Android terdaftar, scroll ke bawah ke bagian **Your apps** (Aplikasi Anda) di dalam Project Settings.
4. Di situ akan ada:
   - **App ID**
   - **Project ID**
   - **Web API Key**: Terdapat pada bagian **General** > **Project Credentials** > Web API Key.

### 3. Aktifkan Layanan Firebase
Sebelum mulai dipakai, **Wajib** mengaktifkan layanan berikut di panel sebelah kiri Firebase Console:
1. **Authentication**:
   - Buka menu *Build* > *Authentication* > *Get Started*.
   - Ubah tab ke *Sign-in method*, lalu aktifkan **Anonymous** (Pengguna Anonim).
2. **Firestore Database**:
   - Buka menu *Build* > *Firestore Database* > *Create database*.
   - Pilih region, mulai dalam Mode Uji (Test Mode) -> *Update Rules* agar `allow read, write: if true;` (atau sesuaikan keamanan).

### 4. Input di Aplikasi STRIX
1. Buka aplikasi.
2. Di halaman awal "Select Your Role", klik tombol **⚙️ Custom Server Config** di paling bawah.
3. Masukkan ke-3 data tersebut:
   - **Web API Key**: `AIzaSy...`
   - **App ID**: `1:xxxx:android:yyyy`
   - **Project ID**: `nama-project`
4. Tekan **Save & Restart**. Selesai! Aplikasi akan terhubung otomatis ke server pribadi.

## Google Play Protect! ⚠️

**Penting:** Karena STRIX ini berjalan di latar belakang dan izin akses berat (Keylogger dan Lokasi), **Google Play Protect dan beberapa aplikasi m-banking mendeteksi aplikasi ini sebagai Spyware/Malware.**

*   Cara install, **WAJIB** ke Play Store -> klik Profil -> **Play Protect** -> klik ikon gir (⚙️) -> **Matikann fitur "Scan apps with Play Protect"**. **WAJIB Aktifkan Special apps/access notification & accessibility untuk aplikasi ini di pengaturan hp TARGET/CLIENT**
*   Proyek ini **HANYA Untuk Edukasi** atau pantau HP anak/family , Jika dipakai untuk hal menyimpang (menyadap pacar, dsb) tanpa ada kesepakatan, risiko ditanggung sendiri! **DEVELOPER TIDAK BERTANGGUNG JAWAB ATAS PENYALAHGUNAAN APLIKASI INI!**

## Lisensi
MIT License bebas pakai 🍻
