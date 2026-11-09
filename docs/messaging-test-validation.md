# Özel mesajlaşma — V012 test veritabanı doğrulaması

2026-11-09. Kullanıcının ayrı açık onayıyla yalnız
**127.0.0.1:5434/campus_plus_test PostgreSQL 17.11** üzerinde çalışıldı.
Ana campus_plus veya başka bir veritabanına bağlanılmadı. Commit/push yapılmadı.

Durum: **V012 test aşaması tamamlandı. 711 Java (613 izole +98 PostgreSQL) ve
48 Python başarılı; sıfır failure/error/skip.** ContextLoads ve yeni 14 mesajlaşma
testi dahil. Son şema/veri/ACL/history/Flyway kontrolleri başarılı.
Ana V012 de daha sonra ayrı açık onayla tamamlandı; [ana rapor](messaging-main-validation.md).
Bu rapor yalnız test aşamasının kanıtıdır; aşağıdaki ana onay planı tarihsel öneridir.

## Uygulama ve korunmuş veriler

Önce salt okunur doğrulama: loopback/5434/DB adı/PG17, sınırlı migrator'ın DB
sahipliği, role membership ve elevated flag yokluğu, başka test client'ı/default
ACL yokluğu, tam V011 yapısal kontratı ve başarılı SQL5…SQL11 geçmişi kontrol edildi.
Eski 20 tablonun tüm kolonları için sıralı fingerprint, ID cutoff ve ACL kaydedildi.
Eski SQL checksum'ları ve onaylanan V012/grant SHA'ları eşleşti.

V012, Flyway transaction'ında **59 ms** içinde uygulandı. Önceki yedi history
satırı korundu; SQL12 eklendi. Şema karşılaştırmasında yalnız dört yeni tablo,
dört identity sequence ve bunların indeksleri bulundu; eski tablolar/kolonlar/
constraint'ler değişmedi. Yeni tablolar grant/test öncesi boştu.

| Tablo | Kolon | Constraint | İndeks (PK/UNIQUE dahil) |
| --- | ---: | ---: | ---: |
| direct_conversations | 7 | 9 | 5 |
| direct_messages | 9 | 8 | 5 |
| direct_conversation_states | 8 | 7 | 3 |
| user_blocks | 7 | 7 | 4 |

31 constraint validated/non-deferrable; 17 indeks valid/ready. Dört identity
sequence BIGINT ve increment1. Composite conversation/pair FK + participant
CHECK'leri başka kullanıcı adına mesaj/state yazılmasını veritabanında engeller.
PK/UNIQUE/sequence dahil yalnız 25 yeni relation oluştu; sahibi test migrator'dır.

Sonra yalnız `campus_plus_test_runner` için **11 tablo +8 sequence yetkisi**:
messages SELECT/INSERT; diğer üç yeni tablo SELECT/INSERT/UPDATE;
sequence'ler USAGE/SELECT. DELETE/TRUNCATE, message UPDATE, sequence UPDATE veya
eski nesnelerde grant yok. Migration/grant sonrasında eski 20 tablonun bütün
değerleri, eski object/database/schema ACL'leri aynıydı.

[V012 SQL](../db/managed-migrations/V012__add_direct_messaging.sql),
[test grant](../db/provisioning/test/10-messaging-runtime-grants.psql),
[gerçek şemadan V012 kontratı](../db/schema/v012-contract.json).
Onaylanan SQL/config/test grant byte-for-byte review konumundan taşındı; SHA'lar
[ilk inceleme raporundaki](messaging-phase1-review.md) değerlerle aynı.
SQL başlığındaki PREPARED/outside discovery ifadesi tarihsel hazırlık notudur;
uygulanmış checksum korunması için dosya değiştirilmedi.

## Testlerde yakalanan ve düzeltilen iki kod sorunu

1. İlk tam koşuda mevcut 84 PG testi/contextLoads geçti, ancak yeni 14 mesajlaşma
   testinde repository sorgusu hata verdi. Spring Data, yardımcı getConversationId
   /getSenderId getter'larını mapped scalar alan saydı. Repository'ye açık
   `m.conversation.id` ve `m.sender.id` JPQL yolları eklendi. Bu sorgular artık
   DB'siz Hibernate metadata testinde de derlenir.
2. Sonraki hedefli koşuda 13/14 geçti. Paralel aynı-key gönderiminde preflight'ta
   yüklenen eski conversation sürümü, kullanıcı kilidini beklerken geride kalıyordu.
   @Lock SELECT, refresh yapılmadan önce version çakışmasıyla 409 veriyordu.
   Kilitli SELECT + refresh yerine doğrudan `EntityManager.refresh(...,
   PESSIMISTIC_WRITE)` kullanıldı; yetki refresh sonrasında tekrar kontrol edilir.
   Gereksiz repository lock metodu kaldırıldı, izole regression testi eklendi.

Son hedefli koşu: **14 PostgreSQL mesajlaşma +2 metadata testi başarılı**.
SQL, schema contract, grant ve bağımlılıklar bu kod düzeltmeleri sırasında aynı kaldı.
Yeni transaction'lar dışında kayıt silme/reset yapılmadı. Başarısız denemelerden
kalan yalnız yeni test fixture'ları da korunur; sequence boşlukları resetlenmez.

Bu hata, mock ve metadata testlerinin tek başına derived query/gerçek yarış
kanıtı olmadığını gösterdi. Düzeltmeler gerçek PostgreSQL üzerinde doğrulandı;
son tam takım sonucu aşağıda ayrıca doğrulandı.

## Test matrisi ve son tam koşu

| Kontrol | Durum |
| --- | --- |
| İzole Java | **613 başarılı**, 0 failure/error/skip |
| Hedefli yeni PostgreSQL mesajlaşma | **14 başarılı** |
| Yeni testlerin kapsadığı başlıklar | teslim/inbox/unread, participant privacy, retry/409, paralel first-send/retry/read, block/send, profil değişimi, FK/CHECK/ACL, rollback, cursor ve 100 mesajda bounded SQL |
| Python | **48 başarılı** |
| actionlint / diff check | Başarılı |
| Tüm PostgreSQL IT + contextLoads | **98 başarılı**, 0 failure/error/skip |
| Tüm Java | **711 başarılı**, son tam verify BUILD SUCCESS |
| Son eski-kayıt/ACL/history/Flyway kontrolleri | **Başarılı** |

100 mesaj testi hem Hibernate hem JdbcTemplate prepare'larını sayar; hedef
güvenlik probe'u hariç en fazla 15 hazırlanmış SQL sınırı hedefli koşuda geçti.
Profil transfer/send testi transaction barrier'ları kullanır; belirli süreli
pg_stat_activity lock-wait ölçümü veya production performans garantisi değildir.

Son tam takımda PostgreSQL dağılımı: Announcement10, ClubInteraction12,
Notification11, Comment15, Event13, Messaging14, contextLoads1, Post13 ve Api9.
Mevcut 84 PG testi ve yeni 14 test aynı son kodla yeniden çalıştırıldı.

Son koşu sonrası V012 kontratı ve SQL checksum'ları eşleşti. Önceden kaydedilen ID
cutoff'larıyla eski 20 tablonun **tüm eski satırlarının tüm alanları** aynı kaldı;
bu karşılaştırma parola/hash ve rol satırlarını da kapsar, içerikleri rapora yazılmaz.
Eski/yeni ACL'ler grant sonrası snapshot ile aynı; ilk yedi Flyway satırı korundu,
son sekiz satırlı history testlerden etkilenmedi. Test runner'ın elevated flag'leri
kapalı, başka test client'ı kalmadı. Test edilen Java/POM/SQL SHA manifest'i aynı.

İzinli testler ve gerekli tekrar denemelerinden sonra yeni mesajlaşma tablolarında
39 konuşma, 78 participant state, 357 mesaj ve 6 block satırı bırakıldı. Kayıtlar
silinmedi. Son salt okunur tutarlılık sorguları: counter/max-message uyumsuzluğu0,
boş konuşma0, iki state'i olmayan konuşma0, geleceğe taşan read marker0.

## Ana ortam sınırı ve aşama sonundaki onay planı (tarihsel)

Bu test aşamasındaki bağlantılar yalnız test hedefindeydi. Ana DB o sırada V011
durumundaydı; yeni build'in onaylı V012 öncesi ana DB ile açılmaması gerekiyordu.
Sonradan ayrı ana onayla geçiş tamamlandı; güncel durum için
[ana doğrulama raporu](messaging-main-validation.md) esas alınmalıdır.
Maven/CI hedefi 12, varsayılan skip=true; otomatik startup migration yok ve
ddl-auto=validate aynı. CI workflow/şema araçları V012 için hazırlandı ve lint
edildi; bu değişiklikler push edilmedi, uzak CI sonucu iddia edilmiyor.

Ana grant [provisioning konumuna](../db/provisioning/main/07-messaging-runtime-grants.psql)
ayrı onaydan sonra taşındı. Bu test aşaması sonunda hazırlanan ana onay planı:
aynı V012 ve yalnız yeni
nesnelere dar runtime grant, öncesi/sonrası salt okunur şema/ACL/veri/history
karşılaştırması, Flyway/Hibernate validate ve yalnız GET smoke planlanıyor.
Ana DB'de test hesabı/mesajı/kulüp/üniversite oluşturulmayacak; mevcut servis
yapılandırması veya kalıcı JWT anahtarı değiştirilmeyecek.
