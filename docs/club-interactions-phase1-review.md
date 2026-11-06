# Etkinlik ve duyuru yorum/beğeni aşaması: kod ve V011 incelemesi

> Bu belge onay öncesi kod/SQL incelemesinin tarihsel kaydıdır. Sonrasında kullanıcı
> yalnız test hedefini açıkça onayladı; V011 aynı byte'larla managed dizinine alındı.
> Güncel 624 Java/44 Python sonucu ve ayrı ana DB onay noktası
> [test doğrulama raporundadır](club-interactions-test-validation.md).

Tarih: 2026-11-06. **Kod aşaması hazır; gerçek PostgreSQL doğrulaması bekliyor.**
Bu oturumda hiçbir veritabanı bağlantısı açılmadı, SQL uygulanmadı, commit/push yapılmadı.

## Hazır olan davranış

- Event ve Announcement için kök yorum, tek seviyeli yanıt, yazar düzenlemesi,
  terminal kaldırma, görünür yorum ve beğeni sayaçları, kaynak/thread bağlı cursor.
- Kaynak/yorum beğenilerinde idempotent PUT; DELETE soft unlike ve 204. Erişim
  kaybında yalnız kendi geçmişi/kaldırma/unlike çalışır. Yanlış kaynakla DELETE
  herhangi bir satırı değiştirmeden 204 verir.
- Aynı üniversite/STUDENT yazma koşulları; özel kaynakta güncel ACTIVE üyelik.
  Public Event okumasının daha geniş kitlesi mevcut sözleşmeyle korunur.
- CANCELLED Event'te yeni sosyal yazım kapalı; mevcut tartışma ve yerel moderasyon
  açık. COMPLETED Event tartışmaya açık. Draft/withdrawn/archive/pasif kaynak normal
  sosyal erişime kapalı; yönetici detail bypass'ı yeni etkileşimlere taşınmaz.
- RESOURCE moderasyonu yalnız doğru kulübün ACTIVE OWNER/MANAGER + CLUB_ADMIN
  yetkilisinde. PLATFORM moderasyonu ayrı ADMIN yolunda. Bayraklar bağımsız;
  yerel mutation sonucu metin/yazar/gerekçe içermez, history SQL'de scope filtreler.
- Kaldırılmış kök, görünür yanıtlar varsa metinsiz ve yazarsız placeholder olur;
  yeni yanıt alamaz. Moderasyon gizlemesi thread'i normal okuyucudan kapatır.
- Transaction içi bildirimler; uygun yerel yöneticiler, reply için kök yazarı,
  yorum beğenisinde yorum yazarı. Aktör hariç, aynı alıcıya reply öncelikli tek
  notice; re-like yeni notice üretmez. Üniversite çapında fan-out yok.
- Bildirim okuyucu üyelik/kaynak/root moderasyonunu yeniden kontrol eder. Yeni
  sosyal like türleri eski yönetici draft görünürlüğünü miras almaz. Announcement
  hedefi bağlı özel Event'in kimliğini taşımaz. Eski bildirim sözleşmeleri korunur.

Kilit sırası: actor NO KEY UPDATE → club SHARE → source WRITE → university SHARE
→ root WRITE → reply WRITE → like. Announcement etkileşimi bağlı Event kilidi
almaz. Yetki ve kaynak durumu kilitlerden sonra tekrar kontrol edilir. Cleanup
yolu yalnız yazar/kişisel like veya ayrı ADMIN doğrulamasından sonra kullanılır.

## Kod/dosya grupları

- `entity/ClubComment`, `ClubContentLike`, `ClubCommentLike`, `ClubCommentModeration`.
- `repository/ClubCommentRepository`, `ClubContentLikeRepository`,
  `ClubCommentLikeRepository`, `ClubCommentModerationRepository`,
  `ClubInteractionPredicates`. Scalar projection ve toplu aggregate kullanılır;
  nullable Event/Announcement üniversite bağları açık LEFT JOIN'dir.
- `dto/clubinteraction/*` ve `service/clubinteraction/*`: kaynak adapter/policy
  bağlantısı, cursor, workspace, query/write/like/moderation servisleri.
- EventComment/Like, AnnouncementComment/Like, OwnClubComment ve
  ClubCommentModeration controller'ları.
- `ClubCommentNotificationWriter`; mevcut Notification entity/type/resource,
  repository graph, reader ve Target DTO'suna sınırlı eklemeler. Target'a nullable
  announcementId eklendi; eski alanlar korunuyor.
- Salt okunur schema inspector V011 tablo listesini tanır; migration çalıştırmaz.
  Eski V001–V010 SQL'leri/kontratları ve Post API/servisleri değiştirilmedi.

## API yolları

`/api/events/{id}` ve `/api/announcements/{id}` altında:

| Yol | Method |
| --- | --- |
| `/comments` | GET, POST |
| `/comments/{commentId}` | GET, PATCH |
| `/comments/{rootId}/replies` | GET, POST |
| `/comments/{commentId}/like` | PUT, DELETE |
| `/comments/{commentId}/hide`, `/restore` | POST |
| `/comments/{commentId}/moderation-actions` | GET |
| `/like` | PUT, DELETE |
| `/interactions` | GET |

Kişisel geçmiş `/api/me/club-comments`, detay `/{id}`, kaldırma `/{id}/remove`.
Platform inceleme `/api/admin/club-comments`, detay `/{id}`, hide/restore/history.
Post'un mevcut `/api/me/comments` ve `/api/admin/comments` yolları aynı kalır.

## Çalıştırılan doğrulama ve sınırları

- **540 izole Java başarılı**, 0 failure/error/skipped. Önceki 514'e 14 JWT/MVC/
  servis ve 12 bildirim/model testi eklendi. Hibernate gerçek entity metadata'sıyla
  repository HQL'lerini bağlantı kurmadan derledi; mevcut modül testleri de geçti.
- **44 Python başarılı**: önceki 40'a eski notification CHECK dalları/type kümesi,
  review/discovery sınırı, eski satırları değiştirmeyen SQL ve dar grant kontrolleri.
- Test hazırlığında Role import ve JWT issuer/audience/saat fixture sorunları
  düzeltildi. Son incelemedeki nullable üniversite join düzenlemesinden sonra tam
  izole Java koşusu tekrar başarılı oldu.
- **12 yeni PostgresClubInteractionIT testi derlendi, henüz çalıştırılmadı.**
  Kapsam: SQL/cursor/placeholder, inbox önceliği/gizlilik, source ve comment like
  yarışları, root-remove/reply ve source-cancel/withdraw yarışları, üyelik kaybı,
  üniversite sınırı, gerçek writer/audit rollback, composite FK/CHECK, ACL ve
  100 satırlık bounded query sayısı. Bunlar başarı iddiası değildir.
- contextLoads ve eski PostgreSQL/API testleri bu oturumda yeniden çalıştırılmadı;
  son tam V010 sonucu tarihsel olarak 521 Java/40 Python'dur.
- SQL gerçek PostgreSQL parser/DDL üzerinde henüz doğrulanmadı. Offline testler
  gerçek constraint, kilit yarışı ve transaction rollback testlerinin yerini almaz.

**Başlangıç sınırı:** yeni entity'ler ve notification referansı V011 ister. Bu yeni
kodu henüz V010 veritabanına karşı başlatmamalıyız; validate eksik tabloları/sütunu
reddeder. `ddl-auto=validate` korunur, update/create'a çevrilmez. Mevcut veritabanı
ve servisler değiştirilmedi. Test/ana geçişleri tamamlanmadan bu çalışma ağacı
deploy/push için tamamlanmış sayılmaz.

## Kesin V011 SQL ve etkisi

[İncelenen SQL (onay sonrası taşındığı konum)](../db/managed-migrations/V011__add_club_content_interactions.sql)
Flyway discovery dışındadır. SHA-256:

`2b64de742ad7425e777a40d32eac32dcb053749dd70ff56cb0a9037fc6181f5d`

- Dört yeni tablo ve dört identity sequence: `club_comments`, `club_content_likes`,
  `club_comment_likes`, `club_comment_moderation_actions`. Kaynaklar gerçek FK'dir;
  CHECK tam bir Event/Announcement kaynağı ister. Root için doğrudan ve iki
  composite FK aynı kaynak/tür bütünlüğünü korur. Fiziksel cascade silme yok.
- Mevcut `notifications` üzerine tek nullable `club_comment_id`, üç FK ve bir
  partial index eklenir. Type CHECK eski 27 türü koruyup sekiz tür ekler. Source
  CHECK eski dalları aynen, yeni alanın NULL koşuluyla korur; yeni dallar kaynak
  türlerini ve comment/like ayrımını sınırlar. UPDATE/backfill yok.
- User/role/password/university/profile/club/event/announcement/Post/yorum kayıtları
  silinmez veya yeniden yazılmaz. Mevcut bildirimlerin eski kolon değerleri aynı
  kalır; eklenen nullable kolon eski kayıtlarda NULL olur.
- Guard PG17, 127.0.0.1:5434 ve Campus+ database adlarını zorunlu tutar; V010 başarılı
  geçmişi ister. Flyway tüm V011'i transaction içinde uygular. Lock timeout 5 sn,
  statement timeout 30 sn. Hata/timeout halinde durulur; otomatik destructive
  düzeltme veya tekrar denemeyle veri değişikliği yapılmaz.
- notifications ALTER/CHECK doğrulaması mevcut tablo üzerinde güçlü kilit ve
  tarama gerektirebilir. Yoğun trafik veya açık transaction varsa uygulama kısa
  süre bekleyebilir/timeout alabilir. Onay bu operasyonel etkiyi de kapsamalıdır.

Grant dosyaları ayrı hazırlanmıştır:

- [Test grant (onay sonrası konumu)](../db/provisioning/test/09-club-interaction-runtime-grants.psql), SHA-256
  `274213e636d29e40589ece3e29efa1b6ff12e147c8550451dc8e1a2bd03a3c1b`.
- [Ana grant](../db/provisioning/main/06-club-interaction-runtime-grants.psql), SHA-256
  `4a41f82c66e193ad4da27a9da1fc59e2e7d3e8396a8f667f027b5526da5521cd`.

Yalnız üç yeni etkileşim tablosunda SELECT/INSERT/UPDATE; audit tablosunda
SELECT/INSERT; dört yeni sequence üzerinde USAGE/SELECT. DELETE, audit UPDATE,
TRUNCATE, sequence reset veya history write yok. Mevcut nesnelerin ACL'leri
genişletilmez. Test runner ve ana runtime için ayrı hedef/rol guard'ları vardır.

## Bir sonraki işlem için istenen onay ve plan

**Şimdi yalnız `127.0.0.1:5434/campus_plus_test` için V011, yukarıdaki test grant'i
ve ayrı yeni fixture'larla test çalıştırma onayı istenir. Ana DB onayı istenmez.**

1. Kimlik/PG17/port/database/owner/rol ve V010 şema/history/checksum doğrulaması;
   beklenmeyen tablo/bağlantı/sürüm durumunda uygulamadan dur.
2. Eski 16 tablonun mevcut ID/değer/ACL/history fingerprint'lerini al. Yeni fixture
   ID'leri eski kapsamı genişletmez. Notifications için eski kolon projeksiyonu ve
   eski ID'lerde yeni alanın NULL olması ayrı doğrulanır.
3. Onaylanan SQL'i byte değiştirmeden managed dizinine al; hedefi kontrollü olarak
   11'e geçir ve yalnız test hedefinde Flyway transaction migration çalıştır.
4. SQL'e göre yeni tablo/kolon/FK/UNIQUE/CHECK/index/sequence'leri ve eski şemada
   yalnız onaylanan notification farklarını doğrula; V011 kontratını çıkarıp
   incele. Başarıdan sonra yalnız test dar grant'ini uygula; tam ACL karşılaştır.
5. contextLoads dahil tüm eski/yeni PostgreSQL testlerini, Java/Python testlerini
   çalıştır. Test fixture'ları yeni hesap/üniversite/kulüp/etkinlik/duyurulardır;
   eski kayıtlar test amacıyla güncellenmez/silinmez. Constraint kontrolleri yalnız
   yeni fixture üzerinde transaction rollback ile yapılır; yeni fixture'lar kalır.
6. Eski kayıt/ACL/history korunumu ve V011 şema/Flyway/Hibernate validate sonucunu
   raporla. Başarı halinde ana DB için aynı SQL/dar yetkilerle **ayrı açık onay** iste.
   Ana DB onayı olmadan uygulama, ana DB test fixture'ı veya servis değişikliği yok.

CI/empty-install V011 bağlantısı test geçişinde güncellenecek; mevcut Maven/CI
migration hedefi hâlâ 10. Commit/push ayrıca açıkça istenir; force push yok.
