# Gönderi yorum/yanıt/beğeni — ana Campus+ V010 doğrulaması

Tarih: 2026-11-02. **Ana V010 aşaması tamamlandı.** Kullanıcı aynı SQL ve belirtilen
dar runtime yetkilerini yalnız ana hedef için ayrıca açıkça onayladı. Bu oturumda
yeni özellik geliştirilmedi; commit/push veya force push yapılmadı.

## Hedef ve transaction migration

READ ONLY preflight gerçek **127.0.0.1:5434/campus_plus, PostgreSQL 17.11** hedefini
doğruladı. V009 kontratı, önceki BASELINE5 + SQL6–SQL9 history kayıtları, on iki eski
tablonun değerleri ve nesne ACL/ownership karşılaştırmaları geçti. Aktif başka ana
istemci, event trigger veya yeni nesneler için default grant yoktu.

Mevcut yerel cem hesabı yalnız onaylanan migration/grant için kullanıldı. Hiçbir rol,
parola, PostgreSQL/Homebrew servis ayarı oluşturulmadı/değiştirilmedi. Diğer proje,
PG14, Docker, Supabase veya Render kullanılmadı.

[V010 SQL](../db/managed-migrations/V010__add_post_comments_and_likes.sql) başarılı
test aşamasındaki byte'larla aynı; source snapshot ve jar sınıfları doğrulandı:

```text
cf1097be5f3a58a5a1457def062b3f13972b0b55b5e4937b54a69440397789ae
```

Flyway transaction'ında yalnız V010 uygulandı; yaklaşık 56 ms. lock_timeout=5s ve
statement_timeout=30s korundu. Repair/clean/drop, seed, backfill veya eski business
satırlarına UPDATE/DELETE yok. Başarılı SQL10 eklendi; önceki beş history satırının
tam içeriği ve history kolon/indeks yapısı korundu. Flyway validate geçti.

## Yapı ve veri korunumu

[V010 kontratı](../db/schema/v010-contract.json) ana DB ile eşleşti. Dört yeni tablo:
post_comments, post_likes, post_comment_likes, comment_moderation_actions. Toplam
34 sütun/31 constraint/18 indeks ve dört identity sequence. Notifications'a iki
nullable alan/üç FK/iki partial indeks; type/source CHECK'lerine dört sosyal tip
eklendi. Tüm constraint ve indeksler validated/valid/ready.

Başka eski tabloda yapısal fark yok. Toplam 28 yeni relation doğru sahiplikte;
bildirim indeksleri mevcut notifications sahibiyle, yeni tablolar/sequence/indeksler
cem sahipliğiyle uyumlu. Eski nesne sahiplik/ACL'leri değişmedi.

| Eski tablo | Korunan kayıt sayısı |
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
| notifications | 0 |
| posts | 0 |
| post_moderation_actions | 0 |

Migration öncesi/sonrası/grant sonrası/GET sonrası aynı eski kolonlar için tam row
fingerprint (count + sıralı JSON digest) eşit. Notifications'ta iki yeni NULL alan
projeksiyondan çıkarıldı, eski değerlerin tamamı karşılaştırıldı; yeni alanların
NULL kalması ayrıca doğrulandı. Parola/hash/rol/profil ve okunma/version değişimi yok.

Dört yeni tablo **boş**; dört yeni sequence `last_value=1,is_called=false`.
Ana DB'de hesap, yorum, beğeni, bildirim veya diğer fixture oluşturulmadı.
Ham kayıt veya gizli değer rapora basılmadı.

## Dar uygulama yetkileri

[Ana grant dosyası](../db/provisioning/main/05-comment-runtime-grants.psql) transaction
içinde uygulandı. Yalnız campus_plus_user: üç yeni etkileşim tablosunda
SELECT/INSERT/UPDATE; audit tablosunda SELECT/INSERT; dört yeni sequence'de
USAGE/SELECT. Tam 11 tablo privilege kaydı grantable=NO olarak doğrulandı.

Audit UPDATE, DELETE/TRUNCATE, sequence UPDATE/reset ve history yazma yok.
Eski tablo yetkileri değişmedi; notifications için ek grant gerekmedi/verilmedi.
Uygulama hesabının önceki sınırlı rol durumu korundu.

## Normal jar ve yalnız GET kontrolleri

Test edilen jar'ın BOOT-INF sınıf byte'ları target/classes ile eşleşti, yeni Comment
controller pakette bulundu. Normal jar geçici loopback portunda campus_plus_user ile
başlatıldı; her iki Hibernate şema ayarı **validate** ve başlangıç başarılı.

Yalnız bu geçici sürecin DB bağlantıları default_transaction_read_only=on kullandı;
driver transaction bayrağı bu sınırı kaldıramayacak şekilde ayarlandı. Kalıcı JDBC/
uygulama/servis ayarı değişmedi. Geçici signing anahtarı yalnız süreç belleğindeydi;
gerçek JWT secret dosyası checksum'ı aynı kaldı. Parolalarla giriş yapılmadı.

**72 salt okunur GET kontrolü geçti**: önceki User/JWT/legacy NULL hash, academic,
Club/Event/Announcement/Notification/Post kontrolleri; yeni kendi yorum geçmişi,
boş kişisel sayfa/no-store, userId parametresiyle kapsam değiştirememe, olmayan
Post/comment/reply/interaction/audit yollarında 404, sayfa sınırlarında 400,
kimliksiz/geçersiz/expired JWT için 401 ve normal kullanıcıya ADMIN yollarında 403.
POST/PUT/PATCH/DELETE, read-mark, Register/Login veya fixture yazısı yapılmadı.

### Doğrulamanın sınırı

Ana DB'de Post/comment/like bulunmadığı ve mevcut profiller aktif üniversiteye bağlı
olmadığı için olumlu yorum/yanıt/beğeni/moderasyon işlemleri ana DB'de çalıştırılmadı.
Normal feed mevcut pasif üniversite nedeniyle beklenen 409 verir. Bu korumayı
atlatmak için kayıt/rol/üniversite değiştirilmedi. Olumlu yazma, dolu liste/cursor,
iki moderasyon scope'u, gerçek sahiplik, bildirimler ve yarış/rollback kanıtı
[test DB sonucundadır](comments-test-validation.md).

Yalnız geçici doğrulama süreci durduruldu. Sonrasında V010 kontrat/history/eski
kolon değerleri/ACL'ler ve boş tablo/kullanılmamış sequence'ler tekrar doğrulandı;
başka ana istemci kalmadı. Mevcut servisler etkilenmedi.

## Sonuç ve sonraki adım

Aynı kaynak kodunun son tam sonucu **521 Java (449 izole +72 PostgreSQL) ve 40 Python**;
contextLoads ve 15 yeni PG testi dahil. Kaynak checksum'ları değişmedi. Bu ana DDL/
grant/doküman aşamasında bütün testler gereksiz yere yeniden koşulmadı; ek kanıt
ana Flyway/Hibernate/kontrat/veri/ACL ve 72 GET kontrolüdür.

**Post yorumları/yanıtları, Post ve yorum beğenileri, yerel/platform moderasyonu ve
bildirim entegrasyonu V010 ana/test ile tamamlandı.** Event/Announcement kaynak
entegrasyonları ayrı aşamadır; bu oturumda başlanmadı. Yeni commit/push ve uzak V010
CI çalışması yok. Son başarılı uzak CI hâlâ a82300e V009 sürümüne aittir.
