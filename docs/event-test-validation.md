# Event / EventParticipation — test veritabanı doğrulaması

> Bu rapor test DB aşamasının kaydıdır. Sonraki açık onayla ana DB V006 geçişi de
> tamamlandı: [ana DB uygulama ve doğrulama raporu](event-main-validation.md).

Tarih: 2026-10-31. **V006 yalnız `127.0.0.1:5434/campus_plus_test` üzerinde
onayla uygulandı; bütün testler başarılı. Ana `campus_plus` V005 olarak kaldı.**
Bu oturumda ana DB bağlantısı/yazması, commit veya push yapılmadı.

## Uygulanan SQL ve kapsam

- [V006](../db/managed-migrations/V006__add_events_and_participations.sql), Flyway'ın
  transaction'ı içinde uygulandı. Dosyanın SHA-256 değeri:
  `046713d9afa9cb508a38b3176b7f2b86808ca4e9a18beb105fc3e6a25f46434c`.
  SQL ve `executeInTransaction=true` ayarı uygulama sonrasında değiştirilmedi.
- Önce gerçek sunucu adresi/portu, PostgreSQL 17 sürümü, DB adı, sınırlı migrator
  kimliği/sahipliği, V005 şeması ve history kaydı doğrulandı. Başka aktif test istemcisi yoktu.
- `events` (26 sütun), `event_participations` (14 sütun), iki identity sequence,
  29 constraint (19 CHECK, 7 FK, 2 PK, 1 unique) ve 12 indeks oluştu.
  İndeks toplamına PK/unique için otomatik oluşan üç indeks dahildir.
- Bütün constraint'ler validated, indeksler valid/ready; iki yeni tablo testlerden önce boştu.
  History'de V005 ve V006 SQL kayıtları başarılı. Otomatik baseline/repair/clean yapılmadı.
- [Test runtime grant](../db/provisioning/test/04-event-runtime-grants.psql) yalnız iki
  yeni tabloya DML ve iki sequence'e USAGE/SELECT verir. Runner'a DDL, TRUNCATE,
  sequence reset, history yazma veya PostgreSQL yönetici yetkisi verilmedi.
  PG rolü/parolası, servis ayarı veya başka proje ortamı değiştirilmedi.

## Veri ve şema korunması

Migration öncesi/sonrası eski altı tablonun satır sayıları ve veri fingerprint'leri
karşılaştırıldı; aynı kaldı. Testler benzersiz fixture kayıtları eklediği için son
karşılaştırma başlangıçtaki ID üst sınırlarıyla yapıldı: eski kullanıcı, rol, parola,
üniversite, profil, kulüp ve üyelik satırlarının fingerprint'leri hâlâ aynı.
Mevcut nesnelerin sahiplik/ACL'leri, DB/public schema yetkileri de değişmedi.
Test fixture'ları yalnız test DB'sinde bırakıldı; otomatik veri temizliği yapılmadı.

[V006 şema kontratı](../db/schema/v006-contract.json) katalog tanımlarının SQL ile
incelenmesi sonrasında hazırlandı. Son envanter kolon/default/identity, FK/CHECK,
indeks ve sequence tanımlarını tam olarak karşılıyor. Kontrat managed V005/V006 SQL
SHA-256 değerlerini de doğrular. Tarihsel V001–V005 ve managed V005 dosyaları korunmuştur.
Önceden incelenmiş dört eski enum CHECK yazım eşdeğerliği dışında genel SQL
normalizasyonu yoktur. Özel bağlantı bilgileri ve ham test çıktıları repoya eklenmedi.

## Test sonucu

`-Ppostgres-it verify`: **273 Java testi başarılı**, hata/başarısız/atlanan yok:

- 250 izole test: mevcut regresyonlar, Event güvenlik/iş kuralları, DTO validation,
  JPA metadata ve HQL derleme kontrolleri.
- 23 gerçek PostgreSQL testi: `contextLoads()` + 9 mevcut API testi + 13 Event testi.
- Event canlı testleri: aynı satırda tekrar kayıt, son koltuk yarışı, çift başvuru,
  kapasite azaltımı/kayıt, etkinlik iptali/kayıt, üyelikten ayrılma/kayıt ve üniversite
  değişimi/kayıt yarışları; erken check-in kontenjanı, transaction rollback,
  doğrudan SQL constraint'leri, özel keşif/roster pagination, complete/no-show/check-in,
  çıkarma/yeniden kayıt izni/erteleme/iptal durumları.
- `python3 -m unittest discover -s scripts/tests`: **32 test başarılı**.
- Tarihsel SQL checksum ve managed SQL checksum kontrolü başarılı.
- Güncellenen CI workflow'u actionlint v1.7.12 statik kontrolünden geçti.

Canlı testte bulunan tarih filtresi hatası düzeltildi: boş tarih parametresinin
PostgreSQL tarafından tiplendirilememesi listelemeyi 500'e düşürüyordu.
`EventRepository.discover` tarih parametresini açık `instant` cast ile tiplendiriyor;
boş ve dolu tarih aralıkları gerçek SQL üzerinden test ediliyor. Cast adı yerel
Türkçe locale'de de çözümlenecek küçük harf biçiminde kullanıldı.
Rollback testinde transaction proxy'si dışında Mockito stub kurulması da test
hedefi unwrap edilerek düzeltildi; üretim transaction zorunluluğu korunuyor.
Son tam test koşusu bu düzeltmeleri içerir.

CI V006 migration/kontrat/grant adımları için hazırlandı ancak henüz GitHub'a
iletilmedi/çalıştırılmadı. Önceki `d0b250e` CI başarısı V005 sürümüne aittir.

## Sonraki onay noktası: ana Campus+ V006

Bu rapor ana DB uygulama onayı değildir. Kullanıcı ayrıca onay verirse:

1. Yalnız `127.0.0.1:5434/campus_plus` PostgreSQL 17 hedefi, mevcut BASELINE 5,
   eski altı tablo/veri/yetki fingerprint'leri ve V005 şeması salt okunur doğrulanacak.
   Beklenmeyen farkta işlem duracak.
2. Yukarıdaki aynı checksum'lı V006 transaction içinde uygulanacak. İki boş tablo,
   40 sütun, 29 constraint, 12 indeks ve iki sequence eklenecek; history'ye V006 SQL
   kaydı yazılacak. Eski tablolara DDL/DML/seed veya kullanıcıya ADMIN ataması yok.
3. Ayrı [ana DB runtime grant SQL'i](../db/provisioning/main/01-event-runtime-grants.psql)
   yalnız yeni iki tablo/sequence için mevcut sınırlı `campus_plus_user` hesabına
   gerekli yetkileri verecek. PG rolü/parolası veya servis ayarı değişmeyecek.
4. Eski kayıtlar/yetkiler, V006 kontratı ve migration history yeniden doğrulanacak;
   normal uygulama Hibernate `validate` ile başlatılıp salt okunur API kontrolü yapılacak.
   Ana DB'de test hesabı/kulüp/etkinlik oluşturulmayacak. Entegrasyon testleri ana DB'ye
   yönlendirilmeyecek; test profili bunu zaten reddeder.

Sonraki ürün modülüne, commit veya push işlemine geçilmedi.
