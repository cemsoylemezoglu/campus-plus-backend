# Announcement — ana Campus+ V007 uygulaması

Tarih: 2026-10-31. **V007 ve yeni nesnelerin sınırlı runtime yetkileri ana Campus+
veritabanında uygulandı; veri koruması, şema ve uygulama kontrolleri başarılı.**
Commit/push yapılmadı. Notification modülü henüz geliştirilmedi.

## Açık onay ve hedef

Kullanıcı somut SQL/hedef/yetki/test sorusuna **“Ana campus_plus üzerinde V007 ve
belirtilen sınırlı yetkileri açıkça onaylıyorum.”** yanıtını verdi. İşlem yalnız
`127.0.0.1:5434/campus_plus`, PostgreSQL 17 üzerinde gerçekleştirildi.

Ön kontrolde gerçek sunucu kimliği, V006 kontratı, BASELINE5+SQL6 history'si,
sekiz tablonun satır fingerprint'leri ve sahiplik/ACL'leri doğrulandı. Başka aktif
ana DB istemcisi, etkin event trigger veya beklenmedik `cem` default ACL'i yoktu.
Migration için mevcut `cem`, normal uygulama için sınırlı `campus_plus_user` kullanıldı.
PG rolü/parolası veya servis yapılandırması değiştirilmedi.

## Uygulanan SQL

[Test DB'de doğrulanmış](announcement-test-validation.md) aynı
[V007](../db/managed-migrations/V007__add_club_announcements.sql) Flyway transaction'ı
ile uygulandı. SHA-256:
`8d00500e3a906dbd1be5fe097237edf18c0813208ac02c7e9c2ecfc660d54ae5`.
Uygulanmış SQL veya `executeInTransaction=true` ayarı değiştirilmedi.

- `club_announcements`: 16 sütun, 13 constraint (8 CHECK, 4 FK, PK), 6 indeks ve bir
  identity sequence. Bütün constraint'ler validated; indeksler valid/ready.
- Tam şema [V007 kontratı](../db/schema/v007-contract.json) ile eşleşiyor.
- Mevcut BASELINE5 ve SQL6 kayıtları/metadata şekli korundu; üçüncü başarılı V007
  SQL history kaydı eklendi. V007 checksum'ı test DB'sindeki kayıtla aynı.
- `flyway:validate` başarılı; baseline/migration tekrar uygulama, repair/clean/drop yok.
- [Ana runtime grant](../db/provisioning/main/02-announcement-runtime-grants.psql)
  ayrı transaction'da uygulandı: yalnız yeni tabloda SELECT/INSERT/UPDATE/DELETE ve
  yeni sequence'de USAGE/SELECT. Tam ACL'de owner dışındaki altı izin yalnız
  `campus_plus_user` hesabına ait; grant option yok. Yeni tablo TRUNCATE, sequence
  reset ve history yazma yetkileri verilmedi.

## Kayıt ve yetki koruması

Öncesi/sonrası ve normal uygulama kontrolü sonunda sekiz tablonun bütün alanlarını
kapsayan aggregate fingerprint'leri aynı. Parola/hash ve roller bu karşılaştırmaya
dahildir; bireysel satırlar veya gizli değerler rapora çıkarılmadı.

| Tablo | Korunan kayıt |
| --- | ---: |
| users | 12 |
| user_roles | 12 |
| universities | 3 |
| student_profiles | 7 |
| clubs | 4 |
| club_memberships | 7 |
| events | 0 |
| event_participations | 0 |

DB/public schema ACL'leri ve bütün eski nesnelerin sahiplik/ACL'leri aynı.
Yalnız onaylı yeni sekiz relation (tablo, sequence, altı indeks) eklendi.
Yeni duyuru tablosu boş, identity sequence kullanılmamış durumda. Ana DB'ye test
hesabı/ADMIN ilişkisi/duyuru/etkinlik eklenmedi veya mevcut kayıt değiştirilmedi.
Ham kanıtlar/loglar repo dışında 0600 dosyalarda tutuldu.

## Normal uygulama ve salt okunur API kontrolü

328 Java testiyle doğrulanmış paketlenmiş jar'ın Announcement model/controller'ını
kapsadığı kontrol edildi. Java/uygulama kaynakları bu ana DB adımında değiştirilmedi.
Jar, mevcut sınırlı hesapla geçici random loopback portunda açıldı; Hibernate
`ddl-auto=validate` başarılı. Yalnız bu kontrol sürecinde pgJDBC oturumlarına
`default_transaction_read_only=on` verildi; kalıcı IDE/URL/DB/servis ayarı değişmedi.

**22 GET kontrolü başarılı:**

- Mevcut hesabın kendi güvenli kullanıcı DTO'su 200; anonim erişim 401,
  ADMIN olmayan kullanıcının kullanıcı listesi 403.
- Geçersiz imza, süresi dolmuş JWT ve NULL-hash legacy hesap 401.
- Üniversite ve kulüp sayfaları 200; Event keşif/tarih aralığı ve kişisel katılım
  sayfası 200. Ters tarih/sayfa sınırları 400, olmayan etkinlik 404.
- Anonim veya geçersiz token'lı Announcement listesi 401; kimliği doğrulanmış
  keşif/kaçırılmış `%` ve `_` araması 200/boş. Sayfa sınırı 400; olmayan duyuru ve
  olmayan kulübün yönetim sayfası 404. Özel sayfalarda no-store doğrulandı.

Gerçek kullanıcı parolası kullanılmadı/değiştirilmedi: yalnız geçici doğrulama
uygulamasına ait rastgele anahtar ve kısa ömürlü JWT bellekte üretildi.
Gerçek JWT_SECRET korundu; key/token/yanıt değerleri yazdırılmadı.
Ana ortamda yazmalı Register/Login/duyuru akışı çalıştırılmadı; bunlar test DB'deki
önceki tam koşuda sınandı. `postgres-it` ana DB'ye yönlendirilmedi.
Sadece kontrol için açılan uygulama kapatıldı; başka süreç/servis değiştirilmedi.

Önceki aynı kaynak sürümünün test DB sonucu **328 Java (297 izole + 31 PostgreSQL,
contextLoads dahil) ve 34 Python testi başarılı**. Java kodu değişmediği için aynı
paket bu adımda tekrar çalıştırılmadı; ana ortamda ek Hibernate/Flyway/kontrat/veri
koruması ve 22 salt okunur GET kontrolü yapıldı.

## Güncel aşama

Announcement modülünün kod, test DB ve ana DB uygulama aşamaları tamamlandı.
Ana ve test şemaları V007 ile uyumlu. CI V007 için hazırlanmış/statik doğrulanmış
ancak henüz push edilmemiştir; yeni uzak CI sonucu yoktur. Önceki `d0b250e` uzak
başarısı V005 sürümünün sonucudur.

Sıradaki aşama onaylanan mimarideki kişisel uygulama içi bildirimlerdir:
transaction içinde güvenilir kayıt, kişisel okunma durumu, kaynak erişiminin güncel
kontrolü ve kulüp/üyelik/etkinlik/duyuru entegrasyonu. Ortak kullanıcı kilidi/FK
uyumu ve çapraz alıcı yarışları önce doğrulanmalıdır. Notification için V008 SQL
hazırlandığında test ve ana DB uygulamaları yine ayrı açık onay gerektirir.

Diğer projeler, PostgreSQL 14, Docker, Supabase ve Render'a dokunulmadı.
