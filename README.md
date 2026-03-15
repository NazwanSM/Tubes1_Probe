# Greedy Algorithm Bots for Battlecode 2025

## Deskripsi

**Battlecode 2025** adalah kompetisi pemrograman di mana pemain merancang bot yang bertarung dalam arena virtual untuk mendominasi peta dan mengamankan sumber daya (cat/paint dan chips). Pemain mengembangkan algoritma bot yang mengatur strategi pembangunan menara, pergerakan unit, dan manajemen cat tanpa kendali manual selama pertempuran. 

Repositori ini berisi kumpulan bot Battlecode 2025 berbasis **Java 21** yang seluruhnya diimplementasikan menggunakan strategi **greedy**. Pada pendekatan ini, setiap unit menghitung skor heuristik untuk banyak opsi aksi yang valid, lalu secara greedy langsung mengeksekusi opsi dengan perolehan skor tertinggi pada giliran tersebut. Terdapat tiga paket bot utama (`mainBot`, `alternativeBots1`, `alternativeBots2`) yang dapat dipertandingkan silang lewat Gradle.

### Algoritma Greedy yang Diimplementasikan

#### 1. `mainBot`
Bot ini berfokus pada optimasi mikro-manajemen dengan evaluasi probabilitas aksi secara paralel menggunakan sistem skor terpadu.
- **Tower**: Secara greedy memilih *spawn* unit berdasarkan skor heuristik adaptif (fase *early/mid/late game*, kebutuhan mopper periodik, dan sedikit noise acak), lalu menembak musuh ber-HP terendah yang bisa dijangkau.
- **Soldier**: Pada tiap giliran menghitung skor untuk berbagai aksi: melengkapi/menandai pola tower, mewarnai tile pola yang salah, menyelesaikan/menandai *resource pattern*, tarik/beri cat, *upgrade* tower, atau bergerak. Aksi dengan skor tertinggi langsung dieksekusi, ditambah eksekusi aksi sekunder (bonus pengecatan) untuk mempercepat pembangunan pola.
- **Splasher**: Memilih pusat serangan dengan skor tertinggi (prioritas area ber-cat musuh/kosong dan bonus dekat tower musuh), bergerak secara greedy mendekati target *rush* (prediksi simetris markas atau intel jaringan), dan menarik cat dari tower saat stok kritis.
- **Mopper**: Memberi skor untuk memukul (*mop*) tile cat musuh terdekat, *mop-swing* jika mengenai kerumunan musuh, transfer cat ke rekan, atau menarik cat dari tower bila stok kritis. Pergerakan selalu diarahkan ke sumber cat musuh atau rekan yang membutuhkan cat.

#### 2. `alternativeBots1`
Bot ini berfokus pada agresi ekonomi awal dengan manajemen tugas berbasis tumpukan (*Stack*) yang bekerja seperti *State Machine*. Ia langsung mengambil keputusan pasti berdasarkan *state* teratas.
- **Tower**: Di fase pembukaan memaksa *spawn* beberapa Soldier, berlajut memilih jenis *spawn* (Soldier/Splasher/Mopper) berdasar urutan sederhana dengan insting reaktif (tergantung *chips* & ancaman musuh), serta me-relai pertempuran.
- **Soldier**: Menggunakan *stack* prioritas berlapis: pada hierarki tertinggi akan *rush* musuh/mempertahankan reruntuhan, di bawahnya terdapat tugas membangun *Money Tower* pada reruntuhan terdekat, lalu mengisi cat secara reaktif, dan selalu mewarnai pijakan.
- **Splasher**: Memprioritaskan keamanan cat (menghindari kehabisan cat dengan reaktif *refill*), menyerang pusat *splash* berprioritas statis (cat musuh > kosong > sekutu), mengejar musuh terdekat tanpa kalkulasi pergerakan rumit, atau eksplorasi.
- **Mopper**: Jika terdeteksi musuh, langsung bergerak ke musuh reaktif terdekat dan melakukan *mop-swing* ke arahnya, saat lengang membersihkan cat musuh terdekat. Ambang isi ulang cat yang rendah seketika memicu transisi ke tugas *refill*.

#### 3. `alternativeBots2`
Bot ini berfokus pada sinkronisasi taktis berskala global menggunakan sistem komunikasi terpusat (*Goal Manager*).
- **Tower**: Pola *spawn* menggunakan probabilitas dinamis per fase (*early/mid/late*) dengan rasio unit yang berbeda. Berfungsi sebagai pusat komando yang me-relai pesan reruntuhan, tower musuh, dan kebutuhan mopper, serta menembak target terpilih oleh modul *combat*.
- **Soldier**: Menggunakan *Goal Manager* yang secara greedy memilih prioritas antara *capture/contest ruin*, *battlefront*, *rush* tower musuh, *paint-area*, atau eksplorasi. Pemilihan tipe tower bergeser secara dinamis (*Money Tower* lalu beralih ke *Paint Tower*) mengikuti fase permainan. Saat di reruntuhan, ia mengecat tile yang salah paling dekat dan menuntaskan pola yang tersedia.
- **Splasher**: Mencari pusat serangan dengan akumulasi skor tertinggi (banyak cat musuh/kosong, penalti jarak), melakukan *refill* cepat di tower terdekat saat cat < 100, lalu bergerak ke *battlefront* atau sumber cat musuh.
- **Mopper**: Menebar cat musuh berdasarkan skor berlapis (kedekatan, kebutuhan transfer cat sekutu, permintaan `NEED_MOPPER` dari jaringan), memprioritaskan *mop-swing* jika mengenai musuh, serta membagikan sisa cat berlebih ke Soldier ber-cat rendah.

## Requirement

Sebelum menjalankan program, pastikan Anda memiliki beberapa dependensi berikut:

- **JDK 21** (wajib, sesuai dengan konfigurasi `build.gradle`).
- **Gradle Wrapper** (sudah disertakan pada repositori ini via `gradlew` / `gradlew.bat`, tidak perlu instal Gradle terpisah).
- **Battlecode 2025 engine**: `artifacts/engine/engine.jar` harus tersedia (sudah disertakan pada repositori ini).
- **Battlecode client**: Letakkan artefak klien sesuai Sistem Operasi Anda di direktori `artifacts/client/` (contoh Linux: `.AppImage`, Windows: `.msi/.exe`). Diperlukan bila ingin menonton tayangan pertandingan secara lokal.

## Cara menjalankan program

1. Clone atau buka repositori ini.
2. Pastikan `artifacts/engine/engine.jar` dan artefak klien sesuai OS tersedia di `artifacts/client/` (lihat pesan error `verifyPrebuiltArtifacts` bila belum ada).
3. Build dan verifikasi:
   - Windows: `./gradlew.bat build`
4. Jalankan match dengan cara run file dalam folder client dan mempilih opsi bots yang tersedia (Alternative bot 1/Alternative bot 2/Main Bot)

## Author

| Nama                        | NIM      | Email                       |
| --------------------------- | -------- | --------------------------- |
| Muhammad Adam Mirza         | 18223015 | 18223015@std.stei.itb.ac.id |
| Nazwan Siddqi Muttaqin      | 18223066 | 18223066@std.stei.itb.ac.id |
| Matthew Sebastian Kurniawan | 18223096 | 18223096@std.stei.itb.ac.id |
