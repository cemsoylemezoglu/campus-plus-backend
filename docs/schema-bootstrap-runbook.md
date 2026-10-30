# Campus+ — boş kurulum ve mevcut şemanın devralınması

Durum (2026-10-31): **Yol A test DB kurulumu ve Yol B ana DB baseline işlemi onayla tamamlandı**.
İki sınırlı test rolü oluşturuldu; test DB V005 migrate ve gerçek PostgreSQL testleri başarılı.
Ana `127.0.0.1:5434/campus_plus` üzerinde yalnız version 5 baseline metadata'sı eklendi;
iş şeması ve verileri değişmedi. History zaten mevcut; baseline tekrar çalıştırılmaz.
Bu belge tek başına yeni kurulum/işlem onayı değildir. Repair/clean/undo uygulanmadı.
Sonuçlar [3. aşama](setup-test-stage3-review.md) ve [4. aşama](setup-test-stage4-review.md) raporlarındadır.

## Dosyalar ve güvenlik sınırı

- `db/migrations/V001…V005`: değişmeden kalan tarihsel manuel migration'lar.
- `db/schema/legacy-checksums.json`: eski beş dosyanın SHA-256 manifesti.
- `db/schema/v005-contract.json`: yalnız yapısal şema; iş satırları, sahip adları,
  parola/hash değerleri veya sequence son değerleri içermez.
- `db/managed-migrations/V005__baseline_schema.sql`: mevcut V005 sonu şemasını boş
  PostgreSQL 17 ortamında oluşturan DDL; yalnız yeni test DB'sinde uygulandı.
- Aynı adlı `.sql.conf`: `executeInTransaction=true`; transaction'ı Flyway yönetir.
- `db/verification/`: salt okunur katalog ve toplu veri özeti SELECT'leri.
- `scripts/schema_tools.py`: salt okunur inceleme, çevrimdışı karşılaştırma ve eski
  dosya checksum kontrolü. Veritabanına yazma yeteneği/komutu içermez.

Normal uygulama başlangıcı, test ve package migration çalıştırmaz. Uygulama bağımlılıklarına
Flyway eklenmedi. `schema-tools` Maven profilindeki plugin ve PostgreSQL desteği ayrı
plugin classpath'ine aittir. Boot BOM'unun yönettiği Flyway 12.4.0 ve JDBC 42.7.13 kullanılır.

Profilde lifecycle execution yoktur; ayrıca `flyway.skip=true` varsayılandır. Otomatik
baseline kapalı, clean kapalı, schema oluşturma kapalı, out-of-order kapalı ve hedef sürüm
5'tir. Yalnız `db/managed-migrations` taranır; eski SQL dizini taranmaz. Bu korumalar
ayrı SQL onayının yerini tutmaz; elle geçersiz kılınmamalıdır.

## Bağlantılar

| İş | Değişkenler | Hedef |
| --- | --- | --- |
| Çalışan uygulama / mevcut şema incelemesi | DB_URL, DB_USERNAME, DB_PASSWORD | 127.0.0.1:5434/campus_plus |
| Gelecekteki izole test incelemesi / testler | TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD | 127.0.0.1:5434/campus_plus_test |
| Onaydan sonraki kontrollü Flyway işlemi | MIGRATION_DB_URL, MIGRATION_DB_USERNAME, MIGRATION_DB_PASSWORD | Her işlemde ayrıca açıkça doğrulanacak hedef |

Hiçbir grupta diğer grubun kimlik bilgilerine fallback yoktur. Sırlar yalnız süreç
ortamından sağlanır; XML/properties/komut argümanı veya kaynak dosyasına yazılmaz.
Repo aracı IDE sırlarını kendiliğinden okumaz. PostgreSQL 14, diğer projeler ve Docker yoktur.

## Şimdi kullanılabilen salt okunur / çevrimdışı kontroller

Python 3.9+ ve PostgreSQL 17 psql gerekir; bunlar backend çalışma zamanı bağımlılığı değildir.

```sh
python3 scripts/schema_tools.py verify-legacy
python3 scripts/schema_tools.py inspect --target existing --output /tmp/campus-plus-schema-inspection.json
python3 scripts/schema_tools.py compare --expected db/schema/v005-contract.json --actual /tmp/campus-plus-schema-inspection.json
python3 -m unittest discover -s scripts/tests -p 'test_schema_tools.py'
```

psql PATH'te değilse `--psql /opt/homebrew/opt/postgresql@17/bin/psql` verilebilir.
Çıktı dosyası zaten varsa üzerine yazılmaz. İnceleme, URL doğrulaması ardından gerçek
DB/host/port/sürüm/read-only kimliğini kontrol eder. psql `-X` ile kullanıcı psqlrc'sini
yüklemez; PGOPTIONS read-only ve sınırlı timeout içerir. Envanter tek repeatable-read,
read-only transaction'da alınır. Şema inceleme SQL'i iş verisi döndürmez.

`fingerprints` alt komutu aynı hedef sınırıyla altı tablonun satır sayısını ve toplu
satır özetini dosyaya alabilir. MD5 burada yalnız değişiklik karşılaştırması içindir;
parolaların saklanma algoritması değildir. Tekil parola/hash/e-posta satırları dışarı çıkmaz.
Bu raporlar geçicidir; Git'e eklenmez.

Karşılaştırma sütun sırası/tipi/null/default/identity/collation, PK/FK/unique/CHECK,
FK silme davranışı, indeks tanımı/predicate/validity, sequence tanımı, RLS, kullanıcı
trigger/routine ve extension listesini denetler. Ayrıca DB encoding/locale/provider eşleşir.
Sahiplik, ACL, tablespace, oturum ayarları, iş verileri ve sequence konumları bu kontratın
parçası değildir; yetkiler ayrı DB/rol onayında incelenir. Katalog metni sürüm farkında
değişirse fark elle incelenir. İlk boş kurulumda incelenen dört sabit enum CHECK için
array-wide text cast ve element-wise text cast yazımları araçta açıkça eşdeğer tanımlandı;
değer/sıra/operatör veya başka kısıt farkları reddedilir. Genel SQL normalizasyonu yoktur.

## Yol A — yalnız yeni boş DB

Bu yol yerelde açık onayla tamamlandı. [Kesin SQL paketi ve parola/yetki planı](test-database-provisioning.md)
başka kurulumlarda yeniden hedef/SQL onayı gerektirir. Mevcut uygulama hesabı DB/rol
oluşturma yetkisine sahip değildir; yerel kurulum yetkili `cem` rolüyle yapıldı.

1. Önerilen test DB adının mevcut olmadığı ve doğru PostgreSQL 17 sunucusunda çalışıldığı
   doğrulanır. Mevcut/beklenmedik DB bulunursa durulur; drop/clean/reuse yapılmaz.
2. Kesin DB/rol/grant SQL'i, parola sağlama yöntemi ve etkileri kullanıcıya sunulur.
   Yalnız açık onaydan sonra oluşturulur. Kaynak DB'nin ACL veya servis ayarı değiştirilmez.
3. Kaynak ortam UTF8, libc provider (`c`), en_US.UTF-8 collate/ctype kullanıyor. Yeni ortamın
   bu davranışı karşılaması gerekir; otomatik farklı locale seçilmez.
4. MIGRATION_DB_* yalnız yeni onaylı test hedefini ve sınırlı hesabı gösterir. Legacy
   checksum'ları, gerçek sunucu kimliği ve public şemanın boşluğu tekrar doğrulanır.
5. Ayrı migration onayı sonrası yalnız sürüm 5 hedefli kontrollü Flyway migrate çalıştırılır.
   Varsayılan skip'in kaldırılması da bu açık işlem kapsamında yapılır; rutin komut değildir.
6. Başlangıç SQL'i PostgreSQL 17 ve boş public şema şartlarını tekrar kontrol eder.
   Flyway'in kendi history tablosu hariç iş tablosu/sequence/view/routine/domain/enum varsa durur.
7. Altı tablo, dört identity sequence, 38 kısıt ve 17 indeks kurulur; kullanıcı/ADMIN/test
   verisi eklenmez. Identity sayacı yeni DB'de 1'den başlar; mevcut DB'den değer kopyalanmaz.
8. Yeni DB read-only envanteri `--target test` ile alınır ve V005 kontratıyla karşılaştırılır.
   Flyway history ve gerçek Hibernate context/schema validation ayrıca doğrulanır.
9. Sonra gerçek JPA/HTTP/transaction testleri yalnız bu DB'de yapılır; ana DB'de test verisi yoktur.

PostgreSQL'de DDL transaction içinde olsa da DB/rol oluşturma ve Flyway history hazırlığı
aynı atomik işlem gibi sunulmaz. CREATE DATABASE transaction bloğunda çalışmaz.
Her adım ayrı sonuçlandırılır; hata sonrası otomatik destructive geri alma yoktur.

## Yol B — mevcut dolu Campus+ DB

Bu yol Yol A sonrasında **ayrı ana DB baseline onayıyla** 2026-10-31 tamamlandı.
Aşağıdaki sıra kullanılan prosedürü açıklar; mevcut history üzerinde tekrar çalıştırılmaz.

1. MIGRATION_DB_* tam olarak kaynak Campus+ hedefini gösterir. Gerçek kimlik, salt okunur
   envanter ve veri özetleri yeniden alınır. Kontrat/checksum farkında durulur.
2. `flyway_schema_history` zaten varsa baseline/repair uygulanmaz; durum ayrıca incelenir.
3. Şema eşdeğerliği ve uygun yetkiler doğrulandıktan sonra kullanıcıya Flyway'in hazırladığı
   history tablo/index/başlangıç kaydı kapsamı gösterilir. Bu metadata da şema değişikliğidir.
4. Açık onay sonrası yalnız `baseline version 5` görevi çalıştırılır; migrate aynı komutta
   zincirlenmez. İş tablolarına başlangıç DDL'si veya V001–V005 yeniden uygulanmaz.
5. Bu kayıt eski migration'ların tek tek Flyway tarafından çalıştırıldığı iddiası değildir.
   Sadece mevcut şemanın sürüm 5 seviyesinde kabulüdür. Baseline şema doğrulaması yapmaz.
6. Flyway history, uygulama validate, katalog kontratı ve önce/sonra iş satırı özetleri
   tekrar kontrol edilir. Veri özetleri aktif kullanımda değişirse kaynağı incelenmeden
   korunmuş sayılmaz; mevcut kullanıcı verisi geri yazılarak 'düzeltilmez'.

Flyway mevcut baseline kaydında V005 SQL'inin uygulanmış checksum'unu üretmez. Bu nedenle
şema kontratı ve tarihsel checksum manifesti adoption'ın ön koşuludur. Yeni baseline SQL'i
ilk onaylı uygulanmasından sonra değişmez; sonraki değişiklikler V006 ve devamıdır.
Hedef sürüm sınırı ancak yeni migration ve SQL onayıyla ilerletilir.

## Doğrulanmış sınır ve kalan riskler

- Boş kurulum, başarılı SQL V005 history kaydı, contextLoads, constraint/rollback ve
  üç paralel HTTP senaryosu yeni test DB'de doğrulandı. Bu sonuç ana DB baseline
  işleminin yapılmış olduğu anlamına gelmez.
- Salt Flyway validate veya Hibernate validate, mevcut şemanın eksiksiz eşdeğerliğini
  kanıtlamaz; katalog karşılaştırması zorunlu tutulur.
- Koruma varsayılanları yetkili kişinin elle komut/ayar değiştirmesini engelleyen bir
  erişim kontrol sistemi değildir. Mutating Flyway komutları onaysız çağrılmaz.
- Genel bir migration yürütücüsü veya CI/deployment eklenmedi. Provisioning SQL'leri
  açık onayla kullanılır; otomatik baseline/repair/clean uygulanmaz.

Kaynaklar: [Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline),
[otomatik baseline riski](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html),
[SQL script transaction ayarı](https://documentation.red-gate.com/flyway/reference/script-configuration),
[PostgreSQL host() ve inet metni](https://www.postgresql.org/docs/17/functions-net.html).
