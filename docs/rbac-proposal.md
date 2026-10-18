# Campus+ RBAC — onaylanan ve uygulanan tasarım

Kullanıcı şema değişikliğini açıkça onayladı. V002 migration 2026-10-18 tarihinde
yalnızca Campus+ PostgreSQL 17 üzerinde uygulandı; aşağıdaki erişim matrisi aktiftir.

## Roller ve kapsam

- STUDENT: kendi profilini görüntüleme/güncelleme; ileride üyelik, katılım ve kendi içerikleri.
- CLUB_ADMIN: öğrenci haklarına ek olarak yalnızca yönettiği kulüpleri yönetme.
  Tek başına bu rol herhangi bir kulübün kaynaklarını yönetmeye yetmez. Kulüp modülünde
  club_memberships ilişkisi üzerinden kullanıcı, club_id ve yönetici görevi birlikte denetlenir.
- ADMIN: platform kullanıcıları ve moderasyon işlemleri. Public register ile atanamaz.

Bir kullanıcı birden fazla role sahip olabilir. Public register yalnızca STUDENT atar;
register/update DTO'larına rol alanı eklenmez. Mevcut kullanıcılardan hiçbiri otomatik ADMIN olmaz.

## Onaylanıp uygulanan kontrollü şema değişikliği

Yalnızca doğrulanmış 127.0.0.1:5434/campus_plus PostgreSQL 17 hedefinde:

```sql
CREATE TABLE public.user_roles (
    user_id BIGINT NOT NULL REFERENCES public.users(id),
    role VARCHAR(20) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('STUDENT', 'CLUB_ADMIN', 'ADMIN'))
);
INSERT INTO public.user_roles (user_id, role)
SELECT id, 'STUDENT' FROM public.users;
```

SQL'in hedef kontrolü, transaction, kilit/zaman sınırı ve users satırları için
birebir koruma kontrolü içeren sürümü `db/migrations/V002__add_user_roles.sql` dosyasında.
Mevcut 3 kullanıcının tüm alanları korundu ve yalnızca STUDENT ilişkileri eklendi.
Migration tekrar veya otomatik çalıştırılmaz. `ddl-auto=validate` korundu ve Hibernate
şema uyumluluğu gerçek veritabanına salt okunur bağlanan context testiyle doğrulandı.
JPA rolleri `@ElementCollection` + enum/string eşlemesiyle yönetir. Yeni kayıtlar
modelin varsayılan STUDENT koleksiyonuyla kaydedilir; public DTO'lar rol kabul etmez.

## İlk erişim matrisi

| İşlem | STUDENT / CLUB_ADMIN | ADMIN |
| --- | --- | --- |
| POST /api/auth/register ve /login | Herkese açık | Herkese açık |
| GET /api/users | 403 | İzinli |
| POST /api/users (eski CRUD oluşturma yolu) | 403 | İzinli |
| GET /api/users/{id} | Yalnızca kendisi | İzinli |
| PUT /api/users/{id} | Yalnızca kendisi | İzinli |
| DELETE /api/users/{id} | 403 | İzinli |

Hesap silme ilk aşamada admin ile sınırlı; kullanıcıya self-delete politikası ileride
üyelik, etkinlik ve mesaj ilişkilerinin yaşam döngüsüyle birlikte ele alınacak.

## Uygulama mimarisi

Spring Security method security servis sınırında uygulanır. Endpoint kuralları
ilk koruma katmanıdır; servis çağrısında da rol ve kaynak sahipliği denetlenir.
Authentication olmayan istek 401, doğrulanmış fakat yetkisiz istek 403 alacak.
Başka kullanıcıya ait kayıt için sahiplik kontrolü veri yüklenmeden yapılır.

JWT'nin mevcut imzası ve claim sözleşmesi korundu. Roller JWT istemci verisinden
alınmak yerine doğrulanmış sub üzerinden veritabanından okunur; böylece rol kaldırma
sonraki istekte etkili olur. Silinmiş, parolasız veya hiç rolü olmayan hesap kabul edilmez.
Pozitif sayısal subject long sınırını aştığında da güvenli 401 yanıtı döner.
İleride kulüp, etkinlik, duyuru, yorum ve mesaj için ayrı policy bileşenleri aynı
kullanıcı/rol modelini kaynak ilişkileriyle birleştirecek. Yeni bağımlılık gerekmiyor.

## Tamamlanan testler

- Önce mevcut 57 authentication/CRUD testi başarılı biçimde tekrar çalıştırıldı.
- Öğrenci kendi profiline erişebilir, başkasını okuyamaz veya güncelleyemez.
- CLUB_ADMIN rolü platform admin yetkisi veya başka kulüpte yönetici yetkisi vermez.
- ADMIN için platform işlemleri; rol atama public payload'dan yapılamaz.
- Servis doğrudan çağrıldığında da erişim kuralları uygulanır.
- Rolü kaldırılmış/silinmiş kullanıcı için mevcut JWT ile erişim tekrar değerlendirilir.
- Register varsayılan rolü, eski NULL parolalı hesaplar ve mevcut kayıtların korunması.

Sonuç: **69 test geçti; 0 hata ve 0 atlanan test**. Bunların 33'ü JWT/RBAC test çalıştırmasıdır.
Admin işlemleri mock repository ile test edildi; gerçek veritabanında hiç ADMIN oluşturulmadı.
Kulüp kaynakları henüz yoktur: kulüp bazlı üyelik/yönetici sorgusu sonraki kulüp modülünde
uygulanacak. Bu aşamada CLUB_ADMIN yalnızca kendi profili için öğrenci haklarına sahiptir;
platform kullanıcılarını yönetemez. Yeni modüller kendi policy'lerini servis katmanında çağırmalıdır.
Metot güvenliği kaynağı: [Spring Security Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).

## Canlı doğrulama ve kayıtların korunması

- Başlangıçta 2 kullanıcı vardı. Authentication testi için ID 3 eklendi.
- Onaylı V002 bu 3 kullanıcıya yalnızca STUDENT ilişkisi ekledi, users satırlarını değiştirmedi.
- RBAC sonrası gerçek Register/Login testi için ayrı ID 4 eklendi; STUDENT ilişkisi JPA ile saklandı.
- Önceden bulunan kullanıcılar her test öncesi/sonrası satır özetleriyle aynı bulundu.
- Register 201; Login 200; kendi profili 200; tokensız 401; yanlış parola/geçersiz JWT 401;
  kullanıcı listesi, başka profil ve sahip olunmayan ID için PUT 403 döndü.
- Test şifreleri/token'ları gösterilmedi veya saklanmadı. Anahtar yeniden üretilmedi;
  mevcut güvenli dosya kullanıldı ve test backend'i sonunda kapatıldı.
- Şu an 4 kullanıcı vardır, tamamı STUDENT; ADMIN ataması yapılmadı.
- Yeni bağımlılık, commit veya push yok. Diğer projeler/servisler/veritabanları kullanılmadı.

## Değişen dosyalar

- `config/SecurityConfig.java`: method security, endpoint rolleri ve DB authority converter.
- `entity/Role.java`, `entity/User.java`: üç rol ve user_roles koleksiyonu.
- `security/DatabaseJwtAuthenticationConverter.java`: güncel DB rollerinden authentication.
- `security/UserAccessPolicy.java`: profil sahipliği/admin kuralı.
- `service/UserService.java`: rol ve sahiplik için servis sınırında @PreAuthorize.
- `security/JwtAuthenticationTest.java`: RBAC, rol enjeksiyonu, değişen roller ve servis kontrolleri.
- `db/migrations/V002__add_user_roles.sql`: açıkça onaylanan, kontrollü migration.
- `docs/development.md`, `docs/local-development.md`, bu belge: güncel kurulum/durum notları.

## Sıradaki aşama

Üniversite/öğrenci/kulüp modelleri ve club_memberships üzerinden kulübe özel yetki politikaları.
İlk platform ADMIN kullanıcısı otomatik seçilmez; ayrı açık talimatla atanmalıdır.
