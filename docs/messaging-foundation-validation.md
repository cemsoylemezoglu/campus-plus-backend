# Mesajlaşma güvenlik temeli — 2026-11-09

Tarihsel ilk adım sonucu. Sonraki entity/API kodu ve güncel 612/48 sonucu için
[V012 inceleme raporu](messaging-phase1-review.md) esas alınmalıdır.

Başlangıç: main `d39c734`, çalışma ağacı temiz. V011 tam doğrulama sonuçları kaynak
README ve önceki raporlardan kontrol edildi; bu adımda uzak GitHub/CI durumu veya
PostgreSQL yeniden sorgulanmadı. Commit/push yapılmadı.

## Değişiklikler

- `DirectConversationMembers`: pozitif ve farklı iki kullanıcı için canonical
  sıralama; ters yönden başlatma aynı kimliği üretir, self/invalid pair reddedilir.
- `DirectMessagePolicy`: yalnız iki katılımcı okur. Yeni mesaj için iki güncel
  STUDENT, kendilerine ait profil, aynı aktif üniversite ve CLEAR contact gerekir.
  NULL contact sonucu güvenli biçimde reddedilir; yönetim rolü bypass sağlamaz.
- `SendDirectMessageRequest`: zorunlu UUID clientMessageId, strip edilmiş boş
  olmayan en fazla 4000 karakterlik metin; içerik ve anahtar log çıktısında gizli.
- `StartDirectConversationRequest`: pozitif alıcı ID'si ve nested message
  validation; recipient dahil toString gizli.
- `messaging-architecture.md`: kalıcı model, API, gizlilik, block/read/idempotency,
  transaction sırası ve ayrı test/ana migration onayları için tasarım.
- İki test sınıfında 25 yeni test: rol/katılımcı sınırları, farklı üniversite,
  profil eşleştirmesi, engeller, geçmiş/yazma ayrımı, kimlik ve input sınırları.

Yeni component henüz controller/service akışına bağlı değildir. Entity/repository,
HTTP endpoint ve migration eklenmedi; mevcut authentication ve iş kuralları aynı.
Bu testler gerçek mesaj teslimi veya DB yarış doğrulaması değildir.

## Çalıştırılan kontroller

| Kontrol | Sonuç |
| --- | --- |
| Java 21, Maven 3.9.16 `-o test` | **565 test, 0 failure/error/skip**, BUILD SUCCESS |
| Yeni DirectMessagePolicyTest | 17 başarılı |
| Yeni DirectMessageInputTest | 8 başarılı |
| `python3 -m unittest discover -s scripts/tests` | **44 başarılı** |
| `git diff --check` | Başarılı |
| PostgreSQL IT / contextLoads | Bu aşamada çalıştırılmadı; DB bağlantısı yok |

Wrapper ilk denemede sandbox dışında Maven distro dizini oluşturamadı. Mevcut
kurulu 3.9.16 binary'si kullanıldı. Sandbox içindeki ilk Java test koşusu Mockito
JVM attach kısıtıyla başarısız oldu; uygulama kodu değiştirilmeden aynı offline
komut uygun izinle tekrar çalıştırıldı ve 565 testin tamamı geçti. Maven wrapper,
pom veya servis ayarı bu nedenle değiştirilmedi.

Önceki V011 sonucu 624 Java (540 izole +84 PG) ve 44 Python'dur. Yeni izole 565
sonucuna eski 84 sayısı eklenerek yeni bir tam PostgreSQL koşusu iddia edilmez.

## Sıradaki adım

Tasarım uyarınca entity/repository/service/controller ve DB bütünlük modelini
geliştirmek; mesaj geçmişi, read state, iki yönlü engel ve idempotent gönderimi
birlikte test etmek. V012 adayı SQL yalnız review dizininde hazırlanacak;
kesin SQL, kilit/veri etkisi ve dar grant'ler gösterildikten sonra kullanıcıdan
ayrı test migration onayı beklenecek. Ana ortam için daha sonra ayrıca onay gerekir.

Mevcut V001–V011, Maven/CI hedefi 11 ve ddl-auto=validate korunmuştur. Bu oturumda
hiçbir veritabanı, rol, parola, servis veya başka proje değiştirilmedi.
