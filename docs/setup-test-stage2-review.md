# Kurulum / test altyapısı — 2. aşama sonucu

Tarih: 2026-10-30. Durum: **salt okunur inceleme ve dosya hazırlığı tamamlandı; SQL uygulanmadı**.
Başlangıç HEAD `8ac68ff`; 1. aşama değişiklikleri korundu. Commit/push yapılmadı.

## Hedef ve veri koruma

Bağlantı SQL üzerinden `127.0.0.1:5434/campus_plus`, PostgreSQL 17.11 (`170011`)
olarak doğrulandı. Bütün sorgular `default_transaction_read_only=on`, kısa timeout ve
psql `-X` ile çalıştı. Envanter repeatable-read/read-only transaction kullandı.
Yalnız SELECT ve oturum/transaction ayarları çalıştırıldı; DDL, DML, migration,
baseline, DB/rol oluşturma veya servis yapılandırması değişikliği yapılmadı.

| Tablo | Satır sayısı |
| --- | ---: |
| users | 12 |
| user_roles | 12 |
| universities | 3 |
| student_profiles | 7 |
| clubs | 4 |
| club_memberships | 7 |

Kontrol aralığının iki ucunda altı tablonun satır sayıları ve tüm alanları kapsayan
toplu satır özetleri aynı bulundu. Tekil kullanıcı/e-posta/parola hash'i çıktıya veya
Git dosyasına alınmadı. Sequence pozisyonları okunmadı/değiştirilmedi.

## Şema bulguları

- 6 tablo, 52 sütun, 38 kısıt, 17 indeks, 4 bigint identity sequence.
- Kısıtlar validated; indeksler valid/ready. Tek aktif OWNER partial unique indeksi mevcut.
- `users.id` BY DEFAULT identity; email unique kısıtı mevcut Hibernate adıyla korundu:
  `uk6dotkott2kjsp8vw4d0m25fb7`. Bu yapısal ad gizli bilgi değildir.
- public içinde ek view/foreign table, kullanıcı trigger/routine veya beklenmedik iş tablosu yok.
- Encoding UTF8, libc locale provider, collate/ctype en_US.UTF-8. Sütun collations default.
- `flyway_schema_history` mevcut değil. Tek extension varsayılan plpgsql.
- V001–V005 içerikleri HEAD ile aynı; SHA-256 manifesti eklendi.

## Hazırlanan çözüm

1. `db/managed-migrations/V005__baseline_schema.sql`: güncel şemanın boş DB kurulumu.
   Kullanıcı/ADMIN seed'i, eski tablo ALTER/DROP veya veri UPDATE/DELETE içermez.
   PG17 ve boş public şema kontrolü vardır; transaction Flyway'in `.sql.conf` dosyasıyla yönetilir.
2. `db/schema/v005-contract.json`: kolon/default/identity/collation, bütün kısıt/indeksler,
   sequence tanımları ve locale dahil karşılaştırma kontratı. İş verisi ve sahiplik yoktur.
3. `schema-tools` Maven profili: BOM'un yönettiği Flyway 12.4.0; PostgreSQL modülü ve
   JDBC yalnız plugin kapsamındadır. Uygulama çalışma zamanı bağımlılığı eklenmedi.
4. Profilin lifecycle bağı yok, `flyway.skip=true`, otomatik baseline/clean/schema oluşturma
   kapalı, hedef sürüm 5. DB_* fallback yok; ileride onaylı işleme özel MIGRATION_DB_*
   sağlanacak. `ddl-auto=validate` değişmedi.
5. `schema_tools.py` ve verification SQL'leri: URL ve gerçek kimlik/read-only kontrolü,
   salt okunur envanter/toplu özetler, çevrimdışı kontrat ve eski dosya checksum kontrolü.
6. [Runbook](schema-bootstrap-runbook.md): yeni boş DB ve mevcut dolu DB için ayrı yollar,
   onay sınırları, etkiler ve hata durumunda durma kuralları.

Mevcut DB ileride ancak yeni test DB kurulumu doğrulandıktan sonra, ayrı açık onayla
sürüm 5 baseline kaydı alabilir. Bu yeni history tablo/index/kaydı oluşturur; iş tablolarını
yeniden kurmaz. Baseline tek başına şema doğrulaması yapmadığından kontrat kontrolü zorunludur.

## Adres kontrolü düzeltmesi

PostgreSQL `inet_server_addr()::text` için `127.0.0.1/32` döndürüyor. İlk aşamadaki test
sınırı sadece `127.0.0.1` beklediğinden geçerli test hedefini reddedebilirdi. Test yardımcısı
ve envanter sorgusu `host(inet_server_addr())` kullanacak şekilde düzeltildi. Hedef
genişletilmedi. Gerçek kaynakta salt okunur SELECT ile çıktı doğrulandı; test DB'sine bağlanılmadı.

## Doğrulama ve sınırları

- **189 Java izole testi geçti**, 0 hata/başarısız/atlanan. Bütün DB/JWT değişkenleri
  kaldırılmış süreçte normal clean test çalıştı. Üretim Java/resource kodu değişmedi.
- **13 Python aracı testi geçti**: yanlış hedef, credential URL, fallback, read-only
  oturum, erken kimlik reddi, şema/locale drift ve legacy checksum kontrolü.
- Gerçek kaynak DB envanteri kontratla eşleşti. Bu, henüz uygulanmamış başlangıç
  SQL'inin aynı şemayı ürettiğinin kanıtı değildir.
- Flyway Maven parametreleri descriptor'dan doğrulandı. Transaction ayarı desteklenen
  script-level `.sql.conf` dosyasındadır. `help:describe` başarılı.
- DB değişkenleri olmadan varsayılan `schema-tools / flyway:info`, **Skipping Flyway execution**
  ve BUILD SUCCESS verdi; DB sorgusu veya migration çalışmadı.
- Gerçek migrate/baseline, yeni DB contextLoads ve boş kurulum/rollback testleri
  **çalıştırılmadı**. Rollback transaction içinde bile DDL uygulanmadı.

## Kalan riskler ve sonraki doğrulama

Başlangıç DDL'si ve Flyway history oluşumu ancak onaylı yeni test DB üzerinde doğrulanabilir.
Kontrat schema-wide izinleri, sahiplik ve tablespace'i kapsamaz; yetkiler ayrı DB/rol SQL'inde
incelenmelidir. Locale uyumsuzluğu/katalog metin farkında otomatik repair veya eski veri düzeltmesi yoktur.

Sonraki aşamada kesin test DB/rol/grant ve parola sağlama planı gösterilecek. Açık onay
sonrası yalnız yeni test hedefi kurulup V005 başlangıç migration'ı uygulanacak; boş iş
tabloları, history, kontrat, gerçek context, constraint/rollback ve API yarışları doğrulanacak.
Ana DB baseline işlemi bundan sonra ayrı onay gerektirir. CI ve Event'e başlanmadı.

Teknik kaynaklar ve komutlar [runbook'tadır](schema-bootstrap-runbook.md).
