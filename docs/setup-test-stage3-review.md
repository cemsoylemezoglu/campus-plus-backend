# Kurulum / test altyapısı — 3. aşama sonucu

Tarih: 2026-10-30. Durum: **izole test ortamı kurulumu ve PostgreSQL doğrulaması tamamlandı**.
Kullanıcı, somut DB/rol/V005 paketi sunulduktan ve `cem` yönetici rolü salt okunur
tespit edildikten sonra Campus+ kapsamında devam etme onayı verdi. Commit/push yapılmadı.

## Uygulanan kapsam

- Aynı PostgreSQL 17.11 sunucusunda yalnız `127.0.0.1:5434/campus_plus_test` oluşturuldu.
  Template0, UTF8, libc provider, en_US.UTF-8 collate/ctype kullanıldı.
- `campus_plus_test_migrator`: yeni test DB sahibi; superuser/CREATEDB/CREATEROLE/
  REPLICATION/BYPASSRLS yok. `campus_plus_test_runner`: sınırlı çalışma hesabı;
  altı iş tablosunda DML ve dört identity sequence üzerinde USAGE/SELECT.
- `db/provisioning/test/01–03` sırasıyla uygulandı. Roller önce NOLOGIN/PASSWORD NULL
  yaratıldı, bağımsız 48 rastgele bayttan üretilmiş parolalar gizli psql istemleriyle SCRAM
  biçiminde atandı, ardından LOGIN açıldı. Sırlar repo dışı Campus+ yerel dizininde
  0600 dosyalarda; dizin 0700. Mevcut JWT anahtarı veya uygulama parolası değişmedi.
- Parola adımından önce SQL/duration loglamasının kapalı ve preload/rol/DB logging
  override'larının bulunmadığı salt okunur kontrol edildi. Parola veya verifier çıktısı alınmadı.
- Flyway 12.4.0, yalnız test hedefinde açık `schema-tools` göreviyle V005'i uyguladı.
  History kaydı version `005`, type `SQL`, success `true`; BASELINE marker değildir.
- İş şeması: altı tablo, 52 sütun, 38 constraint, 17 iş indeksi ve dört identity sequence.
  Migration sonrası iş tabloları boştu; ana DB'den satır veya sequence pozisyonu kopyalanmadı.
  Sonraki HTTP testleri yalnız yeni test DB'sinde benzersiz fixture'lar bıraktı.
- PUBLIC DB/şema yetkileri yalnız yeni test DB'de kaldırıldı. Runner'ın CREATE, TRUNCATE
  ve Flyway history INSERT/UPDATE/DELETE hakları bulunmadığı doğrulandı.
  Başka DB'lerde PUBLIC CONNECT/TEMP hakları değiştirilmedi; roller cluster nesneleridir.

## Şema karşılaştırmasında teşhis edilen fark

İlk katalog karşılaştırması dört enum CHECK tanımında metin farkı bildirdi:
`ANY ((ARRAY[...])::text[])` yeniden parse edilince
`ANY (ARRAY[('...'::character varying)::text, ...])` biçiminde kaydedildi.
Bu dört ifade sabit varchar literal dizisinin text dönüşümünü yapar; incelenen iki
yazım aynı değerleri kabul eder. Kolonlar/enum değerleri/sırası/operatörler değişmedi.

`schema_tools.py` yalnız dört adlandırılmış tablo/kısıt ve tam sabit ifade çifti için
incelenmiş eşdeğerliği tanır. Genel regex/SQL normalizasyonu, constraint atlama veya
kontrat yenileme yapılmadı. Değişen değer/operatör/ad/validated durumu, diğer FK/indeks/
sequence/locale farkları reddedilir. Ham katalog raporları ve V005 kontratı değiştirilmedi.
Yeni veya eski uygulanmış migration SQL'ine düzeltme yapılmadı.

İlk yürütmede runtime grant adımı, katalog karşılaştırmasının hatasında durması gerekirken
çalıştı. Bu yürütme hatasının etkisi yalnız yeni test DB'sindeki önceden onaylı sınırlı
grant'lerdi. Yazma işlemleri durdurulup fark salt okunur incelendi; şema farkı araçta
çözüldükten sonra testlere geçildi. Ana DB'ye grant/DDL/DML yapılmadı.

İlk yeni HTTP testinde farklı üniversite başvurusu için 403 beklenmişti. Mevcut mock
testleri ve iş kuralı sözleşmesi bunun 409 conflict olduğunu doğruladı. Test beklentisi
düzeltildi ve üyelik satırı oluşmadığı ayrıca doğrulandı; üretim davranışı değiştirilmedi.

## Testler

Son `-Ppostgres-it verify` sonucu **203 Java testi başarılı**, hata/başarısız/atlanan yok:

- 193 Surefire izole testi; 4 yeni yazmalı bağlantı koruma testi dahil.
- 1 gerçek `contextLoads()` ve Hibernate `ddl-auto=validate` testi.
- 9 gerçek HTTP/JDBC entegrasyon testi (`PostgresApiIT`):
  register/login/BCrypt ve eski NULL hash; eksik/geçersiz/süresi dolmuş JWT/401;
  ADMIN/RBAC/403; University/Profile/kullanıcı sahipliği;
  yerel CLUB_ADMIN ve üniversite sınırları; çift başvuru yarışı;
  üniversite değişimi/başvuru yarışı; tek OWNER constraint/sahiplik devri/ayrılma;
  rakip sahiplik devirleri; FK/CHECK ihlali ve transaction rollback.

HTTP testleri random loopback portunda gerçek Spring Boot sunucusu ve bağımsız HTTP
istekleriyle çalışır. Yarışlar iki thread/ayrı sunucu transaction'ları kullanır; tek mock
veya test transaction'ı üzerinden eşzamanlılık kanıtlandığı varsayılmaz. Test başlangıcında
ana DB/JWT ortam değişkenleri temizlendi; JWT anahtarı bellek içinde rastgele üretildi.

`PostgresTestDatabase` varsayılan context bağlantısını salt okunur tutar. Yazmalı test
bağlantısı yalnız tam test URL'si ve runner hesabıyla açılır. Her bağlantı önce salt
okunur kimlik ve yetki kontrolü yapar, sonra yazmayı açar; yanlış sunucu/sürüm/DB,
yönetici/migrator hesabı veya ayrıcalıklı runner reddedilir. Hibernate create/update yoktur.

Python araç testleri: **16 başarılı**. Legacy V001–V005 SHA-256 manifesti değişmedi.
Normal uygulama kaynakları/resources ve runtime bağımlılıkları değişmedi.

## Ana veri koruması ve sonraki sınır

Kurulum öncesi ve bütün HTTP/yazma/yarış testleri sonrası ana DB'nin tam şema envanteri
ve altı tablonun satır sayısı/toplu satır özetleri aynı:
users 12, user_roles 12, universities 3, student_profiles 7, clubs 4, club_memberships 7.
Ana DB'de Flyway history hâlâ yok. Her iki yeni rol için ana DB iş tablolarında
INSERT/UPDATE/DELETE/TRUNCATE ve DB/public CREATE hakları olmadığı salt okunur doğrulandı.

Test fixture'ları yalnız test DB'sinde bırakıldı; otomatik cleanup/drop/reset yapılmadı.
Sunucu/servis/authentication yapılandırması değişmedi. PostgreSQL 14, diğer proje
veritabanları, Docker, Supabase ve Render kullanılmadı.

Sonraki altyapı aşaması ana DB'ye yalnız Flyway baseline metadata'sı alınması için
kesin SQL/etki planı ve ayrı onay; ardından CI hazırlığı. Ana DB baseline henüz uygulanmadı.
Event/EventParticipation geliştirmesi başlamadı.
