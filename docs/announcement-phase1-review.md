# Duyurular — kod, test ve V007 onay paketi

> Bu belge kod aşamasının tarihsel kaydıdır. Sonraki açık onayla V007 yalnız
> `campus_plus_test` üzerinde uygulandı; [328 Java/34 Python testli canlı doğrulama](announcement-test-validation.md) başarılı. Ana DB geçişi
> bu onaya dahil değildir. SQL aynı checksum ile managed-migrations dizinine taşındı.
> Daha sonra ayrı açık onayla [ana DB geçişi de tamamlandı](announcement-main-validation.md).

Tarih: 2026-10-31. **Announcement kod ve izole test aşaması hazır; V007 hiçbir
veritabanına uygulanmadı.** Bu aşamada gerçek DB bağlantısı, SQL uygulaması,
commit veya push yapılmadı. Diğer projeler/PG14/Docker/Supabase/Render kullanılmadı.

## Geliştirilen kapsam

- `Announcement`, audience/status enum'ları ve version kontrollü JPA modeli.
- `dto/announcement/`: normalize edilmiş create/patch/version/withdraw istekleri;
  keşif, genel detay, yönetim özeti/detayı ve izinli etkinlik bağlantısı DTO'ları.
- Repository: kitle/üniversite/üyelik filtresi page/count öncesinde, kararlı sıralama,
  club kapsamlı lookup, pessimistic kaynak kilidi ve sıralı arşiv koordinasyonu.
- `AnnouncementPolicy`: ADMIN veya yalnız ilgili ACTIVE OWNER/MANAGER + CLUB_ADMIN.
  Yazar kimliği ayrı yetki değildir. Normal okuyucuda güncel aynı üniversite ve
  gerekirse aktif üyelik zorunlu; gizli içerik 404 olur.
- `service/announcement/`: create/update/publish/withdraw/archive, keşif ve yönetim
  sorguları. Yayından sonra yalnız metin değişir; kitle/event bağlantısı değişmez.
  Geri çekilen duyuru tekrar yayınlanamaz; taslak veya geri çekilmiş duyuru arşivlenir.
- Etkinlik bağlantısı aynı kulüp, kitle uyumu ve yayınlama anında yayınlanmış/
  arşivsiz etkinlik koşullarını denetler. Görünmeyen etkinlik özeti DTO'da null olur.
- `ClubService.archive`: mevcut EventLifecycleService ardından yeni
  AnnouncementLifecycleService aynı transaction'da çalışır. Tüm arşivsiz duyurular
  kapanır; yayınlar gerekçeli geri çekilir ve arşivlenir. Başka kulüp etkilenmez.
- DTO `toString()` çıktıları özel başlık/metin/gerekçe taşımayacak şekilde redakte;
  yanıt JSON'u yetkili kullanıcıya gereken içeriği vermeye devam eder. Özel yanıtlarda no-store.

Yeni runtime bağımlılığı, JWT/BCrypt/SecurityConfig veya ana resource ayarı değişikliği yok.
Notification/telefon push kodu henüz eklenmedi; tasarımdaki ikinci alt aşamadır.
Ortak kullanıcı kilitleri bu aşamada değiştirilmedi; çapraz alıcılı Notification
entegrasyonunun kilit uyumu kendi test aşamasında ele alınacak.

## API

| Metot / yol | İşlem |
| --- | --- |
| GET /api/announcements | Görünür yayınlar; clubId/universityId/query/page/size |
| GET /api/announcements/{id} | Güvenli genel detay |
| GET /api/clubs/{clubId}/announcements/manage | Yerel yönetim listesi; status/archived/page/size |
| GET /api/clubs/{clubId}/announcements/{id}/manage | Gerekçe dahil yönetim detayı |
| POST /api/clubs/{clubId}/announcements | Taslak, 201 + Location |
| PATCH /api/clubs/{clubId}/announcements/{id} | Version kontrollü metin/taslak alanları |
| POST /api/clubs/{clubId}/announcements/{id}/publish | Yayın |
| POST /api/clubs/{clubId}/announcements/{id}/withdraw | Gerekçeli geri çekme |
| POST /api/clubs/{clubId}/announcements/{id}/archive | Terminal arşiv |

Create: `title`, `body`, isteğe bağlı `audience`/`eventId`.
PATCH: `version` ve değişecek alanlar; taslakta eventId veya clearEvent=true,
ikisi birlikte değil. Withdraw: `version`, `reason`; diğer eylemler `version`.
Genel response: `announcement` özeti + `body` + güvenli `event`.
Yönetim response bunlara `status/archivedAt/createdAt/withdrawnAt/withdrawalReason` ekler.
401 authentication, 403 yönetim yetkisi, 404 gizli/yok kaynak, 409 sürüm/durum/bağlantı,
400 validation. Askıdaki kulüpte geri çekme/arşiv ADMIN'e açık; yeni yayın/düzenleme
aktif kulüp ve üniversite ister. Yetki/version kontrolü kilit/refresh sonrasında tekrarlanır.

## Test sonucu

**297 izole Java testi başarılı**, hata/başarısız/atlanan yok:

- Mevcut 250 regresyon korundu.
- 36 yeni AnnouncementModulesTest: gerçek JWT/security/MVC/method validation ve servisler;
  yalnız repository/EntityManager mock. IDOR/marker/rol kaybı/refresh, üniversite ve üyelik
  erişimi, publish/withdraw/archive/version, etkinlik kitle/bağlantı kontrolü, güvenli
  DTO/no-store ve kulüp arşivi entegrasyonu.
- 11 AnnouncementValidationTest: metin sınırları, Unicode boşluk, PATCH/clear/version,
  gerekçe ve log için diagnostic redaksiyon.
- Mevcut Hibernate metadata testi yeni entity ve bütün Announcement repository HQL
  sorgularını gerçek Hibernate ile JDBC bağlantısı açmadan derler.
- `python3 -m unittest discover -s scripts/tests`: **32 Python testi başarılı**.
- Tarihsel V001–V005 checksum'ları ve managed V005/V006 içerikleri korundu.
  Whitespace/doküman bağlantıları kontrol edildi.

İlk test kaynaklarında JSON literal ve Role import çakışması derleme hataları giderildi;
son tam Java koşusu bu düzeltmeleri ve DTO redaksiyonunu içerir.

`PostgresAnnouncementIT` **8 gerçek DB testi hazır ve derlendi; çalıştırılmadı**:
HTTP yaşam döngüsü, gizlilik/pagination/üyelik kaybı, farklı üniversite/marker,
eşzamanlı yayın, yayın/club archive yarışı, event bağlantıları, tam transaction rollback,
doğrudan SQL CHECK/FK korumaları. Bu testler yalnız korumalı campus_plus_test runner
DataSource'u kullanır; eksik şema/yanlış hedefte güvenli hata verir, sessizce atlanmaz.
`contextLoads()` dahil postgres-it profili V007 onayından sonra tekrar çalıştırılacak.
Mock testleri gerçek PostgreSQL/eşzamanlılık kanıtı olarak sayılmadı.

## V007 SQL ve etkiler — uygulama onayı bekleniyor

[Kesin SQL](../db/managed-migrations/V007__add_club_announcements.sql)
ve [transaction ayarı](../db/managed-migrations/V007__add_club_announcements.sql.conf).
SHA-256: `8d00500e3a906dbd1be5fe097237edf18c0813208ac02c7e9c2ecfc660d54ae5`.

- Bir yeni `club_announcements` tablosu, **16 sütun**, bir identity sequence.
- **13 constraint**: 1 PK, 4 FK (club/creator/event/withdrawnBy), 8 CHECK.
- **6 indeks**: 5 açık indeks + otomatik PK indeksi.
- Mevcut sekiz iş tablosuna ALTER/UPDATE/DELETE/seed/backfill yok; yeni tablo boş başlar.
- Flyway history'ye başarılı V007 SQL kaydı eklenir; V001–V006 tekrar uygulanmaz.
- Migration transaction içinde; lock_timeout=5s, statement_timeout=30s. Yeni FK'ler
  mevcut parent tablolarda kısa kilit gerektirebilir; timeout/hata durumunda otomatik
  repair/clean veya sessiz tekrar deneme yoktur.
- [Test runtime grant](../db/provisioning/test/05-announcement-runtime-grants.psql):
  yalnız yeni tabloda SELECT/INSERT/UPDATE/DELETE ve yeni sequence'de USAGE/SELECT.
  PG rolü, parola, schema/DB CREATE veya başka tabloya yeni yetki verilmez.
- [Ana runtime grant](../db/provisioning/main/02-announcement-runtime-grants.psql) yalnız
  sonraki ayrı ana DB onayı için hazırdır; test onayıyla çalıştırılmayacak.

SQL şu anda **db/review/announcement-migration/** altında, Flyway discovery dışında.
Mevcut Maven target=6 ve V006 kontratı kasıtlı korundu. Kod yeni entity'yi beklediğinden
**bu çalışma ağacını V006 DB üzerinde başlatmak Hibernate validate hatası verir**;
validate kapatılmayacak. Yeni PostgreSQL CI koşusu da V007 kurulum adımı eklenmeden
başarılı kabul edilemez. Eski çalışan jar/servis/DB yapılandırması değiştirilmedi.

### Onay sonrası önce yalnız test DB

1. Açık onayla SQL aynı byte/checksum ile managed-migrations konumuna alınır; target 7,
   şema inspection sürümü 7 ve CI/test kurulum adımları hazırlanır. Uygulanmış SQL'ler
   veya eski kontratlar yeniden yazılmaz.
2. Yalnız `127.0.0.1:5434/campus_plus_test` PG17, V006 history/kontrat, sınırlı migrator,
   eski sekiz tablonun tüm satır/fingerprint'leri ve ACL'leri doğrulanır.
3. Yalnız V007 transaction içinde uygulanır; şema, eski veri/ACL ve yeni boş tablo
   doğrulanır. Yukarıdaki test runtime grant uygulanır; tam yeni kontrat katalogdaki
   tanımların SQL ile incelenmesinden sonra hazırlanır.
4. `contextLoads()` dahil tüm Java testleri, eski Event IT ve yeni 8 Announcement IT,
   Python/kontrat/checksum kontrolleri çalışır. Test fixture'ları yalnız test DB'de
   oluşturulur ve silinmeden bırakılır; ana DB'ye bağlantı/yazma yoktur.
5. Başarı raporu sunulur; **ana campus_plus V007 + main grant için ayrı açık onay** beklenir.
   Ardından Notification aşamasına geçilebilir; V008 için de ayrıca SQL onayı gerekir.
