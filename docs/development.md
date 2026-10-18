# Campus+ geliştirme durumu

## Tamamlanan aşamalar

1. CRUD istek ve yanıt DTO'ları, doğrulamalar ve e-posta çakışması kontrolü.
2. Mevcut kayıtları koruyan parola sütunu migration'ı ve şema doğrulaması.
3. BCrypt ile register endpoint'i ve güvenlik filtreleri üzerinden testler.
4. Login, 15 dakikalık JWT access token ve stateless Bearer authentication.

Kaynak sahipliği ve üretim rol modeli henüz geliştirilmedi. JWT authentication tamamlandı;
mevcut CRUD erişimi kimliği doğrulanmış kullanıcılara açıktır. Kullanıcıya özel erişim
ve admin kuralları sonraki yetkilendirme aşamasındadır.

## CRUD sözleşmesi

- POST `/api/users`: `CreateUserRequest` (name, email).
- PUT `/api/users/{id}`: `UpdateUserRequest` (name, email).
- POST, PUT ve GET yanıtları: `UserResponse` (id, name, email).
- Mevcut URL'ler ve HTTP başarı durumları korunmuştur.
- İsim 100, e-posta 255 karakterle sınırlıdır; boş/geçersiz değerler reddedilir.
- Güncelleme başka kullanıcıya ait e-postayı reddeder; kişinin kendi e-postasını korumasına izin verir.
- Profil güncelleme parola hash'ini değiştirmez; eski NULL parolalar da korunur.
- Ön kontrol eşzamanlı çakışmayı tek başına engellemez; nihai koruma veritabanının unique kısıtıdır.
  Bütünlük çakışmaları SQL ayrıntısı içermeyen 409 yanıtına çevrilir.
- Validation yanıtı mevcut alan-hata haritasıdır; diğer iş hataları `ErrorResponse` kullanır.
- DTO kullanımı yetkilendirme sağlamaz. CRUD endpoint'leri authentication gerektirir;
  JWT authentication hazırdır; kullanıcıya özel erişim kuralları sonraki aşamadadır.
- E-postaların büyük/küçük harf davranışı ve mevcut kayıtlar değiştirilmemiştir.
  Büyük/küçük harften bağımsız giriş tasarımı için mevcut kayıtlar incelenip ayrıca karar verilmelidir.

## Onaylanıp uygulanan migration

2026-10-18 tarihinde yalnızca `127.0.0.1:5434/campus_plus` hedefi kullanıldı.
Sunucu SQL üzerinden PostgreSQL 17.11 olarak doğrulandı (`server_version_num=170011`).
Başlangıçta `public.users` tablosunda 1 kullanıcı ve id/email/name sütunları vardı.

`db/migrations/V001__add_user_password_hash.sql` psql ile açıkça çalıştırıldı:

- Hedef veritabanı, adres, port ve PostgreSQL ana sürümü SQL içinde tekrar doğrulanır.
- Tek transaction ve sınırlı kilit bekleme süresiyle yalnızca nullable
  `password_hash VARCHAR(255)` sütunu eklenir.
- Migration sırasında tablonun eski satırları bellek içinde karşılaştırılır;
  herhangi bir değişiklik veya dolu parola alanı işlemi geri aldırır.
- Mevcut kullanıcı ve tüm eski alanları korundu; parola hash'i NULL kaldı.
- Ayrı bir migration geçmiş tablosu eklenmedi; bu manuel, sürümlü SQL migration'ıdır.
  Otomatik çalışmaz; tekrar çalıştırma bilerek hata verir.
- Kullanıcının talimatıyla yedek alınmadı.

Migration tamamlandıktan sonra Hibernate eşlemesi önce salt okunur bağlantıda
`ddl-auto=validate` override'ıyla doğrulandı. Bu test geçtikten sonra uygulama ayarı
kalıcı olarak `spring.jpa.hibernate.ddl-auto=validate` yapıldı. Uygulama artık şema
uyuşmazlığında hata verir; otomatik kolon eklemez veya şemayı değiştirmez.

## Register

`POST /api/auth/register`, `Content-Type: application/json`:

```json
{
  "name": "Öğrenci Adı",
  "email": "ogrenci@example.com",
  "password": "<kendi parolanız>"
}
```

Başarı: HTTP 201 ve yalnızca `id`, `name`, `email`.
Geçersiz alan: 400. Aynı e-posta: 409.

- İsim ve e-posta sınırları veritabanıyla uyumludur.
- Parola en az 8 Java karakter birimi ve UTF-8 olarak en fazla 72 bayt olmalıdır.
  Parola kırpılmaz veya normalleştirilmez; Türkçe karakterler/emoji birden fazla bayt kullanabilir.
- `PasswordEncoder` bean'i `BCryptPasswordEncoder(12)` kullanır. Ham parola entity'ye
  atanmaz; yalnızca hash saklanır. Record'un `toString()` çıktısı parolayı gizler.
- Hash dışarıya DTO ile aktarılmaz ve entity getter'ı `@JsonIgnore` ile ayrıca korunur.
- Aynı parolanın farklı hash üretmesi salt sayesinde beklenen davranıştır.
  Giriş aşamasında hash'ler karşılaştırılmaz; `PasswordEncoder.matches` kullanılır.
  Kaynak: [Spring Security Password Encoding](https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html).
- Mevcut eski hesaplara ortak veya geçici parola atanmaz. Parolası NULL olanlar,
  ileride e-posta sahipliği doğrulanarak parola belirleyene kadar parola ile giriş yapamayacaktır.
- Register oturum veya JWT üretmez. Yalnızca POST register herkese açıktır;
  diğer endpoint'ler için authentication şartı korunur.
- Login/JWT aşamasından itibaren API stateless Bearer authentication kullanır;
  session, form login ve HTTP Basic devre dışıdır. Cookie ile kimlik doğrulaması
  kullanılmadığından CSRF devre dışıdır. İleride cookie authentication eklenirse
  bu karar yeniden değerlendirilmelidir.
  Kaynak: [Spring Security CSRF](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html).

## Testler

Veritabanına bağlanmayan testler:

```sh
./mvnw -Dtest=UserServiceTest,UserControllerTest,AuthControllerTest,JwtAuthenticationTest test
```

CRUD MVC testleri standalone çalışır. Auth MVC testleri gerçek SecurityConfig,
güvenlik filtreleri, AuthService ve BCrypt kullanır; yalnızca repository mock'tur.
Hiçbiri canlı veritabanına kullanıcı eklemez.

Tam test takımı için DB_URL, DB_USERNAME ve DB_PASSWORD ortam değişkenleri gerekir.
Sırları kaynak dosyalarına veya komut argümanlarına yazmayın. Mevcut context testi
canlı şemayı doğrular; bu nedenle tam takımı yalnızca doğrulanmış Campus+ hedefiyle çalıştırın.
Bu aşamada bağlantıya eklenen `options=-c%20default_transaction_read_only%3Don`
JDBC parametresiyle veritabanında yazma işlemleri ayrıca engellendi.

Son sonuç: Java 21 / Spring Boot 4.1.1 / Spring Security 7.1.1 ile
**57 test geçti, 0 hata, 0 atlanan test**.
Kapsam: CRUD, DTO alanları, hash'in yanıt dışında kalması, mevcut hash/NULL korunması,
validation, 72 bayt ASCII/Unicode sınırları, BCrypt doğrulama ve salt davranışı,
anonim erişim, e-posta yarış çakışması, hata ayrıntılarının gizlenmesi ve gerçek şema doğrulaması.
JWT test kapsamı aşağıdadır.

Ortamda Maven önbelleği/ağ ve Mockito agent kısıtları nedeniyle geçici Maven deposu
ve gerekli araç izinleri kullanıldı. Bağımlılık sürümleri değiştirilmedi.
Diğer veritabanları, projeler, Docker, Supabase ve Render kullanılmadı.
Commit veya push yapılmadı.


## Login ve JWT

`POST /api/auth/login`, `Content-Type: application/json`, alanlar: `email`, `password`.
Başarı: HTTP 200, `accessToken`, `tokenType` (Bearer), `expiresIn` (900 saniye).
Yanıt `Cache-Control: no-store` taşır. Parola ve access token DTO `toString()` çıktılarında gizlenir.

Akış:

1. E-posta mevcut verileri değiştirmeden birebir aranır.
2. BCrypt `matches()` parolayı doğrular. Bulunmayan hesap ve NULL hash'li eski hesap
   için de rastgele üretilmiş dummy hash ile BCrypt kontrolü yapılır. Hepsi yanlış
   parola ile aynı genel 401 yanıtını alır; eski hesaplara parola atanmaz.
3. Başarıda HS256 imzalı, 15 dakikalık JWT üretilir. Claim'ler yalnızca `sub`
   (kullanıcı ID), `iss`, `aud`, `iat`, `exp`, `jti` içerir; parola/hash/e-posta içermez.
4. İstemci sonraki istekte `Authorization: Bearer <accessToken>` gönderir.
5. Spring Security Resource Server imzayı ve yalnızca HS256 algoritmasını kabul eder;
   issuer, audience, expiration, issued-at ve kullanıcı ID biçimi doğrulanır.
   `nbf` varsa kontrol edilir. Süre toleransı sıfırdır; iki sunucu varsa saatler senkron olmalıdır.
6. Eksik/geçersiz/süresi dolmuş token 401, doğrulanmış fakat gereken yetkisi olmayan
   istek 403 JSON alır. Gerçek rol modeli eklenmedi; 403 davranışı testlere özel
   bir admin endpoint'i ve gerçek method security ile doğrulanır.

Özel JWT filtresi yazılmadı; Spring'in yerleşik Bearer doğrulama zinciri kullanılır.
Kaynak: [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

### Anahtar ve çalıştırma

- Gerçek ortamda `JWT_SECRET` ortam değişkeni zorunludur: Base64 ile kodlanmış,
  kriptografik olarak rastgele en az 32 bayt. Varsayılan veya kaynak koda gömülü anahtar yoktur.
- Örneğin yerel terminalde, değeri yazdırmadan bir anahtar üretmek için:

  ```sh
  export JWT_SECRET="$(openssl rand -base64 32)"
  ```

- Ortam değişkenini uygulamayı çalıştıran süreç sağlamalıdır. IDE/servis ayarları bu
  aşamada değiştirilmedi; kullanıcı ortamına gerçek anahtar kurulmadı. Sır değerini
  Git'e, komut argümanına, loga veya sohbet mesajına koymayın.
- Uygulama yeniden başlatıldığında aynı anahtar kullanılmalıdır. Anahtar değişince
  eski token'lar doğrulanmaz. Kod anahtarı her açılışta kendiliğinden üretmez.
- Test anahtarları `SecureRandom` ile yalnızca test belleğinde üretilir. Context testi
  `@DynamicPropertySource` kullanır; gerçek JWT secret gerekmez.
- Issuer `https://campus-plus.local`, audience `campus-plus-api` sabittir; issuer bir
  isim alanıdır, bu adrese ağ isteği yapılmaz.
- Yalnızca access token vardır. Refresh token, sunucu tarafı token iptali ve logout
  henüz yoktur. İstemcinin token'ı silmesi sunucudaki geçerliliği kaldırmaz;
  verilmiş bir token süresi dolana kadar geçerli kalabilir.

### JWT testleri

21 yeni test çalıştırması: başarılı login ve gerçek korumalı CRUD erişimi, yanlış/olmayan/eski
hesaplarda aynı 401 yanıtı, dolmuş ve tam süre sınırındaki token, gelecekteki iat/nbf,
yanlış issuer/audience, eksik zorunlu claim'ler, hatalı subject, bozuk token, değiştirilmiş
imza, imzasız token, 401/403 ayrımı, Basic ve session cookie reddi, parola sınırları,
DTO log gizleme ve zayıf/eksik anahtar reddi.

Tam takım gerçek şemaya yalnızca doğrulanmış `127.0.0.1:5434/campus_plus` PostgreSQL 17.11
üzerinde salt okunur bağlandı. Test öncesi/sonrası kullanıcı satırlarının özeti karşılaştırıldı;
1 kullanıcı ve tüm alanları değişmeden korundu. Şema veya servis ayarı değiştirilmedi.

### Bu aşamada eklenen/değişen dosyalar

- `pom.xml`: Boot'un yönettiği Resource Server ve JOSE bağımlılıkları.
- `config/JwtConfig.java`: anahtar kontrolü, encoder/decoder, claim doğrulamaları, saat.
- `config/SecurityConfig.java`: stateless Bearer zinciri ve 401/403 handler bağlantıları.
- `controller/LoginController.java`: login endpoint'i ve önbellekleme koruması.
- `dto/LoginRequest.java`, `dto/LoginResponse.java`: doğrulama ve gizli toString çıktısı.
- `service/LoginService.java`: BCrypt doğrulama, legacy/unknown hesap davranışı.
- `service/JwtTokenService.java`: minimal claim'lerle token üretimi.
- `security/JsonSecurityErrors.java`: sabit, ayrıntı sızdırmayan JSON güvenlik yanıtları.
- `entity/User.java`: `@JsonIgnore` ile korunan hash getter'ı; JPA şeması değişmedi.
- `repository/UserRepository.java`: `findByEmail`.
- `exception/GlobalExceptionHandler.java`: genel yanlış giriş 401 yanıtı.
- `CampusPlusBackendApplicationTests`, `AuthControllerTest`: geçici test anahtarı ve 401 beklentileri.
- `security/JwtAuthenticationTest`, `security/JwtTestKeys`: JWT testleri ve bellek içi anahtar üretimi.
- `docs/development.md`: güncel sözleşme ve çalışma notları.
