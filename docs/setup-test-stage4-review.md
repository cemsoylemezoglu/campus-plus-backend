# Kurulum / test altyapısı — 4. aşama uygulama sonucu

Tarih: 2026-10-31. **Ana DB baseline ve yerel doğrulamalar tamamlandı. CI hazır,
uzak workflow ilk onaylı push sonrasında çalıştırılacak.** Commit/push yapılmadı.

## Onay ve hedef

Somut metadata SQL/etki planının ardından kullanıcı devam etme onayı verdi.
Yalnız `127.0.0.1:5434/campus_plus`, PostgreSQL 17.11 (`170011`) kullanıldı.
Mevcut `cem` yönetici rolüyle sadece `flyway:baseline` çalıştırıldı;
migrate/repair/clean/undo veya tarihsel SQL tekrar çalıştırma yapılmadı.

Ön kontroller: salt okunur gerçek kimlik, V005 kontratı, legacy SHA-256,
history yokluğu, etkin event trigger/default grant yokluğu ve başka aktif client
transaction'ı bulunmadığı doğrulandı. DB trafiği durdurulmadı veya bağlantı öldürülmedi.

## Eklenen metadata

- `public.flyway_schema_history`: beklenen 10 sütun.
- `flyway_schema_history_pk` PK unique indeksi ve `flyway_schema_history_s_idx`.
  İndeksler valid/ready; tablo ve indekslerin sahibi `cem`, ek ACL yok.
- Tek satır: rank `1`, version `5`, type `BASELINE`, checksum NULL, success true,
  execution_time `0`, installed_by `cem`;
  description/script `Campus+ reviewed V005 schema`.

Metadata özet/şekil kontrolü [baseline_history.sql](../db/verification/baseline_history.sql)
ile salt okunur yapıldı. Yeni role/identity/sequence/iş tablosu sütunu eklenmedi.
Uygulama hesabının history INSERT/UPDATE/DELETE/TRUNCATE yetkisi yok.

Flyway info: V005 başlangıç SQL'i **Ignored (Baseline)**, version 5 marker **Baseline**.
Flyway validate başarılı. Bu, V001–V005'in Flyway tarafından tek tek çalıştırıldığı
veya checksum'larının history'ye alındığı anlamına gelmez; sonraki gerçek migration V006 olur.

## Veri ve yetki koruması

Öncesi, baseline sonrası ve bütün başlatma/test işlemleri sonunda:

- İş şeması V005 kontratıyla eşleşiyor; eski V001–V005 dosyaları aynı SHA-256 değerinde.
- Altı iş tablosunun tüm alanları kapsayan toplu satır özetleri ve sayıları aynı:
  users 12, user_roles 12, universities 3, student_profiles 7, clubs 4, club_memberships 7.
- DB/public şema sahipliği ve ACL'leri, bütün eski public tablo/indeks/sequence
  sahiplikleri ve ACL'leri aynı. Yalnız onaylı üç yeni metadata relation'ı eklendi.
  [Salt okunur sahiplik/ACL envanteri](../db/verification/object_permissions.sql).
- Ana DB'ye test kullanıcısı, rol ilişkisi veya başka iş verisi eklenmedi/değiştirilmedi.
- Normal uygulama başlatması ve testler history satırını/şeklini değiştirmedi.

Raporların ham sürümleri yalnız 0600 geçici dosyalarda; kullanıcı/e-posta/hash satırları
veya token/sırlar Git dosyalarına/sohbet çıktısına alınmadı.

## Başlatma ve API kontrolü

Normal paketlenmiş uygulama jar'ı Java 21 ile mevcut sınırlı `campus_plus_user`
hesabını kullanarak ayrı random loopback portunda açıldı; Hibernate `ddl-auto=validate`
başarılı. Test profili ana DB'ye yönlendirilmedi. SQL logging kapatıldı; JDBC bağlantısına
yalnız bu geçici süreçte server read-only oturum seçenekleri verildi. Dosya/rol/DB/servis
yapılandırması kalıcı değiştirilmedi. Kaynak URL korumaları gevşetilmedi.

Yalnız GET çağrıları yapıldı:

- Anonim korumalı kullanıcı okuması: 401.
- Hash'i ve rolü bulunan mevcut hesap, geçici uygulamaya ait bellek içi JWT: 200;
  yanıt DTO'sunda parola/hash yok. Yanıt değerleri veya token loglanmadı.
- Eski NULL-hash hesap, imzası geçerli aynı geçici JWT: 401; mevcut güvenlik kuralı korundu.

Bu deneme için üretilen key/token bellekteydi; gerçek JWT_SECRET dosyası/değeri değişmedi.
Sadece doğrulama için açılan süreç kapatıldı; mevcut uygulama/servislere müdahale edilmedi.

İlk doğrulama başlatıcısında eklenen geçici Hikari read-only/connection-init seçenekleri
pool sealed yapılandırma hatası verdi. Üretim kodu değiştirilmedi; yalnız başlatıcı,
pgJDBC `options` ile session `default_transaction_read_only=on` ve `readOnlyMode=ignore`
seçeneklerini kullanacak şekilde düzeltildi. Bu seçenekler sadece salt okunur smoke
kontrolü içindir; normal yazmalı uygulamaya eklenmedi. İlk GET denemesinde seçilen en
eski hesabın hash'i NULL olduğundan 401 alındı; uygun hesap seçilerek 200 ve legacy
hesapta beklenen 401 ayrı doğrulandı. Authentication davranışı değiştirilmedi.
Yeni Hikari ayarları eklenirse paketlenmiş normal uygulama başlatması ayrıca test edilmelidir.

## Test sonucu

- `-Ppostgres-it verify`: **203 Java testi başarılı** — 193 unit + 10 gerçek PostgreSQL IT,
  hata/başarısız/atlanan yok. ContextLoads, HTTP/JWT/RBAC, constraint/rollback ve üç
  eşzamanlılık senaryosu dahil. Test ortamı yalnız `campus_plus_test` ve sınırlı runner;
  ana DB/JWT değişkenleri test sürecinden kaldırıldı. Test fixture'ları burada bırakıldı.
- **23 Python testi başarılı** — 16 şema aracı + 7 CI sınır/terminal testi.
- Git diff whitespace kontrolü temiz. Runtime Java/resource/dependency değişikliği yok.

## Kalan iş

CI statik kontrolü önceki hazırlıkta geçti; GitHub'da gerçek Ubuntu/PG17 workflow'u
henüz çalıştırılmadı. Commit/push kullanıcı ara verirken ayrıca istediğinde yapılacak;
ilk uzak CI sonucu o zaman incelenecek. Bu sınır yerel test başarısından ayrı tutulur.

Yerel kurulum/migration altyapısı hazır. Sonraki ürün aşaması Event/EventParticipation
tasarımı: kulüp bazlı yönetim, yaşam döngüsü, üniversite/üyelik uygunluğu, kontenjan/
son yer yarışı, tekil katılım, iptal/yeniden kayıt ve saat dilimleri. Önce tasarım sunulur;
yeni migration hazırlanması/uygulanması yine ayrı onaya tabidir.

PostgreSQL 14, diğer projeler/DB'ler, Docker, Supabase ve Render kullanılmadı.
Teknik dayanak: [pgJDBC bağlantı seçenekleri](https://jdbc.postgresql.org/documentation/use/).
