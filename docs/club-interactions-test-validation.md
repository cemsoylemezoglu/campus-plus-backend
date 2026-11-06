# Event/Announcement yorum ve beğeni: V011 test doğrulaması

> Bu rapor test aşamasının kaydıdır. Sonrasında ana hedef ayrıca onaylandı ve
> V011/90 GET doğrulaması tamamlandı: [ana rapor](club-interactions-main-validation.md).

Tarih: 2026-11-06. **Test aşaması başarılı. Ana DB için henüz onay/uygulama yok.**

## Uygulanan hedef ve SQL

- Yalnız PostgreSQL 17, `127.0.0.1:5434/campus_plus_test` kullanıldı. Her bağlantıda
  gerçek server/database kimliği doğrulandı; sınırlı migrator/runner hesapları
  kullanıldı. Ana DB'ye veya diğer projelere bağlantı açılmadı.
- Açık kullanıcı onayıyla V011 transaction içinde uygulandı. İncelenen SQL byte
  değiştirilmeden review dizininden managed dizinine taşındı. Flyway'ın bildirdiği
  DDL süresi yaklaşık 99 ms; bu yerel bir ölçümdür.
- SQL: [V011](../db/managed-migrations/V011__add_club_content_interactions.sql), SHA-256
  `2b64de742ad7425e777a40d32eac32dcb053749dd70ff56cb0a9037fc6181f5d`.
- Test grant: [09-club-interaction-runtime-grants.psql](../db/provisioning/test/09-club-interaction-runtime-grants.psql), SHA-256
  `274213e636d29e40589ece3e29efa1b6ff12e147c8550451dc8e1a2bd03a3c1b`.
- Eski V001–V010 SQL/kontrat dosyaları değiştirilmedi. Yeni
  [V011 şema kontratı](../db/schema/v011-contract.json) gerçek katalogdan çıkarıldı;
  tanımları onaylanan SQL ile incelendi ve önceki şemanın yalnız onaylanan farkları
  taşıdığı karşılaştırmayla doğrulandı.

İlk offline Maven denemesinde Flyway plugin cache eksikti; SQL çalışmadı. V010
history/şema/veri korunumu yeniden kontrol edilip projede zaten tanımlı Flyway
12.4.0 indirildi. Sonraki migration başarılı; hata için SQL değiştirme, history
repair, veri düzeltme veya ikinci bir migration uygulanmadı.

## Şema, yetki ve veri korunumu

| Tablo | Kolon | Constraint | Index |
| --- | ---: | ---: | ---: |
| club_comments | 13 | 16 | 9 |
| club_content_likes | 8 | 9 | 6 |
| club_comment_likes | 7 | 6 | 4 |
| club_comment_moderation_actions | 8 | 8 | 4 |
| notifications (V011 sonrası) | 16 | 20 | 13 |

Yeni dört tabloda 39 constraint ve 23 index; dört bigint identity sequence doğrulandı.
FK/UNIQUE/CHECK'ler validated, index'ler valid/ready. Notification farkı yalnız
nullable club_comment_id, üç FK, bir partial index ve genişletilmiş iki CHECK.

Yeni tablo/sequence'lerin sahipliği yalnız `campus_plus_test_migrator` hesabında.
`campus_plus_test_runner` için etkin yetkiler tam olarak:

- Üç yeni etkileşim tablosunda SELECT/INSERT/UPDATE (9 hak).
- Audit tablosunda SELECT/INSERT (2 hak).
- Dört sequence üzerinde USAGE/SELECT (8 hak).
- DELETE/TRUNCATE, audit UPDATE ve sequence UPDATE/reset yok. Eski nesnelerin
  ACL'leri, schema/database owner/ACL ve önceki Flyway history satırları aynı.

Migration öncesi 16 tablonun bütün mevcut satırları için sıralı içerik fingerprint'i,
satır sayısı ve ID sınırları alındı. Migration/grant sonrasında tam eşitlik;
testlerden sonra yalnız başlangıç ID kapsamındaki satırlar için tekrar eşitlik
kanıtlandı. User, user_roles, password kayıtları ve tüm önceki akademik/kulüp/
etkinlik/duyuru/Post/yorum/bildirim kayıtları korundu. Notifications karşılaştırması
eski kolon projeksiyonuyla yapıldı; eski ID'lerde club_comment_id ayrıca NULL.

Testler yalnız yeni, ayrı fixture'larla çalıştı. Yeni dört tablodaki kalan test
verileri: 679 club_comments, 12 club_content_likes, 6 club_comment_likes ve
14 club_comment_moderation_actions. Bunlar ana DB kayıtları değildir; test aşaması
ve tekrar koşularının verileridir. Constraint denemeleri kendi yeni fixture'larında
transaction rollback ile yapıldı. Veri/şema temizliği, reset veya eski kayıt silme yok.

## Test sonucu

Son tam `mvn -Ppostgres-it verify` koşusu **BUILD SUCCESS**:

- **540 izole Java**: 0 failure/error/skipped.
- **84 PostgreSQL Java**: 0 failure/error/skipped, contextLoads dahil.
- Toplam **624 Java**. Ayrıca **44 Python**, actionlint ve whitespace kontrolleri başarılı.

PostgreSQL dağılımı: Announcement 10, Notification 11, Event 13, Post 13,
PostComment 15, yeni ClubInteraction 12, Api 9 ve contextLoads 1.

12 yeni gerçek PG testi SQL/cursor/placeholder, source/comment beğeni yarışları,
source version korunumu, root-remove/reply ve cancel/withdraw yarışları, FK/CHECK,
üyelik/üniversite/yerel yönetim sınırları, bağımsız moderasyon/audit gizliliği,
bildirim önceliği/dinamik erişim, gerçek writer/audit rollback ve 100 satırlık
sayfada en fazla 20 SQL sorgusu kontrollerini içerir.

İlk koşuda yeni test fixture'larında iki sorun görüldü: olmayan tekil bildirim GET
yolu ve transaction proxy'sinin dış katmanına Mockito stub'ı uygulanması. Inbox
API'si ve gerçek writer target kullanılarak düzeltildi. Audit testinin güçlendirilmiş
kontrolü de repository JDK proxy'sinde callRealMethod kullanımını yakaladı; test
artık hata üretmeden önce aynı transaction içinde gerçek audit satırını ve yorum
bayrağını görüyor, rollback sonrasında ikisinin de geri alındığını doğruluyor.
Bu düzeltmelerden sonra son tam koşu yukarıdaki sayılarla geçti. Uygulama kodu ve
onaylı V011 SQL'i tekrar koşularında değişmedi.

Ek salt okunur EXPLAIN ANALYZE, yalnız kök yorumları getiren sorgu parçasında
Event için ix_club_comments_event_roots, Announcement için
ix_club_comments_announcement_roots kullanımını gösterdi. Her biri 101 satır;
yerel yürütme süreleri yaklaşık 0,137/0,122 ms. Bu tüm API'nin veya üretim yükünün
performans garantisi değildir. Tam API sorgu sayısı ayrıca yukarıdaki PG testindedir.

Son kontroller: V011 kontratı/Flyway validate, eski kayıt/ACL/history eşitliği,
eski notification yeni alanı NULL ve uygulama kaynak/POM checksum eşitliği başarılı.
Gizli yerel Campus+ anahtar/parola değerleri Git-visible dosyalarda bulunmadı;
değerler çıktıya yazılmadı. Commit/push/force push yapılmadı.

## CI ve sonraki onay noktası

Maven hedefi 11, varsayılan Flyway skip=true ve ddl-auto=validate korunuyor. CI
workflow dosyası V005→V011 boş kurulum, V011 kontrat ve yeni dar grant için
güncellendi. Actionlint başarılı. Bu workflow henüz push edilmedi/uzakta
çalıştırılmadı; bu oturumda yerelde boş DB/cluster/Docker kurulmadı.

Ana DB'ye bu oturumda bağlanılmadı. Son bilinen ana şema V010; yeni kod ana hedefte
başlatılmadan önce ayrı V011 onayı/uygulaması/validate gerekir.

Ana onay teklifi: aynı SQL ve [hazır ana grant](../db/provisioning/main/06-club-interaction-runtime-grants.psql)
yalnız `127.0.0.1:5434/campus_plus` üzerinde uygulanacak. Ana grant SHA-256:
`4a41f82c66e193ad4da27a9da1fc59e2e7d3e8396a8f667f027b5526da5521cd`.

Ana hedefte önce kimlik/V010/geçmiş/veri/ACL kontrolü; sonra transaction migration,
yeni şema ve eski değer korunumu; ardından yalnız campus_plus_user için aynı dar
yeni nesne yetkileri. Ana DB'de test verisi oluşturulmayacak. Flyway/Hibernate
validate ve fixture'sız salt okunur GET kontrolleri yapılacak. Notifications ALTER
ve CHECK taramasının güçlü kilit etkisi, 5 sn lock/30 sn statement timeout aynen
geçerli. Beklenmeyen hedef/şema/veri veya DDL hatasında durulacak. Ana uygulama için
ayrı açık onay bekleniyor; commit/push onayı yerine geçmez.
