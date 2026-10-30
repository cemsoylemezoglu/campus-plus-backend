# Campus+ yerel çalıştırma

Güncel uygulama ve test komutları [README](../README.md) içindedir. Bu belgenin aşağıdaki
2026-10-18 test sonuçları tarihsel kayıttır; güncel DB satır sayısı veya test komutu değildir.
`contextLoads()` artık yalnız ayrı `postgres-it` profili ve TEST_DB_* ile çalışır;
ana Campus+ DB'sine bağlanan normal Maven testi olarak kullanılmaz.

## Anahtar oluşturma (yalnızca ilk kurulum)

Bu bilgisayardaki mevcut anahtar kontrol edildi ve korundu; tekrar üretmek gerekmiyor.
Yeni bir kurulumda, değeri ekrana yazmadan oluşturma örneği:

```sh
mkdir -p -m 700 ~/.campus-plus-local
(umask 077; set -C; openssl rand -base64 32 > ~/.campus-plus-local/jwt-secret)
```

`set -C` mevcut dosyanın üzerine yazmayı engeller. Mevcut anahtarı değiştirmek eski
JWT imzalarını geçersiz kılar. Gerçek anahtar kaynak koduna veya Git'e konulmaz.

## IntelliJ IDEA ortam değişkenleri

Yerel anahtar proje dışında `~/.campus-plus-local/jwt-secret` dosyasında tutulur.
32 kriptografik rastgele baytın Base64 karşılığıdır. Dizin izni 700, dosya izni 600'dür.
Anahtarı yeniden üretmeyin; aynı anahtar tekrar başlatmalarda korunmalıdır.

1. Terminalde değeri göstermeden panoya kopyalayın:

   ```sh
   pbcopy < ~/.campus-plus-local/jwt-secret
   ```

2. IntelliJ IDEA: **Run → Edit Configurations → Campus+ Spring Boot yapılandırması**.
3. **Environment variables** alanındaki düzenleme düğmesini açın. Alan görünmüyorsa
   **Modify options → Environment variables** seçeneğini etkinleştirin.
4. Mevcut `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` satırlarını koruyun. Yeni satır ekleyin:
   ad `JWT_SECRET`, değer panodaki anahtar. Alanın tamamını yeni değerle değiştirmeyin.
5. **Apply → OK**. Gizli değişkenler içeren yapılandırmayı **Store as project file**
   ile paylaşmayın; anahtarı kaynak koduna, application.properties'e, komut argümanına veya Git'e koymayın.
6. Mevcut DB_URL yalnızca `127.0.0.1:5434/campus_plus` (aynı hedef için localhost)
   olmalıdır. Backend ana sınıfını IntelliJ'den çalıştırın. `ddl-auto=validate` korunur.

Bu aşamada IntelliJ yapılandırması otomatik değiştirilmedi; JWT_SECRET satırını
kullanıcının yukarıdaki adımlarla eklemesi gerekir. Diğer ortam değişkenleri korunmuştur.

## Gerçek HTTP test sonucu — 2026-10-18

Paketleme başarılı. Çalıştırılabilir JAR, yalnızca 127.0.0.1:8080 üzerinde ve
127.0.0.1:5434/campus_plus PostgreSQL 17.11 bağlantısıyla test edildi.

| İşlem | Sonuç |
| --- | --- |
| Ayrı kullanıcıyla POST /api/auth/register | 201 |
| Aynı kullanıcıyla POST /api/auth/login | 200, Bearer token, 900 saniye |
| GET /api/users/2, tokensız | 401 |
| GET /api/users/2, geçerli token | 200, yalnızca id/name/email |
| Yanlış parola ile login | 401 |
| Geçersiz JWT | 401 |
| Değiştirilmiş JWT imzası | 401 |
| Eski NULL parolalı hesapla login | Genel 401 |

Test kaydı: ID **2**, `campusplus-local-test-3ef02d7dcd4e@example.com`.
Bu ayrı kayıt bilerek bırakıldı; parolası rastgele üretildi ve saklanmadı veya gösterilmedi.
Mevcut 1 kullanıcının tüm alanları test öncesi/sonrası özetleriyle karşılaştırılıp
aynı bulundu. Test sonrası toplam 2 kullanıcı vardır. Yalnızca test kullanıcısı
Register API'si üzerinden eklendi; şema ve eski kayıtlar değiştirilmedi.
Yeni parola için veritabanında yalnızca BCrypt hash bulunduğu doğrulandı.
Parolalar, token'lar ve JWT anahtarı test çıktısına yazılmadı. Anahtarın kaynak
ve backend log dosyalarında bulunmadığı ayrıca kontrol edildi.

IntelliJ'den başlatmaya engel olmaması için yalnızca bu testte açılan Java örneği
kapatıldı. Başka servis, veritabanı veya proje kullanılmadı. Commit/push yapılmadı.


## RBAC sonrası güncel sonuç

İlk doğrulamanın yukarıdaki tarihsel sonucu korunmuştur. Sonraki çalışmada başlangıçtaki
57 test yeniden geçti; onaylı RBAC migration ve güvenlik testlerinden sonra tam takım
69 test ile başarılı oldu. Ayrı test kullanıcıları ID 3 ve ID 4 eklendi; önceki tüm
users alanları korundu. Toplam 4 kullanıcı vardır, tamamı STUDENT; otomatik ADMIN yoktur.

Artık öğrenci token'ıyla `GET /api/users` 403 alır. Kendi profilini doğrulamak için
`GET /api/users/{login-yapılan-kullanıcının-id-si}` kullanın: geçerli token 200, tokensız 401,
başka kullanıcı ID'si 403. `PUT` için de sahiplik kontrol edilir. Ayrıntılar ve dosya listesi:
[RBAC](rbac-proposal.md). JWT_SECRET mevcut dosyada korunmuştur; IntelliJ ortamı otomatik değiştirilmemiştir.
