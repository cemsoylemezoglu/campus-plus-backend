# Campus+ — izole PostgreSQL test ortamı uygulama önerisi

Durum: **Kullanıcı onayıyla campus_plus_test kuruldu; V005 migrate ve testler başarılı** (2026-10-30).
Kullanıcı hazırlanan planın ardından Campus+ kapsamındaki uygun işlemleri ilerletme
yetkisi verdi. Bu yetki yalnız tarif edilen test ortamını kapsar; diğer projelere veya
ana iş tablolarına müdahale izni değildir. Event geliştirmesi başlamadı.

## Salt okunur ön kontrol

`db/verification/test_provision_preflight.sql`, mevcut Campus+ bağlantısıyla
`default_transaction_read_only=on` altında çalıştırıldı:

- Gerçek hedef: `127.0.0.1:5434/campus_plus`, PostgreSQL 17.11.
- Kurulum öncesinde `campus_plus_test` ve aşağıdaki iki rol mevcut değildi.
- Ana public iş tablolarında/sütunlarında/sequencelerinde PUBLIC grant yok;
  PUBLIC için public şemasında veya DB düzeyinde CREATE yok.
- Mevcut uygulama hesabı superuser değil; CREATEROLE ve CREATEDB yetkileri de yok.
  Bu hesabın yetkileri yükseltilmeyecek. Kurulum için aynı PG17 sunucusunda kullanıcı
  tarafından yetkilendirilen mevcut `cem` PG17 yönetici rolü kullanıldı. Mac kullanıcı
  adıyla aynı; doğru TCP hedefinde parolasız erişim doğrulandı. Başka proje ayarlarından
  yönetici parolası aranmadı; parola sohbette istenmedi.

Sorgu diğer projelerin veri tablolarını veya parola kataloglarını okumaz. Uygulamadan
hemen önce aynı kontroller yeniden yapılmalı; isim çakışması veya PUBLIC yetki farkında
durulmalı. Ana DB üzerinde GRANT/REVOKE veya rol değişikliği yapılmaz.

## Onaya sunulan kesin nesneler ve işlemler

| Nesne | Yetki / etki |
| --- | --- |
| `campus_plus_test_migrator` | Yeni PG17 rolü; yalnız yeni test DB'sinin sahibi. Superuser/CREATEDB/CREATEROLE/REPLICATION/BYPASSRLS yok. |
| `campus_plus_test_runner` | Ayrı yeni rol; test DB CONNECT/public USAGE; altı iş tablosunda SELECT/INSERT/UPDATE/DELETE; dört identity sequence üzerinde USAGE/SELECT. DDL/TRUNCATE/history yazma yetkisi yok. |
| `campus_plus_test` | `template0` üzerinden UTF8, libc/en_US.UTF-8; ana DB'den satır kopyalanmaz. PUBLIC DB/şema yetkileri yalnız bu yeni DB'de kaldırılır. |
| V005 başlangıç migration'ı | Yalnız yeni test DB'de altı iş tablosu, dört identity sequence, 38 constraint, 17 iş indeksi. Flyway ayrıca kendi history tablosu/indeksini ve başarılı migration kaydını oluşturur. |

PostgreSQL rolleri cluster düzeyindedir. Bu, diğer DB'lere özel yetki verme anlamına
gelmez; ancak başka DB'lerdeki PUBLIC CONNECT/TEMP haklarını kaldırmaz. Dolayısıyla
"bu roller başka DB'ye hiç bağlanamaz" iddiası yoktur. Test araçları hedefi ayrıca
`127.0.0.1:5434/campus_plus_test` ile sınırlar. Diğer DB ACL'leri incelenmez/değiştirilmez.
Migration hesabı yeni DB'nin sahibi olduğundan orada DDL yapabilir; testler bu hesapla çalışmaz.

## Onay sonrası sıra

1. Mevcut veri özetleri/checksum/şema kontratı salt okunur tekrar doğrulanır. Yetkili
   yönetici bağlantısında gerçek host/port/PG17 ve kaynak DB kimliği tekrar doğrulanır.
2. [01-create-database.psql](../db/provisioning/test/01-create-database.psql) çalıştırılır.
   Roller başlangıçta NOLOGIN ve PASSWORD NULL olur. CREATE DATABASE transaction dışında
   çalışır; rol yaratma kendi transaction'ındadır. Tam paket tek atomik işlem değildir.
   Hata sonrası otomatik drop/repair/yeniden kullanım yapılmaz, durum raporlanır.
3. Yeni test DB'ye geçilip [02-lockdown-schema.psql](../db/provisioning/test/02-lockdown-schema.psql)
   çalıştırılır. Public şema boş değilse veya sahip/hedef farklıysa durur.
4. Her rol için bağımsız en az 32 rastgele bayttan parola üretilir; yalnız repo dışındaki
   `/Users/cem/.campus-plus-local/test-db-migrator-password` ve
   `/Users/cem/.campus-plus-local/test-db-runner-password` dosyalarında,
   dizin 0700/dosya 0600 izinleriyle saklanır. Mevcut dosyanın üzerine yazılmaz. Sohbete,
   kaynak dosyasına, komut argümanına, SQL parola literal'ine veya test çıktısına yazılmaz.
   Onaylanan plan kapsamında dosyalar oluşturuldu; içerikleri çıktıya yazılmadı.
5. Yetkili psql `-X` oturumunda `SET password_encryption='scram-sha-256';` uygulanır.
   `\password campus_plus_test_migrator` ve `\password campus_plus_test_runner`
   ile parolalar gizli terminal girdisinden atanır; SQL literal yöntemi kullanılmaz.
   psql echo/history/terminal kayıtlarına sır girmez. İki atama başarılı olduktan sonra
   aşağıdaki işlemle login açılır; başarısız atamada NOLOGIN korunur:

   ```sql
   BEGIN;
   ALTER ROLE campus_plus_test_migrator LOGIN;
   ALTER ROLE campus_plus_test_runner LOGIN;
   COMMIT;
   ```

   Sunucu authentication/logging ayarı değiştirilmez. SCRAM verifier'ı da sır sayılır;
   sunucu loglama kapsamı belirsizse parola adımından önce durulur ve açıklanır.
6. MIGRATION_DB_* yalnız test hedefi + migrator hesabıyla sağlanır. Doğru kimlik ve boş
   şema kontrolünden sonra, [runbook](schema-bootstrap-runbook.md) sınırlarıyla
   `schema-tools` profilinde Flyway migrate açıkça çalıştırılır (skip=false, target=5).
   Ana DB'ye baseline veya migrate uygulanmaz.
7. Test DB read-only envanteri V005 kontratıyla karşılaştırılır; history'nin BASELINE
   değil başarılı SQL migration kaydı içerdiği doğrulanır. Sonra
   [03-runtime-grants.psql](../db/provisioning/test/03-runtime-grants.psql) çalıştırılır.
8. Runner için TEST_DB_* tanımlanır; ana uygulamanın DB_* değişkenleri değiştirilmez.
   İzinler `has_table_privilege/has_sequence_privilege/has_schema_privilege` ile incelenir:
   runner sahip değil, CREATE/TRUNCATE/history DML yok; migrator rolüne üyelik yok.
   Ana DB'de deneme INSERT/UPDATE/DELETE yapılmaz; ana DB yetki kontrolü salt okunurdur.
9. `./mvnw test`, ardından `./mvnw -Ppostgres-it verify` ile gerçek contextLoads ve
   Hibernate validate çalışır. Context datasource'u salt okunurdur. Ayrı HTTP IT datasource'u
   gerçek hedef/PG17/sınırlı runner ve yetki kontrollerinden sonra yazmaya izin verir;
   yönetici/migrator kimlik bilgilerini bağlantıdan önce reddeder.
10. Ana DB envanteri/veri özetleri tekrar karşılaştırılır. Test verileri yalnız yeni DB'de
    kalır; otomatik cleanup veya kaynak DB satırı değişikliği yapılmaz.

## Doğrulama ve kalan sınır

Yeni DDL yalnız campus_plus_test üzerinde uygulandı; locale/şema/permission/history ve
gerçek test sonuçları [3. aşama raporunda](setup-test-stage3-review.md) açıklanır.
Eski V001–V005 ve çalışan uygulama ayarları değişmedi. Mevcut ana DB için Flyway baseline,
CI hazırlığı, Event, commit/push bu kurulum onayının kapsamında değildir.

Dayanak: [CREATE DATABASE ve transaction sınırı](https://www.postgresql.org/docs/17/sql-createdatabase.html),
[cluster düzeyindeki roller](https://www.postgresql.org/docs/17/sql-createrole.html),
[psql parola değiştirme](https://www.postgresql.org/docs/17/app-psql.html).
