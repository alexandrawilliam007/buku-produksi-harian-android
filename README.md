# BUKU PRODUKSI HARIAN — Proyek Android Studio Native (Kotlin + Jetpack Compose + Room)

## Teknologi
- Bahasa: **Kotlin 2.0**
- UI: **Jetpack Compose + Material Design 3** (Hijau Tua `#14532D`, Putih, Abu-abu)
- Database Lokal (100% Offline): **Room Database (SQLite)**
- Arsitektur: **MVVM + Kotlin Coroutines & StateFlow**
- Penyimpanan File: **Android Storage Access Framework (SAF)**
- Ekspor: **Apache POI (`poi-ooxml`) untuk `.xlsx`**, **`android.graphics.pdf.PdfDocument` untuk `.pdf`**, **Kotlinx Serialization untuk Backup/Restore `.json`**

## Cara Menjalankan Proyek di Android Studio
1. Buka **Android Studio** (versi Ladybug / Meerkat atau terbaru dengan JDK 17).
2. Pilih **File > Open...** lalu arahkan ke folder `android-project`.
3. Tunggu Gradle Sync selesai mengunduh dependensi.
4. Jalankan unit test melalui terminal Android Studio:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Pilih emulator Android (API 26+) atau hubungkan HP Android dengan USB Debugging aktif, lalu klik **Run (▶)**.

## Cara Membangun APK (Debug & Release)
### 1. Debug APK (Untuk Pengujian Langsung di HP)
Melalui menu Android Studio: **Build > Build Bundle(s) / APK(s) > Build APK(s)**
Atau lewat terminal:
```bash
./gradlew assembleDebug
```
Output APK berada di:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Release APK (Untuk Distribusi Produksi)
1. Buat keystore signing melalui **Build > Generate Signed Bundle / APK...** -> pilih **APK**.
2. Isi alias, password keystore, lalu pilih build variant **release**.
3. Output APK siap pasang berada di `app/build/outputs/apk/release/app-release.apk`.
