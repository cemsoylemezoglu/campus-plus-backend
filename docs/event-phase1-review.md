# Event / EventParticipation — kod aşaması ve V006 onayı

> Bu belge kod aşamasının tarihsel kaydıdır. Sonraki test DB uygulaması ve güncel
> sonuçlar [Event canlı doğrulama raporundadır](event-test-validation.md).

Tarih: 2026-10-31. **Kod ve SQL hazır; V006 hiçbir veritabanına uygulanmadı.**
Tasarım kullanıcı tarafından onaylandı: [mimari ve ürün kuralları](event-architecture.md).
Bu aşamada gerçek PostgreSQL bağlantısı/yazması, rol/parola/servis değişikliği,
commit veya push yapılmadı. Diğer projeler/PostgreSQL 14/Docker/Supabase/Render kullanılmadı.

## Geliştirilenler

- Event + EventParticipation entity'leri, string enum'lar, @Version ve FK ilişkileri.
- `dto/event`: sınırlandırılmış/normalize edilmiş create, patch, schedule, version,
  reason, register istekleri; güvenli keşif/detay/kişisel/yönetim DTO'ları.
- EventRepository/EventParticipationRepository: görünürlük filtresi page/count öncesinde,
  toplu kontenjan sayımı, özel sayfalı roster, pessimistic event kilitleri, version artıran
  bulk sonlandırma. Entity koleksiyonları veya N+1 yaratan geniş response graph'ları yok.
- EventService: taslak, yayın, güncelleme, gerekçeli erteleme/iptal, complete/no-show ve archive.
  Audience taslakta değişebilir; yayımdan sonra değişmez. Kapasite azaltımı dolu koltuk
  sayısının altına inemez. Kapasite null ise sınırsızdır.
- EventParticipationService: uygun kayıt, iptal, aynı satırda sürümlü tekrar kayıt,
  yönetici çıkarma/yeniden kayıt izni/check-in. REGISTERED+ATTENDED koltuk kaplar.
- EventPolicy/EventWorkspace: güncel yerel OWNER/MANAGER + marker rolü veya ADMIN;
  önce kullanıcılar ASC, ardından shared club ve exclusive event kilidi. Actor, yerel
  üyelik ve entity'ler kilitten sonra tekrar okunur/refresh edilir. İlk bakıştaki izin
  veya saat koşullarıyla yazma yapılmaz. Creator'a kalıcı ayrıcalık verilmez.
- EventController/EventParticipationController: tasarımdaki REST uçları; 401/403/404/
  409/400 sözleşmesi, DTO validation ve özel yanıtlarda no-store.
- Mevcut ClubService/ClubMembershipService, zorunlu aynı transaction içindeki
  EventLifecycleService koordinatörünü çağırır. Club archive ilgili taslak/yayındaki
  etkinlikleri ve REGISTERED biletleri iptal eder. Leave/remove/suspend yalnız ilgili
  kişinin bitmemiş members-only REGISTERED biletlerini iptal eder; açık üniversite
  etkinliği kaydı ve ATTENDED geçmişi korunur. Sonradan üyelik kazanmak eski bileti açmaz.
- StudentProfilePolicy, bitmemiş PUBLISHED etkinlikte REGISTERED/ATTENDED kaydı varsa
  üniversite değişimini de engeller. Mevcut kulüp koruması devam eder.

Önemli dosyalar: `entity/Event*`, `dto/event/`, `repository/Event*`, `security/EventPolicy`,
`service/event/`, `controller/Event*`. JWT, parola/BCrypt ve SecurityConfig değiştirilmedi.
Uygulama runtime bağımlılığı eklenmedi.

## Çalıştırılan testler

**250 izole Java testi başarılı**, hata/başarısız/atlanan yok:

- Mevcut 193 regresyon korundu. İki eski web fixture'ına yeni bağımlılıklar için mock
  bean eklendi; mevcut iş kuralı testleri kaldırılmadı/gevşetilmedi.
- 41 EventModulesTest: gerçek JWT/security/MVC/method validation/servis akışı, mock
  repository/EntityManager. Yerel yönetim/creator yetki kaybı/IDOR, gizli etkinlikler,
  kayıt/politika/version/kapasite, erken check-in, iptal/tekrar kayıt/çıkarma,
  erteleme/sonlandırma, üyelik/club entegrasyonu, saat sınırları ve SQL hata gizliliği.
- 16 EventValidationTest: kapasite/uzunluk/patch/version, açık offset/UTC, DST overlap,
  offset'siz tarih reddi, ZoneId ve tarih sıralaması.
- Metadata testi yeni JPA eşlemelerini ve bütün repository HQL/JPQL update sorgularını
  gerçek Hibernate ile derler; UserSuppliedConnectionProvider JDBC erişimini engeller.
- Test sürecinde DB_URL/TEST_DB_*/JWT_SECRET temizlendi. Spring Boot canlı context testi
  veya JDBC/migration çalıştırılmadı. Mutable Clock/lock callback testleri gerçek
  PostgreSQL yarış testinin yerine sayılmadı.

**27 Python testi başarılı**: önceki 23 + dört sürüm kontrollü şema envanteri testi.
`schema_tools inspect --schema-version 6` yalnız bilinen sekiz tabloya izin verir;
varsayılan 5 hâlâ yalnız altı tabloyu kabul eder. Tam yapısal compare kaldırılmadı.
psql child ortamında PGHOSTADDR gibi hedefi değiştirebilecek PG_* override'ları
temizlenir; sadece açık bağlantı argümanları/readonly ayarları ve yetkili parola kullanılır.
Çağıranın gerçek ortamı değiştirilmez; testle doğrulandı.
Legacy V001–V005 SHA-256 kontrolü ve whitespace kontrolü başarılı.

## Hazırlanan canlı testler — henüz çalıştırılmadı

`PostgresEventIT` içinde **10 yeni gerçek HTTP/JDBC testi** derlendi:

1. Kayıt/iptal/tekrar kayıt ve özel roster yetkisi.
2. İki farklı kullanıcının son koltuk yarışı.
3. Aynı kullanıcının çift kayıt yarışı/unique güvenliği.
4. Kapasite azaltımı/kayıt yarışı.
5. Event cancellation/kayıt yarışı.
6. Kulüp üyeliğinden ayrılma/members-only kayıt yarışı.
7. Üniversite değişimi/kayıt yarışı.
8. Açık kayıt aralığında erken check-in koltuğunun korunması.
9. Club archive biletleri sonlandırdıktan sonra kontrollü hata: club/event/ticket/rol
   transaction rollback doğrulaması.
10. Yeni CHECK/unique/FK ihlallerinin rollback'i.

Bu testler yalnız doğrulanmış `campus_plus_test_runner` + exact test hedefi kullanır.
Yeni test kullanıcıları/ADMIN fixture rolleri, üniversite/profil/kulüp/etkinlik/katılım
kayıtları yalnız test DB'sinde oluşturulup bırakılacak; ana DB'ye test verisi yazılmayacak.
Check-in penceresi için JDBC tarih güncellemesi de yalnız kendi test fixture'ına uygulanır.
Otomatik cleanup/drop/repair yoktur. V006 eksikse testler sessizce atlanmaz, validate başarısız olur.

## Onaya sunulan SQL

[V006__add_events_and_participations.sql](../db/managed-migrations/V006__add_events_and_participations.sql)
ve `.sql.conf` hazır; **çalıştırılmadı, PostgreSQL parse/execute başarısı henüz kanıtlanmadı**.
Hazırlanan dosyanın SHA-256 değeri:
`046713d9afa9cb508a38b3176b7f2b86808ca4e9a18beb105fc3e6a25f46434c`.

| Etki | Kapsam |
| --- | --- |
| Yeni tablolar | events (26 sütun), event_participations (14 sütun), başlangıçta boş |
| Identity sequence | events_id_seq ve event_participations_id_seq |
| Kısıtlar | 2 PK, 1 unique, 7 FK, 19 CHECK; toplam 29 |
| İndeksler | 9 açık CREATE INDEX + 2 PK/1 unique otomatik indeks; toplam 12 |
| History | V006 başarılı olursa Flyway yalnız SQL 006 migration satırı ekler |
| Mevcut iş verileri | UPDATE/DELETE/backfill/seed yok; sütunlar/sahiplikler değişmez |

Tarihler, enum/state/null alanları, kapasite pozitifliği, bireysel katılım unique ve
FK bütünlüğü DB'de korunur. Kapasite bir çapraz satır kuralıdır; CHECK ile korunuyormuş
gibi sunulmaz, tüm yazma yolları event row lock kullanır. Yeni FK kurulumu parent
tablolarda kısa süreli referans kilitleri ve PostgreSQL'in dahili RI trigger'larını
gerektirir; elle trigger eklenmez. lock_timeout=5s, statement_timeout=30s.
Flyway transaction'ı yönetir; `.sql.conf` executeInTransaction=true.

Eski V001–V005 ve uygulanmış V005 başlangıç dosyası değişmedi. Maven hedefi 6 olarak
hazırlandı; **flyway.skip=true, baselineOnMigrate=false, cleanDisabled=true korunur**.
DB değişkenleri olmadan varsayılan flyway:info çağrısının "Skipping Flyway execution"
ile bağlantı açmadan bittiği doğrulandı. Normal test/package/uygulama migration yapmaz.

### Sınırlı runtime grant'leri

- Test için [04-event-runtime-grants.psql](../db/provisioning/test/04-event-runtime-grants.psql):
  yalnız test runner'a yeni iki tabloda SELECT/INSERT/UPDATE/DELETE, yeni iki sequence'de
  USAGE/SELECT. Başarılı test V006 kaydı ve doğru hedef/sahiplik aranır. DDL/TRUNCATE/
  history yazma/rol üyeliği verilmez. Eski grant'ler değiştirilmez.
- Ana DB için [01-event-runtime-grants.psql](../db/provisioning/main/01-event-runtime-grants.psql):
  cem'in yaratacağı yeni tabloları mevcut sınırlı campus_plus_user hesabının kullanması
  için aynı dar DML/sequence listesi. **Bu dosya ayrı ana DB onayı olmadan uygulanmayacak.**
  Yeni rol/parola veya mevcut hesapta yönetici yetkisi yoktur.

## İlk uygulama için istenen onay — yalnız test DB

Hedef **127.0.0.1:5434/campus_plus_test, PostgreSQL 17**:

1. Gerçek kimlik, history/V005 kontratı ve eski tablo veri/sahiplik/ACL özetleri yeniden
   salt okunur doğrulanır. MIGRATION_DB_* yalnız mevcut test migrator hesabıyla sağlanır.
2. Açık onayla V006 migrate; ardından yalnız yeni tablo/sequence test grant'leri.
   Mevcut V005 yeniden çalıştırılmaz. Beklenmeyen hedef/history/şema durumunda durulur.
3. `inspect --target test --schema-version 6` ile tam katalog alınır. Eski altı tablo/
   dört sequence'nin V005 kontratıyla aynı olduğu ve yeni nesnelerin onaylı SQL'le
   eşleştiği incelenir. Gözden geçirilmiş tam V006 şema kontratı ayrı dosyada kaydedilir;
   mevcut V005 kontratı değiştirilmez. Envanter izin listesi tek başına yapısal onay değildir.
4. Hibernate validate/contextLoads ve mevcut 10 + yeni 10 PostgreSQL IT çalıştırılır.
   Unit testler de beraber çalışır; hata varsa ana DB'ye geçilmez, durum bildirilir.
5. CI'nın schema-version 6 / V006 kontratı / ek grant adımı bu gözden geçirilmiş şema
   ile tamamlanır; yeni tam CI henüz çalıştırılmadı. Önceki başarılı CI d0b250e sürümüne aittir.
6. Sonuç sunulur. Ana `campus_plus` migration/grant işlemi için **ayrıca** açık onay alınır.

## Çalıştırma sınırı

Bu çalışma ağacındaki yeni entity sürümü V006 şemasını gerektirir. V005 DB'ye karşı
normal uygulama veya `-Ppostgres-it verify` şu aşamada Hibernate validate hatası verir;
bu nedenle migration onayından önce başlatılmadı. `ddl-auto=update/create` çözümü yoktur.
Son gönderilmiş d0b250e sürümü ve çalışan sistemin veritabanı korunur. Yeni modül henüz
canlı PostgreSQL üzerinde tamamlandı/yayınlandı olarak değerlendirilmez.
Commit/push, V006 kontratı/CI eşlemesi ve canlı testler tamamlandıktan sonra ayrıca istenir.
