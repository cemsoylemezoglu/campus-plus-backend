# Özel mesajlaşma — V012 ana ortam doğrulaması

2026-11-09. Kullanıcının **ana campus_plus için ayrı açık onayı** sonrasında
yalnız PostgreSQL 17.11 **127.0.0.1:5434/campus_plus** üzerinde V012 ve belirtilen
dar grant uygulandı. Başarılı; beklenmedik şema/veri/yetki sorunu yok.
Başka proje/veritabanı/servis, Docker, PostgreSQL14, Supabase veya Render kullanılmadı.
Commit/push yapılmadı.

## Hedef, SQL ve veri korunumu

Önce salt okunur loopback/port/DB/sürüm ve sınırlı uygulama hesabı kontrol edildi.
Mevcut local `cem` yönetim erişimi doğrulandı; rol/parola/servis ayarı değiştirilmedi.
DB sahibi mevcut `campus_plus_user`; bu sahiplik değiştirilmedi. Exact V011 kontratı,
BASELINE5 +SQL6…SQL11 geçmişi, başka client/default ACL yokluğu doğrulandı.
20 tablonun tüm alanlarını kapsayan sıralı fingerprint ve mevcut ACL kaydedildi.
Test edilmiş Java/POM/SQL SHA manifest'i ve 711/48 test kanıtı aynı kodla eşleşti.

Onaylanan [V012 SQL](../db/managed-migrations/V012__add_direct_messaging.sql)
Flyway transaction'ında **49 ms** içinde uygulandı. Aynı
`5f08e14a190a3206ebca34e5d437498776f90ea4b9a1e0446737c428cb7125bf`
SHA-256 korundu. Eski V001–V011 SQL değiştirilmedi; ana ortamda baseline schema
yeniden uygulanmadı veya yeni baseline atılmadı. Önceki yedi history satırı aynı,
yalnız başarılı SQL12 eklendi (installed_by=cem).

Yeni şema testte kaydedilen [V012 kontratıyla](../db/schema/v012-contract.json)
birebir eşleşti. Dört tablo, dört identity sequence; toplam31 constraint/17indeks:

| Tablo | Kolon / constraint / indeks |
| --- | --- |
| direct_conversations | 7 /9 /5 |
| direct_messages | 9 /8 /5 |
| direct_conversation_states | 8 /7 /3 |
| user_blocks | 7 /7 /4 |

Eski tablo/kolon/constraint'lerde değişiklik yok. Yeni nesnelerin sahibi cem;
eski nesnelerin sahibi, ACL'leri ve database/public schema yetkileri değişmedi.
Yeni FK'ler RESTRICT ve composite pair + participant CHECK'leriyle aynı test
modelini uygular. Şema kontrolü metadata karşılaştırmasıdır; ana DB'de test
INSERT/UPDATE veya constraint bozma denemesi yapılmadı.

## Dar yetkiler

[Ana grant SQL](../db/provisioning/main/07-messaging-runtime-grants.psql)
review konumundan byte-for-byte taşındı; SHA-256:
`16fe633112e379501d5fc75cd925015e355cfb5b59f21027828bf29a5e3eb636`.

Yalnız campus_plus_user için 11 yeni tablo/8 sequence yetkisi:

- direct_messages: SELECT/INSERT; UPDATE yok.
- direct_conversations, direct_conversation_states, user_blocks: SELECT/INSERT/UPDATE.
- Dört yeni identity sequence: USAGE/SELECT; UPDATE/setval yok.

DELETE/TRUNCATE/REFERENCES/TRIGGER veya GRANT OPTION verilmedi. Eski ACL'lerin
tümü korundu; uygulama rolüne elevated flag/membership eklenmedi.
Grant öncesi/sonrası veri fingerprint'leri aynı. Flyway validate başarılı.

## Hibernate ve 116 salt okunur GET

Son başarılı Maven verify JAR'ının class byte'ları target/classes ile karşılaştırıldı.
Bu JAR ayrı geçici Java21 süreçte yalnız loopback/rastgele port üzerinde açıldı.
JDBC `default_transaction_read_only=on`, driver readOnly ve Hikari read-only=true
birlikte kullanıldı; ddl-auto=validate ve show-sql=false. Kalıcı servis/IDE ayarı
değiştirilmedi. JWT için yalnız child süreç ortamında rastgele 48 bayt anahtar
kullanıldı. Kalıcı JWT anahtarı uygulamaya aktarılmadı/değiştirilmedi; korunum için
yalnız dosya hash'i karşılaştırıldı.

Yeni test hesabı açılmadı ve login/register POST yapılmadı. Mevcut iki STUDENT
ID'si için geçici uygulamaya uygun JWT'ler bellekte üretildi; hiçbir token
rapora/komut argümanına veya Git'e yazılmadı. Uygulama Hibernate validate ile açıldı.

116 GET kontrolü başarılı:

- Eksik/geçersiz token401; olmayan kullanıcı ve NULL hash'li eski hesap401.
- Kendi kullanıcı DTO'su, katalog/kulüp/event/announcement ve kişisel history/inbox200;
  başka kullanıcı ve admin işlemleri403, sahte ADMIN claim'leri403.
- Var olmayan kaynak/yorum/konuşma404, geçersiz sayfa/cursor400.
- Yeni konuşma kutusu ve block listesi200/boş; yeni unread200/count0.
- Mevcut pasif üniversiteler nedeniyle /api/feed409; bu kayıtlar aktifleştirilmedi.

116 kontrolün 26'sı yeni mesajlaşma GET/validation/authentication kapsamındadır;
90'ı önceki modüllerin salt okunur regresyonudur. Ana DB'de henüz konuşma olmadığı
için pozitif gönderim/teslim/okundu/engel ve yarış kanıtı burada üretilmedi;
bunlar [test DB'de geçen 14 PG testi](messaging-test-validation.md) ile kanıtlandı.

Sonunda yalnız bu helper'ın başlattığı process PID'si durduruldu. Mevcut servis
başlatılmadı/durdurulmadı; kalıcı JWT dosyasının SHA'sı aynı.

## Son veri/şema/ACL doğrulaması

API kontrollerinden sonra V012 şeması, ACL ve history tekrar eşleşti. Ana DB'deki
**20 eski tablonun bütün satır/kolon fingerprint'leri işlem öncesiyle aynı**:

| Mevcut tablo grubu | Korunan kayıt sayısı |
| --- | ---: |
| users / user_roles | 12 /12 |
| universities / student_profiles | 3 /7 |
| clubs / club_memberships | 4 /7 |
| Diğer 14 eski içerik/etkileşim tablosu | Her biri0 |

Yeni dört tablo hâlâ0 satır. Dört sequence last_value1/is_called=false; hiçbir
ana fixture veya identity tüketimi yok. Son kontrolde başka DB client'ı kalmadı.
Şifre/hash, mevcut rol, üniversite/profil/kulüp verileri ve gizli dosyalar korundu.

Yeni Java değişikliği yapılmadı: bu geçiş aynı **711 Java (613 izole +98 PG) /48
Python** başarılı kod içindir. Yeni bir tam Java koşusu iddia edilmiyor. Ana grant
dosyasının konumuna uyarlanan 48 Python kontrolü, actionlint ve diff check tekrar
başarılı. Workflow hedefi12/skip=true ve validate korunuyor; henüz push/uzak CI yok.

**Mesajlaşmanın test ve ana V012 geçişi tamamlandı.** Birebir REST metin mesajı,
gizlilik, idempotency, history/inbox, unread/read ve block kapsamı doğrulandı.
Grup sohbeti, medya, gerçek zamanlı teslim ve mesaj raporlama/moderasyon işleri
sonraki ayrı modüllerdir; bu rapor bunların tamamlandığını söylemez.
