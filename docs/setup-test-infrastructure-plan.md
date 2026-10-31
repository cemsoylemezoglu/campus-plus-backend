# Campus+ — kurulum ve test altyapısı planı

Güncelleme: 2026-10-31. Durum: **1–4. aşama kurulumu, ana DB baseline, yerel testler
ve ilk uzak CI tamamlandı; unit ve postgres işleri başarılı**.
Bu belge kendi başına veritabanı veya servis ayarı değişikliği onayı değildir.
Bildirim V008 test ve ayrıca açık onayla ana DB'de uygulandı; 374 Java/36 Python
ve ana ortamda 32 salt okunur API kontrolü başarılı. [V008 ana sonuç](notification-main-validation.md).
Duyuru V007 test ve ayrıca açık onayla ana DB'de uygulandı; 328 Java/34 Python testi
ve ana ortamda 22 salt okunur API kontrolü başarılı. [Ana DB sonucu](announcement-main-validation.md).
Event / EventParticipation kodu ve V006 hazır; V006 ve sınırlı runtime grant
test DB'sinde ve ayrıca açık onayla ana DB'de uygulandı. Ana ortamda şema/veri
koruması ve 15 salt okunur API kontrolü başarılı. [Ana DB raporu](event-main-validation.md).
[Güncel migration/test sonucu](event-test-validation.md). Commit/push ara verileceği zaman,
kullanıcının ayrıca açık talimatıyla yapılacak.

1. aşamada 189 izole test geçti; entegrasyon profilinin eksik/ana DB hedefini bağlantıdan
önce reddettiği doğrulandı. DB bağlantısı veya şema değişikliği yapılmadı.
Ayrıntılar: [1. aşama raporu](setup-test-stage1-review.md).

2. aşamada yalnız mevcut Campus+ PostgreSQL 17 salt okunur incelendi; boş kurulum SQL'i,
şema kontratı ve varsayılan kapalı Flyway profili hazırlandı. SQL uygulanmadı. 189 Java ve
13 Python aracı testi geçti. [2. aşama raporu](setup-test-stage2-review.md) ve
[uygulama runbook'u](schema-bootstrap-runbook.md) güncel sınırları açıklar.

3. aşamanın [kesin test DB/rol SQL paketi](test-database-provisioning.md) onayla yalnız
campus_plus_test üzerinde uygulandı. Mevcut `cem` PG17 yöneticisi kullanıldı; uygulama
hesabına yetki eklenmedi. 193 izole + 10 gerçek PostgreSQL Java testi ve 16 Python testi
başarılı. Ana DB envanteri ve altı tablonun veri özetleri değişmedi. Ayrıntılar
[3. aşama raporundadır](setup-test-stage3-review.md). Ana DB baseline işlemi ayrı kalır.

4. aşamada [baseline planı](baseline-adoption-plan.md) onayla ana DB'de uygulandı;
yalnız history tablosu/iki indeks ve tek version 5 BASELINE kaydı eklendi. Mevcut
iş verileri, şema ve yetkiler korundu. 203 Java ve 23 Python testi yeniden geçti.
[CI yapılandırması](ci.md) ilk onaylı push sonrasında unit/postgres işlerini başarıyla tamamladı.
[Uygulama raporu](setup-test-stage4-review.md).

## Başlangıç ve doğrulanmış eksikler

- Ana dal ve GitHub main: `8ac68ff`, Club/ClubMembership aşaması.
- Son incelemede 163 izole test tekrar geçti. Önceki 164 testin ek bir testi gerçek
  veritabanına bağımlı `contextLoads()`; 93 canlı kontrolün önceki sonucu dokümante.
- V001 mevcut `users` tablosunu varsayıyor; boş veritabanı kurulumu tanımlı değil.
- V001–V005 manuel SQL, yerel Campus+ hedefiyle sınırlı; otomatik migration geçmişi yok.
- `contextLoads()` normal Maven testlerinde mevcut DB_URL üzerinden çalışabiliyor.
- Canlı doğrulama aracı ana Campus+ veritabanında kalıcı test kayıtları bırakıyor.
- README ve CI workflow yok; yerel kurulum/test bilgileri birden fazla belgede dağılmış.

## Korunacak sınırlar

- Java 21, Spring Boot 4.1.1, JWT/RBAC ve mevcut API davranışları korunur.
- `spring.jpa.hibernate.ddl-auto=validate` korunur; Hibernate create/update kullanılmaz.
- V001–V005 içerikleri, konumları ve uygulanmış checksum'ları değiştirilmez.
- Mevcut Campus+ kullanıcı/rol/parola/akademik/kulüp/üyelik satırları korunur.
- PostgreSQL 14, diğer proje DB'leri, yerel Docker, Supabase ve Render kullanılmaz.
- Her aşama sonunda sonuç ve test raporu sunulur; sonraki uygulama aşaması için onay beklenir.
- SQL hazırlamak, gerçek DB'de çalıştırma izni değildir. Ana DB ve yeni test DB işlemleri ayrı onaylanır.
- Beklenmeyen şema, hedef veya veri farkında işlem durur; otomatik repair/clean/drop yapılmaz.

## 1. Repo ve test çalıştırma sınırları — DB işlemi yok

Önerilen değişiklikler:

1. README: gereksinimler, sırların ortam değişkenlerinden sağlanması, yerel çalıştırma,
   mevcut modüller ve tek noktadan güncel test komutları. Tarihsel raporlar ayrı işaretlenir.
2. Mevcut DB kullanmayan testler normal `./mvnw test` altında kalır.
3. `contextLoads()` kaldırılmaz veya devre dışı bırakılmaz; entegrasyon test grubuna
   taşınır. Maven Failsafe ve açık `postgres-it` profiliyle çalışır.
4. Entegrasyon testleri ayrı `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`
   değişkenleri ister; ana uygulamanın DB_URL değerine geri düşmez.
5. Test hedefi doğrulanmadan JDBC bağlantısı açılmaz. Eksik/yanlış ayar açık hata verir;
   testler sessizce atlanmaz. `campus_plus` yazmalı test hedefi olarak reddedilir.
6. Test JWT anahtarları bellekte rastgele üretilir; gerçek JWT_SECRET gerekmez.

Başarı ölçütleri: mevcut 163 izole regresyon testi geçer; normal test komutu DB olmadan
çalışır; entegrasyon profili eksik/yanlış hedefte güvenli hata verir. Gerçek context/schema
testinin henüz çalıştırılmadığı raporda açıkça belirtilir. Çalışan uygulama ayarları değişmez.

**Onay:** Bu ilk repo/test düzenlemesi için onay. DB bağlantısı/DDL yetkisi içermez.

## 2. Boş kurulum ve migration geçmişinin hazırlanması — henüz uygulama yok

Öneri: migration sürümü ve checksum takibi için Flyway kullanmak. Bu, özel bir migration
framework'ü yazma ihtiyacını önler. Normal uygulama açılışında migration çalıştırılmaz;
ayrı, açıkça çağrılan kontrollü görev kullanılır. Boot'un yönettiği uyumlu sürüm doğrulanır;
Boot/Java yükseltmesi yapılmaz. Bağımlılık/plugin ayrıntıları bu aşamanın değişiklik listesinde gösterilir.

1. Kullanıcı bu aşamayı onayladığında yalnızca `127.0.0.1:5434/campus_plus`
   PostgreSQL 17 üzerinde salt okunur şema envanteri alınır. Sütun/tip/null/default/identity,
   PK/FK/unique/CHECK ve indeksler incelenir; gerçek parolalar/hash'ler çıktıya dökülmez.
2. Tarihsel `db/migrations/V001…V005` yerinde ve değişmeden kalır; Flyway'in otomatik
   taradığı konuma eklenmez. Yerel hedef kontrolleri kaldırılarak test DB'sine uyarlanmaz.
3. Ayrı bir aktif migration konumu önerilir: `db/managed-migrations/`.
   `V005__baseline_schema.sql`, mevcut V005 sonu şemasının altı tablosunu boş ortamda
   oluşturur. İş verisi, test verisi veya otomatik ADMIN seed'i içermez.
4. Boş yeni DB bu başlangıç migration'ını uygular. Mevcut dolu DB'de başlangıç SQL'i
   çalıştırılmaz; ayrı şema eşdeğerliği doğrulamasından sonra açık `baseline version 5`
   işlemi önerilir. Sonraki gerçek migration numarası V006 olur.
5. Baseline mevcut şemayı kendiliğinden doğrulamaz. Hibernate validate da bütün FK/CHECK/
   indeksleri kanıtlamaz; PostgreSQL katalog karşılaştırması ayrıca yapılır.
6. Otomatik baseline kapalı, destructive clean kapalı olur. Eski SQL'lere yeni araçla
   uygulanmış gibi sahte geçmiş satırları yazılmaz; başlangıç seviyesinin kabulü kaydedilir.

Başarı ölçütleri: gözden geçirilebilir SQL, hedef kontrolleri, şema karşılaştırma yöntemi,
checksum kontrolleri ve iki farklı kurulum yolunu anlatan runbook hazırlanır.

**Onay:** Flyway önerisi/kod hazırlığı ve mevcut hedefte salt okunur envanter için aşama onayı.
Hazırlanan SQL'in uygulanması bu onayın kapsamında değildir.

## 3. Campus+ test ortamı ve gerçek PostgreSQL doğrulaması

Önerilen yerel hedef: aynı PostgreSQL 17 sunucusunda
`127.0.0.1:5434/campus_plus_test`. Bu veritabanı henüz yok kabul edilmez;
oluşturmadan önce varlığı ve sahipliği kontrol edilir. Beklenmedik mevcut DB'de işlem durur.

- Yeni test DB'si ve yalnız test nesnelerinde yetkili test hesabı için kesin SQL/grant
  listesi, yaratılacak nesneler ve etkileri gösterilir. Ana DB yazma yetkisi verilmez.
- Kullanıcı açıkça onaylamadan veritabanı/rol oluşturulmaz; sunucu/servis ayarı değiştirilmez.
- Onaydan sonra başlangıç migration'ı yalnız test DB'sine uygulanır; Flyway geçmişi,
  Hibernate validate ve katalog eşdeğerliği birlikte kontrol edilir.
- `contextLoads()` dahil gerçek JPA/JDBC/HTTP entegrasyon testleri burada çalışır.
- İlk kapsam: Register/Login/JWT/RBAC; University/Profile; Club/Membership;
  unique/FK/CHECK; çift başvuru, sahiplik devri, profil/başvuru yarışı ve rollback.
- Testlerin kendi verileri burada tutulur. Temizleme/reset gerekiyorsa yalnız doğrulanmış
  yeni test ortamında, ayrıca tanımlanmış onaylı kapsamda yapılır. Ana DB'de cleanup yoktur.
- Gerçek HTTP yarışları ayrı bağlantı/transaction gerektirir; yalnız mock veya tek
  rollback transaction'ıyla eşzamanlılık kanıtlandığı varsayılmaz.
- Eski 93 kontrol aracı tarihsel/manual opt-in aracı olarak kalabilir; ana DB'de yeniden
  çalıştırılmaz. Yeni entegrasyon testleri ana DB'ye kalıcı test hesabı/ADMIN eklemez.

Önerilen komutlar, uygulandıktan sonra:

```sh
./mvnw test
./mvnw -Ppostgres-it verify
```

İkinci komut açık test ortamı ister. Test DB'sini kendiliğinden oluşturmaz veya ana DB'ye
fallback yapmaz. Salt okunur hedef kontrolü, sunucu ana sürümünü de doğrular.

**Onay:** Kesin test DB/rol SQL'i ve ardından test DB'sinde çalıştırılacak başlangıç
migration'ı için açık uygulama onayı. Ana DB baseline işlemi ayrı kalır.

## 4. Mevcut DB'nin geçmişe alınması ve CI hazırlığı

İzole boş kurulum ve testler başarılı olduktan sonra:

1. Ana Campus+ DB için kesin baseline planı gösterilir. Beklenen şema değişikliği
   migration geçmiş tablosu/ilgili metadata nesneleridir; iş tablolarına DDL/DML uygulanmaz.
   Kesin nesne listesi ve yetkiler uygulamadan önce sunulur.
2. Onaydan sonra hedef/şema tekrar doğrulanır; iş satırlarının özetleri karşılaştırılır.
   Sürüm 5 baseline kaydı eklenir; V001–V005 yeniden çalıştırılmaz. Hata olursa durulur.
3. Mevcut uygulama `ddl-auto=validate` ile doğrulanır. Migration görevini çağırmadan
   normal başlatmanın şema değiştirmediği de doğrulanır.
4. GitHub Actions taslağı: Java 21 + Maven wrapper ile izole test işi ve yalnız geçici
   Campus+ test hedefiyle PostgreSQL entegrasyon işi. Yerel sırlar veya ana DB adresi
   workflow'a yazılmaz. CI PostgreSQL kurulum yöntemi ayrıca netleştirilir; yerel Docker kullanılmaz.
5. CI yalnız bu altyapı doğrulamasını yapar; otomatik deployment veya ana DB migration'ı yoktur.
   Workflow hazırlanması commit/push veya GitHub'da çalıştırma izni değildir. İlk uzak
   çalıştırma, kullanıcı ara verirken ayrıca push istediğinde mümkün olur.

**Onay:** Ana DB baseline SQL/işlemi için ayrı açık onay. CI/repo hazırlığı için aşama onayı.
Commit ve push bu onayların içinde değildir.

## Event'e geçiş ölçütleri ve sonraki çalışma

- Çalışan API/JWT/RBAC sözleşmeleri ve önceki test kapsamı korunmuş olmalı.
- Boş Campus+ test DB kurulumu tekrarlanabilir ve ana DB'den güvenle ayrılmış olmalı.
- İzole testler ve gerçek PostgreSQL context/constraint/transaction testleri geçmeli.
- Migration geçmişi ile mevcut şema arasındaki geçiş açıkça doğrulanmış olmalı.
- README'deki komutlarla yerel kurulum/test süreci anlaşılır olmalı.

Sonraki adım Event/EventParticipation tasarımı: durum geçişleri, kulüp bazlı yazma
yetkileri, katılımın üniversite/üyelik sınırları, kontenjan ve son yer yarışı, tekil kayıt,
iptal/yeniden kayıt, tarih/saat dilimi ve gelecekte bildirim bağlantıları.
Tasarım sunulur; kodlamaya geçiş için onay alınır. Event migration'ı hazırlanması,
uygulanması ve daha sonra Git kaydı yine ayrı aşamalardır.

Öğrencilik/e-posta doğrulaması, oturum yenileme ve hesap yaşam döngüsü kapsamda kalır;
bu kurulum aşamasına fark ettirmeden eklenmez. Gerçek kullanıcı yayını öncesinde ayrıca ele alınır.

## Teknik dayanak

- [Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline):
  mevcut şema için başlangıç seviyesi kaydeder; bu sürüme kadarki migration'ları atlar.
- [Flyway baselineOnMigrate](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html):
  otomatik baseline yanlış hedef korumasını zayıflatabileceğinden bu planda kapalıdır.
- Repo kaynakları: pom.xml, mevcut context testi, V001–V005 ve club-phase-review.md.
