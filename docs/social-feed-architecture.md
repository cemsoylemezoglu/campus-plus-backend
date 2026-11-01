# Campus+ — gönderiler ve kampüs sosyal akışı

Tarih: 2026-11-01. Durum: tasarımın metin gönderisi/kulüp akışı/moderasyon kodu
ve V009 SQL'i hazır. **V009 ana ve test DB'ye ayrı açık onaylarla uygulandı.**
[451 Java / 38 Python doğrulaması](post-test-validation.md) ve
[ana DB doğrulaması / veri sınırları](post-main-validation.md).
[Kod, test ve SQL onay raporu](post-phase1-review.md).
Bu belge migration, commit veya push onayı değildir. Aşağıdaki plan tasarım kaydıdır.

## 1. Mevcut temel ve ürün yönü

- Çalışma başlangıcı `1c1d01a`; çalışma ağacı temizdi. Son tamamlanan modül
  Notification; ana/test şeması V008. Önceki doğrulama 374 Java + 36 Python testidir.
- `SecurityConfig` stateless Bearer JWT kullanıyor. Cookie oturumu ve refresh token yok.
- `ClubPolicy`, `AnnouncementPolicy`, `StudentProfilePolicy`, `ClubWorkspace`,
  `AnnouncementWorkspace` ve kulüp arşiv koordinasyonu incelendi. Kullanıcı kimliği
  JWT'den; rol, profil ve kulüp yetkileri güncel DB durumundan alınır.
- Kullanıcı satırı `FOR NO KEY UPDATE` ile kilitlenir; refresh kilidi yükseltmez.
  Üyelik/üniversite değişimleri ve bildirim FK kilitleriyle uyum korunmalıdır.

Amaç, öğrencilerin kendi üniversitelerindeki paylaşımları ve kulüp içeriklerini
keşfetmesidir. Öğrenci gönderisi, kulüp gönderisi, resmi duyuru ve etkinlik farklı
iş kurallarına sahip kaynaklardır. Mevcut Announcement/Event tabloları gönderiye
dönüştürülmez; eski API yanıt sözleşmeleri korunur.

## 2. Gönderi kapsamı ve yayıncı

`PostPublisher`: `STUDENT`, `CLUB`.

- Öğrenci gönderisi: güncel STUDENT rolü ve aktif üniversiteye bağlı öğrenci profili
  gerekir. `authorId`, `universityId` sunucuda belirlenir; kullanıcı bunları seçemez.
- Kulüp gönderisi: güncel ADMIN veya ilgili ACTIVE OWNER/MANAGER + CLUB_ADMIN.
  Normal MEMBER, eski yönetici ve yalnız CLUB_ADMIN marker rolü yeterli değildir.
  Oluşturma/yayın/düzenleme için üniversite ve kulüp ACTIVE olmalıdır.
- `createdBy` işlemi yapan kişiyi, publisher ise içeriğin kimin adına yayımlandığını
  belirtir. Kulüp gönderisinde kullanıcı adı yerine kulüp kimliği gösterilir;
  eski yönetici içeriğin yazarı olsa bile yönetim yetkisi kazanmaz.
- Üniversite, publisher ve club ilk kayıt sonrasında değişmez. Başka üniversiteye
  geçen öğrencinin eski gönderileri eski üniversiteye bağlı kalır; taşınmaz.
- Normal öğrenci kendi STUDENT gönderisini yönetebilir; başka öğrencinin metnini
  ADMIN dahil hiç kimse yeniden yazamaz. ADMIN gerekçeli moderasyon uygular.
- Üniversite değişince eski öğrenci gönderisi yayımlanamaz/düzenlenemez/geri
  yüklenemez; sahibi kendi yönetim ekranında görebilir ve kaldırabilir.
  Gönderiler öğrenci profili üniversite değişikliğine yeni bir engel eklemez.

`PostAudience`: `UNIVERSITY_STUDENTS`, `CLUB_MEMBERS`.

- Öğrenci gönderisi yalnız UNIVERSITY_STUDENTS olur; club_id boş olmalıdır.
- Kulüp gönderisi iki kitleyi de kullanabilir. CLUB_MEMBERS için okuyucu aynı
  üniversitede ve ilgili kulüpte güncel ACTIVE üye olmalıdır.
- Bu aşamada anonim veya üniversiteler arası genel paylaşım yok. ADMIN içerik
  denetimini ayrı yönetim API'sinden yapar; normal akış gizli taslakları içermez.
- Askıya alınan kulübün veya pasif üniversitenin içeriği normal akış/detail'da
  gizlenir. Kaynak yeniden aktif olursa, ayrıca kaldırılmamış yayın yeniden görünür.
- Profil/üyelik/rol değişimleri sonraki istekte etkili olur. Listede önce görünür
  olmayan satırları getirip Java tarafında elemek yasaktır; filtre SQL/HQL içinde
  uygulanır. Sayfa metadata'sı da yetkisiz kayıtları açığa çıkarmamalıdır.

## 3. Yaşam döngüsü, içerik ve moderasyon

- `PostStatus`: DRAFT → PUBLISHED → REMOVED. DRAFT da kaldırılabilir. REMOVED
  terminaldir; aynı kimlikle tekrar yayın yok. Yeni içerik için yeni taslak gerekir.
- İlk yayın `publishedAt` belirler; metin düzenlemesi bu tarihi ve sıralamayı değiştirmez.
  Taslakta metin/kitle, yayında yalnız metin düzenlenir. Version her değişimde artar.
- `body`: trim sonrası 1–10000 karakter, düz metin; zorunlu başlık yok. HTML
  çalıştırılmaz. URL veya istemci tarafı metin, backend tarafından uzaktan getirilmez.
- İlk gönderi alt aşaması metin yayınıdır. Görsel/video yükleme ürün yol haritasında
  ayrı alt aşamadır: dosya boyutu/MIME doğrulaması, depolama, erişim kontrolü, silme ve
  zararlı dosya denetimiyle birlikte tasarlanır. Sınırsız URL ekleyerek taklit edilmez.
- Yazar kendi öğrenci gönderisini; yetkili yerel yönetici kulüp gönderisini kaldırır.
  Gerekçe zorunlu, 1–1000 karakter. Kalıcı DELETE endpoint'i yok.
- Moderasyon, yazar silmesinden bağımsız `moderationHidden` işaretidir. ADMIN
  hide/restore gerekçeli, sürüm kontrollü işlem yapar; geri alma REMOVED gönderiyi
  diriltmez. Gizli içeriğin sahibi kendi yönetim görünümünde durumu öğrenebilir.
- `post_moderation_actions` append-only olarak HIDE/RESTORE, admin, gerekçe ve tarih
  saklar. Normal okur DTO'suna gerekçe veya admin kimliği eklenmez. Ayrıntılı rapor,
  şikâyet, itiraz, hesap yaptırımı ve moderasyon paneli ayrı aşamada bunun üzerine kurulur.
- Kulüp arşivlenirken kulüp gönderileri aynı transaction içinde REMOVED olur.
  `PostLifecycleService` MANDATORY, ClubService koordinasyonuna Announcement sonrasında
  eklenir. Pasif kulüpte sıradan yönetici yeni yayın/düzenleme yapamaz; ADMIN
  moderasyon yapabilir. Kulüp içerikleri arşivden geri yüklenmez.

## 4. Önerilen veritabanı ve JPA modeli

### `posts`

| Alan | Tip / kural |
| --- | --- |
| id | BIGINT identity PK |
| university_id | NOT NULL FK universities, RESTRICT; değişmez |
| created_by | NOT NULL FK users, RESTRICT; değişmez |
| publisher | VARCHAR(16), STUDENT veya CLUB |
| club_id | nullable FK clubs, RESTRICT; yalnız CLUB için zorunlu |
| body | VARCHAR(10000), NOT NULL, boş olamaz |
| audience | VARCHAR(24), doğrulanan enum |
| status | VARCHAR(16), DRAFT/PUBLISHED/REMOVED |
| published_at | nullable TIMESTAMPTZ, ilk yayın zamanı |
| removed_at / removed_by / removal_reason | nullable tarih / FK users / VARCHAR(1000) |
| moderation_hidden | BOOLEAN NOT NULL, başlangıç false |
| created_at / updated_at | NOT NULL TIMESTAMPTZ |
| version | BIGINT NOT NULL >= 0, JPA @Version |

CHECK: enum değerleri, publisher/club/audience kombinasyonu, blank body, version,
durum/tarih/kaldıran/gerekçe tutarlılığı ve tarih sırası. REMOVED taslak için
published_at boş kalabilir. Moderasyon durumu yaşam döngüsünden bağımsızdır.

Club-university eşitliği yalnız Java kontrolüne bırakılmaz: clubs üzerinde yeni
UNIQUE(id, university_id), posts üzerinde composite FK(club_id, university_id)
önerilir. Bu **mevcut clubs tablosuna constraint/index eklenmesi** demektir;
mevcut satırlar değiştirilmez, fakat indeks kurma/kilit maliyeti SQL onayında ayrıca
gösterilir. created_by güncel profil üniversitesine FK ile bağlanmaz; profil ileride
değişebilir. Oluşturmada profil/kulüp eşitliği kilit altında kontrol edilir.

### `post_moderation_actions`

BIGINT identity id; NOT NULL post_id FK posts RESTRICT, actor_id FK users RESTRICT,
action HIDE/RESTORE enum CHECK, reason VARCHAR(1000) NOT NULL/blank kontrolü,
created_at TIMESTAMPTZ NOT NULL, resulting_version BIGINT NOT NULL >=0.
UNIQUE(post_id, resulting_version). Runtime yalnız SELECT/INSERT alır;
UPDATE/DELETE/TRUNCATE almaz. Post durumu ile audit kaydı aynı transaction'dadır.

JPA: Post → University/User/Club/removedBy LAZY ManyToOne; audit → Post/User LAZY.
User/Club üzerinde büyük çift yönlü koleksiyon veya cascade REMOVE eklenmez.
Entity serialize edilmez. Composite FK migration'da ek bütünlük korumasıdır;
JPA club tek id ilişkisiyle kullanılır, university_id ikinci kez yazılabilir map edilmez.

İndeks önerileri: görünür PUBLISHED gönderiler için partial
(university_id, published_at DESC, id DESC); kulüp akışı için aynı yapının club_id
önekli hali; author yönetim listesi (created_by, id DESC); club FK desteği;
audit (post_id, id DESC) ve actor/removed_by FK indeksleri. Son SQL'de mükerrer
indekslerden kaçınılır; gerçek PG sorgu planlarıyla kontrol edilir.

## 5. API ve DTO sözleşmesi

Tüm endpoint'ler JWT ister. Kimlik/kulüp yetkisi servis katmanında da uygulanır.

| Endpoint | İşlem |
| --- | --- |
| POST /api/me/posts | Kendi adına öğrenci taslağı |
| GET /api/me/posts | Kendi öğrenci taslak/yayın/kaldırılmış listesi |
| GET /api/me/posts/{id}/manage | Kendi gönderisi yönetim detayı |
| POST /api/clubs/{clubId}/posts | Yetkili kulüp taslağı |
| GET /api/clubs/{clubId}/posts/manage | Yerel yetkili yönetim listesi |
| GET /api/clubs/{clubId}/posts/{id}/manage | Yerel yetkili yönetim detayı |
| PATCH /api/me/posts/{id} | Kendi öğrenci metni/taslak kitlesi |
| PATCH /api/clubs/{clubId}/posts/{id} | Yetkili kulüp gönderisi düzenleme |
| POST /api/me/posts/{id}/publish veya /remove | Öğrenci yaşam döngüsü |
| POST /api/clubs/{clubId}/posts/{id}/publish veya /remove | Kulüp yaşam döngüsü |
| GET /api/posts/{id} | Yalnız güncel görünürlüğe uygun normal detay |
| GET /api/feed | Görünür gönderiler, kronolojik cursor akışı |
| GET /api/admin/posts | Filtreli moderasyon listesi |
| GET /api/admin/posts/{id} | ADMIN inceleme detayı |
| POST /api/admin/posts/{id}/hide veya /restore | Gerekçeli ADMIN moderasyonu |
| GET /api/admin/posts/{id}/moderation-actions | Sayfalı denetim geçmişi |

CreateStudentPostRequest(body); CreateClubPostRequest(body,audience).
UpdatePostRequest(version,body,audience): tam metin değişimi; alan anlamları açık
olur, yayın sonrası kitle değişimi 409. ActionRequest(version);
RemovePostRequest(version,reason); ModeratePostRequest(version,reason).
DTO toString/log üretimi body/gerekçeyi gizler, request gövdesi loglanmaz.

PostSummary: id, publisher, university summary, güvenli author veya club summary,
kısaltılmış metin, publishedAt, updatedAt, version. Öğrenci author yalnız id/name;
e-posta/özel profil/bölüm/parola/rol listesi yok. PostResponse tam metin ekler.
ManagedPostResponse durum/kitle/createdAt/removal/hidden bilgisini yalnız yetkiliye
verir. AdminPostResponse denetim alanlarını ayrıca sunar. Liste yanıtları no-store.

Görünmeyen normal detay ve başka kullanıcının kendi gönderisi 404; bilinen kulüp
yönetim alanında yetki yoksa 403; kimliksiz/geçersiz token 401; validation 400;
eski version veya geçersiz geçiş 409. SQL/hassas içerik hata yanıtına taşınmaz.

## 6. Akış ve sayfalama

- Başlangıç sosyal akış kaynağı Post'tur. Duyuru ve etkinlik mevcut keşif API'leriyle
  ayrı kaynaklar olarak kalır. Birleşik kampüs
  akışı daha sonra açık kaynak türüyle genişletilir; tümünü ortak entity'ye zorlamayız.
- İlk sayfa `GET /api/feed?size=20`; size 1–100. İsteğe bağlı clubId filtresi vardır,
  istemci universityId seçerek sınırı aşamaz. Profil yoksa tamamlanması gereken
  durumu gösteren 409; boş bir sayfa ile eksik profil gizlenmez.
- Sıra `(publishedAt DESC,id DESC)`. Cursor son görünür kaydın iki değeridir;
  sonraki sorguda strict tuple `<` uygulanır. Post yayın tarihi değişmez.
- Cursor versiyonlu, sabit uzunlukta Base64URL ikili yapı: sürüm/zaman/id/club filtresi.
  JSON parser veya yeni bağımlılık gerektirmez; şifreli veya yetki belgesi değildir.
  Bilinmeyen sürüm/yanlış uzunluk/alan sınırı/format 400. Filtre değişirse
  yeni cursor gerekir. Başka cursor gelse de güncel yetki SQL predicate'leri çalışır.
- `FeedResponse(items,nextCursor,hasNext)`; size+1 görünür satır alınır, count query
  yok. Yeni yayınlar sonraki sayfayı kaydırmaz; düzenleme üst sıraya taşımaz.
  Bu bir transaction snapshot garantisi değildir: silinen/görünürlüğü kaybolan
  kayıtlar sonraki istekte görünmez. Geçmiş sayfayı cache'de tutmak yetki sağlamaz.
- Kendi/yönetici/admin listeleri mevcut PageResponse sayfalama yaklaşımını kullanır.
  JOIN ile üye satırı çoğaltmak yerine EXISTS filtreleri; DTO projection/EntityGraph
  ve gerekli tek toplu yükleme ile N+1 önlenir.

## 7. Transaction ve yarış koşulları

Ön kontrol yalnız erken hata içindir; transaction içi kilit sonrası yetki ve sürüm
yeniden okunur. Öğrenci post yazma: actor NO KEY UPDATE → university SHARE → post
WRITE. Kulüp post yazma: actor NO KEY UPDATE → club SHARE → university SHARE → post
WRITE. Moderasyon da gönderinin kaynak yolunu izler; yalnız post kilidinden sonra
user/club kilidine dönmez. Birden çok post gerekiyorsa id ASC.

Kulüp arşivi mevcut users ASC → club WRITE → events ASC → announcements ASC yoluna
posts ASC ekler. Yeni post işlemi event/announcement kilidi almaz; ters kenar yaratmaz.
Diğer kaynak işlemlerindeki mevcut üniversite kilit sırası korunur. Metadata/JPA
refresh işleminin önceki kilidi FOR UPDATE'a yükseltmemesi regresyonla doğrulanır.

Kendi profil değişimi ve öğrenci paylaşımı ortak kullanıcı kilidinde sıralanır.
Kulüp yönetici yetkisinin kaldırılması/arşiv/publish yarışları ortak kulüp kilidiyle
sıralanır. Author ownership, university, local membership her yazmada tekrar kontrol
edilir. Eşzamanlı edit/remove/hide tek kayıtta seri olur; eski version 409.
Post ile moderasyon audit insert atomiktir; audit hatasında post gizlenmesi rollback.

NotificationWriter'a bu alt aşamada gelişigüzel POST enum'u eklenmez: V008 CHECK ve
kaynak FK'leri mevcut beş kaynak grubunu doğrular. Tüm üniversiteye post başına inbox
fan-out yapılmaz. Yorum/beğeni/mention bildirimi kendi aşamasında posts FK'si ve
yeni tipleri içeren onaylı migration + gizlilik/read-all testleriyle eklenecektir.

## 8. Kodlama, migration ve doğrulama sırası

1. Post entity/enums/DTO/policy/repository/workspace/query/lifecycle/controller,
   servis seviyesinde kaynak yetkisi; öğrenci ve kulüp akışı birlikte geliştirilir.
2. ADMIN hide/restore ve append-only audit; ClubService arşiv entegrasyonu.
   Yeni bağımlılık veya SecurityConfig'te genel gevşetme yok.
3. V009 adayı yalnız review dizininde: posts, post_moderation_actions, identity
   sequence/constraint/index ve clubs composite FK desteği. Mevcut V001–V008
   dosyaları ve eski kontratlar değişmez. Yeni satır backfill/rol/parola değişimi yok.
4. İzole testler ve IT derleme; kesin SQL, SHA, kısıt/indeks/kilit etkisi, dar grant
   dosyaları, uygulanmamış entegrasyon testleri ve riskler raporlanır.
5. **Yalnız test DB için açık onaydan sonra** hedef/PG17/history/checksum/aktif
   istemci doğrulama; transaction içinde migrate/grants, V009 kontrat ve eski on
   tablonun kayıt/ACL korunumu. Testler yalnız campus_plus_test fixture'larına yazar.
6. contextLoads dahil bütün Java/Python/PG testleri ve CI boş kurulum planı.
   Başarıdan sonra **ana campus_plus için ayrı açık onay** istenir. Ana geçişte
   yalnız yeni DDL/dar grant ve salt okunur uygulama kontrolleri; fixture yok.

Maven hedefi/CI discovery/kontrat allowlist'i yalnız SQL ve uygulama paketiyle
birlikte kontrollü güncellenir. Bu tasarım adımında hedef hâlâ 8'dir. ddl-auto=validate
korunur; yeni entity V009 olmayan DB'de normal açılış validate'ını başarısız yapar.
Kod aşamasında bunun beklenen kurulum sınırı olduğu açıkça bildirilecek.

Testler: tüm publisher/audience/status kombinasyonları; DTO blank/length/log gizliliği;
owner/diğer öğrenci/marker/yerel ve başka kulüp yönetici/ADMIN; profil kaybı/üniversite
değişimi/üyelik sonu/askı/arşiv sonrası list-detail-count gizliliği; yayın sonrası kitle
kilidi; moderation reason sızıntısı/restore-removed; cursor eşit zaman/id/tam sınır/
bozuk cursor/filtre değişimi/yeni yayın/sonradan gizleme; SQL FK/CHECK/UNIQUE ve dar
runtime yetkileri; audit rollback; update-update/remove-hide; publish-universityChange,
publish-managerRevoke, publish-clubArchive; eski inbox ve deadlock regresyonları;
akış N+1/sorgu sayısı ve indeks EXPLAIN kontrolleri. Normal test DB bağlantısı açmaz.

## 9. Sonraki ürün aşamaları

Post alt aşamasını yorum/yanıtlar, beğeni ve bunların gizliliğe duyarlı bildirimleri
izler. Sonra dosya/medya, mesajlaşma, bildirim tercihleri ve moderasyon/şikâyet
sisteminin tamamlanması gelir. Refresh token, parola sıfırlama/e-posta doğrulama,
rate limit ve hesap yaşam döngüsü yayın öncesi güvenlik aşamasındadır. Entegrasyon,
performans ve deployment hazırlıkları ayrıca ele alınır. Commit/push yalnız ayrı
kullanıcı talimatıyla yapılır; diğer proje/veritabanı/servislere dokunulmaz.

## 10. Bu tasarım adımındaki doğrulama

- DB bağlantısı açılmadan Maven offline `test`: **330 test başarılı**, hata/atlama yok.
  İlk sandbox denemesi Mockito agent attach mekanizması engellendiği için başarısızdı;
  aynı komut uygun çalıştırma izniyle başarılı oldu. Kod/test davranışı değiştirilmedi.
- `python3 -m unittest discover -s scripts/tests`: **36 test başarılı**.
- `git diff --check`: temiz. Yalnız README ve tasarım/geliştirme belgeleri değişti.
- 44 gerçek PostgreSQL testi bu adımda yeniden çalıştırılmadı; son 374 Java toplamı
  önceki V008 doğrulamasıdır. Bu adımda yeni Post testi, Java kodu, SQL, DB bağlantısı,
  migration, grant, commit veya push yoktur. Sıradaki adım bu tasarımın kodlanmasıdır.
