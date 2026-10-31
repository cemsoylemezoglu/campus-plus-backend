# Announcement — test veritabanı V007 doğrulaması

> Bu rapor test DB aşamasının tarihsel kaydıdır. Sonraki ayrı açık onayla ana DB
> V007 geçişi de tamamlandı: [ana uygulama raporu](announcement-main-validation.md).

Tarih: 2026-10-31. **V007 ve sınırlı test yetkileri yalnız
`127.0.0.1:5434/campus_plus_test` üzerinde uygulandı; bütün testler başarılı.**
Ana `campus_plus` bu adımda bağlanılmadan/yazılmadan korundu; son doğrulanmış sürümü V006.
Commit veya push yapılmadı. Notification koduna geçilmedi.

## Onay ve uygulama

Kullanıcı hedefi/SQL'i/yetkileri/testleri belirten soruya **“Yalnız campus_plus_test
üzerinde V007, sınırlı yetkiler ve belirtilen testleri açıkça onaylıyorum.”** yanıtını verdi.

- Salt okunur ön kontrolde gerçek PG17 adres/port/DB adı, V006 tam kontratı, iki başarılı
  SQL history kaydı, sınırlı migrator/DB sahibi ve diğer aktif istemci yokluğu doğrulandı.
  Beklenmedik event trigger veya migrator default ACL bulunmadı.
- Onaylanan [V007](../db/managed-migrations/V007__add_club_announcements.sql) ve
  [transaction ayarı](../db/managed-migrations/V007__add_club_announcements.sql.conf)
  review dizininden managed-migrations dizinine **aynı byte'larla** taşındı.
  İlk SQL açıklaması review aşamasını kaydeder; uygulanmış dosya düzenlenmedi.
- SHA-256: `8d00500e3a906dbd1be5fe097237edf18c0813208ac02c7e9c2ecfc660d54ae5`.
- Flyway transaction'ında yalnız V007 uygulandı. Mevcut V005/V006 SQL veya history
  satırları tekrar uygulanmadı/değişmedi; üçüncü başarılı V007 SQL kaydı eklendi.
- Son `flyway:validate` kaynak/history checksum uyumunu doğruladı; repair/clean/drop yok.

## Şema ve yetkiler

`club_announcements`: 16 sütun, 13 constraint (8 CHECK + 4 FK + PK), 6 indeks
(5 açık + PK), bir bigint identity sequence. Bütün constraint'ler validated,
indeksler valid/ready. Yeni tablo testler başlamadan boştu.

Kolon/default/identity, durum CHECK'leri, FK silme davranışları, partial indeksler ve
sequence tanımları uygulanan SQL ile incelendi. [V007 tam şema kontratı](../db/schema/v007-contract.json)
bu incelemeden sonra hazırlandı; final katalog aynı kontratı karşılıyor. Manifest
managed V005/V006/V007 SQL checksum'larını kapsar. Eski kontratlar ve uygulanmış
V001–V006 SQL dosyaları değiştirilmedi; genel SQL normalizasyonu eklenmedi.

[Test grant SQL'i](../db/provisioning/test/05-announcement-runtime-grants.psql) yalnız:

- `club_announcements`: SELECT, INSERT, UPDATE, DELETE;
- `club_announcements_id_seq`: USAGE, SELECT.

Tam ACL kontrolünde owner dışındaki **altı ayrı izin** yalnız test runner'a ait ve
grant option yok. Runner'ın DDL, TRUNCATE, sequence reset, history yazma veya PG
SUPERUSER/CREATEDB/CREATEROLE yetkisi yok. Rol/parola/servis yapılandırması değiştirilmedi.
Ana DB için hazırlanmış [ayrı grant dosyası](../db/provisioning/main/02-announcement-runtime-grants.psql)
çalıştırılmadı.

## Mevcut kayıtların korunması

Migration öncesi ve sonrası sekiz iş tablosunun tüm alanları kapsayan aggregate
fingerprint'leri aynı. Eski şema, nesne sahiplik/ACL'leri ve DB/public schema yetkileri
aynı kaldı. Yalnız onaylı sekiz relation (bir tablo, bir sequence, altı indeks) eklendi.

Testler yalnız yeni benzersiz fixture'lar oluşturdu. Son karşılaştırma başlangıçtaki
ID üst sınırlarıyla yapıldı; eski users/user_roles/universities/student_profiles/clubs/
club_memberships/events/event_participations satırları, parolalar ve roller dahil
aynı fingerprint'leri korudu. Fixture'lar silinmedi; ayrı test hesaplarının ADMIN
ilişkileri de yalnız izole test ortamındadır. Kullanıcı/hash/token değerleri rapora
çıkarılmadı. Ham kanıtlar ve loglar repo dışında 0600 dosyalarda tutuldu.

## Test sonucu

`-Ppostgres-it verify`: **328 Java testi başarılı**; hata/başarısız/atlanan yok:

| Grup | Test |
| --- | ---: |
| İzole Java: eski regresyonlar ve yeni duyuru testleri | 297 |
| PostgresAnnouncementIT | 8 |
| PostgresEventIT | 13 |
| PostgresApiIT | 9 |
| CampusPlusBackendApplicationIT (`contextLoads()`) | 1 |

Yeni gerçek PostgreSQL testleri; duyuru yaşam döngüsü/sürümleri, geri çekme gerekçesi
gizliliği, üyelik kaybında erişim/count/page değişimi, farklı üniversite ve marker-only
reddi, iki eşzamanlı yayın, yayın/club archive yarışı, event bağlantısı/kitle uyumu,
club+announcement rollback ve doğrudan SQL CHECK/FK ihlali rollback'ini doğruladı.
Mevcut Event/Club/Auth regresyonları da aynı V007 ortamında geçti.

- **34 Python testi başarılı**: V007 için tam dokuz tablo zorunluluğu ve farklı/eksik
  tablo reddi eklendi; eski hedef/sır/readonly/checksum korumaları devam eder.
- Güncellenen workflow actionlint v1.7.12 kontrolünden geçti.
- CI kurulumu V005→V007, yeni V007 kontratı ve test grant adımı için hazırlandı.
  GitHub'a push veya uzak CI çalışması yapılmadı; eski uzak başarı V005 sürümüne aittir.

## Sonraki açık onay: ana Campus+ V007

Bu rapor **ana veritabanı uygulama onayı değildir**. Ayrı onay verilirse:

1. Yalnız `127.0.0.1:5434/campus_plus` PG17, mevcut BASELINE5+SQL6 history,
   V006 kontratı ve sekiz tablonun veri/yetki fingerprint'leri salt okunur doğrulanacak.
2. Aynı checksum'lı V007 transaction içinde uygulanacak: bir boş tablo, 16 sütun,
   13 constraint, 6 indeks, bir sequence ve tek V007 SQL history kaydı eklenecek.
   Mevcut sekiz iş tablosuna DDL/DML/seed/backfill yok.
3. `02-announcement-runtime-grants.psql` mevcut sınırlı `campus_plus_user` hesabına
   yalnız yeni tablonun DML ve sequence'in USAGE/SELECT yetkilerini verecek.
   PG rolü/parolası veya servis ayarı değişmeyecek.
4. Eski kayıtlar/yetkiler, tam V007 şeması ve Flyway validate doğrulanacak. Normal jar
   `ddl-auto=validate` ile geçici loopback portunda, yalnız bu kontrol sürecine özel
   read-only DB oturumlarıyla başlatılıp GET/JWT/API kontrolleri yapılacak.
   Ana DB'de test hesabı/duyuru oluşturulmayacak; test profili ana DB'ye yönlendirilmeyecek.

Bu çalışma ağacı yeni entity nedeniyle V007 ister. Ana DB onayı/uygulaması olmadan
V006 ana şemada validate hatası beklenir; validate kapatılmaz. Diğer projeler,
PG14, Docker, Supabase ve Render bu aşamada kullanılmadı.
