# Campus+ — mevcut ana veritabanını Flyway geçmişine alma planı

Durum: **2026-10-31 kullanıcı onayıyla uygulandı ve doğrulandı**.
Bu belge onaylanan planın kaydıdır; tekrar baseline çalıştırma talimatı değildir.
[Uygulama ve test raporu](setup-test-stage4-review.md).
Hedef yalnız `127.0.0.1:5434/campus_plus`, PostgreSQL 17.
İzole test DB kurulumu ve 203 Java / 16 Python testi önceki aşamada başarılıydı.

## Baseline ne yapacak?

Flyway'e mevcut iş şemasının sürüm 5 seviyesinde olduğunu kaydedeceğiz. V001–V005
tekrar çalıştırılmayacak; kullanıcılara, rollere, parola hash'lerine, üniversitelere,
profillere, kulüplere veya üyeliklere DDL/DML uygulanmayacak.
V005 başlangıç dosyası ana DB'de çalıştırılmayacak. Sonraki gerçek migration V006 olur.

Kesin metadata SQL önizlemesi:
[baseline-v005-metadata.sql](../db/review/baseline-v005-metadata.sql).
Bu dosya **çalıştırılmaz**; yanlışlıkla tüm dosya uygulanırsa exception/rollback ile
engellenir. Gerçek history Flyway'in baseline komutuyla oluşturulacak; elle sahte
migration kaydı yazılmayacak. Önizleme, kullanılan Flyway 12.4.0 PostgreSQL modülünün
`getRawCreateScript` ve çekirdeğin `getBaselineStatement` kodundan doğrulandı.

Oluşacak kalıcı nesneler:

- `public.flyway_schema_history`: 10 sütunlu tek metadata tablosu.
- `flyway_schema_history_pk`: installed_rank PK ve PostgreSQL'in ilişkili unique indeksi.
- `flyway_schema_history_s_idx`: success sütununda normal indeks.
- Tek BASELINE satırı: rank 1, version `5`, checksum NULL, success true;
  description/script `Campus+ reviewed V005 schema`, execution_time 0,
  installed_by `cem`, installed_on işlem zamanı.
- Yeni identity/sequence, extension, trigger, iş tablosu sütunu veya rol yaratılmayacak.
  Tablo sahibi işlemi yapan `cem` olur; mevcut tablo sahiplikleri/ACL'leri değişmez.
  Uygulama hesabına history yazma veya yönetici yetkisi verilmeyecek.

Flyway transaction/advisory lock kullanabilir; yeni DB/rol veya servis ayarı değişikliği
yapılmaz. Baseline, eski SQL'lerin tek tek çalıştırıldığı veya checksum'larının history'ye
alındığı iddiası değildir. Legacy checksum manifesti ve şema kontratı bu nedenle korunur.

## Uygulama ön koşulları

1. Uygulamanın yazma trafiği olmadığı bir aralık seçilir. Aktif işlem/veri değişikliği
   fark edilirse mevcut verileri geri yazarak eşitleme yapılmaz; durum kullanıcıya bildirilir.
2. `DB_*` yalnız mevcut hedefi gösterir. Gerçek DB/host/port/PG17/read-only kimliği,
   legacy SHA-256 dosyaları ve şema envanteri yeniden doğrulanır.
3. Kontrat karşılaştırması başarılı ve `migrationHistoryPresent=false` olmalı. History
   zaten varsa, hatta boş görünse bile baseline/repair/clean uygulanmaz.
   `db/verification/baseline_preflight.sql` ile etkin DDL event trigger'ları ve `cem`
   için yeni tablolara otomatik grant'ler de incelenir. İlk salt okunur kontrolde ikisi
   de boş bulundu; history yok. Beklenmeyen trigger/grant çıkarsa işlem durur;
   bunlar otomatik kaldırılmaz veya değiştirilmez.
4. Altı iş tablosunun sayıları/toplu satır özetleri özel geçici dosyalara alınır;
   tekil kullanıcı/parola/hash satırları dökülmez. Dosyalar Git'e girmez.
5. `cem` yönetici bağlantısı aynı hedefte doğrulanır. Şu an parolasız TCP erişimi
   mümkündür; bağlantı yöntemi değişirse parola tahmini veya authentication ayarı
   değişikliği yapılmaz. MIGRATION_DB_* ayrı sağlanır; DB_* fallback yoktur.

## Onay sonrası tek yazmalı görev

Yalnız hedefi ve `cem` hesabı doğrulanmış MIGRATION_DB_* ortamıyla:

```sh
./mvnw -B -ntp -Pschema-tools -Dflyway.skip=false flyway:baseline
```

Sürüm 5 ve açıklama pom.xml içinde sabittir. Komut `migrate`, `repair`, `clean` veya
V005 SQL'i ile zincirlenmez. Otomatik baselineOnMigrate kapalı kalır. Önizleme dosyası
Flyway locations dışında tutulur; onaydan sonra da migrate komutu ile taranmaz.

Normal uygulama başlangıcı ve Maven test/package migration çalıştırmaz. Uygulama
bağımlılıkları ve `ddl-auto=validate` değişmez. PostgreSQL'de DDL transaction'la
korunsa da beklenmeyen başarısızlıkta otomatik DROP/repair yapılmaz; sonuç incelenir.

## Başarı ölçütleri

- Yalnız beklenen history tablosu/iki indeks ve tek BASELINE satırı mevcut.
- İş şeması kontratı hâlâ eşleşiyor; altı tablonun sayıları/toplu özetleri aynı.
- Mevcut iş tablosu sahiplikleri/ACL'leri korunmuş; uygulama hesabı history DML yapamıyor.
- Flyway info/validate V005'in baseline nedeniyle atlandığını kabul ediyor; çalıştırmıyor.
- Çalışan uygulama `ddl-auto=validate` ile başlayıp mevcut kayıtları salt okunur okuyabiliyor.
  Bu kontrol için test profili ana DB'ye yönlendirilmez; ayrı kontrollü normal uygulama
  başlatması kullanılır, HTTP mutation/Register/test seed çağrısı yapılmaz.
- Normal başlatma öncesi/sonrası envanter ve history değişmiyor.
- İzole 193 Java testinin ve ayrı test DB'sindeki 10 PostgreSQL IT'nin durumu raporlanır.

## Bu onayın kapsamı dışında

Ana DB'de Event tabloları, V006, yeni test satırları, kullanıcı/rol/parola güncelleme,
başka projeler/DB'ler, PostgreSQL 14, Docker, Supabase, Render, commit ve push yoktur.
CI dosyası hazırlamak uzak workflow başlatma izni değildir; ilk çalışma ancak ayrıca
istenen push sonrasında olur.

Dayanak: [Flyway baseline semantiği](https://documentation.red-gate.com/flyway/reference/commands/baseline).
