# University ve StudentProfile — Aşama 1 tamamlandı

V003 migration kullanıcı tarafından açıkça onaylandı ve 2026-10-18 tarihinde yalnızca
**127.0.0.1:5434/campus_plus, PostgreSQL 17.11** üzerinde transaction içinde uygulandı.
Club/ClubMembership geliştirmesine başlanmadı. Commit veya push yapılmadı.

## Şema ve mevcut verilerin korunması

Uygulanan dosya: [V003](../db/migrations/V003__add_universities_and_student_profiles.sql).
Migration'ın DDL gövdesi onaylanan içeriktir; üst yorum yalnızca uygulandı durumuna güncellendi.

- universities: identity PK, unique code, temel katalog alanları, active, UTC zamanları, version.
- student_profiles: user_id PK/FK → users.id, university_id FK → universities.id,
  department, study_year, bio, UTC zamanları ve version.
- İki yardımcı indeks, PK/unique indeksleri, biçim/boş alan/sınıf/version CHECK kısıtları doğrulandı.
- Her iki FK `ON DELETE RESTRICT`; kullanıcı/parola/rol kolonlarında ALTER veya UPDATE yok.
- Hedef adres/port/veritabanı/sürüm SQL ile önceden ve migration içinde tekrar doğrulandı.
- 5 saniye lock, 15 saniye statement timeout; tüm DDL tek transaction'da COMMIT edildi.
- Önceki 4 users satırı parola hash'i dahil satır özetleriyle aynı bulundu; eski user_roles
  ilişkileri de aynen korundu. Migration sonrasında yeni iki tablo boştu.
- `ddl-auto=validate` korundu ve gerçek context/schema validation geçti.

## Tamamlanan uygulama kapsamı

- University: değişmez kanonik code/countryCode, ad, şehir, website, aktiflik,
  UTC zamanları ve Long @Version.
- StudentProfile: User ile paylaşılan PK (@MapsId), LAZY User/University ilişkileri,
  bölüm, hazırlık dahil 0..8 sınıf, isteğe bağlı bio, zamanlar ve @Version.
- Güvenli DTO'lar, trim/kanonikleştirme, ISO ülke, boyut, HTTP/HTTPS URL ve version doğrulamaları.
- Katalogda varsayılan yalnızca aktif kurumlar; includeInactive=true yalnızca ADMIN.
- Sayfa boyutu 1..100; sabit id ASC sıralama; LIKE özel karakterleri escape edilir.
- Üniversite yönetimi ADMIN. Profil self yollarında hedef kullanıcı JWT'den alınır;
  istemci userId veya global rol seçemez. Başka profile yalnızca ADMIN erişir.
- Profil yanıtları no-store'dur; entity, e-posta, parola/hash ve roller dışarı verilmez.
- Kullanıcı satırı kilidi profil oluşturma/güncellemeyi sıralar; üniversitedeki paylaşımlı
  kilit pasifleştirme yarışıyla eşgüdüm sağlar. Admin kurum güncellemesi exclusive lock kullanır.
- Version/unique/lock çakışması 409, bulunamayan kaynak 404, doğrulama hatası 400.
- İlişkilere REMOVE/ALL cascade veya orphanRemoval eklenmedi.
- Pasif kurumda yeni profil veya kurum değişikliği reddedilir; mevcut kurumunda kalan
  kişi kendi profilini düzenleyebilir. Pasifleştirme login'i veya profil satırlarını silmez.

## API sözleşmesi

| Endpoint | Yetki / sonuç |
| --- | --- |
| GET /api/universities?query=&page=&size=&includeInactive=false | Authentication; includeInactive ADMIN |
| GET /api/universities/{id} | Authentication; pasif kurum yalnızca ADMIN |
| POST /api/universities | ADMIN, 201 + Location |
| PATCH /api/universities/{id} | ADMIN, version zorunlu |
| GET /api/me/student-profile | Kendi profili; yoksa 404 |
| POST /api/me/student-profile | Kendi profilini oluşturma, 201; varsa 409 |
| PUT /api/me/student-profile | Kendi profili, version zorunlu |
| GET /api/admin/users/{userId}/student-profile | ADMIN |
| PUT /api/admin/users/{userId}/student-profile | ADMIN; aynı bütünlük/version kuralları |

Self profil yollarında STUDENT/CLUB_ADMIN/ADMIN kabul edilir. Silme endpoint'i yoktur.
University PATCH için eksik/null alan değişmez; websiteUrl: "" URL'yi temizler.
Profil PUT tam temsil kullanır; studyYear/bio null ile temizlenebilir.

## Otomatik testler

```sh
./mvnw test
```

Doğrulanmış Campus+ bağlantısı DB_URL/DB_USERNAME/DB_PASSWORD ortamından sağlandı;
DB_URL'e read-only PostgreSQL options eklendi. Context testi geçici test anahtarı kullanır.
Gerçek parola/anahtar komut argümanına veya kaynak koduna yazılmadı.

**113 test başarılı, 0 hata, 0 atlanan test; package başarılı.**

- 42 AcademicModulesTest: gerçek JWT/security, method validation ve service bean'leri;
  rol/sahiplik, admin/self, ID/rol enjeksiyonu, pasif kurum, duplicate/stale/race/lock.
- 2 AcademicMappingsTest: JDBC bağlantısını reddeden provider ile mapping bootstrap,
  HQL derleme ve paylaşılan PK + null version için persist/isNew tespiti.
- Önceki 69 testin tamamı: 33 JWT/RBAC, 11 CRUD MVC, 16 Register, 8 UserService,
  1 CampusPlusBackendApplicationTests.contextLoads.
- Context gerçek PostgreSQL şemasıyla validate edilerek yüklendi; testler mevcut veri yazmadı.

## Gerçek veritabanıyla canlı API kontrolü

Yalnızca Campus+ JAR'ı 127.0.0.1:8080 üzerinde başlatıldı ve sonunda kapatıldı.
Kullanıcı, yeni test hesabına geçici ADMIN verilmesini ve sonunda kaldırılmasını ayrıca onayladı.

**24 canlı HTTP kontrolü başarılı:**

- Register 201, Login 200, başlangıçta olmayan akademik profil 404.
- Geçici ADMIN aynı JWT ile üniversite oluşturma 201; duplicate code 409.
- Üniversite arama/detail 200; version ile update 200, stale version 409.
- Kendi akademik profilini oluşturma 201; gövdedeki başka userId/roles etkisiz.
- İkinci profil oluşturma 409; kendi profilini update 200, stale version 409.
- Admin profil get/update 200.
- Yalnızca yeni test üniversitesi pasifleştirildi; admin pasif kurum detail 200.
- Geçici ADMIN ilişkisi yalnızca yeni hesaptan kaldırıldı.
- Aynı JWT ile admin üniversite oluşturma ve admin profil erişimi artık 403.
- Öğrenci pasif kurum detail 404, kendi profilini get/update 200;
  anonim profil erişimi 401, includeInactive=true 403.

SQL üzerinden profilin user_id/university_id ilişkisi ve sürüm artışı doğrulandı.
Eski 4 hesap/parola ve roller korunurken yeni test hesabının hash'i de profil işlemlerinde
aynı kaldı. Yeni hesabın son rolü yalnızca STUDENT; kalıcı ADMIN atanmadı.

Bırakılan test verileri:

- Kullanıcı ID 5: ayrı, rastgele parolalı akademik API test hesabı; parola saklanmadı/gösterilmedi.
- Üniversite ID 1: açıkça test amaçlı kurum; **active=false**, normal katalogda görünmez.
- student_profiles.user_id=5, university_id=1, son version=3.
- Test hesabı/kurum/profil silinmedi; sadece açıkça onaylanan geçici ADMIN ilişkisi kaldırıldı.

Canlı testler PostgreSQL-backed gerçek CRUD ve kilitli sorguların çalıştığını doğrular.
Eşzamanlı çok bağlantılı yarış/deadlock yük testi bu sonuçların kapsamında değildir;
mock yarış testleri bu tür bir yük testiyle eşdeğer sayılmaz.

## Değişen dosya grupları

- Entity/repository: University, StudentProfile ve repository'leri.
- DTO: dto/academic altında create/update/summary/response; dto/PageResponse.
- Servis/controller: UniversityService/Controller, StudentProfileService/Controller.
- Yardımcılar: CurrentActor, HttpWebsite/Validator, ResourceNotFound/ConflictException.
- UserRepository kilitli kullanıcı sorgusu, SecurityConfig admin yolları,
  GlobalExceptionHandler doğrulama ve çakışma yanıtları.
- AcademicModulesTest, AcademicMappingsTest; V003 SQL ve bu inceleme belgesi.

## Korunan sınırlar ve sonraki adım

Yeni bağımlılık veya servis ayarı yok; JWT secret dosyası korundu, log/kaynakta bulunmadı.
Diğer projeler/veritabanları/PostgreSQL 14/Docker/Supabase/Render kullanılmadı.
Henüz Club/ClubMembership entity, endpoint veya migration'ı yazılmadı.

Üyelik tablosu henüz olmadığından aktif üniversiteye profil geçişi bu aşamada mümkündür.
İleride üyelik modülü açılmadan önce PENDING/ACTIVE/SUSPENDED kontrolü aynı kullanıcı
kilidi altında profile update'e eklenecek. Bu kapsam ertelenen bağımlı aşamadır, kaldırılmadı.

Bu aşama tamamlandı. Commit/push ve Club/ClubMembership geliştirmesi yeni kullanıcı
isteğine kadar bekler. Gerçek kullanıcıya kalıcı ADMIN ataması da ayrıca açık talimat gerektirir.

Teknik kaynaklar: [JPA entity tespiti](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html),
[JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html),
[Spring MVC validation](https://docs.spring.io/spring-framework/reference/7.1/web/webmvc/mvc-controller/ann-validation.html).
