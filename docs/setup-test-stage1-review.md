# Kurulum / test altyapısı — 1. aşama sonucu

Tarih: 2026-10-30. Durum: **repo ve test sınırları tamamlandı; 2. aşama için onay bekleniyor**.
Başlangıç commit'i `8ac68ff`; commit veya push yapılmadı.

## Yapılan değişiklikler

- README eklendi: kurulum, ayrı uygulama/test değişkenleri, güncel komutlar ve henüz
  tamamlanmayan boş DB kurulumunun sınırı açıklanıyor.
- `contextLoads()` silinmedi veya devre dışı bırakılmadı; `CampusPlusBackendApplicationIT`
  sınıfına taşınıp normal test grubundan ayrıldı.
- `postgres-it` Maven profiline Boot parent'ın yönettiği Failsafe 3.5.6 eklendi.
  integration-test/verify execution'ları parent'tan gelir; test bulunmazsa hata verir.
- Çalışma zamanı bağımlılığı veya üretim Java/resource ayarı değişmedi.
- `.env` ve `.env.*` Git dışında tutuldu; `.env.example` paylaşılabilir istisna.
- development/local-development belgelerinde eski sonuçlar tarihsel olarak işaretlendi.

## Test bağlantısı sınırı

Test-only `PostgresTestDatabase` yalnız TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD
değişkenlerini okur; ana uygulamanın DB_* değerlerine geri düşmez. Eksik/boş ayar hata verir.
URL tam olarak `jdbc:postgresql://127.0.0.1:5434/campus_plus_test` olmalıdır. Ana DB,
diğer port/host/DB, URL parametreleri veya alternatif JDBC biçimleri kabul edilmez.
Reddedilen URL gizli bilgi içerebileceğinden hata mesajında yazdırılmaz.

Context testi kendi DataSource bean'ini kullanır; uygulama/Hikari URL override'ı üzerinden
başka hedefe yönlenmez. Gelecekte onaylı test DB'sine bağlantı kurulduğunda adres, port,
DB adı ve PostgreSQL 17 sürümü salt okunur SELECT ile ayrıca doğrulanır. Uygunsuz
bağlantı kapatılır; sürücü hatasının ayrıntıları yeniden fırlatılıp loga taşınmaz.

Context bağlantısı read-only, Hibernate schema action validate'dir. Test JWT anahtarı
bellekte rastgele oluşturulur. Migration, seed veya DB oluşturma kodu eklenmedi.

## Doğrulama

**Normal takım: 189 test geçti; 0 başarısız, 0 hata, 0 atlanan.**

- Önceki 163 DB bağımsız testin tamamı korundu.
- 26 yeni güvenlik testi: eksik/boş TEST_DB ayarı, DB_* fallback reddi, ana DB/yanlış URL
  reddi, credential gizliliği, hedef kimliği, hatada bağlantı kapatma ve alternatif
  kimlikle bypass reddi.
- Hedef kimliği/JDBC davranışları mock DataSource/Connection ile sınandı. Bunlar gerçek
  PostgreSQL doğrulaması veya gerçek DB bağlantısı değildir.

Normal takım `clean test` ile, bütün DB_*, TEST_DB_* ve JWT_SECRET değişkenleri kaldırılmış
alt süreçte çalıştı. Emekli test sınıfının eski derlenmiş çıktısı da temizlendi. Maven için
Campus+ geçici önbelleği ve Mockito agent izni kullanıldı.

Failsafe profili iki ayrı olumsuz senaryoda ayrıca denendi:

1. DB_* mevcut fakat TEST_DB_* yok: `TEST_DB_URL is required for postgres-it` hatası.
2. TEST_DB_URL ana `campus_plus` DB'sini gösteriyor: özel hedef doğrulama hatası.

İkisinde de dynamic property hazırlığı DataSource başlatılmadan durdu; ana DB'ye fallback
olmadı. Bu iki Maven verify çalışmasının başarısız olması beklenen güvenlik sonucudur;
context testinin sessiz atlandığı veya başarıyla yüklendiği iddia edilmez. Rastgele dummy
credential canary değerinin hata çıktılarında bulunmadığı da kontrol edildi.

Gerçek `contextLoads()` başarısı **bu aşamada çalıştırılmadı**. Test DB'si henüz
oluşturulmadı; gerçek metadata/context doğrulaması 3. aşama onayı sonrasındadır.

## Komutlar ve sonraki adım

```sh
./mvnw test
./mvnw -Ppostgres-it verify
```

İkinci komut henüz kurulmamış, ayrıca onay gerektiren test DB'sini ister. Eski sınıfı
derlemiş çalışma kopyalarında ilk seferde `./mvnw clean test` kullanılmalıdır.
Eski belgelerin ana DB ile context çalıştıran tarihsel komutları güncel yöntem değildir.

Bu aşamada PostgreSQL bağlantısı yapılmadı; gerçek kullanıcı/rol/parola/kulüp verisine,
şemaya, servis/IDE ayarlarına veya Docker ortamına müdahale edilmedi. V001–V005 ve
üretim kodu korundu. Flyway, başlangıç SQL'i, CI veya Event eklenmedi.

Sonraki adım [planın 2. aşaması](setup-test-infrastructure-plan.md): ayrıca onay alındıktan
sonra ana Campus+ PostgreSQL 17 şemasının salt okunur envanteri ve kontrollü boş kurulum /
migration geçmişi dosyalarının hazırlanması. Hiçbir SQL yeni açık onay olmadan uygulanmaz.
