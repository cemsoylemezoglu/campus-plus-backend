# Bildirimler — kod aşaması ve V008 onay paketi

> Bu belge 2026-10-31 kod aşamasının tarihsel kaydıdır. Sonraki açık onayla V008
> yalnız test DB'de uygulandı. [Güncel canlı doğrulama](notification-test-validation.md).
> SQL aynı byte/checksum ile managed-migrations konumuna taşındı. Daha sonra ayrı
> açık onayla [ana DB geçişi de tamamlandı](notification-main-validation.md).

Tarih: 2026-10-31. **Uygulama içi bildirim kodu ve izole testler hazır; V008 hiçbir
veritabanına uygulanmadı.** Ana Campus+ DB'ye bu aşamada bağlanılmadı; commit/push yok.
Diğer projeler, PG14, Docker, Supabase veya Render kullanılmadı.

## Geliştirilenler

- `Notification`, 23 `NotificationType` ve beş typed kaynak kategorisi.
- `dto/notification/`, NotificationRepository, NotificationReader/Service/Writer ve Controller.
- Yalnız kişisel sayfalı kutu, unreadOnly filtresi, okunmamış sayısı, version kontrollü
  okundu/okunmadı ve upToId ile sınırlı toplu okundu. Aynı bildirim zaten okunmuşsa
  read tekrarında yeni yazma/sürüm artışı yok; stale unread 409 verir.
- Liste/count/yazma sorguları daima token'daki recipient ID ile sınırlı. ADMIN dahil
  hiç kimse başkasının kutusunu okuyamaz veya okundu yapamaz.
- Kaynak görünürlüğü Club/Announcement/Event ve kişisel üyelik/katılım kurallarıyla
  her okumada yeniden hesaplanır. Erişim yoksa yalnız genel kabuk döner;
  `available=false`, target=null; kaynak kimliği/başlığı/metni/gerekçesi taşınmaz.
  Kişinin mevcut API'lerde erişebildiği üyelik/katılım geçmişi korunur.
- DTO'lar e-posta/parola/hash/actor/sourceKey taşımaz; log diagnostic'leri redakte.
  Yanıtlarda no-store, kararlı createdAt/id sırası, boyut 1–100 sınırı.
- Kaynak ilişkileri to-one EntityGraph ile, yerel üyelikler tek batch ve profil tek
  lookup ile yüklenir. Alıcılar Java'da tek tek sorgulanmaz/kilitlenmez.

## Kalıcı üretim ve domain entegrasyonu

`NotificationWriter` **MANDATORY** transaction ile çağıran iş işleminin transaction'ında
set tabanlı `INSERT … SELECT` yapar. Kaynak ve inbox birlikte commit/rollback olur;
async/bellek içi AFTER_COMMIT teslimi veya dış servis yok. Yeni runtime bağımlılığı eklenmedi.

UNIQUE(recipient_id,source_key) ve yalnız bu conflict hedefinde DO NOTHING kullanılır.
Anahtar backend'deki kaynak ID/type/flush edilmiş version'dan üretilir; istemciden alınmaz.
Beklenmeyen diğer SQL hataları yutulmaz. Başlık/metin/gerekçe DB'de kopyalanmaz;
mesaj, sabit type üzerinden render edilir.

| Kaynak işlem | Bildirim |
| --- | --- |
| İlk duyuru/etkinlik yayını | Snapshot'taki aynı üniversitede ACTIVE kulüp üyelerine; aktör hariç |
| Kulüp onay/ret | Güncel ACTIVE OWNER |
| Kulüp askı/restore/archive | Geçişteki ACTIVE üyelere |
| Üyelik başvurusu | İlgili ACTIVE OWNER/MANAGER + güncel CLUB_ADMIN |
| Üyelik onay/ret/çıkarma/yeniden başvuru izni/askı/restore | İlgili öğrenci |
| Yerel rol ve sahiplik devri | Etkilenen üyelikler; devirde eski/yeni sahip |
| Etkinlik erteleme/manuel iptal | İşlem öncesi REGISTERED/ATTENDED katılımcılar |
| Katılım kaydı/check-in/çıkarma/yeniden kayıt izni | İlgili öğrenci |
| Üyelik kaybıyla veya kulüp arşiviyle bilet iptali | Gerçekten sonlandırılan REGISTERED kayıtlar |

Yayında alıcı snapshot'ı tek SQL statement'ında alınır; sonradan katılanlara backfill yok.
Duyuru metin düzeltmesi, taslak işlemleri, kişinin kendi üyelik/katılım iptali ve NO_SHOW
kutuyu ayrıca doldurmaz. Manuel etkinlik iptalinde aynı kişi için ikinci bilet-iptal
bildirimi üretilmez. Arşivde REGISTERED için bilet iptali, ATTENDED için etkinlik iptali
seçilir. Önce bildirim INSERT SELECT, sonra event kilidi altındaki eşleşen bulk update
çalışır; bilet anahtarı beklenen version+1'i kullanır. Rollback ikisini de geri alır.

E-posta, tercihler ve dış teslim/outbox genel kapsamda kalır; bu aşamada
uygulama içi kutu geliştirildi. Dış ağ çağrısı DB transaction'ına eklenmedi.

## Kilit altyapısı — önce ayrı doğrulandı

`UserRepository.findByIdForUpdate` native PostgreSQL **FOR NO KEY UPDATE** kullanır.
`ClubWorkspace.lockUser` kilidi FOR UPDATE'a yükseltmeyen refresh ile güncel entity/rolü
okur. Kullanıcı → club → event → announcement → notification sırası korunur.
Kimlik/e-posta değiştiren kullanıcı CRUD akışına fan-out eklenmedi.

Notification entity/kodu eklenmeden önce, mevcut V007 test DB'sinde tam verify:
**297 izole + 33 PostgreSQL = 330 Java testi başarılı**, contextLoads dahil.
Bu 33 gerçek test; önceki 31 ve iki yeni kilit testidir:

- İki transaction karşılıklı kullanıcı NO KEY UPDATE kilitleri altında KEY SHARE
  alabiliyor; FK alıcı kontrolüyle uyum kanıtlandı.
- Aynı kullanıcıyı değiştiren ikinci yazıcı hâlâ kilitleniyor; kontrollü lock_timeout
  ve SQLSTATE 55P03 doğrulandı. Güvenlik/kapasite/üyelik/üniversite yarışları da geçti.

Bu koşu yalnız campus_plus_test üzerinde ayrı yeni fixture'lar oluşturdu; şema veya
PG rol/parola/servis ayarı değiştirilmedi. Ana DB kullanılmadı.

## Son bildirim kodunun test sonucu

**330 izole Java testi başarılı**, hata/başarısız/atlanan yok:

- Eski 297 regresyon korundu.
- 27 NotificationModulesTest: gerçek JWT/security/MVC/servis/policy, mock persistence;
  sahiplik ve ADMIN izolasyonu, sayfalama/count, read/unread/version/read-all, üyelik/
  üniversite/rol kaybı, gizli/tutarsız kaynak redaksiyonu, kişisel geçmiş, batch lookup,
  validation, no-store, diagnostic içerik ve geri giden saat koruması.
- Altı ek Announcement/Event/Club entegrasyon testi: publish-save sırası, actor/recipient
  kimliği, yayın/düzeltme ayrımı, erteleme/iptal, gerçek bilet iptali ve sahiplik devri.
- Hibernate metadata testi Notification eşlemelerini ve yeni HQL query/bulk update'i
  gerçek Hibernate ile JDBC erişimi olmadan derledi. Native user lock sorgusu, önceki
  V007 canlı koşusunda test edildi; HQL derlemesine native SQL verilmez.
- İlk bazı policy testleri security context olmadan context yüklemeye çalışıyordu;
  yalnız bu doğrudan policy fixture'ları açık actor context kullanacak şekilde düzeltildi.
  Üretim authentication kuralı gevşetilmedi. Son tam koşu başarılıdır.
- **34 Python testi başarılı**; V001–V007 checksum'ları ve mevcut migration seti korunur.
- Güncel V007 CI workflow'u actionlint'ten geçti. V008 CI kurulumu henüz hazırlanmadı/
  çalıştırılmadı; push yok.

İlk 330 sayısı bildirimden önceki karma Java koşusudur; ikinci 330 yalnız son kodun
izole test sayısıdır. **Notification kodunun gerçek context/API/SQL koşusu henüz yapılmadı.**

`PostgresNotificationIT` **11 test hazır ve derlendi**, V008 onayı sonrası çalışacak:
alıcı snapshot/dedupe, okunma ve ADMIN izolasyonu, kaynak gizliliği, gerçek çapraz
manager fan-out, read-all/unread yarışı, insert sonrası tam rollback, REGISTERED/
ATTENDED iptal ayrımı, üyelik/archive bileti, SQL CHECK/unique/FK, cascade/SET NULL,
100 ayrı kaynakta sorgu bütçesi ve MANDATORY transaction koruması.
Hesap silme testi yalnız yeni disposable fixture hesabı/rolüne transaction içinde
uygulanıp rollback edilir; kullanıcı/rol/bildirim kayıtları net olarak silinmez.
Yeni notification SQL'leri bu noktada gerçek PostgreSQL'de yürütülmüş sayılmaz.

## REST

| Metot / yol | İstek |
| --- | --- |
| GET /api/me/notifications | unreadOnly=false, page=0, size=20 |
| GET /api/me/notifications/unread-count | Yok |
| POST /api/me/notifications/{id}/read | version >=0 |
| POST /api/me/notifications/{id}/unread | version >=0 |
| POST /api/me/notifications/read-all | upToId >0 |

NotificationResponse: id/type/createdAt/readAt/version/available/message/target.
Target yalnız erişilebilir kaynakta resource/id/clubId/eventId içerir.
401 authentication, 404 başka alıcının/yok notification, 409 eski version;
400 eksik/negatif version veya geçersiz cutoff/sayfa. Kullanıcı userId/recipientId
isteğiyle alıcı değiştiremez. Genel notification oluşturma veya admin inbox endpoint'i yok.

## V008 SQL — uygulama onayı bekleniyor

[Kesin SQL](../db/managed-migrations/V008__add_notifications.sql) ve
[transaction ayarı](../db/managed-migrations/V008__add_notifications.sql.conf).
SHA-256: `d552f4293b011363c0083b8139816e4c63b7a5ab705befec1175a0ef6c46acd7`.

- Bir yeni `notifications` tablosu: **13 sütun**, bir identity sequence.
- **14 constraint**: 7 FK + PK + unique + 5 CHECK; 23 tip ve kaynak kombinasyonları doğrulanır.
- **10 indeks**: 8 açık + PK/unique için 2 otomatik indeks; okunmamış partial indeks dahil.
- recipient FK CASCADE yalnız gelecekte gerçekten silinen hesabın kendi inbox'unu temizler;
  actor FK SET NULL diğer bildirimleri korur. Diğer kaynak FK'leri RESTRICT.
- Mevcut dokuz iş tablosunda ALTER/UPDATE/DELETE/backfill/seed yok. Yeni tablo boş başlar.
  FK oluşturmanın parent tablolarda kısa kilit etkisi olabilir; lock_timeout=5s ve
  statement_timeout=30s. Hata durumunda repair/clean veya sessiz tekrar deneme yok.
- Flyway history'ye yalnız V008 SQL kaydı eklenecek; uygulanmış V001–V007 değişmeyecek.
- [Test grant](../db/provisioning/test/06-notification-runtime-grants.psql): yalnız yeni
  tabloya SELECT/INSERT/UPDATE ve sequence'e USAGE/SELECT. DELETE/TRUNCATE, sequence
  reset, PG rolü/parola/DDL yetkisi yok.
- [Ana grant](../db/provisioning/main/03-notification-runtime-grants.psql) sonraki ayrı
  ana DB onayı için hazır; test onayı ana uygulama izni değildir.

V008 **review dizininde, Flyway discovery dışında** tutulur. Mevcut target/kontrat/CI V007.
Yeni entity nedeniyle **bu çalışma ağacını V007 DB ile başlatmak validate hatası verir**;
validate kapatılmayacak. İlk kilit testinde paketlenen V007 jar/servis ayarları bu kod
adımında yeniden paketlenip değiştirilmedi. Ana/test DB şemaları V007 olarak korundu.

## Onay sonrası önce yalnız test DB

1. SQL aynı byte/checksum ile managed konumuna alınır; Maven target=8, sürümlü inspector,
   yeni V008 kontratı ve CI provisioning adımları hazırlanır. Eski kontratlar korunur.
2. Yalnız `127.0.0.1:5434/campus_plus_test` PG17 ve V007 history/şema, dokuz tablonun
   veri/ACL özetleri ve sınırlı migrator kimliği salt okunur doğrulanır.
3. V008 transaction içinde uygulanır; eski veri/ACL ve yeni tablo/constraint/indeksler
   doğrulanır. Yukarıdaki sınırlı test grant uygulanır. Katalog SQL ile incelendikten
   sonra yeni tam kontrat hazırlanır; körlemesine snapshot kabul edilmez.
4. contextLoads dahil bütün Java testleri, eski 33 PG test ve 11 yeni Notification IT,
   Python/kontrat/checksum testleri çalışır. Yeni ayrı fixture'lar yalnız test DB'de
   bırakılır; rollback testleri mevcut gerçek kayıtları silmez. Ana DB'ye bağlanılmaz.
5. Başarı raporu sunulur; ana V008 + main grant için **ayrı açık onay** beklenir.
