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

### 1. Buat Project Baru di Firebase
1. Buka [Firebase Console](https://console.firebase.google.com/).
2. Klik **Add project** (Tambah proyek), beri nama, dan lanjutkan sampai selesai.

### 2. Dapatkan Kredensial (API Key, App ID, Project ID)
1. Di halaman utama Firebase Console (dalam project yang baru dibuat), klik logo **Android** `< / >` atau ikon gear ⚙️ **Project settings** (Pengaturan Proyek).
2. Tambahkan aplikasi Android. Masukkan **Package Name** (contoh: `com.strix.safesync`). Anda tidak perlu mengupload SHA-1 atau mendownload `google-services.json`.
3. Setelah aplikasi Android terdaftar, scroll ke bawah ke bagian **Your apps** (Aplikasi Anda) di dalam Project Settings.
4. Di situ Anda akan menemukan:
   - **App ID**: Kombinasi unik seperti `1:1234567890:android:abcdefghij`
   - **Project ID**: Biasanya nama project Anda dengan angka (contoh: `my-safesync-1a2b3`)
   - **Web API Key**: Terdapat pada bagian **General** > **Project Credentials** > Web API Key (kombinasi panjang huruf dan angka).

### 3. Aktifkan Layanan Firebase
Sebelum mulai dipakai, Anda **Wajib** mengaktifkan 3 layanan berikut di panel sebelah kiri Firebase Console:
1. **Authentication**:
   - Buka menu *Build* > *Authentication* > *Get Started*.
   - Ubah tab ke *Sign-in method*, lalu aktifkan **Anonymous** (Pengguna Anonim).
2. **Firestore Database**:
   - Buka menu *Build* > *Firestore Database* > *Create database*.
   - Pilih region, mulai dalam Mode Uji (Test Mode) -> *Update Rules* agar `allow read, write: if true;` (atau sesuaikan keamanan).
3. **Storage** (Hanya jika perlu fitur file download):
   - Buka menu *Build* > *Storage* > *Get Started*.
   - Sama seperti Firestore, set Rules ke Test Mode (`allow read, write: if true;`).
   - Copy **Storage Bucket** URL-nya (contoh: `my-safesync-1a2b3.appspot.com`).

### 4. Input di Aplikasi SafeSync
1. Buka aplikasi SafeSync di profil Host/Client.
2. Di halaman awal "Select Your Role", klik tombol **⚙️ Custom Server Config** di paling bawah.
3. Masukkan ke-4 data tersebut:
   - **Web API Key**: `AIzaSy...`
   - **App ID**: `1:xxxx:android:yyyy`
   - **Project ID**: `nama-project`
   - **Storage Bucket**: `nama-project.appspot.com`
4. Tekan **Save & Restart**. Selesai! Aplikasi akan terhubung otomatis ke server pribadi The User.

## Google Play Protect! ⚠️

**Penting:** Karena STRIX ini fungsinya di latar belakang dan izin akses berat (Keylogger dan Lokasi), **Google Play Protect detect aplikasi ini sebagai Spyware/Malware.**

*   Cara install, **WAJIB** ke Play Store -> klik Profil -> **Play Protect** -> klik ikon gir (⚙️) -> **Matiin fitur "Scan apps with Play Protect"**.
*   Proyek ini *HANYA* Untuk **Edukasi** atau pantau HP anak/family yang memang sudah janjian! Kalau dipakai buat hal menyimpang (nyadap pacar, dsb) tanpa ada kesepakatan, risiko ditanggung sendiri!, **DEVELOPER TIDAK BERTANGGUNG JAWAB ATAS PENYALAHGUNAAN APLIKASI INI!**

## Lisensi
MIT License bebas pakai 🍻
