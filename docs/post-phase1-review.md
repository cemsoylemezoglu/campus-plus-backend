# Post / kampüs sosyal akışı — kod aşaması ve V009 onay noktası

> Bu belge kod aşamasının tarihsel raporudur. Sonraki açık test DB onayıyla
> V009 yalnız campus_plus_test üzerinde uygulandı; SQL byte'ları korunarak
> managed dizine taşındı. Ardından ayrı ana onayla da uygulandı;
> [ana doğrulama](post-main-validation.md). Aşağıdaki onay beklentileri tarihseldir.
> [Sonuç: 451 Java / 38 Python ve V009 test DB doğrulaması](post-test-validation.md).

Tarih: 2026-11-01. Bu aşamada yalnız Campus+ çalışma ağacı değiştirildi.
**DB bağlantısı açılmadı, SQL/grant uygulanmadı; commit/push yapılmadı.**

## Hazır olan kod

- Post/enum ve PostModeration entity'leri, Repository, scalar PostRow projection.
- Student/Club PostService, PostQueryService, PostWorkspace, PostPolicy,
  ADMIN PostModerationService ve MANDATORY PostLifecycleService.
- Öğrenci/kulüp taslak, yayın, düzenleme, gerekçeli terminal kaldırma.
- Sahiplik, güncel üniversite/üyelik/rol ve yerel kulüp yönetimi kontrolleri.
  CLUB_ADMIN marker rolü ve eski kurucu/yazar tek başına yetki sağlamaz.
- Üniversite ve aktif kulüp sınırları SQL/HQL predicate'lerinde. Normal detail
  görünmeyeni 404 yapar; yönetim DTO'ları normal akışa gönderilmez.
- ADMIN metni değiştiremez; ayrı hide/restore ve append-only gerekçeli audit yapar.
  Kaldırılmış içerik restore edilemez; audit hatası source transaction'ını geri alır.
- `(publishedAt DESC,id DESC)` cursor, size+1, filtresine bağlı sürümlü Base64URL
  ikili format. Bilinmeyen/büyük/bozuk alanlar JDBC'den önce 400; cursor yetki vermez.
- ClubService arşivinde event → announcement → post sırası; tüm işlemler aynı Tx.
- Metin düz metindir; istemci HTML çalıştırmamalıdır. Dosya/medya, yorum/beğeni,
  mention bildirimleri ve birleşik Post/Event/Announcement akışı sonraki alt aşamalar;
  [kapsam ve gerekçeler](social-feed-architecture.md) korunur.

Yeni API'lerin tam listesi [tasarımda](social-feed-architecture.md#5-api-ve-dto-sözleşmesi).
Ana yollar: `/api/me/posts`, `/api/clubs/{clubId}/posts`, `/api/posts/{id}`,
`/api/feed`, `/api/admin/posts`. Başarılı kişisel yanıtlar `Cache-Control: no-store`.
DTO log temsilleri gövde/gerekçe gizler; yeni bağımlılık eklenmedi.
Mevcut JWT/BCrypt/RBAC/SecurityConfig ve eski API sözleşmeleri değiştirilmedi.

## Çalıştırılan ve henüz çalıştırılmayan testler

| Kontrol | Sonuç |
| --- | --- |
| Maven offline `test` | **394 başarılı**, 0 hata/başarısız/atlama |
| PostModulesTest | 45 yeni MVC/JWT/servis/yetki/lifecycle testi başarılı |
| PostInputTest | 19 yeni cursor/validation/gizli DTO log testi başarılı |
| AcademicMappingsTest | Yeni entity/projection/HQL dahil DB'siz Hibernate kontrolü geçti |
| Python unittest | **36 başarılı** |
| Tarihsel V001–V005 checksum | Aynı |
| Önceki managed V005–V008, kaynak config, pom | Değişmedi |
| `git diff --check` | Temiz |
| PostgresPostIT (13 yeni test) | **Derlendi, çalıştırılmadı** |
| `contextLoads()` ve diğer 44 PG testi | Bu kod aşamasında **çalıştırılmadı** |

Mock persistence testi gerçek SQL gizlilik/transaction/yariş kanıtı sayılmaz.
Yeni PostgreSQL testleri bunları doğrulamak üzere hazırlandı: gerçek version ve
ownership; üyelik/üniversite/pasif kulüp akışı; eşit tarihli cursor/new insert;
append-only audit yetkileri; publish/publish, publish/archive, publish/profile transfer,
publish/manager demotion ve update/hide yarışları; audit/club archive rollback;
CHECK/FK/UNIQUE SQLSTATE ve 100 gönderide sınırlı sorgu sayısı.

İzole test komutu bu oturumda Mockito agent için izinli çalıştırıldı; testleri
geçirmek için kod, JWT kontrolleri veya DB hedef koruması gevşetilmedi.
PG IT mevcut `PostgresTestDatabase` sınırını kullanır: yalnız tam loopback:5434
campus_plus_test ve ayrı sınırlı runner; her bağlantı başlangıcında kimlik/yetki kontrolü.
JWT test belleğinde; test parolaları/token'ları rapora yazılmaz.

## Kesin SQL ve mevcut verilere etki

[Onaylanan V009 SQL'i](../db/managed-migrations/V009__add_posts_and_moderation.sql)
bu kod raporu hazırlanırken review dizininde, Flyway discovery dışındaydı.
Sonraki test DB onayıyla byte'ları değiştirilmeden taşındı.
`executeInTransaction=true` yan dosyası var.
SQL SHA-256:

```text
c29d73d7bc6ee279b7293117f6e6e963a58febd18c22440a0e4c9b4307787be3
```

1. Exact loopback adresi/5434/DB adı/PostgreSQL 17 ve başarılı V008 kontrolü.
2. **Mevcut clubs tablosuna** `UNIQUE(id,university_id)` eklenir. Zaten id PK
   olduğu için mevcut çiftler benzersizdir; satırlar veya mevcut PK değiştirilmez.
   Destek indeksi oluşturulurken tablo kısa süre kilitlenir; lock_timeout 5 saniye,
   statement_timeout 30 saniye. Beklenmeyen eşzamanlı kullanımda işlem durur;
   timeout yükseltilmez veya aktif istemciler zorla sonlandırılmaz.
3. Yeni `posts`: 16 sütun, identity sequence, PK, 5 FK (club FK + ek composite
   club/university FK dahil), 8 CHECK ve 6 explicit indeks.
4. Yeni `post_moderation_actions`: 7 sütun, identity sequence, PK, 2 FK, 1 UNIQUE,
   3 CHECK ve 2 explicit indeks. Audit runtime tarafında append-only.
5. Mevcut on iş tablosunda UPDATE/DELETE/INSERT/backfill yok; kullanıcı/rol/parola/
   üniversite/profil/üyelik/etkinlik/duyuru/bildirim kayıtları korunur. Eski şema
   değişikliği yalnız clubs üzerindeki ek UNIQUE/index'tir.
6. Yeni FK'ler nedeniyle ileride gerçekten Post/audit kaynağı olan bir kullanıcıyı
   kalıcı silme RESTRICT edilir; sessiz cascade ile içerik silinmez. Hesap yaşam
   döngüsü ayrı aşamada ele alınacak, bu adım hesap silme uygulamaz.

SQL manuel BEGIN/COMMIT kullanmaz: Flyway transaction'ı yönetir. Her hedef için
önce kimlik/history/SQL SHA/on eski tablonun kayıt fingerprint'i/ACL/aktif bağlantı
durumu alınacak. DDL hatasında migration transaction'ı rollback; repair/drop/clean yok.
Identity sequence işlemleri genel olarak transaction rollback'inde boşluk bırakabilir;
ID'lerin ardışıklığı iş kuralı veya veri korunumu göstergesi kabul edilmez.

## Hazır dar grant dosyaları

- [Yalnız test](../db/provisioning/test/07-post-runtime-grants.psql):
  `campus_plus_test_runner` için posts SELECT/INSERT/UPDATE;
  audit SELECT/INSERT; yalnız iki yeni sequence için USAGE/SELECT.
- [Yalnız ana](../db/provisioning/main/04-post-runtime-grants.psql):
  aynı yetkiler yalnız `campus_plus_user` için; **ayrı ana DB onayı gerekir**.

DELETE/TRUNCATE/DDL/rol oluşturma/parola değiştirme/eski nesnelere grant yok.
Audit UPDATE izni yok; sequence UPDATE/reset izni yok. Grant dosyaları ayrı transaction
ve hedef/history/limited-role koruması içerir. Migration başarılı, grant başarısız
olursa DDL'yi geri silmek yerine durup durum raporlanır; runtime testleri başlatılmaz.

## Onaydan sonra uygulama sırası

Şu an ana ve test DB V008 olarak kabul edilen son doğrulanmış durumdadır; bu oturumda
yeniden bağlanıp kontrol edilmedi. Bu yüzden "şu an şema doğrulandı" iddiası yok.
Yeni entity'lerle normal uygulama/context açılışı V009 ister; **validate korunuyor**.
Uygulanmamış SQL discovery'ye alınmadı, Maven/CI hedefi ve v008 kontrat değişmedi.
Bu hazırlık hali henüz push/CI çalışması için hazır bir teslim değildir.

1. Kullanıcıdan **yalnız campus_plus_test üzerinde V009 + yukarıdaki dar grant'ler +
   yeni benzersiz test fixture'larıyla PG testleri** için açık onay alınır.
2. Kesin SQL SHA korunarak managed dizine taşıma; v009 hedef/kontrat/CI ve araç
   allowlist'lerini kontrollü hazırlama. Eski migration/kontrat dosyaları değiştirilmez.
3. Onaylı test hedefi read-only preflight → transaction migrate → yeni şema ve eski
   kayıt/ACL doğrulaması → yalnız yeni nesne grant'leri → limited runner kontrolü.
   Post/audit schema kontratı hem SQL niyetine hem gerçek kataloglara karşı incelenir;
   sadece mevcut DB'den kopyalayıp otomatik doğru sayılmaz.
4. contextLoads dahil bütün Java/Python testleri, SQL constraint ve gerçek HTTP yarışları.
   Yalnız yeni test fixture'ları oluşturulur; silme davranışı testleri transaction
   rollback kullanır. Ana DB'de test verisi oluşturulmaz.
5. Sonuçlar sunulur. Testler başarılı olsa bile **ayrı ana campus_plus V009/grant
   onayı** beklenir. Ana geçiş sonrası uygulama validate ve yalnız GET kontrolleri.

Diğer proje, PostgreSQL 14, Docker, Supabase, Render veya servis ayarına müdahale yok.
Commit/push bu aşama kapsamında yetkilendirilmedi ve yapılmadı.
