# Post / sosyal akış — ana Campus+ V009 doğrulaması

Tarih: 2026-11-01. **V009 ana geçişi tamamlandı.** Kullanıcı aynı SQL, clubs UNIQUE
ve belirtilen dar uygulama yetkilerini yalnız ana hedef için ayrıca açıkça onayladı.
Bu oturumda yeni modül veya feature geliştirilmedi; commit/push yapılmadı.

## Hedef, migration ve veri korunumu

- Gerçek sunucu: **127.0.0.1:5434/campus_plus, PostgreSQL 17.11**. Ön kontrol ve
  bütün envanter sorguları READ ONLY çalıştı. Başka DB/projeye bağlantı açılmadı.
- Başlangıç şeması V008 kontratıyla eşleşti. History BASELINE5 + SQL6 + SQL7 + SQL8,
  önceki migration satırları başarılıydı; uygulamanın history yazma yetkisi yoktu.
- Mevcut yerel `cem` hesabı yalnız onaylanan migration/grant için kullanıldı.
  Hesap/rol/parola oluşturulmadı veya değiştirilmedi. Başlangıçta başka aktif ana
  istemci, event trigger veya yeni nesnelere otomatik default grant yoktu.
- Testte başarılı olan kaynak dosyaları ve paketlenmiş sınıf byte'ları doğrulandı.
  [V009 SQL](../db/managed-migrations/V009__add_posts_and_moderation.sql) değişmedi:

```text
c29d73d7bc6ee279b7293117f6e6e963a58febd18c22440a0e4c9b4307787be3
```

Flyway transaction içinde yalnız V009 uygulandı; yaklaşık 54 ms. Timeout sınırları
korundu: lock 5 saniye, statement 30 saniye. Repair/clean/drop, backfill veya seed yok.
Yeni SQL9 history kaydı eklendi, önceki dört kaydın tam içerikleri/indeksleri korundu.
Flyway validate ve [V009 kontratı](../db/schema/v009-contract.json) karşılaştırması geçti.

Yeni yapı: posts (16 sütun, 14 constraint, 7 indeks), post_moderation_actions
(7 sütun, 7 constraint, 4 indeks), iki identity sequence. Mevcut clubs üzerinde
yalnız onaylı UNIQUE(id,university_id) + destek indeksi eklendi. Tüm kısıtlar validated,
indeksler valid/ready; eski şema üzerinde başka fark yok.

İlk sahiplik kontrolünde doğrulama aracının "tüm yeni nesneler cem'e aittir"
varsayımı hata verdi. İncelemede eski nesne/ACL değişikliği olmadığı görüldü:
clubs destek indeksi, PostgreSQL davranışı gereği mevcut tablo sahibi
`campus_plus_user` hesabına aitti. Aracın beklentisi buna göre düzeltildi;
**DB sahipliği değiştirilmedi**. Diğer 15 yeni relation'ın sahibi cem olarak doğrulandı.

Migration öncesi/sonrası/grant sonrası/GET kontrolleri sonrası tam row fingerprint
(sayı + sıralı satır JSON digest) ve eski nesne ownership/ACL karşılaştırmaları aynı:

| Mevcut tablo | Korunan kayıt sayısı |
| --- | ---: |
| users | 12 |
| user_roles | 12 |
| universities | 3 |
| student_profiles | 7 |
| clubs | 4 |
| club_memberships | 7 |
| events | 0 |
| event_participations | 0 |
| club_announcements | 0 |
| notifications | 0 |

Yeni posts ve audit tabloları son kontrolde de **boş**. İki yeni sequence
`last_value=1, is_called=false`: ana DB'de fixture veya geçici Post oluşturulmadı.
Parola/hash/rol/üniversite/profil satırları aynı kaldı; ham içerikler rapora yazılmadı.

## Runtime yetkileri

[Ana grant dosyası](../db/provisioning/main/04-post-runtime-grants.psql) ayrı transaction
içinde uygulandı. Yalnız `campus_plus_user`:

- posts: SELECT / INSERT / UPDATE;
- post_moderation_actions: SELECT / INSERT;
- posts_id_seq ve post_moderation_actions_id_seq: USAGE / SELECT.

Tam beş tablo privilege kaydı grantable=NO olarak doğrulandı. Audit UPDATE,
tablolarda DELETE/TRUNCATE, sequence UPDATE/reset ve history yazma yetkileri yok.
Eski nesnelerin yetkileri değişmedi. Uygulama hesabı sınırlı kaldı.

## Normal jar ile salt okunur uygulama kontrolleri

Başarılı testte üretilen jar'ın BOOT-INF sınıf byte'ları test edilen target/classes
ile eşleşti; Post controller/repository sınıfları pakette bulundu. Normal uygulama
geçici loopback portunda, campus_plus_user ile çalıştırıldı:

- Hem `ddl-auto` hem Hibernate schema mode **validate**; başlangıç başarılı.
- Yalnız bu geçici sürecin JDBC oturumlarında `default_transaction_read_only=on`;
  sürücünün transaction bayrağıyla bu sınırı kaldırması engellendi. Kalıcı uygulama,
  Homebrew veya PostgreSQL servis ayarı değiştirilmedi.
- Geçici JWT signing anahtarı yalnız bellekte üretildi; bu süreç için test token'ları
  kullanıldı. Gerçek yerel JWT secret dosyası checksum'ı değişmedi. Gerçek kullanıcı
  parolaları okunmadı veya giriş için kullanılmadı; Register/Login POST yapılmadı.
- **49 GET kontrolü başarılı**: eski kullanıcı/JWT/legacy NULL hash/401/403,
  üniversite/kulüp listeleri, Event/Announcement/Notification regresyonları;
  Post kişisel liste/detay/başkasının userId parametresiyle kapsamı değiştirememe,
  admin 403, olmayan kaynak 404, size/clubId sınırları ve geçersiz/expired token 401.
- Başarılı kişisel boş listelerde no-store ve yanıt şekli kontrol edildi. İçerik,
  token veya secret çıktısı basılmadı. POST/PATCH/DELETE veya read-mark yok.

### Mevcut verinin getirdiği doğrulama sınırı

Mevcut yedi profilde parola hash'i var, fakat **aktif üniversiteye bağlı profil yok**.
İlk smoke seçiminde aktif profil bulunamadığı görüldü; hiçbir üniversite/profil/rol
değiştirilmedi. Salt okunur seçim sorgusundaki bir yazım hatası özel araçta düzeltildi;
bu sorgu DB'ye veri yazmıyordu. Son smoke bu mevcut duruma göre çalıştırıldı.

Normal feed için beklenen yanıt **409 / Üniversite aktif değil**; bu koruma doğrulandı.
Aktif üniversiteyle başarılı feed/cursor/üyelik filtreleme veya uygun yerel kulüp
yöneticisiyle olumlu yönetim listesi ana DB'de çalıştırılmadı. Bu olumlu senaryolar
ve yarış koşulları [gerçek test DB doğrulamasında](post-test-validation.md) geçti.
Mevcut verileri etkinleştirmek bu onayın parçası değildir; ayrıca istenmeden yapılmaz.

Yalnız geçici doğrulama süreci kapatıldı. Son kontrolde başka ana istemci kalmadı;
V009 kontratı/history/ACL/row digest/boş yeni tablolar ve kullanılmamış sequence'ler
yeniden doğrulandı. Diğer servis veya projelere müdahale edilmedi.

## Test durumu ve sonraki aşama

Aynı kaynak kodunun son tam test sonucu **451 Java (394 izole +57 PostgreSQL) ve
38 Python başarılı**, contextLoads ve 13 Post PG testi dahil. Kaynak checksum'ları
bu sonuçtan sonra değişmedi; yalnız ana DDL/grant ve dokümantasyon için bütün testler
gereksiz yere yeniden çalıştırılmadı. Bu oturum ek kanıtı: Flyway/Hibernate validate,
ana şema/veri/ACL karşılaştırmaları ve 49 salt okunur GET.

Post metin gönderisi/sosyal akış/moderasyon aşaması ana ve test V009 ile tamamlandı.
Sonraki aday yorum/yanıtlar, beğeniler ve görünürlüğe duyarlı bildirim entegrasyonu.
Yeni özellik, migration veya commit/push bu doğrulama oturumunda başlatılmadı.
CI V009 workflow'u hazırlanmış durumda; henüz yeni commit/push veya bu değişiklikler
için uzak CI çalışması yok. Gizli/özel doğrulama çıktıları Git dışında tutuluyor.
