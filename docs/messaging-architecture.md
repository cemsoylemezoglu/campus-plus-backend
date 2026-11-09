# Özel mesajlaşma — tasarım ve güvenlik temeli

Durum: 2026-11-09, entity/repository/service/controller ve **V012 test/ana aşamaları tamamlandı**.
**711 Java (613 izole +98 PG) /48 Python başarılı**, contextLoads ve 14 mesajlaşma
PG testi dahil. V012 test ve ana DB'de ayrı açık onaylarla uygulandı. Ana ortamda
Hibernate/Flyway validate ve **116 salt okunur GET** geçti; kayıtlar korundu ve
yeni tablolar boş kaldı. Maven/CI hedefi12, skip=true ve validate korunuyor.
Commit/push yok. [Test kanıtı](messaging-test-validation.md), [ana doğrulaması](messaging-main-validation.md).
Bu belge aşağıda iş kuralları ve uygulama sözleşmesini tanımlar.

## 1. Kapsam ve erişim

İki öğrenci arasında kalıcı metin konuşması, cursor geçmişi, konuşma kutusu,
okunmamış sayısı, okundu işaretleme ve engelleme birlikte geliştirilecek.
Grup/kulüp sohbetleri, dosya ekleri ve gerçek zamanlı teslim sonraki ayrı
genişletmelerdir; birebir mesajlaşma tablolarına grup yetkisi eklenmeyecek.
İlk taşıma REST olacaktır; yeni broker veya WebSocket bağımlılığı gerekmiyor.

- Yeni konuşma ve her yeni mesaj: iki mevcut STUDENT, her iki kullanıcıya ait
  güncel profil, aynı aktif üniversite ve iki yönde de engel olmaması gerekir.
- İstekten gönderen kimliği alınmaz; güvenilir kimlik mevcut JWT principal'ıdır.
  Profil/rol/engel bilgileri her yazmada veritabanından yüklenir.
- Konuşma geçmişi yalnız iki katılımcıya açıktır. ADMIN ve CLUB_ADMIN dahil üçüncü
  kullanıcıların özel mesaj erişimi yoktur. Kulüp yöneticiliği mesaj yetkisi değildir.
- Üniversite değişimi, üniversitenin pasife alınması, STUDENT rolünün kaldırılması
  veya engel, yeni mesajı durdurur. Mevcut katılımcı kendi konuşma geçmişini ve
  okundu durumunu yönetebilir. Bu erişim içerik sahibinin geçmişini korur;
  başka kullanıcıya profil/e-posta erişimi vermez.
  Bu kural mevcut JWT authentication şartlarını kaldırmaz: NULL hash'li veya hiç
  rolü kalmamış hesaplar mevcut converter gereği 401 alır. Geçmiş erişimi, kimliği
  doğrulanabilen katılımcılar için korunur.
- Engellenen kişi engelin yönünü, alıcının profil/rol durumunu öğrenmez. Yazmaya
  elverişsiz alıcılar aynı genel hata ile döner. Yetkisiz konuşma/mesaj 404,
  authentication eksikliği 401, geçersiz istek 400, idempotency çakışması 409.
- Mesajlar düz metindir. HTML olarak render edilmez. Mesaj gövdesi, alıcı ve retry
  anahtarı DTO toString çıktısında gizlidir; body/request logging etkinleştirilmez.
  Bu tasarım uçtan uca şifreleme iddiası taşımaz.

## 2. Planlanan veritabanı ve JPA ilişkileri

| Tablo | Temel alanlar ve kurallar |
| --- | --- |
| direct_conversations | id, lower_user_id FK users, higher_user_id FK users, created_at, last_message_at, last_message_number, version; CHECK 0 < lower < higher; UNIQUE(lower,higher) ve UNIQUE(id,lower,higher) |
| direct_messages | id, conversation_id, lower_user_id, higher_user_id, sender_id FK users, message_number, client_message_id UUID, body varchar(4000), created_at; conversation/pair composite FK, sender IN(pair); UNIQUE(conversation_id,message_number), UNIQUE(conversation_id,sender_id,client_message_id); boş gövde reddi |
| direct_conversation_states | id PK, conversation_id, lower_user_id, higher_user_id, user_id FK users, last_read_message_number, updated_at, version; conversation/pair composite FK, user IN(pair), UNIQUE(conversation_id,user_id), CHECK last_read >= 0 |
| user_blocks | id PK, blocker_id FK users, blocked_id FK users, active, created_at, updated_at, version; UNIQUE(blocker,blocked), kendini engelleme reddi; kaldırma soft update |

İki User ilişkisi LAZY, mesaj → konuşma ve gönderen LAZY; entity'ler doğrudan
serialize edilmez. Cascade REMOVE/orphanRemoval yok. Kullanıcı silme mevcut
referansları yok etmeyecek; yeni FK'ler RESTRICT kullanır. Katılımcı bütünlüğü
trigger gerektirmeden sağlandı: message/state satırları konuşmanın canonical pair
alanlarını taşır; composite FK bunların doğru konuşmaya ait olmasını, CHECK ise
sender/user'ın o iki kişiden biri olmasını zorunlu tutar. JPA'da state user_id
scalar tutulur; DB'de users FK'si vardır. State/block için basit identity PK ve
iş anahtarında UNIQUE kullanıldı. Bunlar ilk taslaktaki composite PK'nin aynı
benzersizlik kuralını koruyan nihai mapping karşılığıdır.

Mesaj gövdesi ilk sürümde değişmez; retry aynı normalize gövdeyle aynı mesajı döndürür,
aynı anahtarla başka gövde 409 verir. İstek üst sınırı Java String uzunluğuyla 4000;
PostgreSQL karakter sınırı tek başına uygulama doğrulamasının yerine geçmez.

## 3. API ve DTO sözleşmesi (kod hazır, canlı doğrulama bekliyor)

| Method / path | Davranış |
| --- | --- |
| POST /api/me/conversations | recipientUserId ve message ile konuşmayı bul/oluştur, ilk mesajı atomik gönder |
| GET /api/me/conversations | Katılımcıya özel cursor listesi; minimal karşı taraf kimliği, son mesaj ve okunmamış sayısı |
| GET /api/me/conversations/{id}/messages | Katılımcıya özel mesaj geçmişi; conversation-bound message_number cursor, limit 1–100 |
| POST /api/me/conversations/{id}/messages | SendDirectMessageRequest; gönderen principal'dan |
| PUT /api/me/conversations/{id}/read | throughMessageNumber; kendi receipt'i monoton ilerler |
| GET /api/me/conversations/unread-count | Kullanıcının okunmamış toplamı |
| GET /api/me/blocks | Yalnız kişinin kendi engelleri |
| PUT /api/me/blocks/{userId} | İdempotent engelleme |
| DELETE /api/me/blocks/{userId} | İdempotent soft kaldırma |

Hazır DTO'lar: `SendDirectMessageRequest(clientMessageId, body)` ve
`StartDirectConversationRequest(recipientUserId, message)`; nested validation var.
Yanıtlar `MessagingResponses` içinde Message, Conversation, CursorPage, ReadState,
UnreadCount ve BlockPage DTO'larıdır. Başlatma/gönderme idempotent başarıda 200
döner; Message yanıtında conversationId vardır. E-posta, hash, rol koleksiyonu ve profil
biyografisi yanıtlanmayacak. Özel yanıtlar Cache-Control: no-store taşıyacak.

Mesaj geçmişi sırası immutable message_number DESC; conversation listesi
last_message_at/id cursor kullanacak. Yeni mesaj listeyi değiştirebilir; sayfalar
arasında konuşmanın tekrar görünmesi mümkündür, istemci conversationId ile
birleştirir. Kesintisiz snapshot garantisi verilmeyecek. Mesaj sayfalaması ise
konuşmaya bağlı sabit sıra ve strict `<` ile yapılacak. Cursor yetki belgesi değildir.

## 4. Transaction ve eşzamanlılık

Mevcut kullanıcı/akademik lock sıralarıyla karşılaştırılan yazma sırası:
iki kullanıcı artan ID sırasıyla kilitlenir; iki profil ve güncel roller yeniden
yüklenir, konuşma kilitlenir, üniversite SHARE alınır ve yetki tekrar değerlendirilir.
Hiçbir akış konuşma kilidinden sonra user kilidi almamalıdır. Engelleme de aynı iki
kullanıcı kilidini kullanır: block/send yarışının açık bir sırası olur. Henüz konuşma
yokken iki paralel ilk gönderim kullanıcı kilitleri ve UNIQUE pair ile tek konuşma
üretir; constraint hatası transaction içinde yakalanıp körlemesine devam edilmez.

Konuşmanın mesaj sayacı aynı transaction'da artırılır; mesaj ve iki state atomik
yazılır. Sayaç, timestamp/id commit sırası tersliğinde okundu sayacının teslim
edilmemiş mesajı atlamasını önler. İstemci read numarası o konuşmadaki mevcut mesaja
ait olmalı; başka konuşmanın mesajı veya gelecek sayı kabul edilmez. Read marker
max(eski,yeni) ile geriye gitmez. Retry yeni mesaj/sayaç artışı üretmez.

Servislerin locked-state testleri, DB constraint ve gerçek paralel HTTP testleri
birlikte gerekli. Inbox/count projeksiyonları ve toplu sorgular N+1 üretmemeli.
Mesaj bildirimi eklenirse gövde ve kişi adı kalıcı genel bildirim metnine kopyalanmaz;
recipient erişimi okumada tekrar doğrulanır. Bildirim CHECK değişikliği ayrıca
migration onayı gerektirir; bu temel aşamasında mevcut notifications değişmez.

## 5. Uygulama ve migration planı

1. Tamamlandı: erişim policy'si, canonical pair, güvenli DTO ve DB'siz test temeli.
2. Tamamlandı: entity/repository/service/controller, cursor/idempotency/engel/okundu;
   mevcut user ASC → kaynak → university SHARE sırası korunuyor. V012 SQL managed,
   test/ana grant'ler provisioning konumunda. Kesin SHA
   ve etkiler raporlarda; Flyway hedefi12, skip=true. Yeni entity'ler V012 ister.
3. Tamamlandı: **ayrı açık onayla yalnız campus_plus_test** migration/grant,
   veri/ACL korunumu, contextLoads, API ve yarış testleri. Test fixtures yalnız
   test DB'sinde oluşturuldu; eski satırlar değişmedi. İlk iki test sorunu SQL'e
   dokunmadan kodda düzeltildi; son tam takım başarılı.
4. Tamamlandı: **ayrı ana campus_plus onayıyla** transactional migration, dar grant,
   şema/Flyway/Hibernate doğrulaması ve 116 salt okunur ana GET. Eski 20 tablonun
   mevcut satırları ve ACL'leri aynı; ana DB'de test mesajı veya hesap oluşturulmadı.

Eski V001–V011 değişmeyecek. Yeni tablolar için DML/sequence grant'leri iş bazında
sınırlandırılacak; DELETE/TRUNCATE veya eski tablo yetkisi genişletilmeyecek.
Commit/push ayrıca istenecek. Diğer proje/veritabanı/servislerde işlem yok.

## 6. Zorunlu test matrisi

- İki katılımcı / üçüncü kişi / sahte token rolü / ADMIN gizlilik sınırı.
- Aynı üniversite, yabancı üniversite, eksik/yanlış profile eşleme, pasif üniversite,
  rol kaybı; bunların geçmiş okuma ve yeni mesaj davranışlarının ayrılması.
- Her iki yönde engel ve kaldırma; block/send ve profil değişimi/send yarışları.
- Paralel ilk mesaj → tek konuşma; eşzamanlı idempotent retry → tek mesaj;
  farklı gövde → 409; alıcı ve sender spoofing reddi.
- Cursor konuşma bağlama/bozuk cursor, sınırlar, aynı timestamp, unread ve
  monoton receipt, geciken commit/read yarışları, rollback atomikliği.
- API no-store, içerik log gizleme, DTO validation, FK/pair/participant bütünlüğü,
  dar ACL, migration verileri koruma ve 100 satırda bounded sorgu sayısı.
- İzole testler sonrası tüm mevcut PG IT ve contextLoads; önceki 84 PG sonucu
  yeni kodun gerçek DB testiymiş gibi raporlanmayacak.
