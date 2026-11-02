# Gönderi yorumları, yanıtlar ve beğeniler

Tarih: 2026-11-02. **V010 ana/test aşamaları ayrı açık onaylarla tamamlandı.**
[Ana doğrulama ve veri sınırları](comments-main-validation.md).
[521 Java /40 Python ve PG doğrulaması](comments-test-validation.md).
[449 izole Java/38 Python sonucu, SQL ve onay noktası](comments-phase1-review.md).
Bu belgenin sonraki adım planları tasarım kaydıdır.
Mevcut kod ve başarılı V009 doğrulaması temel alınmıştır. Bu belge migration,
commit/push veya force push onayı değildir.

## 1. Mevcut temel ve bu aşamanın sınırı

- Başlangıç HEAD `a82300e`; çalışma ağacı temiz. Son push'ın GitHub Actions
  [41333220058](https://github.com/cemsoylemezoglu/campus-plus-backend/actions/runs/41333220058)
  çalışması completed/success olarak salt okunur doğrulandı.
- Post/akış/moderasyon V009 ana/test aşamaları tamamlandı. Son yerel tam doğrulama
  451 Java +38 Python; normal ana uygulamada 49 salt okunur GET başarılıydı.
- `PostPolicy.visible`, `PostWorkspace`, `PostQueryService`, `PostModerationService`,
  `NotificationReader/Writer`, Notification entity/DTO/repository incelendi.
- İlk bütünleşik kaynak **Post**: kök yorumlar, yanıtlar, gönderi ve yorum beğenileri,
  kaynak/ADMIN moderasyonu, kişisel içerik yönetimi ve bildirim entegrasyonu.
- Event ve Announcement yorum/beğeni desteği sonraki kaynak entegrasyonlarıdır;
  mevcut görünürlükleri farklı olduğu için bu aşamada sessizce aynı kural uygulanmaz.
  Gerçek FK ve kaynak policy yaklaşımı korunur; serbest `targetType/targetId` yok.
- Ana verideki pasif üniversiteler/profiller bu aşamada etkinleştirilmez. Yeni
  olumlu senaryolar yalnız ayrıca onaylanmış test DB fixture'larında doğrulanır.

## 2. Görünürlük ve katılım yetkisi

Yeni yorum/yanıt/beğeni için güncel STUDENT rolü ve **normal Post görünürlüğü** gerekir:
aynı aktif üniversite, PUBLISHED ve moderasyonla gizlenmemiş gönderi; kulüp gönderisinde
aktif kulüp, CLUB_MEMBERS kitlesinde güncel ACTIVE üyelik. ADMIN inceleme hakkı veya
kulüp yönetim ekranını görebilmek normal katılım yetkisi yerine geçmez.

Kimlik JWT'den alınır. authorId, userId, universityId, publisher, state, audit veya
bildirim alıcısı istek gövdesinden atanmaz. Yorumlar kişi adına yazılır; kulüp adına
yorum için yeni rol/yetki veya dolaylı temsil modeli eklenmez.

Post gizlenir/kaldırılırsa, üniversite/kulüp pasifleşirse veya okuyucu üyelik/
üniversite koşulunu kaybederse yorum listesi, detay, sayaç ve yeni işlem yolları
aynı görünürlük sınırını uygular. Yok/görünmez kaynak normal uçlarda 404 olur.
Liste filtreleri SQL'dedir; yetkisiz satır getirilip Java'da elenmez.

## 3. Yorum ve yanıt modeli

- Düz metin, trim sonrası 1–3000 karakter; HTML çalıştırılmaz, URL uzaktan getirilmez.
  İçerik, gerekçe, token ve SQL parametreleri loglanmaz; DTO toString gizlenir.
- `CommentStatus`: ACTIVE veya terminal REMOVED. Yazar kendi metnini düzenleyebilir;
  başkasının metnini ADMIN veya kaynak sahibi de yeniden yazamaz.
- Bir kök yorum ve altında sayfalı yanıtlar: **iki seviye**. Yanıtın root_id'si
  gerçek bir kök yorumu göstermeli, aynı post'a ait olmalıdır. Yanıtın yanıtına
  sınırsız zincir veya otomatik sessiz yeniden bağlama yoktur.
- Oluşturma tarihi, source ve root ilişkisi değişmez. Düzenleme sıralamayı değiştirmez.
  Güncelleme/kaldırma/moderasyon beklenen @Version ister; eski sürüm 409.
- Yazarın kök yorumu kaldırması başkalarının yanıtlarını silmez. Görünür yanıt varsa
  kök listede `removed=true, body=null, author=null` placeholder olarak kalır;
  görünür yanıt yoksa kök normal listeden çıkar. Silinmiş köke yeni yanıt eklenemez.
- Moderasyonla gizlenen kökün tüm yanıtları normal görünümden gizlenir. Yazarın
  kaldırdığı kökün mevcut görünür yanıtları ise okunabilir. Bu iki durum ayrıdır.
- Yazar kendi kaldırılmış yorumunu geri getiremez; moderasyon restore da kullanıcı
  silmesini geri alamaz. Body fiziksel satırda korunur; normal placeholder'da sızmaz.
- Yazar, kaynağa erişimi kaybetse de kendi yorum geçmişinden kendi içeriğini görebilir
  ve kaldırabilir. Bu görünüm başka kullanıcıların metnini, güncel gizli Post gövdesini
  veya gizli kulüp ayrıntılarını yüklemez. Düzenleme için güncel normal kaynak yetkisi gerekir.

## 4. Moderasyon yetkileri

İki bağımsız gizleme bayrağı önerilir: `resource_hidden`, `platform_hidden`.
Normal görünürlük her ikisinin false olmasını ister; kök bayrakları yanıtları da etkiler.

| İşlem | Yetki |
| --- | --- |
| Kendi yorumunu düzenleme | Yazar + güncel normal katılım yetkisi; gizli/kaldırılmış olamaz |
| Kendi yorumunu kaldırma | Yazar; kaynak erişimi kaybolsa da kendi geçmişinden |
| RESOURCE hide/restore | Kendi öğrenci gönderisinin sahibi veya ilgili kulübün güncel OWNER/MANAGER + CLUB_ADMIN |
| PLATFORM hide/restore | Yalnız güncel ADMIN |
| Moderasyon geçmişini okuma | ADMIN; kaynak yöneticisine yalnız kendi RESOURCE işlemleri |

RESOURCE işlemi için kaynak hâlâ normal görünür ve aktif olmalı; eski üniversite/
eski yönetici/başka kulüp veya yalnız CLUB_ADMIN marker yetki sağlamaz. Student Post
sahibi başka Post yorumlarını yönetemez. ADMIN platform yolunda ayrı inceleme yapabilir.

RESOURCE restore, PLATFORM bayrağını temizleyemez; yerel yönetici platform kararını
geri alamaz. İki bayrağın bağımsız olması durum önceliği ve gizleme nedenlerinin
karışmasını önler. ADMIN yalnız PLATFORM restore yapınca RESOURCE gizlemesi varsa
içerik hâlâ normal görünmez. Her işlem 1–1000 karakter gerekçeyle audit'e yazılır;
normal okuyucu gerekçe/admin kimliği almaz. Yazar yalnız durumunu öğrenir.
Kaldırılmış kökte görünür yanıtlar kaldıysa kökün moderasyon bayrakları bu yanıt
zincirini gizleyebilir/geri açabilir; restore kökün kaldırılmış body/author alanını
geri getirmez. Bu, terminal yazar kaldırması ile moderasyon durumunu ayrı tutar.

## 5. Beğeni davranışı

- Post ve görünür ACTIVE yorum/yanıt ayrı ayrı beğenilebilir; silinmiş placeholder
  beğenilemez. Kendi içeriğini beğenmek mümkün; kendine bildirim üretilmez.
- Her `(kaynak,user)` çifti için tek kalıcı satır ve UNIQUE vardır. `active` ve
  `version` ile beğen/geri al; yeni satır üreterek geçmiş ve dedupe kimliği sıfırlanmaz.
- **PUT beğen, DELETE geri al**: toggle endpoint yok. Aynı PUT iki kez sayıyı artırmaz;
  aynı DELETE yok/silinmiş satır için de 204. Ağ retry'si ters işlem üretmez.
- Yeni PUT güncel normal kaynak/yorum görünürlüğünü tekrar kontrol eder. Erişim
  kaybolunca eski beğeni varlığı PUT üzerinden görünmez kaynak hakkında bilgi vermez.
- DELETE yalnız aktörün kendi beğenisini geri alır; kaynak gizli/eski üniversitede
  olsa da çalışır. Kaynak veya kişiye ait satır yoksa aynı 204; başkasının userId'si
  hiçbir zaman işleme alınmaz. Dönen yanıt gizli kaynak detayı veya sayacı içermez.
- Katılım yetkisi sonradan kaybolunca geçmiş beğeni otomatik silinmez. Kaynak şu an
  görünürse aktif beğeni sayısı gösterilir; beğenenlerin özel profil listesi açılmaz.
- Sayaçlar denormalize Post sütunu değildir. Sayfada görünen kaynaklar için toplu
  aggregate sorgular kullanılır. Like sayısı aktif satırlardan; yorum sayısı normal
  görünür ACTIVE kök/yanıtlardan gelir, placeholder sayılmaz. Post'un kendi version'ı
  sırf yorum/beğeni yüzünden artırılmaz.

## 6. Önerilen tablolar ve bütünlük

### `post_comments`

BIGINT identity id; NOT NULL post_id FK posts RESTRICT, author_id FK users RESTRICT;
nullable root_id; body VARCHAR(3000) NOT NULL; status VARCHAR(16); resource_hidden
ve platform_hidden BOOLEAN NOT NULL false; created_at/updated_at TIMESTAMPTZ NOT NULL;
removed_at nullable TIMESTAMPTZ; version BIGINT NOT NULL >=0.

CHECK enum/blank/body/time/removal tutarlılığı; CHECK root_id IS NULL OR root_id<>id.
UNIQUE(id,post_id) ve composite FK(root_id,post_id) → post_comments(id,post_id),
RESTRICT: başka Post köküne yanıt DB düzeyinde engellenir. Hedefin kök olma ve
aktiflik koşulu source/root kilidi altında servis katmanında doğrulanır. Sınırsız
recursive constraint/trigger eklenmez. User/Club üzerinde büyük OneToMany yok.

İndeksler: kök akışı (post_id,created_at,id) WHERE root_id IS NULL; yanıt akışı
(root_id,created_at,id); kendi geçmişi (author_id,id); composite FK ve görünürlük
sorgu planına uygun destek indeksleri. Filtreler gerçek PostgreSQL planıyla ölçülür.

### `post_likes` / `post_comment_likes`

BIGINT identity id, source FK RESTRICT, user_id FK users RESTRICT, active BOOLEAN
NOT NULL, created_at/updated_at TIMESTAMPTZ, version BIGINT >=0.
UNIQUE(post_id,user_id) veya UNIQUE(comment_id,user_id). Kaynak sayaçları için aktif
partial indeks, user geçmişi için user_id destek indeksi. Hard DELETE endpoint yok;
HTTP DELETE yalnız active=false yapar. Hesap kalıcı silme ayrı yaşam döngüsüdür.

### `comment_moderation_actions`

Identity id; comment_id ve actor_id gerçek FK RESTRICT; scope RESOURCE/PLATFORM;
action HIDE/RESTORE; reason VARCHAR(1000); created_at; resulting_version >=0.
UNIQUE(comment_id,resulting_version). Append-only runtime SELECT/INSERT; UPDATE/
DELETE/TRUNCATE yok. Gizleme ve audit aynı transaction'dadır; audit hatası ikisini
birden rollback eder. Body kopyası audit'e yazılmaz.

JPA ilişkileri LAZY ManyToOne, cascade REMOVE yok. User'ın eager roles koleksiyonunu
sayfa başına her satırda yüklememek için scalar DTO projection ve toplu sorgu kullanılır.
Yorum/like entity'leri doğrudan serialize edilmez.

## 7. REST ve DTO sözleşmesi

| Yol | İşlem |
| --- | --- |
| POST /api/posts/{postId}/comments | Yeni kök yorum |
| GET /api/posts/{postId}/comments | Görünür kökler ve gerekli placeholder'lar |
| GET /api/posts/{postId}/comments/{id} | Görünür yorum/yanıt detayı |
| POST /api/posts/{postId}/comments/{rootId}/replies | Kök altında yeni yanıt |
| GET /api/posts/{postId}/comments/{rootId}/replies | Sayfalı görünür yanıtlar |
| PATCH /api/posts/{postId}/comments/{id} | Yazarın kendi metnini düzenlemesi |
| GET /api/me/comments | Kendi içerik geçmişi; gizli kaynak ayrıntıları olmadan |
| POST /api/me/comments/{id}/remove | Yazarın version kontrollü terminal kaldırması |
| POST /api/posts/{postId}/comments/{id}/hide veya /restore | RESOURCE moderasyonu |
| POST /api/admin/comments/{id}/hide veya /restore | PLATFORM moderasyonu |
| GET /api/admin/comments ve /{id} | Filtreli ADMIN incelemesi |
| GET /api/admin/comments/{id}/moderation-actions | ADMIN audit listesi |
| GET /api/posts/{postId}/comments/{id}/moderation-actions | Yalnız RESOURCE audit |
| PUT /api/posts/{postId}/like | İdempotent beğen |
| DELETE /api/posts/{postId}/like | Yalnız kendi beğenisini geri al, 204 |
| PUT /api/posts/{postId}/comments/{id}/like | Yorum/yanıt beğen |
| DELETE /api/posts/{postId}/comments/{id}/like | Yalnız kendi yorum beğenisini geri al, 204 |
| GET /api/posts/{postId}/interactions | Görünür kaynağın comment/like count ve likedByMe bilgisi |

CreateCommentRequest(body), UpdateCommentRequest(version,body),
CommentActionRequest(version), ModerateCommentRequest(version,reason).
Like PUT/DELETE version veya istemci userId istemez; işlem kaynak kilidi ve UNIQUE
ile sıralanır. Cross-post yorum/yanıt oluşturma, like PUT veya kaynak yönetim IDOR
denemeleri 404/403 olur. Like DELETE'te post/comment eşleşmiyorsa satır değiştirilmeden
aynı 204 döner; yanlış Post yolu başka bir kendi beğenisini de etkileyemez.

CommentResponse: id/postId/rootId, güvenli author id/name, body, timestamps, version,
removed, replyCount, likeCount, likedByMe. Placeholder'da body/author null; gerekçe,
roller, e-posta ve parent'ın özel içeriği yok. OwnCommentResponse yalnız kendi
metin/geçmiş durumunu verir. Yönetim DTO'ları/audit normal DTO'lardan ayrılır.

Kök ve yanıt listeleri createdAt/id ASC keyset cursor, size 1–100 ve size+1 kullanır.
Cursor source post ve root filtresine bağlıdır; yetki belgesi değildir. İlk sayfa ve
cursor'lu sorgu ayrı olur: V009'da gerçek PG testiyle bulunan nullable timestamptz
parametre hatası tekrarlanmaz. Cursor sıralama alanları değişmez; sonradan gizlenen
kayıtlar sonraki sorguda görünmez. Kendi/admin geçmişi mevcut PageResponse ile sayfalanır.

Başarılı kişisel yanıt no-store; 401 authentication, 403 bilinen yönetim yetkisi,
404 görünmeyen normal kaynak, 400 validation, 409 stale version/yaşam döngüsü.

## 8. Kilit ve transaction sırası

Yorum/yanıt/beğeni yazıcıları:
actor NO KEY UPDATE → varsa club SHARE → university SHARE → post WRITE →
varsa root comment WRITE → varsa reply comment WRITE → kendi like satırı.
Birden fazla user/comment gerekiyorsa ID ASC. Preflight erken hatadır; kilit sonrası
profil, rol, üyelik, post ve root durumu tekrar okunur. User refresh kilidi yükseltilmez.

Ortak post WRITE, aynı kaynakta publish/remove/moderation ile yorum/like/root işlemlerini
sıralar. Post değiştirilmedikçe @Version artırılmaz. Bu bilinçli başlangıç tercihidir:
tek popüler kaynakta yazma throughput'u sınırlayabilir; ölçüm olmadan kuyruk/cache/
distributed lock eklenmez. Başka Post'lar birbirini bu kilitle engellemez.

Own-remove ve unlike, kaynak görünürlüğü kaybolsa da aynı kilit sırasıyla yalnız
kendi satırını değiştirir. Kaynak kilidinden sonra recipient user kilidine dönülmez;
Notification FK KEY SHARE ile kullanıcı NO KEY UPDATE uyumu korunur.

Post/Club arşivi yeni yorumları kilitte durdurur. Eski yorum/like satırlarını topluca
yeniden yazmaya gerek yok: bütün okumalar kaynak policy'siyle görünürlüğü kapatır.
Üniversite/profil veya üyelik kaybı da dinamik okuma/işlem kontrolünde etkili olur.

## 9. Bildirim entegrasyonu ve kritik mevcut varsayım

Mevcut NotificationReader `if(c!=null) switch(...)` kullanıyor ve target oluştururken
club.getId() çağırıyor. Öğrenci Post'unda club=null olduğundan yalnız yeni enum eklemek
yeterli değildir. Yeni POST/POST_COMMENT dalları kulüpsüz çalışmalı; mevcut beş kaynak
grubu kendi club zorunluluğunu korumalı. Bu genişletme eski bildirim testleriyle sınanır.

Yeni tipler: POST_COMMENTED, COMMENT_REPLIED, POST_LIKED, COMMENT_LIKED.
Notification'a nullable post_id ve comment_id gerçek FK eklenir; comment varsa
composite FK(comment_id,post_id) aynı kaynağı zorunlu kılar. Eski tiplerde bu alanlar
NULL olur; eski satırlar backfill ile değiştirilmez. Type/source CHECK genişler.

- Yeni kök: öğrenci Post sahibine; kulüp Post'unda güncel uygun OWNER/MANAGER'lara.
- Yeni yanıt: uygun kök yorum yazarına ve kaynak alıcılarına. Aynı alıcıya aynı
  yorum olayı için tek notice: REPLIED öncelikli, source_key `COMMENT_CREATED:id`.
- İlk beğeni: öğrenci içerik yazarına veya kulüp kaynağında uygun yöneticilere;
  yorum beğenisinde yorum yazarına. Aktör kendine notice almaz.
- Beğen → geri al → tekrar beğen spam'i yeni inbox üretmez: kalıcı like satırı
  kimliğiyle first-ever source_key. Edit, unlike veya sıradan moderasyon toplu yayın
  bildirimi üretmez. İlk like notice geçmiş olayıdır; unlike olunca kaynak hâlâ
  görünürse geçmiş bildirimi okunabilir, yeniden like ikinci notice yaratmaz.
- Alıcıda güncel kaynak görünürlüğü olmalı; tüm üniversiteye dağıtım yok. Yeni
  bildirim insert'i yorum/like transaction'ına MANDATORY katılır; hata tam rollback.

Reader kaynak/root moderasyonunu ve güncel profile/membership'i her okumada kontrol
eder. Artık erişilemiyorsa mevcut generic shell available=false,target=null; body,
author adı, gerekçe ve gizli ID yok. Target DTO mevcut alanları koruyup nullable
postId ekler; POST_COMMENT resource id'si commentId, POST resource id'si postId olur.
Toplu context/entity graph/projection ile N+1 engellenir; satır başına kaynak lookup yok.

## 10. Migration ve geliştirme sırası

1. Comment/Like entity, DTO, policy, workspace, repository/service/controller;
   iki seviyeli görünürlük/placeholder ve kaynak/ADMIN moderasyonu.
2. İdempotent beğeni/geri al, toplu sayaçlar; mevcut Post DTO sözleşmesini bozmayacak
   ayrı interaction endpoint'i. Sonra transactional Notification entegrasyonu.
3. İzole güvenlik/MVC/HQL testleri ve PostgreSQL IT hazırlığı. **V010 adayı** review
   dizininde hazırlanır: dört yeni tablo/sequence, notification nullable FK/index ve
   yeni CHECK. V001–V009 değişmez. Yeni nesne grant'leri minimum; audit INSERT/SELECT,
   comment/like SELECT/INSERT/UPDATE; DELETE/TRUNCATE/sequence reset yok.
4. Kesin SQL/SHA ve mevcut notifications tablosundaki ALTER kilidi/constraint
   doğrulama maliyeti sunulur. Bu tasarım SQL uygulama onayı değildir.
5. Ayrı açık **test DB** onayı → exact PG17 loopback:5434/history/kontrat/aktif istemci/
   eski on iki tablonun row/ACL fingerprint'i → transaction migration → grant/kontrat
   → contextLoads dahil tam testler ve gerçek yarışlar.
6. Başarıdan sonra ayrı açık **ana DB** onayı. Ana geçişte fixture yok, yalnız GET/
   Hibernate/Flyway/şema/veri/ACL doğrulaması. Commit/push ayrıca istenir, force push yok.

Başlangıçta hiçbir SQL veya servis ayarı değiştirilmez. Yeni entity'ler eklendikten
sonra V010 olmayan DB'de validate başarısız olur; bu sınır gizlenmez veya validate
kapatılmaz. CI/Maven hedefi ve kontrat yalnız uygulama paketiyle kontrollü güncellenir.

## 11. Gerekli test matrisi

- Yanlış author/source/root, cross-post reply/like, reply-of-reply, marker-only ve
  başka kulüp yönetimi; normal ADMIN görünürlük bypass'ı olmaması; service doğrudan çağrı.
- Üyelik/üniversite/rol kaybı, pasif kulüp/üniversite, Post platform hide/remove,
  root hide ve owner-remove placeholder; liste/count/cursor/body/author sızıntısı.
- RESOURCE restore PLATFORM bayrağını kaldıramaz; moderatör başkasının body'sini
  düzenleyemez; audit private/append-only, audit insert hatasında tam rollback.
- Like PUT/PUT, PUT/DELETE, tekrar DELETE, re-like spam dedupe; kendi kendine notice yok;
  root author ile source alıcısı aynıysa tek reply notice; yanlış recipient/target yok.
- Reply/root-remove, reply/root-hide, edit/moderate, like/Post-remove, comment/member-leave,
  comment/profile-change ve club-archive yarışları; son durum ve kilit sırası doğrulaması.
- Composite FK/CHECK/UNIQUE/limited-role gerçek SQLSTATE; yeni bildirim kaynak FK'leri,
  eski inbox/read/read-all/unread ve mevcut 23 tip regresyonları.
- 100+ kök/yanıt/like fixture'ında sınırlı sorgu sayısı, scalar author projection,
  partial indeks EXPLAIN ve sayfa sınırları; placeholder root boş sayfa üretmez.
- Mevcut 451 Java/38 Python baseline korunur; bu tasarım oturumunda yeniden DB testi
  çalıştırılmadı. Yeni test sonucu ancak kod uygulaması ve onaylı test DB sonrası verilir.
