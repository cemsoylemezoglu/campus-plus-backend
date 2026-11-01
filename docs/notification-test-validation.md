# Notification — test veritabanı V008 doğrulaması

> Bu belge test DB aşamasının kaydıdır. Daha sonra ayrı açık onayla ana V008 geçişi
> de tamamlandı: [ana DB uygulama raporu](notification-main-validation.md).

Tarih: 2026-11-01. **V008 ve sınırlı runtime yetkileri yalnız
`127.0.0.1:5434/campus_plus_test` üzerinde uygulandı; tüm testler başarılı.**
Ana `campus_plus` bu aşamada bağlanılmadan/yazılmadan korundu; son doğrulanmış sürümü V007.
Commit/push yapılmadı. Diğer projeler, PG14, Docker, Supabase ve Render kullanılmadı.

## Açık onay ve migration

Kullanıcı somut hedef/yetki/test sorusuna “Yalnız campus_plus_test üzerinde V008,
sınırlı yetkiler ve belirtilen testleri açıkça onaylıyorum.” yanıtını verdi.
Önce gözden geçirilmiş kaynak snapshot'ı, SQL ve grant checksum'ları doğrulandı.

Salt okunur ön kontrolde gerçek PG17/127.0.0.1/5434/test DB kimliği, V007 tam kontratı,
üç başarılı SQL history kaydı, sınırlı migrator/DB sahibi doğrulandı. Başka aktif test
istemcisi, etkin event trigger veya beklenmedik migrator default ACL bulunmadı.

[V008 SQL](../db/managed-migrations/V008__add_notifications.sql) ve
[executeInTransaction ayarı](../db/managed-migrations/V008__add_notifications.sql.conf)
review dizininden **byte'ları değiştirilmeden** managed-migrations konumuna taşındı.
İlk SQL açıklaması hazırlık aşamasının kaydıdır; uygulanmış SQL düzenlenmedi.
SHA-256: `d552f4293b011363c0083b8139816e4c63b7a5ab705befec1175a0ef6c46acd7`.

Flyway yalnız V008'i transaction içinde uyguladı; V005–V007 tekrar çalıştırılmadı.
History'de önceki üç kayıt aynı kaldı, dördüncü başarılı V008 SQL kaydı eklendi.
`flyway:validate` başarılı; repair/clean/drop veya servis/PG rolü/parola değişikliği yok.

## Şema ve yetkiler

Yeni `notifications`: **13 sütun, 14 constraint, 10 indeks ve bir identity sequence**.
Kısıtlar 7 FK + 5 CHECK + PK + unique; indeksler 8 açık + PK/unique için 2 otomatik.
23 bildirim türü ve kaynak FK kombinasyonları doğrulanıyor.
Recipient FK CASCADE, actor FK SET NULL; diğer kaynak FK'leri RESTRICT.
Bütün constraint'ler validated, indeksler valid/ready. Tablo testlerden önce boştu.

Kolonlar/default/identity, kaynak/durum CHECK'leri, FK silme davranışları, partial
indeksler ve sequence tanımı uygulanan SQL ile incelendi. [V008 tam kontratı](../db/schema/v008-contract.json)
bu incelemeden sonra hazırlandı; final katalog eşleşiyor. Managed V005–V008 SHA-256
manifesti doğrulandı; eski kontratlar/SQL'ler değişmedi. Önceden incelenmiş dört eski
enum CHECK eşdeğerliği dışında SQL normalizasyonu eklenmedi.

[Test grant SQL'i](../db/provisioning/test/06-notification-runtime-grants.psql) yalnız
`notifications` SELECT/INSERT/UPDATE ve identity sequence USAGE/SELECT verir.
Tam ACL listesinde owner dışındaki **beş izin** yalnız test runner'a ait; grant option yok.
DELETE/TRUNCATE, sequence reset, history yazma, DB/schema CREATE veya PG yönetici yetkisi yok.
[Ana grant dosyası](../db/provisioning/main/03-notification-runtime-grants.psql) çalıştırılmadı.

## Mevcut kayıtların korunması

Migration öncesi/sonrası dokuz iş tablosunun bütün alanlarını kapsayan fingerprint'ler
aynı. Eski tablo/indeks/sequence sahiplik/ACL'leri ve DB/public schema yetkileri aynı.
Yalnız onaylı 12 relation (yeni tablo, sequence ve 10 indeks) eklendi.

Testler yeni benzersiz fixture'lar oluşturdu. Son karşılaştırma başlangıçtaki ID üst
sınırlarıyla yapıldı; önceki users/user_roles/universities/student_profiles/clubs/
club_memberships/events/event_participations/club_announcements satırları, parola ve
roller dahil aynı fingerprint'leri korudu. Mevcut kayıtlar silinmedi/değişmedi.
Disposable hesap cascade/SET NULL testi yalnız yeni fixture hesabı ve rolünü transaction
içinde değiştirip rollback etti; kullanıcı kaydı geri geldi. Test fixture'ları bırakıldı;
izole ADMIN test ilişkileri ana DB'ye taşınmadı. Ham kanıtlar/loglar repo dışında 0600
izinle saklandı; satır/parola/hash/key/token değerleri Git'e veya rapora çıkarılmadı.

## Test sonucu

`-Ppostgres-it verify`: **374 Java testi başarılı**; hata/başarısız/atlanan yok:

| Grup | Test |
| --- | ---: |
| İzole Java | 330 |
| PostgresNotificationIT | 11 |
| PostgresAnnouncementIT (iki kilit testi dahil) | 10 |
| PostgresEventIT | 13 |
| PostgresApiIT | 9 |
| CampusPlusBackendApplicationIT (`contextLoads()`) | 1 |

Yeni gerçek PostgreSQL kontrolleri:

- Yayın snapshot'ında yalnız uygun üyeler; kendi yayınını yapan aktör hariç.
  Kaynak yazarı tekrar çağrılsa da unique conflict hedefi ikinci teslimi önler;
  sonradan katılana eski yayın otomatik gönderilmez.
- ADMIN/diğer kullanıcı notification IDOR reddi; read/unread/version/idempotence,
  cutoff ile read-all ve yeni kayıtların unread kalması.
- Üyelik kaybında hedef redaksiyonu; kişisel inbox okunma işlemi sürer.
- İki yöneticinin aynı anda birbirine bildirim üretmesi: iki yayın da 200,
  doğru alıcı sayısı; gerçek notification FK'leriyle deadlock oluşmadı.
- Read-all/stale unread yarışı, insert sonrası zorlanmış hata ile kaynak+inbox rollback.
- Manuel etkinlik iptali REGISTERED ve ATTENDED'a tek olay; ikinci bilet bildirimi yok.
  Üyelik/archive yalnız gerçekten sona eren kayıtları bildirir, tekrar üretmez.
- CHECK, unique ve FK ihlalinde rollback; recipient cascade/actor SET NULL davranışı,
  yalnız yeni disposable hesapta geri alınmış silme kontrolü.
- 100 ayrı kaynaklı sayfada SQL sayısı <=14 bütçesi geçti; bu sınırlı sorgu sayısı
  testidir, sınırsız yük/performance garantisi değildir.
- Writer mevcut transaction olmadan çalışmıyor; MANDATORY koruması gerçek context'te geçti.

Mevcut authentication/RBAC/akademik/kulüp/duyuru/etkinlik yarışları ve kullanıcı kilidi
uyumu da aynı V008 ortamında başarılı. Yeni runtime bağımlılığı veya auth kuralı eklenmedi.

**36 Python testi başarılı**: V008 için tam on tablo zorunluluğu, eski sürümle/eksik veya
fazla tabloyla reddetme testleri eklendi. Tarihsel/managed checksum kontrolleri başarılı.
V008 workflow'u actionlint v1.7.12'den geçti; kurulum V005→V008/kontrat/test grant adımı
hazır. GitHub'a henüz push/uzak çalışma yok; eski uzak başarı V005 sürümüne aittir.

## Sonraki ayrı onay: ana Campus+ V008

Bu rapor ana DB uygulama onayı değildir. Açık onay verilirse:

1. Yalnız `127.0.0.1:5434/campus_plus` PG17; BASELINE5+SQL6+SQL7 history, V007 kontratı,
   dokuz tablonun veri/ACL fingerprint'leri salt okunur doğrulanacak. Farkta işlem duracak.
2. Aynı checksum'lı V008 transaction içinde uygulanacak: bir boş tablo, 13 sütun,
   14 constraint, 10 indeks, bir sequence ve V008 SQL history kaydı eklenecek.
   Mevcut dokuz iş tablosunda ALTER/UPDATE/DELETE/backfill/seed yok.
3. `03-notification-runtime-grants.psql` mevcut sınırlı `campus_plus_user` hesabına
   yalnız yeni tablo SELECT/INSERT/UPDATE ve sequence USAGE/SELECT yetkilerini verecek.
   PG rolü/parolası veya servis ayarı değişmeyecek.
4. Eski kayıt/yetkiler, tam V008 şeması ve Flyway validate doğrulanacak. Normal jar
   Hibernate validate ile, geçici loopback portunda salt okunur DB oturumlarıyla
   açılıp GET/JWT/notification list/count kontrolleri yapılacak. Ana DB'de test
   hesabı/bildirim kaydı oluşturulmayacak; test profili ana DB'ye yönlendirilmeyecek.

Güncel çalışma ağacı V008 ister; ana DB geçişi onaylanmadan V007 ana şemayla
başlatılırsa validate hatası beklenir. Validate kapatılmaz. Dış teslim/outbox,
sosyal akış, yorum/beğeni, mesajlaşma ve diğer modüller genel kapsamda kalır.
