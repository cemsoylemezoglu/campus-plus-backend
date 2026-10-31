# Event / EventParticipation — ana Campus+ V006 uygulaması

Tarih: 2026-10-31. **V006 ve yalnız yeni nesnelerin runtime yetkileri ana Campus+
veritabanında uygulandı; şema, veri koruması ve uygulama kontrolleri başarılı.**
Commit/push yapılmadı; başka ürün modülüne geçilmedi.

## Onay ve hedef

İlk migration girişimi otomatik onay denetimi tarafından yanıtın yeterince açık
olmaması nedeniyle çalıştırılmadan reddedildi. Kullanıcı daha sonra hedefi ve işlemi
belirten soruya **“Ana Campus+ veritabanında V006 ve belirtilen sınırlı yetkileri açıkça
onaylıyorum.”** yanıtını verdi. İşlem ancak bu açık onaydan ve yenilenen ön kontrollerden
sonra gerçekleştirildi.

Yalnız `127.0.0.1:5434/campus_plus`, PostgreSQL 17 hedefi kullanıldı. Mevcut `cem`
yönetici hesabıyla V006 uygulandı; günlük uygulama hesabı sınırlı `campus_plus_user`
olarak korundu. Ön kontrollerde:

- Gerçek sunucu adresi/portu, veritabanı adı, PG17 sürümü ve hesaplar doğrulandı.
- V005 kontratı, tek başarılı version 5 BASELINE kaydı ve önceki kayıt/yetki
  fingerprint'leri doğrulandı. Yenilenen onay sırasında da aynı kaldıkları kontrol edildi.
- Başka ana DB istemcisi, etkin event trigger veya `cem` için yeni nesneleri
  beklenmedik yetkilerle yaratacak default ACL bulunmadı.
- Tarihsel V001–V005 ve managed V005/V006 SQL SHA-256 değerleri doğrulandı.

## Uygulanan değişiklik

[Test veritabanında doğrulanan](event-test-validation.md) aynı
[V006 SQL](../db/managed-migrations/V006__add_events_and_participations.sql) dosyası
Flyway transaction'ı içinde uygulandı. SHA-256:
`046713d9afa9cb508a38b3176b7f2b86808ca4e9a18beb105fc3e6a25f46434c`.
Uygulanmış SQL veya transaction ayarı değiştirilmedi.

- `events`: 26 sütun; `event_participations`: 14 sütun.
- İki identity sequence, 29 constraint ve 12 indeks (PK/unique indeksleri dahil).
- Bütün constraint'ler validated, indeksler valid/ready; tam V006 şema kontratı eşleşiyor.
- Mevcut BASELINE 5 satırı/metadata şekli korundu; bir başarılı V006 SQL history kaydı eklendi.
  V006 Flyway checksum'ı test DB'sinde uygulanmış kayıtla aynı; `flyway:validate` başarılı.
- [Ana DB grant SQL'i](../db/provisioning/main/01-event-runtime-grants.psql) ayrı transaction
  içinde uygulandı. `campus_plus_user` yalnız yeni iki tabloda SELECT/INSERT/UPDATE/DELETE,
  iki sequence'de USAGE/SELECT aldı. Tam ACL listesinde bu 12 ayrı izin var;
  başka grantee veya grant option yok. Yeni tablolar için TRUNCATE, sequence reset ve
  migration history yazma yetkileri yok.

Rol/parola, servis ayarı veya eski iş tabloları üzerinde DDL/DML yapılmadı.
Otomatik repair/clean/drop, seed, test hesabı veya ADMIN ataması yapılmadı.

## Mevcut verilerin korunması

Önce/sonra ve uygulama kontrolü sonunda altı tablonun tüm alanlarını kapsayan
aggregate fingerprint'leri ve sayıları aynı:

| Tablo | Korunan kayıt |
| --- | ---: |
| users | 12 |
| user_roles | 12 |
| universities | 3 |
| student_profiles | 7 |
| clubs | 4 |
| club_memberships | 7 |

Parola/hash dahil alanlar fingerprint hesabına dahildir; bireysel satırlar veya
hash'ler rapora çıkarılmadı. DB/public schema sahipliği/ACL'leri ve bütün eski
nesnelerin sahiplik/ACL'leri aynı. Yalnız onaylı yeni 16 relation (iki tablo,
iki sequence, 12 indeks) eklendi. Yeni tablolar hâlâ boş, identity sequence'ler
kullanılmamış durumda. Ham kanıtlar/loglar repo dışında 0600 dosyalarda tutuldu.

## Uygulama ve API doğrulaması

Daha önce 273 Java testiyle doğrulanmış güncel jar'ın, tarih sorgusu düzeltmesini
kapsadığı ayrıca kontrol edildi. Normal paketlenmiş uygulama mevcut sınırlı hesapla
random loopback portunda başlatıldı; Hibernate `ddl-auto=validate` başarılı.
Yalnız bu geçici süreçte pgJDBC oturumları `default_transaction_read_only=on` ile
çalıştı; kalıcı URL, IDE ayarı veya uygulama/DB servisi yapılandırması değiştirilmedi.

**15 salt okunur GET kontrolü başarılı:**

- Anonim kullanıcı ve etkinlik uçları 401; mevcut geçerli hesap kendi DTO'sunu 200 alır.
- ADMIN olmayan kullanıcı, kullanıcı listesini 403 alır.
- Geçersiz imza, süresi dolmuş JWT ve NULL-hash legacy hesap 401 alır.
- Event keşif ve kişisel katılım sayfaları 200/boş; özel yanıtlarda `no-store` var.
- Dolu tarih aralığı 200; ters tarih aralığı ve sınır dışı sayfa boyutu 400.
- Olmayan etkinlik 404; üniversite ve kulüp sayfaları 200.

Mevcut hesapların parolaları kullanılmadı/değiştirilmedi: yalnız bu geçici kontrol
uygulamasına ait rastgele key ve kısa ömürlü JWT bellekte üretildi. Gerçek JWT_SECRET
korundu; key/token veya yanıt değerleri yazdırılmadı. Test profili ana DB'ye
bağlanmadı; ana DB üzerinde Register/Login veya yazmalı iş akışları denenmedi.
Kontrol için açılan süreç sonunda kapatıldı; başka süreç/servise müdahale edilmedi.

Önceki aynı kod tabanının test DB koşusu: **250 izole + 23 gerçek PostgreSQL = 273 Java**
ve **32 Python testi başarılı**, atlanan yok (`contextLoads()` dahil). Bu ana DB
adımında Java kodu değişmediği için paket yeniden çalıştırılmadı; ana ortamda
Hibernate/Flyway/kontrat ve yukarıdaki salt okunur API kontrolleri yapıldı.

## Güncel sınırlar ve sonraki adım

Ana ve test DB'leri V006 ile uyumlu. Event/EventParticipation bu aşamayla tamamlandı.
CI V006 güncellemesi yerel çalışma ağacında hazır, henüz GitHub'a gönderilmedi;
önceki başarılı `d0b250e` CI çalışması V005 sürümüne aittir.

Sonraki modül için gereksinim ve tasarım aşamasına geçilebilir; duyuru/bildirim
entegrasyonu uygun adaydır. Yeni modül, migration veya commit/push bu onayın
kapsamına alınmadı. Diğer projeler, PostgreSQL 14, Docker, Supabase ve Render'a dokunulmadı.
