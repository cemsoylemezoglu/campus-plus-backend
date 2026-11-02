# Yorum / yanıt / beğeni — V010 test DB doğrulaması

> Sonraki ayrı açık onayla ana V010 da tamamlandı: [ana rapor](comments-main-validation.md).
> Aşağıdaki ana geçiş planı bu test raporunun hazırlandığı andaki onay noktasıdır.

Tarih: 2026-11-02. **Yalnız test DB aşaması başarılı.** Kullanıcı V010, tanımlanan
dar grant'ler ve benzersiz fixture'larla gerçek testleri açıkça onayladı.
Ana campus_plus'a bağlanılmadı; ana geçiş için ayrıca onay gerekir.
Commit/push veya force push yapılmadı; başka proje/DB/servis kullanılmadı.

## Hedef ve migration

Gerçek hedef READ ONLY preflight ile doğrulandı: **127.0.0.1:5434/campus_plus_test,
PostgreSQL 17.11**. V009 kontratı, başarılı SQL5–SQL9 history kayıtları, eski on iki
tablonun değer/ACL snapshot'ı ve onaylanan SQL SHA kontrol edildi. Migrator mevcut
campus_plus_test_migrator: DB sahibi, SUPERUSER/CREATEDB/CREATEROLE/REPLICATION/
BYPASSRLS yok. Başka aktif test istemcisi, event trigger veya default grant yoktu.

[V010 SQL](../db/managed-migrations/V010__add_post_comments_and_likes.sql) onaylanan
byte'ları korunarak review dizininden managed dizine taşındı:

```text
cf1097be5f3a58a5a1457def062b3f13972b0b55b5e4937b54a69440397789ae
```

Flyway transaction'ında yalnız V010 uygulandı; yaklaşık 70 ms. lock_timeout 5 saniye,
statement_timeout 30 saniye korundu. Eski migration'lar değiştirilmedi; repair/clean/
drop, seed veya backfill yok. History SQL10 başarı kaydı eklendi; önceki beş kayıt
korundu. Son Flyway validate başarılı.

SQL'deki PREPARED/outside-discovery yorumu kod aşamasının tarihsel notudur; uygulanmış
dosya yorum için yeniden yazılmaz. Gerçek konum ve history durumu bu raporda kayıtlıdır.

## Gerçek şema ve dar yetkiler

| Tablo | Sütun | Constraint | İndeks |
| --- | ---: | ---: | ---: |
| post_comments | 12 | 11 | 6 |
| post_likes | 7 | 6 | 4 |
| post_comment_likes | 7 | 6 | 4 |
| comment_moderation_actions | 8 | 8 | 4 |
| notifications, V010 sonrası | 15 | 17 | 12 |

Yeni dört identity sequence BIGINT/start1/increment1/cache1/no-cycle, doğru tablo/
kolona internal bağımlı. Tüm constraint'ler validated ve indeksler valid/ready.
Root/post composite FK, RESTRICT davranışları, unique user/source ve scope/lifecycle/
blank/version/time CHECK tanımları SQL niyetine karşı incelendi.

Notifications'a yalnız iki nullable alan, üç FK ve iki partial indeks eklendi;
type/source CHECK'leri onaylanan dört sosyal tipi kapsayacak şekilde genişletildi.
Eski 23 tipin kombinasyonları korundu ve yeni alanları NULL şartına bağlandı.
Başka eski tabloda yapısal fark yok. [V010 kontratı](../db/schema/v010-contract.json)
immutable V009 üzerine yalnız incelenen farklar eklenerek üretildi; tüm katalog
çıktısı kendiliğinden doğru sayılmadı. Eski kontratlar ve yalnız dört enum CHECK
normalizasyon istisnası korundu; genel SQL normalizasyonu eklenmedi.

[Test grant dosyası](../db/provisioning/test/08-comment-runtime-grants.psql):

- Üç etkileşim tablosunda runner SELECT/INSERT/UPDATE;
- comment_moderation_actions için yalnız SELECT/INSERT;
- yalnız dört yeni sequence için USAGE/SELECT.

Tam 11 tablo privilege kaydı grantable=NO olarak doğrulandı. Audit UPDATE,
DELETE/TRUNCATE, sequence UPDATE/reset, schema/database CREATE ve history yazma
yok. Eski nesne ACL/ownership aynı; notifications'a yeni grant verilmedi.
Test bağlantıları mevcut PostgresTestDatabase ile her seferinde gerçek test hedefi
ve sınırlı runner rolünü doğrular; ana DB credential fallback'i yok.

## Gerçek testte bulunan hata ve düzeltme

İlk tam PG çalışmasında 15 yeni testin 11'i geçti; dört Post like testi HTTP 500
ile kaldı. Bildirim INSERT'inin SELECT DISTINCT kısmındaki çıplak NULL kolonları
PostgreSQL tarafından text olarak çözümlenip bigint hedefle uyuşmuyordu.

CommentNotificationWriter tekilleştirmeyi alıcı alt sorgusuna taşıdı. Dış INSERT
SELECT DISTINCT kullanmıyor; NULL alanlar hedef kolon tipine göre çözümleniyor.
Parametre sırası, source_key, alıcı filtreleri ve hedefli ON CONFLICT korundu.
Migration/constraint/grant değiştirilmedi. Düzeltmeden sonra **tam verify tekrar**
çalıştırıldı; yalnız başarısız testler seçilip diğerleri atlanmadı.

## Son sonuç

| Test grubu | Başarılı |
| --- | ---: |
| İzole Java | 449 |
| PostgresApiIT | 9 |
| PostgresAnnouncementIT | 10 |
| PostgresNotificationIT | 11 |
| PostgresEventIT | 13 |
| CampusPlusBackendApplicationIT / contextLoads | 1 |
| PostgresPostIT | 13 |
| PostgresCommentIT | 15 |
| **Toplam Java** | **521** |
| Python schema/CI araç testleri | **40** |

0 başarısızlık, 0 hata, 0 atlama. actionlint ve diff whitespace kontrolü başarılı.
Yeni PG testleri ownership/edit/remove/placeholder, üyelik kaybı/kendi unlike,
RESOURCE/PLATFORM gizleme ve audit gizliliği; PUT retry/re-like/tek notice;
reply alıcı önceliği/kulüpsüz hedef/root hide; farklı uygun alıcılar; concurrent
like PUT ve root-remove/reply; bildirim INSERT sonrası rollback/audit rollback;
FK/CHECK/UNIQUE/dar yetki; marker/local kulüp sınırı; gerçek cursor/sonradan hide;
100 yorumda bounded sorgu ve kulüp yöneticisi üyelik kaybı senaryolarını kapsadı.

100 yorumlu fixture'da HTTP sorgu sayısı ≤18 kontrolü geçti. READ ONLY EXPLAIN ANALYZE
SELECT'te ix_post_comments_roots ve ilişkili destek/PK indeksleri kullanıldı;
100 görünür satır için yaklaşık 0.451 ms execution gözlendi. Küçük yerel fixture
sonucudur; üretim throughput veya sınırsız ölçek garantisi değildir.

## Mevcut kayıtların korunumu

On iki eski tablonun migration öncesi ve hemen sonrası değer/count digest'leri
aynı. Test sonrası başlangıç ID sınırlarıyla **başlangıçta var olan aynı satırlar**
yeniden karşılaştırıldı; sonuç aynı. Profil/rol karşılaştırmasında başlangıç user ID
sınırı kullanıldı. Kapsam: users, user_roles, universities, student_profiles, clubs,
club_memberships, events, event_participations, club_announcements, notifications,
posts ve post_moderation_actions.

Bildirim fingerprint'i eski kolonlar üzerinden alındı: to_jsonb(row)-post_id-comment_id.
Böylece iki yeni NULL kolon yanlış veri değişimi sayılmadı; bütün eski alanlar
karşılaştırıldı. Başlangıçtaki tüm notification ID'lerinde yeni iki alanın NULL
kaldığı ayrıca doğrulandı. Eski okunma durumu/version/source kayıtları değişmedi.

Yalnız yeni test fixture'ları oluşturuldu ve test DB'de bırakıldı. İki çalışmanın
sonunda 241 comment, 4 Post like, 1 comment like ve 14 comment audit satırı var;
yeni hesap/üniversite/kulüp/Post/notification fixture'ları da yalnız bu test ortamında.
Otomatik cleanup/reset yok. Negatif SQL testleri yalnız yeni fixture'lara yönelik
rollback transaction'ları kullandı. Sequence boşlukları beklenir; veri korunumu
ID ardışıklığına göre ölçülmez.

Final V010 kontrat/SHA/history, tüm ACL eşitliği ve Flyway validate geçti; başka test
istemcisi kalmadı. Başarılı kod kaynak checksum snapshot'ı özel /tmp altında alındı.
Ham body/reason, parola/hash, token veya row çıktısı repo/rapora eklenmedi.
CI V005→V010 kurulum/kontrat/grant için hazırlandı; yerelde statik kontroller geçti.
**Bu workflow henüz push edilmedi veya uzakta çalıştırılmadı.** Son a82300e uzak CI
başarısı önceki V009 sürümüne aittir. Yerel geçiş boş uzak CI kurulumunun yerine geçmez.

## Sonraki ayrı onay: ana Campus+ V010

Ana hedef yalnız **127.0.0.1:5434/campus_plus, PG17**. Bu oturumda bağlanılmadı;
son doğrulanmış durum V009. Ayrı açık onayla önce exact identity/V009/history/aktif
istemci/SQL SHA/eski on iki tablonun kolon değerleri/ACL snapshot'ı alınır.

Aynı V010 transaction içinde dört tablo/sequence ve yalnız notifications üzerindeki
onaylı nullable FK/indeks/CHECK değişimlerini uygular. Ana satırlarda seed/backfill/
UPDATE/DELETE veya okundu değişikliği yok. Notifications ALTER/CHECK doğrulama kilidi
5 saniye lock/30 saniye statement sınırında; beklenmeyen farkta durulur.

[Ana grant dosyası](../db/provisioning/main/05-comment-runtime-grants.psql) yalnız
campus_plus_user için aynı üç tablo DML, audit SELECT/INSERT ve dört sequence
USAGE/SELECT haklarını verir. Başka nesne/rol/parola/servis yetkisi değiştirilmez.
History BASELINE5+SQL6+SQL7+SQL8+SQL9 korunur, SQL10 eklenir.

Sonra V010 kontrat/Flyway/Hibernate validate, eski kolon değer/ACL karşılaştırmaları
ve **yalnız GET** kontrolleri yapılır. Ana DB'de test hesabı/yorum/beğeni oluşturulmaz;
pasif üniversite veya profiller etkinleştirilmez. Yeni kod V010 gerektirir; ana V009'a
karşı validate kapatılarak başlatılmaz. Commit/push ve force push yapılmaz.
