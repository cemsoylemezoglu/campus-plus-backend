# Mesajlaşma — kod sonucu ve V012 uygulama onay planı

Tarihsel kod/SQL inceleme raporu. Ayrı açık onay sonrasında test V012 uygulandı;
güncel ilerleme ve testlerde düzeltilen sorunlar [test raporundadır](messaging-test-validation.md).
Ana V012 de ayrı açık onayla tamamlandı; [ana rapor](messaging-main-validation.md).
Aşağıdaki sayılar ve bekleyen adımlar ilk kod aşamasına aittir.

2026-11-09. **612 izole Java /48 Python başarılı**. 14 yeni PostgreSQL IT yalnız
derlendi; çalıştırılmadı. İnternet kesintisi sonrası tüm izole takım yeniden
çalıştırıldı. Bu oturumda DB bağlantısı, migration, grant, fixture, servis değişimi,
commit veya push yapılmadı. Başlangıç commit'i d39c734; önceki aşama değişiklikleri korundu.

## Hazır kod

- Dört JPA entity ve dört repository: DirectConversation, DirectMessage,
  DirectConversationState, UserBlock. Lazy ilişkiler, optimistic version,
  append-only mesaj, soft block, hiçbir cascade delete yok.
- DirectMessageWorkspace: MANDATORY transaction, read-only yazma reddi;
  iki kullanıcı ASC NO KEY UPDATE → conversation WRITE → university SHARE.
  Kilit sonrasında kullanıcı/profil/üniversite refresh ve güncel block kontrolü.
- DirectMessageService: aynı pair için tek konuşma; ilk mesaj ve iki state aynı
  transaction'da. Idempotency conversation+sender+UUID kapsamındadır; aynı anahtar
  ve normalize metin aynı mesaj, farklı metin 409. Yetki her retry'da önce kontrol
  edilir. Sıra numarası konuşma kilidi altında artar, retry artırmaz.
- Okundu konumu mevcut mesajda olmalı, gerilemez. Sender'ın kendi mesajları
  unread sayılmaz; gönderme, okunmamış gelen mesajları kendiliğinden okundu yapmaz.
- Scalar JdbcTemplate read projection: konuşma kutusu, unread, cursor geçmişi,
  yalnız kendi block listesi. Query parametreleri bind edilir; SQL hedefleri sabit.
- API /api/me/conversations ve /api/me/blocks; endpoint listesi
  [tasarım belgesinde](messaging-architecture.md). Başlatma/gönderme 200, block
  PUT/DELETE 204. Yanıtlar no-store. DTO/entity/page toString içerik gizler.
- Mesaj body 1–4000, strip, NULL/blank/NUL reddi. Gönderen principal'dan alınır;
  alıcıya ait e-posta, hash, rol ve retry anahtarı response'a eklenmez.
- Global ADMIN/CLUB_ADMIN başka kişilerin mesajlarını okuyamaz. Engel, profil/
  üniversite/rol uygunluğu kaybı yeni mesajı kapatır, kendi geçmişini kapatmaz.

Bildirim tablosu bu adımda değişmedi; unread mesaj sayacı doğrudan yeni modelden
hesaplanır. Genel inbox'a mesaj gövdesi kopyalanmıyor. Grup sohbeti, medya,
WebSocket ve mesaj şikâyet/moderasyon akışları bu birebir modülün tamamlandığı
iddiasına dahil değildir; genel ürün kapsamındaki sonraki işlerdir.

## Test kanıtı ve sınırlar

| Kontrol | Sonuç |
| --- | --- |
| Java 21 / Maven 3.9.16 offline test | **612 başarılı, 0 failure/error/skip** |
| Mesajlaşma izole testleri | 72: önceki 25 temel +47 yeni domain/workspace/service/MVC/query testi |
| Python unittest | **48 başarılı**, 4 yeni migration/grant güvenlik kontrolü |
| Hibernate metadata ve repository HQL | Yeni dört entity/repository dahil DB'siz başarılı |
| V001–V005 historical checksums | schema_tools verify-legacy başarılı |
| Uygulanmış V006–V011 ve config | Git diff'te değişiklik yok |
| diff whitespace | Başarılı |
| PostgreSQL/contextLoads | **Çalıştırılmadı**; V012 ayrı onay bekliyor |

Kod sırasında testte Role import çakışması düzeltildi. JWT/MVC fixture'larına,
mevcut converter'ın istediği non-null test hash'i ve rol kaybı senaryosu için
kalan ADMIN rolü eklendi; authentication kuralları gevşetilmedi. Son tam koşu 612
başarılıdır. Offline Maven mevcut kurulu binary ile, Mockito JVM attach için
uygun izinle çalıştırıldı. Üretim parola/JWT dosyaları okunmadı veya değiştirilmedi.

14 PostgreSQL testi hazır: gerçek teslim/inbox/unread/read, ADMIN/401 sınırı,
retry/409, paralel ilk gönderim, paralel retry, iki yönlü block/unblock, block/send
yarışı, eksik/yabancı/pasif profil, cursor ve read sınırları, forged sender/state
constraint'leri/dar ACL, transaction rollback, 100 mesajda bounded SQL,
paralel monoton read ve user kilidi altında profil değişimi/send.
SQL sayacı hem Hibernate hem JdbcTemplate prepare'larını kapsar (DataSource'un
hedef güvenlik probe'u hariç). Bunlar henüz canlı başarı kanıtı değildir.
Profil yarışı barrier'lıdır; belirli süre pg_stat_activity lock wait kanıtı iddiası yoktur.

## V012 SQL ve mevcut verilere etkisi

Hazırlanan SQL: [V012__add_direct_messaging.sql](../db/managed-migrations/V012__add_direct_messaging.sql).
Flyway taramasının **dışında**; .conf executeInTransaction=true. Varsayılan
Maven/CI hedefi **11**, skip=true, ddl-auto=validate korunuyor. Yeni build V012
gerektirir: **onaylı migration bitmeden V011 DB üzerinde bu build başlatılmamalı**.
Mevcut çalışan servis durdurulmadı veya yeniden başlatılmadı. Bu ara kod henüz
push edilmemeli; mevcut CI'nin V011 test şeması yeni entity'ler için yeterli değildir.

Yalnız yeni nesneler:

| Tablo | Kolon sayısı | Temel bütünlük |
| --- | --- | --- |
| direct_conversations | 7 | İki users FK, sıralı farklı pair, tek pair UNIQUE, composite identity UNIQUE, negatif olmayan sayaç/version ve zaman CHECK |
| direct_messages | 9 | Composite conversation/pair FK, sender users FK + pair CHECK, message number ve retry UNIQUE, body CHECK |
| direct_conversation_states | 8 | Composite conversation/pair FK, user users FK + pair CHECK, conversation/user UNIQUE, nonnegative read/version |
| user_blocks | 7 | İki users FK, self reddi, tek yönlü pair UNIQUE, version/time CHECK |

Toplam **4 yeni tablo /4 identity sequence**, 7 ek arama indeksi (PK/UNIQUE
indeksleri bunlara ek). Eski users/roles/password/university/profile/club ve diğer
tablolarda DML, ALTER, backfill, DELETE veya yeni notification alanı yok.
FK kurulumunda mevcut users tablosunda kısa metadata/referans kilidi gerekebilir;
5 saniye lock ve 30 saniye statement timeout aşımında işlem durur/rollback edilir.
Yeni FK'ler ON DELETE RESTRICT; eski hesap silme işlemi, ileride o hesapla yeni
mesaj/block ilişkisi kurulursa veriyi silmek yerine mevcut genel 409 davranışına gider.

Mesajlar immutable; runtime direct_messages için yalnız SELECT/INSERT alacak.
Diğer üç yeni tablo için SELECT/INSERT/UPDATE; dört sequence için USAGE/SELECT:
**11 tablo yetkisi +8 sequence yetkisi**. DELETE/TRUNCATE, messages UPDATE,
sequence UPDATE/setval veya eski nesnelerde yetki genişletme yok.

Test ve ana grant SQL'leri ayrı hedef/account guard'ları taşır:
[test](../db/provisioning/test/10-messaging-runtime-grants.psql),
[ana](../db/provisioning/main/07-messaging-runtime-grants.psql).
Ana grant dosyasının hazırlanması ana uygulama onayı değildir.

SHA-256:

| Dosya | SHA-256 |
| --- | --- |
| V012 migration | 5f08e14a190a3206ebca34e5d437498776f90ea4b9a1e0446737c428cb7125bf |
| V012 .conf | ddaf198a3fde7d4d00f914293e32fb453f58e386f90d9166f6f5d9914001c698 |
| Test grant | 38a50e6650f1c2bcb0299e7c8003e83252f3bf0d772324fcb059109e8a369d2a |
| Ana grant | 16fe633112e379501d5fc75cd925015e355cfb5b59f21027828bf29a5e3eb636 |

## İstenen sonraki onay ve doğrulama sırası

İlk onay yalnız **127.0.0.1:5434/campus_plus_test PostgreSQL 17** için istenecek:
V012, yukarıdaki test-runner grant'leri ve ayrı yeni test kayıtlarıyla tüm PG
testleri. Ana campus_plus için şu anda uygulama izni istenmiyor.

Onaydan sonra önce bağlantı/DB/sürüm/rol ve V011 geçmişi, eski satırların tüm
alanlarının fingerprint'i/ID cutoff'u, ACL/şema ve yeni nesnelerin yokluğu kontrol
edilecek. Beklenmedik durumda durulacak. İncelenen SQL byte-for-byte managed
konuma taşınacak; migration target/şema araçları/CI hedefi ve güvenlik testleri
onaylı V012 için güncellenecek. Flyway transactional migrate, şema/constraint
envanteri ve kontrat, sonra yalnız belirtilen dar grant'ler doğrulanacak.

V012 sonrasında contextLoads ve mevcut tüm IT dahil tam verify çalıştırılacak;
yalnız yeni benzersiz test hesapları, üniversiteleri, profilleri, konuşma/message/
block kayıtları oluşturulacak. Test üniversitesi pasife alma ve profil transferi
yalnız yeni fixture'lara uygulanır. Fixture'lar silinmez; rollback testleri yalnız
yeni transaction içindedir. Eski kayıtların alanları ve eski ACL'ler karşılaştırılır.
Başarılı sonuç sunulduktan sonra ana DB için ayrı onay beklenir; orada test verisi yok.

Bu aşama private mesajlar için rate limit/abuse report veya production güvenlik
hazırlığının tamamlandığını göstermez. İlgili sertleştirme işleri yayından önce
genel güvenlik/moderasyon aşamasında ele alınmalıdır.
