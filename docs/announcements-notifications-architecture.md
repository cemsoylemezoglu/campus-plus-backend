# Campus+ — kulüp duyuruları ve uygulama içi bildirimler

Tarih: 2026-10-31. **Tasarım/kodlama onaylandı; Announcement ana ve test V007 aşamaları tamamlandı.**
[V007 ana DB doğrulaması](announcement-main-validation.md).
Notification kod/test/ana DB aşamaları tamamlandı; [V008 ana doğrulama](notification-main-validation.md).
Ana ve test şemaları V008 ile uyumlu. Aşağıdaki bölüm planları tasarım kaydıdır.
Event sonrası ana/test veritabanları V006 ile uyumlu. Bu belge SQL uygulama,
commit veya push onayı değildir. Son doğrulama: [Event ana DB raporu](event-main-validation.md).

## 1. Mevcut koddan hareketle

- `ClubPolicy` güncel ADMIN veya ilgili ACTIVE OWNER/MANAGER + CLUB_ADMIN kontrolü yapar.
  Marker rolü, kurucu veya içeriğin yazarı tek başına yönetim hakkı vermez.
- `ClubWorkspace` kullanıcıları ID sırasıyla, ardından kulübü kilitler ve entity'leri
  refresh eder. `EventWorkspace` kullanıcı → shared club → exclusive event sırasını izler.
- Club/Event servisleri transaction içindedir. `EventLifecycleService` aynı transaction'ı
  zorunlu tutarak kulüp arşivi ve üyelik kaybının etkinlik etkilerini uygular.
- Entity'ler yerine DTO, string enum, `Instant`, `@Version`, sayfalama ve 401/403/404/409
  sözleşmesi korunur. Java 21, Boot 4.1.1, Security/JWT/JPA/PostgreSQL/Maven değişmez.
- Kaynakta Announcement/Notification modülü, push sağlayıcısı, kuyruk veya outbox yok.
  Önceki mimari belgeleri bunları gelecekteki entegrasyon noktaları olarak tanımlar.

## 2. Ürün kapsamı ve geliştirme sırası

1. **Duyuru:** kulübe bağlı taslak, yayın, düzenleme, geri çekme, arşiv, güvenli keşif
   ve isteğe bağlı etkinlik bağlantısı.
2. **Uygulama içi bildirim:** kişisel kutu, okunma/okunmama, okunmamış sayısı,
   toplu okundu; duyuru, kulüp/üyelik ve etkinlik işlemleriyle kalıcı entegrasyon.
3. **Sonraki ayrı aşama:** bildirim tercihleri/abonelikler ve dış teslim kanalları.
   Bu başlıklar mevcut uygulama içi bildirimden ayrı değerlendirilir.

Duyuru, sosyal akış gönderisinden ayrı bir kulüp yayınıdır. Yorum/beğeni ileride bu
kaynağa gerçek FK ile bağlanabilir. Genel sosyal akış ve özel mesajlaşma modülleri korunur;
kulüp yöneticiliği kullanıcının özel bildirim kutusunu okuma yetkisi vermez.

## 3. Announcement iş kuralları

- Oluşturma/yayın/düzenleme: ADMIN veya ilgili aktif OWNER/MANAGER + CLUB_ADMIN.
  Üniversite ve kulüp ACTIVE olmalı. Kullanıcı kimliği JWT'den; `clubId` değişmez.
- `AnnouncementAudience`: `UNIVERSITY_STUDENTS`, `CLUB_MEMBERS`.
  Üniversite kitlesi: güncel öğrenci profili kulübün üniversitesiyle aynı olmalı.
  Kulüp kitlesi: güncel ACTIVE üyelik de gerekir. Bu yeni modülün kuralıdır;
  mevcut Event keşif kuralları değiştirilmez.
- Yönetici/ADMIN taslak dahil kendi yetkili kaynağını inceleyebilir. Normal kullanıcı
  yalnız PUBLISHED, arşivlenmemiş duyuruyu, aktif üniversite/kulüp ve kitle koşuluyla görür.
  Üniversite değiştirme veya üyelik kaybı sonraki okumada etkili olur.
- `DRAFT → PUBLISHED → WITHDRAWN`; DRAFT veya WITHDRAWN arşivlenebilir.
  Yayındaki kayıt önce gerekçeli geri çekilir. Geri çekilen kayıt tekrar yayınlanmaz;
  yeni yayın için yeni taslak oluşturulur. Kalıcı DELETE yoktur.
- Taslakta başlık/metin/kitle/etkinlik bağlantısı değişebilir. Yayından sonra başlık
  ve metin düzenlenebilir; kitle/bağlantı değişmez. `updatedAt/version` revizyonu gösterir.
  Yazım düzeltmesi toplu bildirim üretmez; her PATCH yeni bir yayın sayılmaz.
- Geri çekme gerekçesi yönetim DTO'sundadır; normal liste/detail veya bildirim metnine
  konmaz. Yazarın yönetici yetkisi kalkarsa içerik sahipliği ayrıcalık sağlamaz.
- `eventId` isteğe bağlıdır; kaynak gerçekten aynı kulübün etkinliği olmalıdır.
  Taslak bağlantı kurulabilir, fakat duyuru yayınlanırken etkinliğin `publishedAt`
  değeri olmalı ve güncel görünürlük kontrolü yapılmalıdır. Kulübe özel etkinliği
  üniversite kitlesine açan duyuru reddedilir. Bağlantılı etkinliğin başlığı/detayı
  döndürülmeden önce EventPolicy de uygulanır; görünmez etkinlik içeriği sızdırılmaz.
- Kulüp askıya alınırsa normal okuma/yeni yayın durur. Arşivlenirse duyurular aynı
  transaction'da terminal hale getirilir: PUBLISHED önce WITHDRAWN, ardından tümü
  arşivlenir. Aktif üniversite koşulu okumada/yazmada güncel değerlendirilir.
- Başlık 1–200, metin 1–10000, gerekçe 1–1000 karakter; trim ve boşluk kontrolü.
  Metin düz metindir; HTML işlenmez. Gelecekte zengin içerik ayrı sanitize sözleşmesi ister.

## 4. Önerilen tablolar ve JPA ilişkileri

### `club_announcements` — V007 adayı

| Alan | Tip/kural |
| --- | --- |
| id | BIGINT identity PK |
| club_id | NOT NULL FK clubs, RESTRICT |
| created_by | NOT NULL FK users, RESTRICT; değişmez |
| event_id | nullable FK events, RESTRICT |
| title / body | VARCHAR(200) / VARCHAR(10000), NOT NULL, boş olamaz |
| audience | VARCHAR enum, NOT NULL |
| status | VARCHAR enum, NOT NULL, başlangıç DRAFT |
| published_at | nullable TIMESTAMPTZ |
| withdrawn_at / withdrawn_by / withdrawal_reason | nullable tarih / FK users / VARCHAR(1000) |
| archived_at | nullable TIMESTAMPTZ |
| created_at / updated_at | NOT NULL TIMESTAMPTZ |
| version | BIGINT NOT NULL, >=0; JPA @Version |

CHECK: durum/kitle enum'ları, boş metinler, published/withdrawn alanlarının durumla
uyumu, arşivin yalnız DRAFT/WITHDRAWN için mümkün olması. Yayındaki kayıt için
published_at zorunlu, WITHDRAWN published_at değerini korur. Etkinlik-kulüp eşitliği
serviste kilit altında kontrol edilir; FK tek başına bu çapraz kaynak kuralını sağlamaz.
İndeksler: `(club_id,status,published_at,id)`, arşivsiz keşif için partial
`(status,published_at,id)`, event_id ve kullanıcı FK'leri için destek indeksleri.
Nihai SQL/indeks planı kod aşamasında açıklanacak.

JPA `ManyToOne(fetch=LAZY)` club/creator/event/withdrawnBy; cascade REMOVE yok.
User veya Club üzerinde geniş `OneToMany` koleksiyon eklenmez. Kullanıcı yanıtları
entity serialize etmez; toplu projection/EntityGraph ile N+1 kontrol edilir.

### `notifications` — V008 adayı

| Alan | Tip/kural |
| --- | --- |
| id | BIGINT identity PK |
| recipient_id | NOT NULL FK users, ON DELETE CASCADE |
| actor_id | nullable FK users, ON DELETE SET NULL |
| type | VARCHAR(64), doğrulanan NotificationType enum |
| source_key | VARCHAR(200), backend üretir; NOT NULL |
| club_id | nullable FK clubs, RESTRICT |
| announcement_id | nullable FK club_announcements, RESTRICT |
| event_id | nullable FK events, RESTRICT |
| participation_id | nullable FK event_participations, RESTRICT |
| membership_id | nullable FK club_memberships, RESTRICT |
| created_at | NOT NULL TIMESTAMPTZ |
| read_at | nullable TIMESTAMPTZ |
| version | BIGINT NOT NULL, >=0; JPA @Version |

UNIQUE `(recipient_id,source_key)`. CHECK: enum, boş source_key, version >=0,
read_at >= created_at; type'a göre gerekli kaynak FK kombinasyonu. Örneğin
ANNOUNCEMENT_PUBLISHED → club+announcement; EVENT_CANCELLED → club+event;
PARTICIPATION_REGISTERED → club+event+participation; MEMBERSHIP_APPROVED → club+membership;
CLUB_APPROVED → yalnız club. Güncel tipler bu kombinasyonları zorunlu kılar;
ileride mesaj gibi kulüpsüz bir tip yeni CHECK/migration ile eklenebilir.

İndeks: `(recipient_id,created_at DESC,id DESC)`, okunmamışlar için aynı sırada
`WHERE read_at IS NULL`, ayrıca kullanılan kaynak FK'lerine destek indeksleri.
Recipient cascade yalnız gelecekte gerçekten silinen hesabın özel kutusunu temizler;
mevcut kullanıcı DELETE akışına sırf inbox nedeniyle yeni engel koymaz. Diğer iş
FK'lerinin mevcut silme korumaları kalır. Bu aşamada hiçbir hesap silinmez.
Bildirim bir audit kaydı değildir; güvenlik/moderasyon audit log'u ayrı modüldür.

JPA LAZY ilişkiler ve güvenli projection kullanılır. İçerik başlığı, açıklama, parola,
e-posta, JWT veya karar gerekçesi bildirim tablosuna kopyalanmaz. Kaynak alanlarının
aynı kulübe ve ilgili alıcıya ait olması üretici servisin sorumluluğudur; FK/CHECK
varlık ve biçimi korur, kaynak policy'sinin yerine geçmez.

## 5. Bildirim üretimi ve güvenilirlik

Uygulama içi kutunun teslimi **veritabanı kaydının oluşmasıdır**. Kaynak değişikliği ve
bildirim INSERT'i aynı transaction'da yapılır; `NotificationWriter` için
`Propagation.MANDATORY` kullanılır. INSERT başarısızsa iş değişikliği de rollback olur.
Bu aşamada asenkron worker, harici kuyruk veya outbox eklemek gerekmez.
`@Async`/bellek içi AFTER_COMMIT listener ile kalıcılık garantisi iddia edilmez.

Yayın alıcıları, transaction içindeki tek `INSERT … SELECT` statement snapshot'ında
uygun görünen ACTIVE kulüp üyeleridir; üniversite profili/kitle şartı da uygulanır.
Bütün üniversiteye varsayılan toplu gönderim yapılmaz: üniversite kitlesi okuma
kapsamıdır, üyelik ise bu aşamadaki yayın bildirim aboneliğidir. Sonradan katılana eski
bildirimler otomatik dağıtılmaz; mevcut uygun duyurular listeden okunabilir.

- Alıcılar Java'da tek tek yüklenmez veya her alıcı için kullanıcı kilidi alınmaz.
  Set tabanlı INSERT ve kaynak sürümünden türeyen idempotency anahtarı kullanılır.
- `source_key` örneği `announcement:42:published:3`; ID/version gerçek kilitli,
  flush edilmiş kayıttan gelir. İstemci source_key veya recipient_id belirleyemez.
- `ON CONFLICT (recipient_id,source_key) DO NOTHING` yalnız beklenen tekrar teslimi
  yutar; diğer FK/CHECK/SQL hataları gizlenmez. Bu, HTTP işlemini genel olarak
  idempotent yapmaz; mevcut durum/version kontrolleri korunur.
- Aktörün kendisine sıradan toplu yayın bildirimi gönderilmez. Kişisel işlem sonucu
  bildirimleri (kayıt onayı gibi) kendi işlemi olsa da gönderilir.
- Toplu yayın maliyeti hâlâ alıcı sayısıyla artar. Büyük kulüp fixture'ıyla sorgu
  sayısı ve transaction süresi ölçülür; kapasite kanıtı olmadan sınırsız ölçek iddiası
  yoktur. Gerekirse kalıcı alıcı snapshot'lı outbox ayrı planlanır; snapshot alınmadan
  sonradan taranan değişebilir üyelik satırları yayın-anı alıcı listesi sayılmaz.
- Dış teslim aşamasında kaynak transaction'ıyla birlikte yazılan outbox,
  retry/backoff, teslim dedupe ve teslim tercihleri ayrıca eklenecek. Harici servise
  DB transaction'ı içinden ağ çağrısı yapılmayacak.

### İlk entegrasyon matrisi

| Olay | Alıcı |
| --- | --- |
| Duyuru yayınlandı | Yukarıdaki yayın snapshot'ındaki uygun ACTIVE üyeler |
| Kulüp onaylandı/reddedildi | Güncel OWNER |
| Kulüp askıya alındı/geri açıldı/arşivlendi | Geçiş öncesi ACTIVE üyeler; archive durumunda önceden alınmış alıcı seti |
| Üyelik başvurusu | İlgili kulübün ACTIVE OWNER/MANAGER + CLUB_ADMIN yöneticileri |
| Başvuru onayı/reddi, üye çıkarma/askı/geri alma | İlgili öğrenci |
| Üyelik rolü değişti / sahiplik devri | Etkilenen öğrenci; devirde eski ve yeni sahip |
| Etkinlik yayınlandı | Uygun ACTIVE kulüp üyeleri; kitle koşulu uygulanır |
| Etkinlik ertelendi / iptal edildi | İşlem öncesi REGISTERED veya ATTENDED katılımcılar |
| Katılım kaydı / check-in / yönetici çıkarma / yeniden kayıt izni | İlgili öğrenci |
| Üyelik kaybı veya kulüp arşiviyle otomatik bilet iptali | Gerçekten etkilenen kayıtların öğrencileri |

Kendi başvurusunu iptal etme/üyelikten ayrılma/katılım iptali kutuyu ayrıca doldurmaz;
ilgili HTTP yanıtı sonucu bildirir. Event complete otomatik NO_SHOW bildirimi üretmez.
Eski kayıtlardan geçmiş bildirim türetilmez. Gerekçeler ve eski durumlar kopyalanmaz;
gösterilen kaynak detayının güncel policy'si her okumada uygulanır.

Toplu iptallerde alıcılar statü değişmeden belirlenir; güvenli UPDATE RETURNING veya
kilitli kaynak altındaki aynı transaction sorgusu kullanılır. Değişen tüm satırların
kaynak sürümleri anahtar üretiminde tutarlı olmalı. Manuel event iptali ve archive
akışları aynı değişim için iki eşdeğer bildirim üretmez: tek semantik sonuç/sebep seçilir.

## 6. Erişim ve gizlilik

- Her inbox sorgusu ve yazması `recipient_id = CurrentActor.userId` içerir.
  ADMIN de başka kullanıcının kutusunu okuyamaz/okundu yapamaz. Böyle bir endpoint yok.
- Bildirim detayı kaynak erişimi değildir. DTO oluşturulurken Club/Announcement/
  Event/kişisel üyelik-katılım policy'leri yeniden uygulanır.
- Kaynak artık görünmüyorsa kutuda yalnız genel, kişiye ait bildirim kabuğu gösterilir:
  `available=false`, kaynak başlığı/metni/gerekçesi/bağlantısı/kimliği DTO'da null.
  Kaynak okunabilirken de yalnız minimal type, izinli kısa özet ve güvenli hedef döner.
  Kutunun toplam/okunmamış sayısı alıcının kendi kayıtlarını sayar.
- Kaynak tekrar görünür olursa sonraki okumada erişim yeniden hesaplanır. Eski
  token/rol, kaybedilmiş üyeliği veya üniversite erişimini geri kazandırmaz.
- Duyuru görünürlük filtresi page/count öncesinde SQL'e uygulanır. Bildirim listesi
  sahibinin tüm kabuklarını sayfalar; gizli kaynak içeriği sonradan güvenli projection
  ile redakte edilir. Toplu kaynak/üyelik lookup kullanılır; kaynak başına N+1 yok.
- Özel yanıtlarda `Cache-Control: no-store`; istemci HTML render etmez. Loglarda
  duyuru gövdesi, bildirim içeriği, alıcı listeleri ve gizli bilgiler yazılmaz.

## 7. REST ve DTO sözleşmesi

Tüm uçlar Bearer authentication ister. Listeleme PageResponse; sayfa >=0, boyut 1–100.
Duyuru sırası `publishedAt DESC,id DESC`, yönetimde `id DESC`; inbox `createdAt DESC,id DESC`.

| Metot / endpoint | Amaç |
| --- | --- |
| GET /api/announcements | Kitle/club/university/query filtreli görünür yayınlar |
| GET /api/announcements/{id} | Görünür yayın detayı veya yetkili yönetim okuması |
| GET /api/clubs/{clubId}/announcements/manage | Yerel yönetim; status/arşiv filtresi |
| POST /api/clubs/{clubId}/announcements | Taslak oluştur; 201 |
| PATCH /api/clubs/{clubId}/announcements/{id} | Version kontrollü düzenleme |
| POST /api/clubs/{clubId}/announcements/{id}/publish | İlk yayın |
| POST /api/clubs/{clubId}/announcements/{id}/withdraw | Gerekçeli geri çekme |
| POST /api/clubs/{clubId}/announcements/{id}/archive | Terminal arşiv |
| GET /api/me/notifications | unreadOnly filtresi; yalnız kişisel kutu |
| GET /api/me/notifications/unread-count | Kişisel okunmamış sayısı |
| POST /api/me/notifications/{id}/read | Okundu, version; zaten okunduysa 200/no-op |
| POST /api/me/notifications/{id}/unread | Okunmadı, version |
| POST /api/me/notifications/read-all | `upToId`; kendi görülebilen kayıtlarına üst sınır |

Önerilen DTO'lar:

- CreateAnnouncementRequest(title, body, audience, eventId).
- UpdateAnnouncementRequest(version, title?, body?; taslakta audience?/eventId?/clearEvent).
  En az bir değişiklik, null/clearEvent çakışması reddi; yayın sonrası kitle/bağlantı reddi.
- AnnouncementActionRequest(version); WithdrawAnnouncementRequest(version, reason).
- AnnouncementSummary(id, club özeti, title, audience, publishedAt, updatedAt, version).
- AnnouncementResponse(summary, body, izinli etkinlik özeti); yönetim için ayrı
  ManagedAnnouncementResponse(status, archive/withdraw metadata). Normal DTO gerekçe taşımaz.
- NotificationResponse(id, type, createdAt, readAt, version, available, güvenli target/özet).
- NotificationReadRequest(version), ReadAllNotificationsRequest(upToId),
  UnreadCountResponse(count), ReadAllNotificationsResponse(updatedCount).

Toplu okundu, işlem statement'ında görünen `id <= upToId` kendi unread kayıtlarını
atomik günceller; version'ı da artırır. Daha yeni bildirimler veya başka kullanıcılar
etkilenmez. Tekil okuma/no-op ilkesi read-all ile yarışta okundu durumunu geri almaz.
Unread işlemi eski version ile 409 verir. Her işlem sonunda güvenli güncel DTO döner.

401: geçersiz/eksik token. 403: görünür kaynakta yerel yönetim yetkisi yok.
404: gizli/yok kaynak veya başka alıcının notification ID'si.
409: eski version/uygunsuz durum/başka kulübün etkinliğini bağlama.
400: biçim, uzunluk, sayfa, null/clear çakışması veya ters filtre.

## 8. Transaction ve kilit sırası

Yazma sırası korunur: kullanıcılar ASC → kulüp → gerekiyorsa etkinlik → duyuru → bildirim.
Kaynak okuma preview'dır; yetki ve version kilitten/refresh'ten sonra yeniden kontrol edilir.
Birden çok event/duyuru kilitleniyorsa ID sırası kullanılır. Kulüp arşiv koordinatörü
önce mevcut EventLifecycleService işini, sonra AnnouncementLifecycleService işini yürütür.

`NotificationWriter` yalnız INSERT yapar; mevcut notification satırını kilitleyip
kaynağa geri dönmez. Read/unread/read-all işlemleri kullanıcı → kendi notification
sırasını izler, kaynak kilidi almaz; DTO için kaynak görünürlüğü kilitsiz okunur.
**Notification kodundan önce çözülecek kilit uyumu:** mevcut kullanıcı
`FOR UPDATE` kilidi, başka transaction'ın aynı kullanıcıya bildirim eklerken aldığı
FK `KEY SHARE` kilidiyle çakışabilir. İki yönetici birbirine bildirim üretirse, kullanıcıları
tek tek sıralamak fan-out alıcılarının tamamını kapsamadığı için yeterli değildir.

Önerilen çözüm, kimlik alanlarını değiştirmeyen bu iş akışlarında ortak kullanıcı
kilidini PostgreSQL `FOR NO KEY UPDATE` olarak almaktır. Bu kilit diğer yönetim/profile
kullanıcı yazıcılarıyla seri erişimi korur, FK KEY SHARE ile uyumludur. Native repository
sorgusu + kilidi yükseltmeyen `refresh(user)` birlikte tasarlanmalıdır; mevcut
`refresh(user, PESSIMISTIC_WRITE)` bırakılırsa tekrar FOR UPDATE'a yükselip çözümü bozar.
Kullanıcı PK/unique alanlarını değiştiren CRUD işlemleri ayrıca incelenir; bu işlemlere
bildirim fan-out eklenmez. Bu bir şema değişikliği değildir, fakat ortak güvenlik/
eşzamanlılık altyapısı değişikliğidir: kod aşamasının ilk alt adımında tüm eski kilit
regresyonları ve yeni çapraz-alıcı yarışları gerçek PostgreSQL ile doğrulanacak.
Bu kanıt sağlanmadan notification entegrasyonu tamamlanmış sayılmaz. Kilit/FK kaldırma,
uygulamanın her isteğinde hatayı körlemesine tekrar deneme veya tüm alıcıları kullanıcı
kilidiyle toplama çözüm olarak kullanılmaz. Bu tasarım adımında mevcut kod değiştirilmedi.

Kilit uyumluluğu için teknik kaynak: [PostgreSQL 17 satır kilitleri ve çakışma
tablosu](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS).
Çapraz bildirim yarışı, mevcut `ClubWorkspace.lockUser` kullanımı üzerinden yapılan
tasarım çıkarımıdır; henüz testle yeniden üretilmiş bir üretim hatası olarak raporlanmaz.

Kulüp yayınları alıcı üyelikleriyle tutarlı snapshot üretir. Duyuru yayınlama aynı
kulüp üzerinde üyelik/rol değişimleriyle uyumlu kulüp kilidi kullanır. Başka kulüplerin
işlemlerini global kilitle seri hale getirmez. Kaynak güncellenip bildirim insert'inde
hata olursa ikisi de rollback olur; servis katmanındaki güvenlik doğrudan çağrıda da geçerlidir.

## 9. Migration ve doğrulama planı

Henüz SQL hazırlanmadı/uygulanmadı. Kod aşamasında sıralı adaylar:

- V007: yalnız club_announcements + identity sequence/constraint/indeksler.
- V008: yalnız notifications + identity sequence/constraint/indeksler.
- Ayrı, yeni nesnelerle sınırlı main/test runtime grant dosyaları ve V008 tam şema kontratı.
- V001–V006 dosyaları, mevcut roller/parola/kayıtlar değişmez; geçmiş bildirim backfill yok.
- Maven migration hedefi/CI kontratı ancak hazırlanmış sürümle birlikte güncellenir;
  otomatik startup migration ve Hibernate update/create açılmaz.

Önce SQL ve etkiler sunulur; test DB uygulaması için ayrı açık onay beklenir.
Test DB migrate → şema/ACL/veri koruması → bütün Java/Python testleri/gerçek HTTP ve
PG yarışları → ayrı ana DB onayı → normal uygulama validate + yalnız GET kontrolleri.
Ana DB'de test kullanıcıları veya duyurular oluşturulmaz. Mevcut V006 taban çizgisi
ve Event testleri korunur. Her uygulama öncesi gerçek `127.0.0.1:5434`/DB adı/PG17
kimliği, history, aktif istemciler ve SQL checksum doğrulanır; beklenmeyen farkta durulur.

## 10. Güvenlik ve otomatik test senaryoları

- CRUD/auth/JWT/RBAC/University/Profile/Club/Event için mevcut 273 Java testi korunur;
  32 Python altyapı testi sürümlü kontrat/checksum değişimleriyle genişletilir.
- Sahte creator/recipient, yanlış clubId, yalnız marker rolü, eski OWNER/MANAGER,
  başka üniversite, gizli taslak, geri çekilmiş/arşivlenmiş içerik ve cross-club event.
- Üniversite/üyelik değişince liste count/page ve eski bildirim hedefi sızıntı yapmaz.
  ADMIN başka kişinin inbox'una erişemez. Gövdede/hash/e-postada/logda sızıntı yok.
- Publish/withdraw/update/archive geçişleri; içerik revizyonu yeni toplu bildirim üretmez.
  Etkinlik kitle uyumu ve aktif üniversite/kulüp şartları; HTML düz metin sözleşmesi.
- Kaynak değişikliği ile notification rollback, yinelenen source_key, aynı olayın
  retry'si, SQL FK/CHECK/unique ve yalnız beklenen ON CONFLICT davranışı.
- Aynı anda publish/publish, publish/üyelikten ayrılma, publish/rol kaybı, publish/club
  archive, read/read-all/unread; kullanıcı silme/notification insert FK kilit yarışı;
  iki yöneticiye çapraz bildirim fan-out deadlock kontrolü ve güvenli tam rollback.
- Event iptal/archive ve üyelik kaybı bulk bildirimleri yalnız gerçekten etkilenenlere
  gider; erken ATTENDED kontenjan/katılım kuralları ve var olan transaction'lar korunur.
- inbox N+1/sorgu sayısı, unread partial indeks, büyük alıcı setinde INSERT SELECT
  transaction ölçümü. Yeni paket gerektirmeden mevcut mockMVC/PG17 altyapısı kullanılır.
- Normal `test` DB'ye bağlanmaz; yalnız açık `postgres-it` profili test DB'sine yazar.

## 11. Onaya sunulan kararlar

Öneri: duyuruda üniversite veya aktif kulüp kitlesi; yayında yalnız metin revizyonu;
tekrar bildirim üretmeyen düzenleme; aktif üyelere yayın bildirimi; dinamik kaynak
gizliliği; aynı transaction'da kalıcı inbox ve iki aşamalı V007/V008 hazırlığı.
Tasarım/kodlama onayı SQL uygulama onayı sayılmayacak. Önce Announcement kodu ve izole
testler, ardından Notification ve entegrasyonlar geliştirilecek; her aşama raporlanacak.
