# Event/Announcement etkileşimleri: V011 ana ortam doğrulaması

Tarih: 2026-11-06. **Ana aşama tamamlandı.** Kullanıcının testten ayrı açık onayıyla
yalnız PostgreSQL 17 `127.0.0.1:5434/campus_plus` üzerinde işlem yapıldı.

## Migration ve güvenlik sınırı

- Aynı [V011 SQL](../db/managed-migrations/V011__add_club_content_interactions.sql)
  Flyway transaction içinde uygulandı; SQL SHA-256 değişmedi:
  `2b64de742ad7425e777a40d32eac32dcb053749dd70ff56cb0a9037fc6181f5d`.
- Önce gerçek hedef/sürüm, V010 kontratı, BASELINE5+SQL6…10 geçmişi, mevcut veri
  fingerprint'leri ve ACL'ler kontrol edildi. Başka client/default ACL yoktu.
- Ön kontrol betiğindeki database owner=cem varsayımı düzeltilerek önceki V010
  ACL kaydıyla doğrulandı: database owner `campus_plus_user`, mevcut DDL yöneticisi
  `cem`. Sahiplik veya rol değişikliği yapılmadı. Runtime hesabına history okuma
  yetkisi eklenmedi; history kontrolü `cem` ile salt okunur yapıldı.
- Flyway V011 başarılı; bildirilen yerel DDL süresi yaklaşık 65 ms. Son history
  BASELINE5+SQL6…11; önceki satırlar aynı, son migration installed_by=cem.
- [Ana grant](../db/provisioning/main/06-club-interaction-runtime-grants.psql)
  onaylanan byte'larla taşınıp uygulandı. SHA-256:
  `4a41f82c66e193ad4da27a9da1fc59e2e7d3e8396a8f667f027b5526da5521cd`.

## Şema, ACL ve kayıt korunumu

Ana katalog [V011 kontratıyla](../db/schema/v011-contract.json) eşleşiyor.
Dört yeni tablo/sequence, 39 constraint ve 23 index doğrulandı. Notifications
yalnız onaylanan nullable alan/FK/index/CHECK değişikliklerini içeriyor.

Runtime hesabında yalnız üç yeni etkileşim tablosu SELECT/INSERT/UPDATE, audit
SELECT/INSERT ve dört sequence USAGE/SELECT: 11 tablo +8 sequence hakkı. DELETE,
TRUNCATE, audit UPDATE, sequence reset yok. Yeni nesnelerin sahibi `cem`; eski
nesnelerin owner/ACL'leri ve database/schema sahipliği/yetkileri aynı.

Migration, grant ve uygulama kontrolleri sonrasında eski 16 tablonun tam satır
sayısı/içerik fingerprint'leri başlangıçla eşit. Kullanıcı/rol/parola ve akademik/
kulüp kayıtları korundu: users 12, user_roles 12, universities 3,
student_profiles 7, clubs 4, club_memberships 7. Diğer eski on tabloda kayıt yoktu
ve hâlâ yok. Eski notifications kolonları korundu; yeni alan NULL koşulu doğrulandı.

Yeni dört tablo **boş**, identity sequence'ler last_value=1/is_called=false.
Ana DB'de fixture veya gerçek sosyal içerik oluşturulmadı; üniversitelerin pasif
durumu değiştirilmedi. Eski V001–V010 SQL ve kontratları değişmedi.

## Uygulama ve test sonucu

- Testte geçen JAR'ın class byte'ları mevcut derlenmiş class'larla karşılaştırıldı.
  Uygulama kaynakları/POM tam test koşusundaki checksum'larla aynı.
- Ayrı geçici loopback süreç, JDBC/connection read-only ve ddl-auto=validate ile
  başlatıldı. Flyway validate ve Hibernate validate başarılı.
- **90 yalnız GET kontrolü geçti**: auth/bozuk veya olmayan kullanıcı token'ı,
  NULL parolalı eski hesap, own-user sınırı, DTO güvenliği, inbox/geçmiş, yeni
  source yollarında 404 ve validation, ADMIN yollarında 403, sahte ADMIN token
  claim'lerinin DB rolleri yerine geçmemesi.
- Smoke betiğinin eski feed yolu varsayımı `/api/feed` olarak düzeltildi; son
  koşu başarılı. Bu düzeltme uygulama kodunda veya veritabanında değişiklik değildi.
- Süreç yalnız bellekte üretilmiş geçici JWT anahtarı kullandı. Kalıcı JWT secret
  dosyası aynı kaldı; token/parola/anahtar çıktıya yazılmadı. Yalnız kendi geçici
  uygulama sürecimiz kapatıldı; mevcut servis yapılandırmaları değiştirilmedi.
- Son salt okunur karşılaştırma: şema/ACL/history/veri aynı, yeni tablolar boş ve
  sequence'ler kullanılmamış; başka DB client yok.

Pozitif içerik yazma, üyelik/rol değişimi, yarış ve rollback kanıtları ayrı test
DB'sindeki **624 Java (540 izole +84 PG) /44 Python** başarılı koşusundadır;
contextLoads ve 12 yeni PG testi dahil. Ana DB'de bu yazma testleri tekrarlanmadı.
Ana ortamda kaynak içerik bulunmadığından pozitif Event/Announcement yorum listesi
kanıtı burada üretilmedi. Pasif üniversite nedeniyle feed'in mevcut 409 koruması
doğrulandı; bu veri durumu düzeltilmiş gibi raporlanmaz.

CI V011 boş kurulum/kontrat/grant için hazır ve actionlint başarılı; yeni workflow
henüz push edilmedi veya uzakta çalıştırılmadı. Diğer projeler/PG14/Docker/Supabase/
Render ortamlarına dokunulmadı. Migration aşamasında commit veya push yapılmadı.
