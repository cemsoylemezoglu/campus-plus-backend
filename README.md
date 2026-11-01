# Campus+

Campus+, üniversite öğrencilerini, öğrenci kulüplerini ve kampüs yaşamını bir araya
getiren sosyal platformun Spring Boot tabanlı REST API projesidir.

> Geliştirme durumu: Post metin gönderileri, sosyal akış ve moderasyon aşaması tamamlandı.
> V009 ana ve test Campus+ DB'lerinde ayrı açık onaylarla uygulandı. Aynı kodun
> 451 Java/38 Python testi ve ana ortamda 49 salt okunur GET kontrolü başarılı.
> [Ana doğrulama ve mevcut veri sınırları](docs/post-main-validation.md),
> [otomatik test sonucu](docs/post-test-validation.md).

Tamamlanan son aşama [gönderiler ve kampüs sosyal akışı](docs/social-feed-architecture.md).
Kod ve V009 SQL'i hazır; **V009 ana ve test DB'de ayrı açık onaylarla uygulandı**.
[Kod aşaması ve SQL etkileri](docs/post-phase1-review.md).

**Güncel test sonucu: 451 Java (394 izole + 57 PostgreSQL) ve 38 Python başarılı**;
contextLoads ve 13 yeni Post PG testi dahil. Eski on tablonun kayıt/yetkileri korundu.
[V009 test DB doğrulaması](docs/post-test-validation.md).

> Ana/test şema V009 ile uyumlu, Hibernate validate korunuyor. Maven/CI hedefi 9;
> migration varsayılan olarak skip durumundadır. Mevcut ana profillerin üniversiteleri
> pasif olduğu için normal feed şu an 409 döner; bu kayıtlar değiştirilmedi.

## Mevcut kapsam

- Güvenli DTO'larla kullanıcı CRUD ve hesap sahipliği kontrolleri.
- Register, BCrypt(12), Login ve 15 dakikalık HS256 JWT access token.
- Veritabanından güncel okunan STUDENT, CLUB_ADMIN ve ADMIN rolleri.
- Üniversite kataloğu ve kişiye özel öğrenci profili.
- Kulüp oluşturma/onay/yaşam döngüsü ve üyelik başvuruları.
- Kulübe özel OWNER/MANAGER/MEMBER yetkileri, sahiplik devri ve aynı üniversite kuralı.
- Event yaşam döngüsü, kontenjanlı katılım ve check-in.
- Duyuru taslak/yayın/düzenleme/geri çekme/arşiv ve güvenli etkinlik bağlantıları.
- Kişisel bildirim kutusu, okunma durumu ve duyuru/kulüp/üyelik/etkinlik entegrasyonları.
- Öğrenci/kulüp metin gönderileri, cursor sosyal akışı ve gerekçeli ADMIN moderasyonu.

CLUB_ADMIN tek başına başka kulüpleri yönetme hakkı vermez. Parola/hash yanıt DTO'larına
konulmaz. Event/EventParticipation; yayın, kontenjan, kayıt/iptal, check-in, sonlandırma
ve arşivlemeyi kapsar. Ana veritabanına geçiş onayla doğrulandı. Refresh token, parola
sıfırlama ve diğer sosyal modüller sonraki aşamalardır.

## Teknolojiler

- Java 21, Spring Boot 4.1.1, Spring Security 7.1.1.
- Spring Data JPA 4.1.1, Hibernate, Bean Validation, REST API.
- PostgreSQL 17; Maven Wrapper 3.9.16.

Sürümlerin kaynağı [pom.xml](pom.xml) ve Boot'un yönettiği bağımlılıklardır.
Uygulama çalışma zamanı bağımlılıkları korunmuştur. Flyway yalnız ayrı Maven araç
profilinde hazırlanmıştır; normal uygulama açılışında çalışmaz.

## Yerel uygulamayı çalıştırma

1. Java 21 kurulu olmalıdır (`java -version`). Maven kurulumu zorunlu değildir;
   `./mvnw` kullanılır. Windows'ta eşdeğer komut `mvnw.cmd`'dir.
2. Mevcut Campus+ hedefi PostgreSQL 17 üzerinde `127.0.0.1:5434/campus_plus` olmalıdır.
   Başka PostgreSQL kurulumunu veya projeyi kullanmayın.
3. Aşağıdaki değişkenleri uygulamayı başlatan sürecin ortamında sağlayın:

| Değişken | Açıklama |
| --- | --- |
| DB_URL | `jdbc:postgresql://127.0.0.1:5434/campus_plus` |
| DB_USERNAME | Mevcut Campus+ veritabanı kullanıcısı |
| DB_PASSWORD | Yerel gizli parola; repoya veya komut argümanına yazılmaz |
| JWT_SECRET | Base64 biçiminde en az 32 rastgele bayt; mevcut anahtar korunur |

IntelliJ ayarları ve değeri ekrana yazdırmadan anahtar oluşturma/kullanma adımları
[yerel geliştirme rehberindedir](docs/local-development.md). IDE ortamı ile terminal
ortamı ayrıdır; terminal komutu IDE'deki gizli değişkenleri kendiliğinden okumaz.
Uygulama `.env` dosyasını otomatik yüklemez.

4. Şema mevcut ve uyumluysa uygulamayı IntelliJ'den veya şu komutla başlatın:

```sh
./mvnw spring-boot:run
```

`ddl-auto=validate` korunur. Uygulama boş şemayı otomatik kurmaz veya güncellemez.
Yeni bilgisayardaki kurulum için aşağıdaki migration bölümü ve onaylı provisioning
runbook'u kullanılır; normal uygulama başlangıcı DDL çalıştırmaz.

## Test komutları

### Veritabanına bağlanmayan testler

```sh
./mvnw test
```

Bu komut uygulama/test DB değişkenleri veya gerçek JWT_SECRET istemez. CRUD, authentication,
RBAC, akademik modüller, kulüp/üyelik ve test hedefi koruma testlerini çalıştırır.
Repository mock testleri yanında DB bağlantısı açmadan JPA metadata/HQL kontrolleri de vardır.

Test sınıfı adlarının değiştiği bu güncellemeden sonra ilk seferde eski derlenmiş test
sınıflarını temizleyin:

```sh
./mvnw clean test
```

Surefire raporları `target/surefire-reports/` altındadır. Normal `test` veya `verify`,
`postgres-it` profili açılmadıkça gerçek Spring Boot/JDBC context testini çalıştırmaz.
Post kodunda **394 izole Java testi başarılı**; önceki 330 test korunur.
[Post kod raporu](docs/post-phase1-review.md).

### PostgreSQL entegrasyon testleri

```sh
./mvnw -Ppostgres-it verify
```

Yerel test DB'sinde V009 ve gerekli grant'ler onayla uygulandı. Başka ortamda
önce onaylı V009 kurulumu gerekir. Context testi eksik tabloyu görmezden gelmez veya
otomatik oluşturmaz.

Failsafe, korunmuş `contextLoads()` testini `CampusPlusBackendApplicationIT` sınıfında,
gerçek HTTP/transaction/eşzamanlılık testlerini `PostgresApiIT`, `PostgresEventIT` ve
`PostgresAnnouncementIT`, `PostgresNotificationIT` ve `PostgresPostIT` sınıflarında çalıştırır.
Test atlanmaz: eksik/yanlış ayarda build güvenli biçimde başarısız olur.
`TEST_DB_*` olmadan ana `DB_*` değişkenlerine geri düşülmez.

| Değişken | Gerekli değer |
| --- | --- |
| TEST_DB_URL | Tam olarak `jdbc:postgresql://127.0.0.1:5434/campus_plus_test` |
| TEST_DB_USERNAME | `campus_plus_test_runner` — sınırlı test hesabı |
| TEST_DB_PASSWORD | Bu test hesabının süreç ortamındaki gizli parolası |

Yerel test ortamında onayla V005–V009 uygulanmış, sınırlı runner hesabına yeni
etkinlik/duyuru/bildirim/gönderi tabloları için gerekli DML/sequence yetkileri verilmiştir.
Post moderasyon audit tablosunda yalnız SELECT/INSERT vardır. Başka bir ortamda
önce onaylı kurulum ve V009 gerekir.
Farklı port/sunucu,
`campus_plus`, URL parametreleri ve alternatif JDBC biçimleri reddedilir. Bağlantı
kurulduğunda sunucunun adresi, portu, DB adı ve PostgreSQL 17 sürümü ayrıca kontrol edilir.
Context testi salt okunur bağlantı kullanır; şema yaratmaz, migration/seed çalıştırmaz.
HTTP testleri her bağlantıda gerçek hedef ve sınırlı runner yetkileri doğrulandıktan
sonra yazmaya izin verir. Yönetici/migrator hesabı reddedilir. JWT anahtarı test belleğinde
üretilir. Failsafe raporları `target/failsafe-reports/` altındadır.

Son V005 sürümünde **193 izole + 10 gerçek PostgreSQL testi başarılıydı**.
Önceki V006 kodunda **250 izole + 23 gerçek PostgreSQL = 273 Java testi başarılıydı.**
Önceki Announcement sürümünde **297 izole + 31 PostgreSQL = 328 Java testi başarılıydı**;
`contextLoads()` dahil. Kilit ön aşamasında 33 PG testi geçti; son Notification kodunda
**330 izole + 44 PostgreSQL = 374 Java testi başarılı**, contextLoads dahil.
[Güncel sonuç](docs/notification-test-validation.md).
Bu sayı V008 tarihsel sonucudur. V009 Post kodunda **394 izole + 57 PG = 451 Java**
ve **38 Python** testi geçti; [en güncel sonuç](docs/post-test-validation.md).
V006 sonuçları ve kapsamı [güncel rapordadır](docs/event-test-validation.md).
Her HTTP çalıştırması
benzersiz test kayıtları oluşturur ve yalnız test DB'sinde bırakır; otomatik cleanup yoktur.
Test ADMIN kayıtları da yalnız bu izole ortamdadır. Gerçek kullanıcılarla paylaşılmaz.
Bu komut test DB'sini/rolünü oluşturmaz veya migration uygulamaz.

Bu Mac'te onayla oluşturulan yerel parola dosyası değeri ekrana yazdırmadan kullanılabilir:

```sh
export TEST_DB_URL=jdbc:postgresql://127.0.0.1:5434/campus_plus_test
export TEST_DB_USERNAME=campus_plus_test_runner
export TEST_DB_PASSWORD="$(cat "$HOME/.campus-plus-local/test-db-runner-password")"
./mvnw -Ppostgres-it verify
unset TEST_DB_PASSWORD
```

Sırları işlerken shell trace (`set -x`) açmayın. Başka bilgisayarda aynı isimli dosyanın
mevcut olduğu varsayılmaz; onaylı kurulum ve ayrı sır sağlama gerekir.

## Migration durumu

- `db/migrations/V001…V005` tarihsel, açık onayla uygulanmış manuel SQL dosyalarıdır.
- V001 önceden var olan `users` tablosunu varsayar; bu dosyalar henüz boş kurulum yolu değildir.
- Uygulanmış SQL değiştirilmez veya gelişigüzel tekrar çalıştırılmaz.
- Ayrı `db/managed-migrations/V005__baseline_schema.sql` başlangıç şeması,
  **yalnız yeni campus_plus_test üzerinde başarıyla uygulanmıştır**. Ana DB'de uygulanmadı.
  Eski dizin değiştirilmedi ve Flyway taramasına alınmadı. Ana DB'ye 2026-10-31 onayla
  yalnız version 5 BASELINE metadata'sı eklendi; iş şeması/verileri korundu.
- `schema-tools` Maven profilinde Flyway 12.4.0 varsayılan skip durumundadır;
  otomatik baseline/clean kapalı, hedef sürüm 9'dur. V008 ana ve test DB'de açık onayla
  uygulandı. V009 da iki hedefte ayrı onaylarla uygulandı; ana DB
  BASELINE5+SQL6+SQL7+SQL8+SQL9 durumunda. Ana DB'ye her yeni migrate yalnız
  ayrı açık onayla çalıştırılır.
  Lifecycle execution yoktur.
  Onay olmadan skip kaldırılıp Flyway komutu çalıştırılmaz.
- Gerçek DB'de migration, yeni DB/rol veya veri değişikliği öncesinde hedef ve SQL gösterilir;
  açık uygulama onayı beklenir. Otomatik clean/drop/reset yoktur.

`scripts/verify_clubs_postgres.py` eski açık opt-in canlı doğrulama aracıdır. Ana Campus+
DB'sine test verileri ve geçici test ADMIN rolü yazar; yeni veri oluşturacağı için rutin
test komutu veya CI aracı olarak kullanılmamalıdır. Yeniden çalıştırılması ayrıca onay gerektirir.

## Proje yapısı

İki ayrı GitHub-hosted CI işi, izole testleri ve yalnız geçici PostgreSQL 17 üzerinde
boş kurulum/HTTP testlerini çalıştırır. Yerel servis veya Docker kullanılmaz.
2026-10-31 kontrolünde [ilk CI çalışması](https://github.com/cemsoylemezoglu/campus-plus-backend/actions/runs/41144506168)
başarılıdır: unit ve postgres işleri geçti.
[CI ayrıntıları](docs/ci.md). Ana DB baseline işlemi ve normal uygulama doğrulaması
tamamlandı; [4. aşama sonucu](docs/setup-test-stage4-review.md).

Salt okunur envanter ve çevrimdışı karşılaştırma için Python 3.9+ ile PostgreSQL 17
psql kullanılır; bunlar backend çalışma zamanı gereksinimi değildir:

```sh
python3 scripts/schema_tools.py verify-legacy
python3 -m unittest discover -s scripts/tests
```

Boş kurulum ve mevcut DB'nin geçmişe alınması iki ayrı işlemdir. Hedefler, komutlar,
onaylar ve henüz doğrulanmayanlar [runbook'ta](docs/schema-bootstrap-runbook.md) açıklanır.

```text
src/main/java/com/campusplus/backend/
  config/       JWT ve Spring Security
  controller/   HTTP uçları
  dto/          İstek/yanıt sözleşmeleri
  entity/       JPA modelleri
  repository/   Veri erişimi
  security/     Roller ve kaynak yetkileri
  service/      İş kuralları ve transaction'lar
src/test/java/  İzole testler, IT ve test bağlantı sınırı
db/migrations/ Tarihsel manuel migration'lar
docs/          Tasarım, kurulum ve aşama raporları
```

## Geliştirme ve güvenlik kuralları

Yalnız Campus+ üzerinde çalışılır. Diğer projeler, PostgreSQL 14, yerel Docker,
Supabase ve Render ortamları kullanılmaz. Sırlar süreç ortamında/proje dışında tutulur;
IDE ayarları, `.env`, token'lar ve geçici test çıktıları Git'e eklenmez.
Commit ve push ayrıca açık kullanıcı talimatıyla yapılır.

## Dokümantasyon

- [Güncel altyapı planı](docs/setup-test-infrastructure-plan.md)
- [Altyapı 1. aşama sonucu](docs/setup-test-stage1-review.md)
- [Altyapı 2. aşama sonucu](docs/setup-test-stage2-review.md)
- [Altyapı 3. aşama sonucu](docs/setup-test-stage3-review.md)
- [4. aşama uygulama ve test sonucu](docs/setup-test-stage4-review.md)
- [GitHub Actions CI](docs/ci.md)
- [İzole test DB/rol kurulum paketi](docs/test-database-provisioning.md)
- [Şema kurulumu ve baseline runbook'u](docs/schema-bootstrap-runbook.md)
- [Geliştirme günlüğü](docs/development.md)
- [Gönderiler ve sosyal akış tasarımı](docs/social-feed-architecture.md)
- [Post V009 test doğrulaması](docs/post-test-validation.md)
- [Post V009 ana DB doğrulaması](docs/post-main-validation.md)
- [Yerel anahtar ve IntelliJ kurulumu](docs/local-development.md)
- [Üniversite/kulüp mimarisi](docs/university-club-architecture.md)
- [Event/EventParticipation tasarım önerisi](docs/event-architecture.md)
- [Event kod sonucu ve V006 SQL onayı](docs/event-phase1-review.md)
- [Club/ClubMembership doğrulama raporu](docs/club-phase-review.md)

Tarihsel raporlardaki 164 otomatik test ve 93 canlı kontrol önceki doğrulamalara aittir;
güncel komutlar için bu README esas alınır. Event, Announcement ve Notification
aşamaları tamamlandı; Post ve sosyal akış da V009 ile doğrulandı. Sıradaki backend
aşaması yorum/yanıtlar ve beğenilerdir.
