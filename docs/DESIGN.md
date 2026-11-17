# ArsipOS — Blueprint Identitas Visual (terkunci)

Konsep terpilih: **A — Kotak arsip + pesawat kertas** (arsip + Telegram). Konsep B (tumpukan berkas) dan C (perisai) ditolak: terlalu generik / metafora tidak menyatu.

## Aset
- `res/drawable/ic_launcher_foreground.xml` — ikon adaptif (zona aman 66 dp), `ic_launcher_monochrome.xml` — ikon tema Android 13.
- `res/mipmap-anydpi-v26/ic_launcher.xml` (adaptif) dan `res/mipmap-anydpi/ic_launcher.xml` (API 24–25).
- `res/drawable/ic_empty_archive.xml` — ilustrasi keadaan kosong.
- Splash Android 12+: `values-v31` dan `values-night-v31`.

## Palet (HEX)
| Peran | Terang | Gelap |
|---|---|---|
| Primary | #1E3A8A | #A9C0FF |
| Primary container | #DBE5FF | #2A4AA8 |
| Secondary | #0369A1 | #7DD3FC |
| Tertiary (aksen) | #B45309 | #FBBF24 |
| Latar / Permukaan | #F8FAFC / #FFFFFF | #0B1220 / #111A2E |
| Teks | #0F172A | #E5EAF5 |
| Error | #B91C1C | #FCA5A5 |
| Sky (khusus logo) | #38BDF8 | — |

Kontras WCAG (contrast_check.py): semua pasangan teks ≥ 4.8:1 (AA lulus).

## Tipografi & bentuk
Roboto sistem; judul SemiBold. Sudut: 8 / 12 / 20 / 28 dp. Implementasi: `Theme.kt` (`ArsipTheme`).

## Komponen
Top bar navy tetap dengan logo + status koneksi (hijau/merah/abu-abu); ikon kategori dalam lingkaran berwarna kategori (alpha 18%); keadaan kosong dengan ilustrasi.

## Tema (v2.2.0)
4 tema: Klasik, Alam, Luar Angkasa (selalu gelap), Samudra. Latar digambar prosedural (Canvas, tanpa aset); intensitas & animasi dapat diatur. Panel/kartu semi-transparan menjaga kontras teks.
