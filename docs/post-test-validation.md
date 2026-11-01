# Post / sosyal akış — V009 test veritabanı doğrulaması

> Sonraki ayrı açık onayla V009 ana DB'de de tamamlandı.
> [Ana doğrulama](post-main-validation.md). Aşağıdaki ana geçiş bölümü, bu test
> raporunun hazırlandığı andaki onay planıdır.

Tarih: 2026-11-01. **Yalnız test DB aşaması başarılı.** Kullanıcı V009, yeni nesnelere
dar runner yetkileri ve benzersiz test kayıtlarıyla testleri açıkça onayladı.
Ana `campus_plus` için V009 onayı alınmadı; bu oturumda ona bağlanılmadı.
Commit/push yapılmadı. Diğer proje, DB, Docker veya servis yapılandırması kullanılmadı.

## Hedef ve kontrollü migration

- Her preflight/inventory sorgusu READ ONLY transaction'da gerçek hedef kimliğini
  doğruladı: **127.0.0.1:5434/campus_plus_test, PostgreSQL 17.11**.
- Kullanılan migrator `campus_plus_test_migrator`: DB sahibi, SUPERUSER/CREATEDB/
  CREATEROLE/REPLICATION/BYPASSRLS yetkileri yok. Başka aktif test istemcisi,
  açık event trigger veya yeni nesnelere otomatik default grant yoktu.
- V008 kontratı/SQL SHA manifesti, dört başarılı SQL history kaydı ve eski on
  tablonun row fingerprint/ACL/ownership durumu migration öncesinde doğrulandı.
- [V009 SQL](../db/managed-migrations/V009__add_posts_and_moderation.sql) onaylanan
  byte'ları korunarak review dizininden managed dizine taşındı. SHA-256 değişmedi:

```text
c29d73d7bc6ee279b7293117f6e6e963a58febd18c22440a0e4c9b4307787be3
```

Flyway transaction'ında yalnız V009 uygulandı: yaklaşık 61 ms. `lock_timeout=5s`,
`statement_timeout=30s`; eski migration'lar düzenlenmedi/tekrar çalıştırılmadı.
History SQL5–SQL8 kayıtları korundu, başarılı SQL9 eklendi. Son Flyway validate geçti.
Normal açılışta otomatik migration yok; `ddl-auto=validate` korunuyor.
SQL dosyasındaki PREPARED/outside-discovery başlığı onay öncesinin tarihsel yorumudur;
uygulanmış dosya sırf bu yorum için değiştirilmez. Gerçek konum/history bu raporda kayıtlıdır.

## Şema ve yetkiler

| Nesne | Doğrulanan yapı |
| --- | --- |
| posts | 16 sütun, PK + 5 FK + 8 CHECK, 7 indeks |
| post_moderation_actions | 7 sütun, PK + 2 FK + UNIQUE + 3 CHECK, 4 indeks |
| Yeni identity sequence | 2; BIGINT, sahiplik/kolon bağımlılığı doğru |
| clubs | Yalnız onaylanan UNIQUE(id,university_id) + destek indeksi eklendi |

Tüm constraint'ler validated, indeksler valid/ready. Yeni tablolar grant/test öncesi
boştu. Foreign key RESTRICT davranışları, composite kulüp/üniversite bağı ve enum/
durum/tarih/blank/version CHECK tanımları SQL niyetine karşı incelendi.
[V009 kontratı](../db/schema/v009-contract.json) immutable V008 üzerine yalnız bu
incelenmiş farklar eklenerek oluşturuldu; sadece katalog çıktısının tamamı doğru
varsayılmadı. Eski tablolarda ek UNIQUE dışında yapısal fark yok.

[Test grant dosyası](../db/provisioning/test/07-post-runtime-grants.psql) ayrıca
transaction içinde uygulandı. Runner `campus_plus_test_runner`:

- posts: SELECT / INSERT / UPDATE;
- post_moderation_actions: SELECT / INSERT;
- yalnız iki yeni sequence: USAGE / SELECT.

Tam beş tablo privilege kaydı, grantable=NO olarak doğrulandı. Runtime audit UPDATE,
iki tabloda DELETE/TRUNCATE, sequence UPDATE/reset, schema/database CREATE veya
üst rol üyeliği yok. Her test bağlantısında mevcut `PostgresTestDatabase` gerçek
hedef ve limited-role kontrolü yapar. Eski nesnelerin ACL/ownership'i değişmedi.

## Test sonucu ve giderilen hata

İlk gerçek çalışmada mevcut 44 PG testi ve yeni Post testlerinin 9'u geçti;
4 feed testi HTTP 500 ile kaldı. PostgreSQL SQLSTATE **42P18**, ilk sayfadaki
`:beforeTime IS NULL` timestamptz parametresinin tipini belirleyemediğini gösterdi.
Bu hata mocked repository testinde görülemezdi; gerçek DB testiyle yakalandı.

`PostRepository.feedFirst` ayrı ilk sayfa sorgusu olarak eklendi; sonraki sayfa
sorgusu yalnız gerçek cursor zamanı/id ile çalışıyor. `PostQueryService` doğru
yolu seçiyor; unit test her iki çağrıyı doğruluyor. Şema, grant veya onaylanan SQL
değişmedi; sorgu güvenlik predicate'leri kaldırılmadı.

Düzeltmeden sonra **tam Maven verify yeniden çalıştırıldı**:

| Test grubu | Başarılı |
| --- | ---: |
| İzole Java | 394 |
| PostgresApiIT | 9 |
| PostgresAnnouncementIT | 10 |
| PostgresNotificationIT | 11 |
| PostgresEventIT | 13 |
| CampusPlusBackendApplicationIT / contextLoads | 1 |
| PostgresPostIT | 13 |
| **Toplam Java** | **451** |
| Python schema/CI araç testleri | **38** |

0 başarısızlık, 0 hata, 0 atlama. actionlint ve `git diff --check` temiz.
Gerçek Post testleri lifecycle/ownership, eski üniversite, üyelikten ayrılınca
gizlilik, pasif kulüp, cursor sırası/yeni kayıt, private append-only audit,
publish/publish, publish/archive, publish/profile transfer, publish/manager demotion,
update/hide, transaction rollback ve SQL FK/CHECK/UNIQUE/privilege senaryolarını kapsadı.
Kontrollü 500/lock timeout/constraint hataları yalnız negatif test senaryolarıdır.

100 gönderilik fixture ile HTTP feed sorgu sayısı ≤12 kontrolü geçti. Ek READ ONLY
EXPLAIN ANALYZE SELECT'te `ix_posts_feed`, kullanıcı PK ve üyelik destek indeksi
kullanıldı; 100 görünür satır yaklaşık 0.886 ms plan execution süresinde okundu.
Bu küçük yerel fixture gözlemidir; üretim yükü veya ölçek garantisi değildir.

## Veri korunumu ve test sınırları

Migration öncesi ve hemen sonrası **on eski tablonun tüm satır fingerprintleri aynı**:
users, user_roles, universities, student_profiles, clubs, club_memberships, events,
event_participations, club_announcements, notifications.

Testler eski kayıtları değiştirmedi. Sonrasında başlangıç ID sınırlarıyla aynı
satırlar yeniden karşılaştırıldı; içerik/sayı/digest eşit. Role/profile kontrolünde
başlangıç user ID sınırı kullanıldı. Yeni test verileri yalnız test DB'de tutuldu;
otomatik cleanup, silme veya reset yok. İki çalışmanın sonunda 231 yeni Post ve
6 audit satırı kaldı; diğer yeni hesap/üniversite/kulüp fixture'ları da aynı test
ortamında kaldı. SQL hata davranışı yazıları rollback transaction'larında yapıldı.
Identity sequence boşlukları normaldir; satır korunumu sequence sayısıyla ölçülmez.

Son V009 şema/SQL/history karşılaştırması, eski ACL eşitliği ve Flyway validate
başarılı; test istemcileri kapandı. Token/parola/body/reason veya kullanıcı satır
çıktıları repo/rapora eklenmedi. Özel kanıtlar yalnız yerel /tmp altında; test raporları
Git dışında target dizininde. Kaynak checksum snapshot'ı alındı.

CI workflow V005→V009 boş kurulum, v009 kontrat ve yeni dar grant için güncellendi;
yerelde actionlint/Python geçti. **Bu workflow henüz push edilmedi veya uzakta
çalıştırılmadı.** Yerel mevcut test DB geçişi, sıfırdan uzak CI kurulumunun yerine geçmez.

## Sonraki ayrı onay: ana Campus+ V009

Ana hedef yalnız **127.0.0.1:5434/campus_plus, PostgreSQL 17** olacak. Önce gerçek
identity/V008 schema/history/SQL SHA/aktif istemciler/ACL/on eski tablonun fingerprintleri
READ ONLY kontrol edilir. Beklenmeyen farkta durulur.

Ayrı açık onay verilirse aynı V009 transaction içinde uygulanır; iki yeni tablo ve
sequence, clubs üzerinde ek UNIQUE/index oluşturulur. Mevcut kayıtlarda seed/backfill/
UPDATE/DELETE yok. [Ana dar grant dosyası](../db/provisioning/main/04-post-runtime-grants.psql)
yalnız campus_plus_user için yukarıdaki aynı SELECT/INSERT/UPDATE/sequence haklarını
verir; audit UPDATE veya DELETE yok. Ana DB history BASELINE5+SQL6+SQL7+SQL8 korunur,
SQL9 eklenir. Kısa clubs kilidi ve timeout sınırları aynıdır; servis ayarı değişmez.

Sonra V009 kontrat/history/ACL/row fingerprint karşılaştırması, Flyway/Hibernate
validate ve normal uygulamada yalnız salt okunur GET kontrolleri yapılır. Ana DB'de
test hesabı/gönderi veya başka fixture oluşturulmaz; var olan hash/JWT secret/roller
değişmez. Bu onay beklerken ana DB'ye yeni kodla uygulama başlatılmamalıdır:
ana şema V008, yeni entity'ler V009 gerektiriyor. Commit/push ayrıca istenir.
