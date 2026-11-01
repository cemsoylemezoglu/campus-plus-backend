# Notification — ana Campus+ V008 uygulaması

Tarih: 2026-11-01. **V008 ve sınırlı runtime yetkileri ana Campus+ veritabanında
uygulandı; veri koruması, şema ve uygulama kontrolleri başarılı.** Commit/push yapılmadı.
Başka ürün modülüne geçilmedi. Diğer projeler, PG14, Docker, Supabase ve Render kullanılmadı.

## Açık onay ve hedef

Kullanıcı somut hedef/SQL/yetki/test sorusuna **“Ana campus_plus üzerinde V008 ve
belirtilen sınırlı yetkileri açıkça onaylıyorum.”** yanıtını verdi.
Yalnız `127.0.0.1:5434/campus_plus`, PostgreSQL 17 kullanıldı.

Önce son test edilen source snapshot'ı ve SQL/grant checksum'ları doğrulandı. Salt okunur
ön kontrolde gerçek DB kimliği, tam V007 kontratı, BASELINE5+SQL6+SQL7 geçmişi,
dokuz tablonun veri fingerprint'leri ve bütün mevcut sahiplik/ACL'ler incelendi.
Başka aktif ana DB istemcisi, event trigger veya beklenmedik cem default ACL yoktu.
Migration için mevcut `cem`, uygulama kontrolü için sınırlı `campus_plus_user` kullanıldı.
PG rolü/parolası veya servis ayarı değiştirilmedi.

## Uygulanan değişiklik

[Test DB'de doğrulanan](notification-test-validation.md) aynı
[V008 SQL](../db/managed-migrations/V008__add_notifications.sql), Flyway transaction'ı
ile uygulandı. SHA-256:
`d552f4293b011363c0083b8139816e4c63b7a5ab705befec1175a0ef6c46acd7`.
SQL veya `executeInTransaction=true` ayarı düzenlenmedi.

- `notifications`: 13 sütun, 14 constraint (7 FK, 5 CHECK, PK, unique), 10 indeks ve
  bir identity sequence. Bütün constraint'ler validated, indeksler valid/ready.
- Tam şema [V008 kontratı](../db/schema/v008-contract.json) ile eşleşiyor.
- Önceki üç history kaydı ve metadata şekli aynı; dördüncü başarılı V008 SQL kaydı eklendi.
  V008 Flyway checksum'ı test DB'sindekiyle aynı, `flyway:validate` başarılı.
- Eski migration'lar tekrar uygulanmadı; repair/clean/drop/seed/backfill yok.
- [Ana grant SQL'i](../db/provisioning/main/03-notification-runtime-grants.psql) ayrı
  transaction'da uygulandı. `campus_plus_user` yalnız yeni tabloda SELECT/INSERT/UPDATE,
  yeni sequence'de USAGE/SELECT aldı. Tam ACL'de owner dışındaki beş izin yalnız bu
  hesaba ait; grant option yok. Yeni nesnelerde DELETE/TRUNCATE, sequence reset veya
  history yazma yetkisi yok. Önceki nesne yetkileri aynı.

Recipient CASCADE ve actor SET NULL yalnız gelecekte gerçekten silinen bir hesapta
bildirim yaşam döngüsünü yönetir; bu ana DB işleminde hiçbir hesap/rol/kayıt silinmedi.

## Veri koruması

Migration öncesi/sonrası ve uygulama kontrolü sonunda bütün alanları kapsayan
aggregate fingerprint'ler ve sayılar aynı. Parola/hash/rol alanları karşılaştırmaya
dahildir; bireysel değerler rapora veya sohbet çıktısına çıkarılmadı.

| Tablo | Korunan kayıt |
| --- | ---: |
| users | 12 |
| user_roles | 12 |
| universities | 3 |
| student_profiles | 7 |
| clubs | 4 |
| club_memberships | 7 |
| events | 0 |
| event_participations | 0 |
| club_announcements | 0 |

DB/public schema ve eski tablo/indeks/sequence sahiplik/ACL'leri aynı.
Yalnız onaylı 12 relation (tablo, sequence, 10 indeks) eklendi.
Yeni bildirim tablosu boş, identity sequence kullanılmamış durumda.
Ana DB'de test hesabı/ADMIN ilişkisi/bildirim/içerik oluşturulmadı veya değiştirilmedi.
Ham kanıtlar/loglar repo dışında 0600 izinle tutuldu; sırlar Git'e eklenmedi.

## Normal uygulama doğrulaması

374 Java testiyle doğrulanmış jar'ın Notification model/controller/service/writer
sınıflarını içerdiği kontrol edildi. Bu ana DB adımında Java kaynakları değiştirilmedi.
Normal jar geçici random loopback portunda açıldı; Hibernate `ddl-auto=validate` başarılı.
Yalnız bu geçici süreçte pgJDBC oturumlarına `default_transaction_read_only=on` verildi.
Kalıcı IDE/URL/DB/servis ayarı değiştirilmedi; test profili ana DB'ye yönlendirilmedi.

**32 salt okunur GET kontrolü başarılı:**

- Kendi kullanıcı DTO'su 200; anonim erişim 401, ADMIN olmayan kullanıcı listesi 403.
- Geçersiz/süresi dolmuş JWT ve NULL-hash legacy hesap 401.
- Üniversite/kulüp, Event/Announcement keşif ve kişisel etkinlik katılım sayfaları 200;
  sınır/geçersiz tarih 400, olmayan kaynak/yönetim kulübü 404.
- Notification listesi/unread filtresi ve okunmamış sayısı 200/boş/sıfır.
  recipientId query parametresiyle başka alıcıya geçilmiyor. Özel yanıtlarda no-store.
- Notification anonim/geçersiz/expired token erişimi 401; sayfa/boyut sınırları 400.

Main ortam boş olduğundan başka alıcıya ait gerçek bildirim üzerinden IDOR testi
burada üretilmedi; bu durum test DB'deki 11 yeni PG testinde gerçek satırlarla doğrulandı.
Ana ortamda POST/read/unread/read-all veya domain yazma işlemi çalıştırılmadı.
Mevcut kullanıcı parolaları kullanılmadı/değiştirilmedi: yalnız geçici doğrulama
uygulamasına ait rastgele key ve kısa ömürlü JWT bellekte üretildi. Gerçek JWT_SECRET
korundu; key/token/yanıt değerleri yazdırılmadı. Yalnız kendi kontrol süreci kapatıldı;
başka süreç/servise müdahale edilmedi.

Önceki aynı kaynak sürümünün **374 Java (330 izole + 44 PostgreSQL, contextLoads dahil)
ve 36 Python testi başarılıydı**. Kod değişmediğinden aynı paket tekrar çalıştırılmadı;
ana ortamda ek Flyway/Hibernate/kontrat/veri koruması ve 32 GET kontrolü yapıldı.

## Tamamlanan aşama ve devam noktası

Uygulama içi bildirimlerin kod, test DB ve ana DB aşamaları tamamlandı. Ana ve test
şemaları V008 ile uyumlu. Duyuru/kulüp/üyelik/etkinlik kaynaklarıyla aynı transaction'da
kalıcı üretim, alıcıya özel kutu, okunma durumu ve güncel kaynak erişimi uygulanıyor.

CI V008 kurulumu/kontratı/grant adımı yerelde hazır ve statik doğrulandı; henüz push
edilmedi/uzakta çalıştırılmadı. Eski uzak d0b250e başarısı V005 sürümüne aittir.

Sonraki uygun ürün adayı sosyal akış/gönderiler, ardından yorum/beğeni entegrasyonudur;
önce ayrı tasarım hazırlanır. Mesajlaşma, bildirim tercihleri/dış teslim/outbox,
moderasyon ve yayınlama geniş kapsamda kalır. Bu migration onayıyla yeni modül veya
commit/push işlemine başlanmadı.
