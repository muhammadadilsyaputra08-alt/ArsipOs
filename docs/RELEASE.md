# Cara merilis ArsipOS

1. Isi 3 secret di GitHub (Settings > Secrets and variables > Actions):
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (opsional `KEY_PASSWORD`, default sama dengan password keystore).
2. Naikkan `versionCode` dan `versionName` di `app/build.gradle.kts` setiap rilis.
3. Rilis: buat tag `git tag v2.1.0 && git push origin v2.1.0`, atau jalankan workflow "Release APK" manual.
4. Hasil: APK bertanda tangan + AAB + SHA256SUMS di halaman Releases (tag) atau di Artifacts (manual).

Simpan file keystore di tempat aman (cadangan offline). Jika hilang, pembaruan tidak bisa dipasang di atas versi lama. Jangan pernah commit keystore (`*.jks` sudah ada di `.gitignore`).
