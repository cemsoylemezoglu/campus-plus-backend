# Yorum / yanıt / beğeni — kod sonucu ve V010 onay noktası

> Bu belge kod aşamasının tarihsel raporudur. Sonraki açık onayla V010 yalnız
> campus_plus_test üzerinde uygulandı; SQL byte'ları korunarak managed dizine
> taşındı; ardından ayrı açık onayla ana geçiş de tamamlandı:
> [ana rapor](comments-main-validation.md). Aşağıdaki bekleyen onaylar tarihseldir.
> [Son test sonucu: 521 Java /40 Python ve veri korunumu](comments-test-validation.md).

Tarih: 2026-11-02. **Kod ve izole test aşaması tamamlandı.** Bu oturumda DB bağlantısı
açılmadı; V010 hiçbir DB'ye uygulanmadı. Commit/push veya force push yapılmadı.
Son tamamlanan V009 sürümü ve ana kayıtları bu aşamada değiştirilmedi.

## Hazır olanlar

- Kök yorum ve iki seviyeli yanıt; create/edit/kendi içeriğini terminal kaldırma;
  görünürlük filtreli cursor liste/detail ve kişiye özel içerik geçmişi.
- Kaldırılmış kökün görünür yanıtları korunur; normal DTO kök body/author alanını
  null yapar. Moderasyonla gizlenen kök tüm alt yanıtları gizler.
- RESOURCE ve PLATFORM gizlemeleri bağımsızdır. Kendi öğrenci Post sahibi veya
  yalnız kendi kulübünün OWNER/MANAGER + CLUB_ADMIN yetkilisi RESOURCE işlemi yapar;
  yalnız ADMIN platform gizlemesini geri alabilir. Kimse başkasının metnini yazamaz.
- Kaynak moderasyon mutasyonu yalnız metinsiz `CommentModerationResult` döndürür:
  yerel restore üzerinden platformun gizlediği body/author okunamaz. RESOURCE audit
  sorgusu yalnız kendi scope'unu sayfalar; platform gerekçesi/count'u sızmaz.
- Post ve yorum beğenileri: PUT/DELETE idempotent; aynı user/source için tek kalıcı
  satır. Re-like ilk like kimliğini korur ve yeni inbox üretmez. Silme active=false;
  yazar kendi içeriğini ve beğenisini kaynak erişimi kaybolsa da geri alabilir.
- Aktör/path kaynaklı kimlik, güncel STUDENT ve normal PostPolicy görünürlüğü;
  SQL/HQL görünürlük filtreleri, @Version ve ortak actor→club→university→post→root→reply
  kilit sırası. Etkileşim Post'un kendi version'ını artırmaz.
- Sayaçlar toplu aggregate sorguları, author scalar projection, sayfa boyutu 1–100.
  Sınırsız ağaç, N+1 body/rol yükleme veya toggle endpoint eklenmedi.
- Dört yeni bildirim tipi: POST_COMMENTED / COMMENT_REPLIED / POST_LIKED /
  COMMENT_LIKED. MANDATORY writer, hedefli ON CONFLICT ve güncel uygun alıcılar.
  Reply aynı kişiye hem post hem root alıcısı olarak gitse bile tek REPLIED üretir.
- Bildirim okuyucusu kulüpsüz Post'u destekler. Mevcut beş kaynak grubunun club
  zorunluluğu korunur; gizli kaynak generic available=false,target=null döner.
  Target mevcut alanlarını koruyup nullable postId ekler. İçerik/isim/gerekçe inbox'a
  kopyalanmaz. Eski Notice tipleri ve read/unread/read-all metotları değiştirilmedi.

Java 21 / Spring Boot / Security / JWT mimarisi ve runtime bağımlılıkları korundu;
SecurityConfig, datasource ayarı, ddl-auto, parolalar/roller veya servis ayarı değişmedi.
Event/Announcement etkileşimleri ayrı kaynak entegrasyonlarıdır; bu alt aşama Post içindir.
[Tasarım ve tam API listesi](comments-likes-architecture.md).

## Dosya grupları

- entity: PostComment, ContentLike mapped superclass, PostLike, PostCommentLike,
  CommentModeration ve enum'lar; Notification'da nullable Post/Comment ilişkileri.
- dto/comment: request, scalar row, normal/own/admin, metinsiz moderation result,
  history/cursor page/interaction count DTO'ları.
- repository: comment/like/audit query'leri; Notification'ın bounded to-one graph'ı.
- security/service/comment: policy, workspace, cursor, query/write/like/moderation.
- controller: normal Post comment, own comment, ADMIN moderation ve like endpoint'leri.
- service/notification: yeni CommentNotificationWriter ve kaynak-aware reader.
- tests: yeni MVC/servis/input/writer testleri, genişletilmiş Notification ve Hibernate
  metadata/HQL regresyonları; yeni PostgresCommentIT.

## Test durumu

| Kontrol | Sonuç |
| --- | --- |
| Tam izole Java `test` | **449 başarılı**, 0 hata/başarısız/atlama |
| CommentModulesTest | 37 yeni JWT/MVC/servis/gizlilik/ownership/moderasyon/like testi |
| CommentInputAndWriterTest | 14 yeni cursor/validation/placeholder/SQL parametre/dedupe/hata yayılım testi |
| NotificationModulesTest | 27 önceki +4 yeni reader testi başarılı |
| AcademicMappingsTest | Yeni entity ve HQL/projection'lar DB'siz Hibernate metadata ile geçti |
| Python | **38 başarılı** |
| actionlint / diff whitespace | Başarılı / temiz |
| Tarihsel ve managed V001–V009 | Değişmedi; V009 kontrat/checksum korunuyor |
| PostgresCommentIT | **15 yeni test derlendi, çalıştırılmadı** |
| contextLoads ve önceki 57 PG testi | Bu kod aşamasında **yeniden çalıştırılmadı** |

İlk tam çalışmada yeni JDBC hata testi Mockito varargs stub'ı yanlış eşleştiğinden
kaldı; stub türü düzeltildi, beklenen exception yayılımı doğrulandı ve **tam izole
suite yeniden geçti**. Üretim hatasını yutma, test atlama veya auth gevşetme yapılmadı.
Kaynak moderasyon yanıtında metinsiz DTO ile gizlilik regresyonu ayrıca kontrol edildi.

Mock testler SQL/transaction/yariş kanıtı sayılmaz. Yeni PG testleri: gerçek yorum
lifecycle/placeholder/count; private club membership loss/unlike; iki scope/audit;
PUT/retry/re-like/tek notice; reply alıcı dedupe/clubless target/root hide;
farklı root/post alıcıları; concurrent PUT ve root-remove/reply; writer insert sonrası
rollback/audit rollback; FK/CHECK/UNIQUE/limited-role; club scope/marker; gerçek cursor;
100 yorum için bounded query count; güncel kulüp yöneticileri ve üyelik kaybı bildirimleri.
V010 onayı olmadan bunlar çalıştırılmayacak, başarı iddia edilmeyecek.

## Kesin SQL ve etki

[Onaylanan V010 SQL'i](../db/managed-migrations/V010__add_post_comments_and_likes.sql)
bu rapor hazırlanırken review dizininde, Flyway discovery dışındaydı. Sonraki test
DB onayıyla byte'ları değiştirilmeden taşındı. `.sql.conf`: executeInTransaction=true.
SHA-256:

```text
cf1097be5f3a58a5a1457def062b3f13972b0b55b5e4937b54a69440397789ae
```

- Gerçek loopback/5434, campus_plus veya campus_plus_test, PG17 ve başarılı SQL9 guard'ı.
- Dört yeni tablo: post_comments, post_likes, post_comment_likes,
  comment_moderation_actions. Toplam 34 sütun, 31 constraint, 18 indeks ve 4 identity
  sequence. Sayılar inceleme SQL'ine göre; gerçek katalog henüz kontrol edilmedi.
- Yorum→Post/author/root; root+post composite FK, like→source/user; audit→comment/actor
  gerçek RESTRICT FK'lerdir. Yazar/moderasyon/kaldırma/blank/enum/time/version CHECK'leri.
- **Mevcut notifications tablosuna** nullable post_id/comment_id, üç FK ve iki partial
  indeks eklenir. İki mevcut type/source CHECK transaction içinde genişletilir.
  Eski 23 tipin kombinasyonları aynı kalır ve yeni kolonları NULL gerektirir;
  yeni dört tip ayrı Post/Comment kombinasyonlarını gerektirir.
- Eski satırlarda UPDATE/DELETE/seed/backfill veya okundu durumu değişikliği yok.
  Yeni kolonların NULL olması eski satırların önceki kaynak kurallarını korur.
  Eski SQL/migration dosyaları düzenlenmez. Notification row JSON'una iki NULL alan
  ekleneceğinden korunum karşılaştırması **eski kolon projeksiyonuyla** yapılmalı;
  ayrıca tüm başlangıç ID'lerinde yeni alanların NULL kaldığı doğrulanmalıdır.
- Notifications ALTER ve CHECK doğrulama taraması kısa tablo kilidi gerektirir.
  lock_timeout=5s, statement_timeout=30s. Beklenmeyen yük/fark/timeout'ta durulur;
  istemci öldürme veya timeout yükseltme yok. Migration transaction rollback sağlar.

## Hazır grant'ler ve onay sırası

[Test grant'i](../db/provisioning/test/08-comment-runtime-grants.psql) yalnız
campus_plus_test_runner; [ana grant](../db/provisioning/main/05-comment-runtime-grants.psql)
yalnız campus_plus_user. Üç yeni etkileşim tablosuna SELECT/INSERT/UPDATE, audit'e
SELECT/INSERT, yalnız dört yeni sequence'e USAGE/SELECT. Audit UPDATE/DELETE,
TRUNCATE, sequence UPDATE/reset, CREATE veya rol/parola değişikliği yok.
Mevcut notifications için ek grant gerekmiyor: var olan tablo hakları korunur.

1. Kullanıcıdan **yalnız 127.0.0.1:5434/campus_plus_test PG17 için V010 + bu dar
   grant'ler + benzersiz fixture'larla testler** için açık onay alınır.
2. V009 şema/history/aktif istemci/SQL SHA/eski on iki tablonun kayıt ve ACL snapshot'ı
   READ ONLY alınır. SQL byte'ları korunarak managed dizine taşınır; v010 kontrat,
   Maven/CI target ve araç allowlist'i SQL paketiyle kontrollü hazırlanır.
3. Test migration transaction'ı → eski kolon/kayıt/ACL korunum doğrulaması ve yeni
   şema/catalog SQL-niyet karşılaştırması → yalnız yeni nesne grant'leri.
4. contextLoads dahil tam Java/Python, gerçek PG API/constraint/rollback/race/sorgu
   sayısı/plan kontrolleri. Kaynak/body/secret veya raw row rapora/loga yazılmaz;
   yalnız yeni fixture'lar test DB'de kalır; otomatik cleanup yok.
5. Başarıdan sonra **ayrı ana DB onayı** istenir. Ana hedefte fixture yok; yalnız
   V010/grant ve salt okunur uygulama/şema/ACL/eski kolon değer kontrolleri.

**Bu çalışma ağacının yeni entity/notification kolonları V010 gerektirir.** Şu an
son doğrulanmış ana/test şeması V009; bu oturumda DB'ye yeniden bağlanıp bakılmadı.
Normal yeni uygulama/context V010 olmadan validate'ı geçemez. Validate kapatılmaz;
normal test DB'ye geri düşmez. Maven/CI hedefi hâlâ 9, eski kontratlar korunuyor.
Bu hazırlık hali onaysız DB'ye başlatılmamalı veya push edilmemeli.

Sınırlı ilk-like dedupe bir rate limiter değildir; yoğun farklı yorumlar/reaksiyonlar
için rate-limit ve geniş moderasyon/şikâyet aşaması ayrıca ele alınır. Bu oturumda
başka proje, PG14, Docker, Supabase, Render veya dış teslim servisi kullanılmadı.
