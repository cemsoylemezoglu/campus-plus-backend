# Event ve Announcement yorum/beğeni kaynak entegrasyonu

Tarih: 2026-11-02. **Tasarım ve ilk policy/test aşaması hazır.**
ClubInteractionPolicy +27 test eklendi; 476 izole Java/40 Python başarılı.
Henüz yeni endpoint/entity/repository veya SQL yok; DB bağlantısı açılmadı.
Policy mevcut endpoint'lere bağlanmadığı için bu adım normal API davranışını değiştirmez.
Post etkileşimleri V010 ana/test ile tamamlandı. Bu belge sonraki kaynak entegrasyonudur;
Post davranışını veya eski migration'ları değiştirme onayı değildir.

## 1. Kodda doğrulanan farklar

- EventPolicy.visible yöneticiye taslak erişimi sağlar. Normal UNIVERSITY_STUDENTS
  etkinlik görünürlüğü aynı üniversite profilini her durumda aramaz; eski Event
  keşif/detail sözleşmesi Post'tan farklıdır. Bu fark mevcut davranıştır, burada
  yeni tespit edilmiş bir üretim güvenlik açığı olarak raporlanmaz.
- AnnouncementPolicy.visible de yönetici/ADMIN için taslak bypass içerir. Normal
  okuyucuda PUBLISHED, arşivsiz, aktif kulüp/üniversite, aynı üniversite profili ve
  gerektiğinde ACTIVE kulüp üyeliği ister.
- Post yorum/like workspaceleri Post entity/repository'sine bağlıdır. Bunları
  tüm kaynaklarda kullanmak veya policy.visible sonucunu tek başına yeni yazma
  yetkisi saymak, taslak ve üniversite sınırlarını yanlış taşıyabilir.
- EventWorkspace mevcut yazma sırası user → club SHARE → event WRITE → university
  SHARE şeklindedir. AnnouncementWorkspace bağlantılı event'leri ID ASC, sonra
  announcement WRITE, ardından university SHARE alır. Yeni etkileşimler bu sırayla
  uyumlu olmalı; Post'un university→post sırası buraya aynen kopyalanmamalıdır.
- Son tam test 521 Java/40 Python, ana 72 GET başarılı. Bu tasarım adımında
  DB işlemi yapılmadı. Yeni policy sonrası izole test sonucu aşağıdadır.
  V010 değişiklikleri henüz commit/push edilmedi.

## 2. Kapsam ve değişmeden kalacak sözleşmeler

Event ve Announcement için kök yorum/yanıt, source/comment like, kişisel geçmiş,
RESOURCE/PLATFORM moderasyonu, sayaçlar, cursor, inbox ve dinamik gizlilik.
V010'daki iki seviyeli yanıt, terminal yazar kaldırması, metinsiz placeholder,
bağımsız moderasyon bayrakları ve idempotent PUT/DELETE davranışları korunur.

Post tabloları, endpoint/DTO/source_key sözleşmeleri ve mevcut Post servisleri
genelleştirilip yeniden yazılmaz. Yeni kulüp etkileşimleri ortak bir alt modüldür.
Mevcut ContentLike mapped superclass, CommentStatus/ModerationScope/Action,
request validation ve güvenli DTO/log yaklaşımı tekrar kullanılır. Kaynak politikası,
kilit ve scalar repository sorguları yeni alt modülde açıkça tanımlanır.
Yeni framework, generic JPA inheritance ağı veya serbest targetType/targetId yok.

## 3. Normal okuma, katılım ve yönetim ayrımı

Normal etkileşim okuyucusu yalnız aşağıdaki kaynak durumlarını görür; kaynak detay
API'sindeki yönetici bypass'ı burada uygulanmaz. Görünmeyen kaynak 404.

| Kaynak durumu | Etkileşim okuma | Yeni yorum/yanıt/like |
| --- | --- | --- |
| Event DRAFT | Hayır | Hayır |
| Event PUBLISHED, arşivsiz | Normal kitleye uygun | Katılımcı koşullarıyla evet |
| Event COMPLETED, arşivsiz | Normal kitleye uygun | Etkinlik sonrası tartışma için evet |
| Event CANCELLED, arşivsiz | Mevcut tartışma okunabilir | Hayır |
| Event archived veya pasif kaynak bağlamı | Hayır | Hayır |
| Announcement DRAFT/WITHDRAWN/archived | Hayır | Hayır |
| Announcement PUBLISHED, arşivsiz | Normal kitleye uygun | Katılımcı koşullarıyla evet |

Aktif kaynak bağlamı: club ACTIVE ve university.active. Taslak yönetimini normal
yorum listesine açmak veya pasif kaynaktaki metni yönetici rolüyle döndürmek yok.
ADMIN ayrı inceleme endpoint'inden görebilir; bu, sosyal katılım izni değildir.

- Event UNIVERSITY_STUDENTS normal etkileşim okuması mevcut geniş keşif yaklaşımını
  korur: aynı üniversite şartı okuma için eklenmez. Yeni etkileşim yazımı ise
  mevcut kayıt/katılım modelindeki gibi STUDENT, tamamlanmış aktif profil ve kaynağın
  üniversitesiyle eşleşme ister. Böylece görüntüleme ve katılım ayrılır.
- Event CLUB_MEMBERS okuma/yazımı, aynı kaynak kulübünde ACTIVE üyelik ve güncel aynı
  üniversite profili ister. Üniversite/üyelik değişince sonraki okumada erişim kapanır.
- Announcement normal okuması kendi üniversitesiyle sınırlı kalır; CLUB_MEMBERS
  ayrıca güncel ACTIVE üyelik ister. Yeni yazımda STUDENT de zorunlu.
- Bu kurallar mevcut Event/Announcement detail/discovery veya bilet sözleşmelerini
  değiştirmez. Yeni InteractionPolicy ve SQL predicate'leri bu açık sözleşmeyi uygular.
- Kaynak iptal/geri çekme/arşivi yorum satırlarını fiziksel silmez. Önceden yazılmış
  kendi yorumu kaldırma ve kendi beğenisini geri alma erişim kaybında da çalışır.
- Kaynak iptalinde CANCELLED geçişiyle yeni yazma yarışı aynı source kilidinde
  sıralanır. Tamamlanma, kayıt kapanışı veya check-in durumu yorum sırasını değiştirmez.

## 4. Yönetim ve içerik gizliliği

RESOURCE hide/restore yalnız ilgili kulübün güncel ACTIVE OWNER/MANAGER + CLUB_ADMIN
yetkilisine ait. Kaynağın eski createdBy kimliği, normal üye veya yalnız marker rolü
yetmez. İlgili kaynak normal etkileşim okumaya açık olmalı. CANCELLED Event'te yeni
yorum kapalı olsa da mevcut tartışmanın yerel moderasyonu mümkündür.

PLATFORM hide/restore yalnız ADMIN; kaynak gizliyken de ayrı admin incelemesiyle
yapılabilir. İki bayrak bağımsız kalır; yerel restore platform gizlemesini kaldıramaz.
Yerel mutasyon yanıtı body/author/gerekçe içermez. RESOURCE history yalnız bu scope'u
SQL'de filtreler; PLATFORM gerekçesi/count'u yerel yöneticiye sızmaz.

Yazar kendi metnini ancak güncel katılım yetkisiyle düzenler; yeni yazım kapalıysa
edit de kapalıdır. Kaynak erişimi kaybolsa bile kendi geçmişinden terminal kaldırma
yapabilir. Own history yalnız kendi metni/geçmiş source kimliğini verir; gizli kaynak
başlığı, bağlantılı etkinlik metni veya başka kişinin yorumunu getirmez.

Kaldırılan kök, görünür yanıtlar için body=null,author=null placeholder'dır; yeni
yanıt kabul etmez. Moderasyon gizlemesi bütün thread'i normal okuyucudan gizler.
Removed kökün flag restore'u metnini/kimliğini geri getirmez. Sınırsız reply-of-reply
yok. Sayaçlar normal görünür ACTIVE yorumları sayar, placeholder saymaz.

## 5. Önerilen ek şema — V011 adayı

### club_comments

PostComment'teki state/time/version/body/author/root alanlarıyla aynı davranış,
fakat kaynak için nullable event_id ve announcement_id gerçek FK'leri bulunur.
CHECK ile **tam biri NOT NULL**. Source, author ve root ilk kayıttan sonra değişmez.
Kaynak discriminator API'de EVENT veya ANNOUNCEMENT enum'udur; FK yerine geçmez.

UNIQUE(id,event_id), UNIQUE(id,announcement_id); root_id için doğrudan FK ve
composite FK(root_id,event_id), FK(root_id,announcement_id). Bir kaynak sütunu her
zaman dolu olduğu için aktif composite FK yanıtın aynı türde/aynı kaynakta kalmasını
DB düzeyinde sağlar. Root'un gerçekten kök/aktif olma koşulu kaynak kilidi altında
serviste kontrol edilir; self-root CHECK vardır. Cascade REMOVE veya trigger yok.

Her kaynakta kök created_at/id partial indeksleri; root replies, author geçmişi ve
FK destek indeksleri. Nihai sayılar ve SQL kod aşamasında incelenir.

### club_content_likes

ContentLike alanları + nullable event_id/announcement_id, tam biri dolu CHECK;
UNIQUE(event_id,user_id), UNIQUE(announcement_id,user_id). NULL'lu diğer sütunun
unique davranışı bu CHECK ile birlikte değerlendirilir. Tek kalıcı satır/active/
version ile idempotent PUT/DELETE; kullanıcı ilişkisi gerçek RESTRICT FK.

### club_comment_likes / club_comment_moderation_actions

Gerçek club_comment_id/user veya actor FK'leri; V010'daki unique source-user,
append-only scope/action/reason/resulting_version ve dar runtime hakları korunur.
Entity ve tablo adları Post history/audit kimlikleriyle karıştırılmaz.

### notifications genişletmesi

Mevcut event_id/announcement_id kullanılır; nullable club_comment_id eklenir.
Comment varsa ilgili source ile composite FK'ler aynı kaynağı doğrular. Sekiz yeni
type ve iki yeni comment resource türü önerilir:

- EVENT_COMMENTED / EVENT_COMMENT_REPLIED / EVENT_LIKED / EVENT_COMMENT_LIKED;
- ANNOUNCEMENT_COMMENTED / ANNOUNCEMENT_COMMENT_REPLIED / ANNOUNCEMENT_LIKED /
  ANNOUNCEMENT_COMMENT_LIKED.

Eski 27 type/source kombinasyonu korunur, yeni alanı NULL gerektirir. Yeni dallarda
club_id zorunlu ve tam bir kaynak doludur; Post/comment/participation/membership
alanları birbirine karışmaz. Migration eski satırları UPDATE/backfill yapmaz.
Fingerprint eski notifications kolon projeksiyonuyla + yeni alanın eski ID'lerde
NULL kalmasıyla doğrulanır. Type/source CHECK taraması/kilidi onay raporunda açıklanır.

## 6. Servis, API ve sayfalama

Yeni ClubInteractionWorkspace yalnız iki açık türü çözümler: EventSource ve
AnnouncementSource. Her adapter gerçek entity/repository ve normal interaction
policy'sini kullanır. Genel SQL tablo/kolon adı veya hedef kullanıcı istemciden alınmaz.

Kaynak yolları `/api/events/{id}` ve `/api/announcements/{id}` altında:
`/comments`, `/comments/{commentId}`, `/comments/{rootId}/replies`,
`/comments/{id}/like`, `/comments/{id}/hide|restore|moderation-actions`,
`/like`, `/interactions`. HTTP method ve idempotence V010 ile aynı.

Kendi geçmişi `/api/me/club-comments`; kaldırma `/api/me/club-comments/{id}/remove`.
ADMIN inceleme/moderasyon `/api/admin/club-comments`. Post `/api/me/comments` ve
`/api/admin/comments` sözleşmesi değişmez; farklı ID namespace'leri karıştırılmaz.

Yeni yanıt DTO'su source={kind,id} içerir, postId adı Event kimliği için kullanılmaz.
Like normal okuyucuya yalnız liked durumunu verir; unlike gizli kaynakta da 204 ve
body yok. Kaynak türü/id uyuşmazlığında DELETE hiçbir satırı değiştirmeden aynı 204;
PUT/create/read normal 404/403 korumasını uygular.

Cursor source kind+id+rootId+createdAt+id'ye bağlı sürümlü sabit biçimdir. Event 7 ve
Announcement 7 cursor'ları aynı sayılmaz. İlk/cursor'lu sorgu ayrı; nullable zaman
parametresi IS NULL sorunu tekrarlanmaz. Liste ve sayaç filtreleri SQL'de; scalar
author projection ve sayfa başına toplu aggregate, size 1–100, no-store.

## 7. Kilit sırası ve yaşam döngüsü

Yeni yazıcı sırası user NO KEY UPDATE → club SHARE → **source WRITE** → university
SHARE → root WRITE → reply WRITE → kendi like satırı. Event/Announcement'ın mevcut
yazma sırasıyla uyumludur. Post akışının sırası değiştirilmez; yeni kaynak yazıcısı
Post kilidi almaz. Böylece mevcut kaynak/üniversite sırasını tersine çeviren yeni
kenar eklenmez. Kimlik değişmezleri preflight sonrası kilit altında tekrar doğrulanır.

Announcement etkileşimi bağlı event'in metnini/kimliğini değiştirmez veya response'a
kopyalamaz; bu nedenle ilişkili event'e sonradan kilit almaz. Mevcut Announcement
yönetimi event'leri ASC → announcement sırasını korur. Club arşivi mevcut club
WRITE → events ASC → announcements ASC sırasıyla yeni yorumları durdurur; yeni
yorumları topluca yeniden yazmaya gerek kalmadan kaynak policy'si görünürlüğü kapatır.

Recipient user kilidi alınmaz; SQL insert'inin FK KEY SHARE ile actor NO KEY UPDATE
uyumu korunur. Etkileşim source version'ını sırf yorum/like için artırmaz.

## 8. Inbox ve kaynak görünürlüğü

Yeni kök yorum/like uygun yerel yöneticilere; reply ayrıca uygun root yazarına;
comment like uygun yorum yazarına gider. Aktör kendine notice almaz. Aynı alıcı
iki rolde bulunursa reply öncelikli tek notice. Source key `CLUB_COMMENT_CREATED:id`;
Post'taki COMMENT_CREATED prefix'iyle karışmaz. Like key yeni type+kalıcı like id,
re-like yeni inbox üretmez. Bu mekanizma rate limiter değildir.

Alıcının güncel normal interaction okumaya erişimi olmalı; yönetici marker'ı tek
başına yetmez. Event'in geniş okuma kitlesi, write için aynı üniversite kuralından
ayrı tutulur. SQL alıcı filtresi ve reader aynı read predicate'ini uygular.
Yayıncının bütün üniversitesine fan-out yapılmaz.

Yeni Event/Announcement like notice'ları mevcut EVENT/ANNOUNCEMENT resource'larını
kullansa da **yeni tipler interaction read policy'sinden** geçirilir. Eski iptal/
üyelik/katılım notice'larının tarihsel erişim sözleşmeleri sessizce daraltılmaz.
Yeni comment tipleri ayrı source dalına girer; root/platform hide ve güncel üyelik/
profil/kaynak durumu kontrol edilir. Gizli hedef generic available=false,target=null.

Target'a nullable announcementId eklenebilir; mevcut alanlar korunur. Yeni Announcement
yorum hedefi bağlı özel Event kimliğini/başlığını taşımayacak. Toplu graph/context
yeni kaynakları batch yükler; satır başına lookup veya eager User.roles N+1 yok.
Writer MANDATORY; source/comment/audit/inbox aynı transaction'da rollback edilir.
SELECT DISTINCT yalnız alıcı alt sorgusunda; INSERT payload'daki NULL tip sorunu
V010 gerçek PG testinde giderilmiş haliyle korunur.

## 9. Geliştirme, migration ve test planı

1. Kaynak adapter/policy ve okuma-yazma matrisini önce izole testle sabitle.
2. Yeni kulüp comment/like/moderation DTO/entity/repository/service/controller;
   V010 Post kodu ve public API'leri korunur, ortak basit tipler tekrar kullanılır.
3. SQL alıcı/reader entegrasyonu; V011 review SQL, dar grant ve gerçek PG IT hazırla.
   Eski V001–V010 dosyaları, eski veri ve şema kontratları değiştirilmez.
4. Kesin SQL/SHA/kilit/NULL alan etkileri sun; **ayrı test DB onayı** bekle.
5. Yalnız PG17 127.0.0.1:5434/campus_plus_test üzerinde preflight → transaction
   migration → eski on altı tablo değeri/ACL korunumu → yeni grant/kontrat → tüm
   Java/Python/contextLoads/API/constraint/race testleri. Ana DB'ye dokunma.
6. Başarıdan sonra **ayrı ana DB onayı**, aynı SQL/dar grant ve fixture'sız salt
   okunur uygulama/şema/veri/ACL doğrulaması. Commit/push ayrıca istenir; force push yok.

Testler: her source/state/audience/role; cross-university Event read/write ayrımı;
manager/ADMIN draft bypass olmaması; eski Event/Announcement API regresyonları;
source kind/id ve root composite FK; kaynaklar arası cursor/IDOR; cancelled/completed/
withdrawn/archive; own remove/unlike; RESOURCE/PLATFORM/audit gizliliği; manager-revoke,
publish/withdraw/cancel/archive/comment ve root/reply/like yarışları; inbox alıcı
önceliği/kendi kendine notice/dedupe/read-all; insert sonrası tam rollback; 100+ satır
bounded query/EXPLAIN. Güncel 521 Java/40 Python baseline korunur; yeni testler
çalıştırılmadan yeni başarı sayısı verilmez. DB/servis/git geçmişi bu aşamada değişmez.

## 10. İlk kod aşaması doğrulaması

- `security/ClubInteractionPolicy.java`: Event/Announcement normal okuma, sosyal
  yazma ve yerel kaynak yönetimi ayrı boolean policy metotları; yönetici taslak
  bypass'ı yok. Mevcut EventPolicy/AnnouncementPolicy/PostPolicy değiştirilmedi.
- `ClubInteractionPolicyTest`: 27 yeni senaryo; state/audience/üyelik/kulüp matrisi,
  cross-university Event read/write farkı, actor/profile/club kimliği uyuşmazlığı,
  expired üyelik, archived/pasif kaynak, rol/marker/ADMIN ve iptal sonrası moderasyon.
- Tam izole Maven `test`: **476 başarılı**, 0 hata/başarısız/atlama. Önceki 449 test
  korunuyor. Python unittest **40 başarılı**; diff whitespace kontrolü temiz.
- Yeni policy yalnız bean olarak hazır; endpoint, entity, SQL veya transaction
  yazıcısına bağlanmadı. Var olan DB/model/API davranışı değişmedi. Gerçek PostgreSQL
  testleri bu adımda yeniden çalıştırılmadı; son tam V010 sonucu 521 Java/40 Python.
- Sonraki kod adımı: yeni kulüp comment/like/audit entity-repository-workspace-service
  ve controller'lar; ardından inbox entegrasyonu ve review V011. Migration uygulama
  onayı ancak kesin SQL ve test hazırlığı tamamlanınca istenir.
- Commit/push/force push yapılmadı. Diğer proje veya veritabanlarına dokunulmadı.
