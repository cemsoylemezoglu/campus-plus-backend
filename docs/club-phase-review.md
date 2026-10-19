# Club ve ClubMembership — uygulama / migration incelemesi

Tarih: 2026-10-19. Durum: **V004/V005 uygulandı; 164 otomatik test ve 93 canlı kontrol başarılı**.
Bu aşamanın ana dal başlangıcı b25b070; Git kaydı doğrulama sonrasında kullanıcı tarafından ayrıca onaylandı.
Kullanıcının açık migration ve canlı test talimatıyla yalnızca `127.0.0.1:5434/campus_plus`
PostgreSQL 17.11 kullanıldı. Önceden bulunan kullanıcı/rol/akademik kayıtlar korundu;
servis yapılandırması, diğer veritabanları ve Docker ortamları değiştirilmedi.

## Aşamalar ve tamamlanan kapsam

1. Mevcut çalışma ağacı ve onaylı mimari incelendi; başlangıçtaki 112 izole test geçti.
2. Club, ClubMembership, enum'lar, repository sorguları, DTO'lar ve policy'ler eklendi.
3. Kulüp oluşturma, inceleme/onay ve yaşam döngüsü; tüm üyelik işlemleri ve sahiplik devri eklendi.
4. Profil üniversitesi değişiminde üyelik engeli bağlandı; güvenlik ve metadata testleri tamamlandı.
5. Açık onaydan sonra hedef SQL üzerinden doğrulandı; V004 ve V005 sırasıyla commit edildi.
6. Gerçek şema doğrulaması, tüm otomatik testler ve canlı PostgreSQL/API kontrolleri tamamlandı.

### Kulüp yaşam döngüsü

- Profil sahibi STUDENT kendi üniversitesinde kulüp kurar. Üniversite aktif olmalıdır.
- Tek transaction: PENDING_APPROVAL kulübü + kurucunun ACTIVE OWNER üyeliği + CLUB_ADMIN marker'ı.
- Kullanıcı universityId, creator, status veya global rol seçemez.
- İsim/slug/açıklama/URL sınırları doğrulanır. Slug kanonik küçük harf/rakam/tiredir;
  kurum içinde tekildir, sonradan değişmez; arşivlenince yeniden kullanılamaz.
- ADMIN: PENDING_APPROVAL → ACTIVE/REJECTED; ACTIVE → SUSPENDED; SUSPENDED → ACTIVE.
  Ret ve askıya alma gerekçe ister. OWNER'sız kulüp onaylanamaz.
- OWNER/ADMIN: REJECTED → PENDING_APPROVAL yeniden başvurusu; arşivleme.
- ACTIVE kulüpte OWNER/MANAGER bilgileri güncelleyebilir. Taslak/ret durumunda OWNER/ADMIN;
  SUSPENDED/ARCHIVED durumda bilgi düzenleme ve yeni başvuru kapalıdır.
- Arşiv geri açılmaz; fiziksel silme yoktur. Geçmiş üyelikler korunur.
- Aktif kulüpler authentication ile listelenir. Taslak/reddedilen kulüp OWNER/ADMIN'e,
  askıdaki kulüp mevcut üyeye/admin'e, arşiv geçmiş kabul edilmiş üyeye/admin'e görünür.
- Review note ve reviewedBy yalnızca özel review DTO/endpoint'inde; public detayda yoktur.

### Üyelik ve kulüp rolleri

- Aynı üniversite ve tamamlanmış profil gerekir; üniversite aktif olmalıdır.
- Aynı öğrenci birden fazla kulübe başvurabilir/üye olabilir; aynı kulüpte tek ilişki satırı.
- İlk başvuru PENDING MEMBER, HTTP 201. LEFT/REJECTED yeniden başvuru aynı satırda HTTP 200.
- Yeniden başvuruda version zorunlu; varsayılan 60 saniye bekleme uygulanır.
  İsteğe bağlı `campus.clubs.reapply-cooldown-seconds` 1..86400 aralığındadır;
  hiçbir gerçek ortam ayarı değiştirilmedi.
- Yerel OWNER/MANAGER veya ADMIN başvuruyu onaylar/reddeder. Başvuru sahibi iptal edebilir.
- ACTIVE veya SUSPENDED üye ayrılabilir. Arşivlenmemiş kulübün OWNER'ı devretmeden ayrılamaz.
- MANAGER sıradan üyeyi çıkarabilir; yöneticiyi çıkarma OWNER/ADMIN gerektirir.
- REMOVED kişi kendiliğinden tekrar başvuramaz; yetkili allow-reapplication ile açar,
  ardından aynı cooldown/version kuralları uygulanır.
- OWNER/ADMIN MEMBER ↔ MANAGER değiştirir; birden fazla MANAGER olabilir.
  Rol endpoint'i OWNER atayamaz veya son OWNER'ı indiremez.
- ADMIN üyeyi askıya alabilir. OWNER için önce devir/arşiv gerekir.
  Askıdan dönüş ACTIVE MEMBER'dır; eski yönetici yetkisi kendiliğinden geri gelmez.
- Sahiplik devri aynı kulübün ACTIVE, uygun profilli üyesine; eski OWNER MANAGER olur.
- CLUB_ADMIN tek başına yetmez: ilgili club_id için ACTIVE OWNER/MANAGER üyeliği de gerekir.
- Son yerel yöneticilik bitince başka arşivlenmemiş kulüpte yönetici değilse marker kaldırılır.
  Diğer kulüpte yönetim devam ediyorsa korunur. STUDENT/ADMIN silinmez.
- Üniversite pasif olsa da mevcut MANAGER'ı indirmek ve hak geri almak mümkündür.

## Transaction ve veri bütünlüğü

Tüm yazmalar service sınırında transaction içindedir. Lock sırası users ID ASC → club → üyelik.
Actor da kilitlenen kullanıcılara dahildir. Rol/üyelik yönetimi affected kullanıcıyı,
devir eski/yeni sahibi, arşivleme aktif yöneticileri kilitler.

Lock sonrasında managed entity'ler refresh edilir; OSIV/ön kontrol sırasında yüklenmiş eski
rol/üyelik bilgisiyle devam edilmez. Arşivlemede yönetici ID kümesi, devirde OWNER kimliği
tekrar kontrol edilir; yarış varsa kısmi işleme devam edilmeden 409 döner.
Üyelik hedefi hem membershipId hem clubId ile sorgulanır; başka kulübe ait ID kullanılamaz.

At most one ACTIVE OWNER DB partial unique index ile, arşivlenmemiş kulüpte owner'ın
varlığı kilitli servis akışlarıyla korunur. Bir CHECK kısıtının çapraz satırlarda
"en az bir OWNER" garantisi verdiği iddia edilmez; manuel/uygulama dışı yazma daima ayrı yetkidir.

Devirde eski OWNER MANAGER'a indirilip flush edilir, sonra yeni OWNER atanır; index'in
anlık ihlali önlenir. İkinci adım başarısızsa dış transaction tamamını geri almalıdır.
Başarılı devir ve iki paralel devir gerçek HTTP ile doğrulandı. Ayrıca doğrudan SQL transaction'ında
eski OWNER indirilip yeni OWNER atandıktan sonra unique hatası oluşturuldu; bağlantının kapanmasıyla
bütün rol/version değişikliklerinin geri alındığı doğrulandı. Bu SQL rollback testi,
Spring servisinin ikinci flush'ına yapay hata enjekte eden bir test değildir.

Tüm state/role action'ları request.version ister. Devir clubVersion, ownerMembershipVersion
ve targetMembershipVersion'ı ayrı kontrol eder. Membership değişimi kendi @Version'ını
artırır; her üyelik değişikliğinin clubVersion'ı artırdığı varsayılmaz. ClubVersion kulübün
bilgi/durum sürümüdür. Lock'lar ve ayrı üyelik sürümleri yarışları birlikte sınırlar.

Yeni StudentProfilePolicy, aynı users lock'ı altında PENDING/ACTIVE/SUSPENDED üyelik varken
üniversite değişimini engeller. İsim/bio gibi aynı üniversitedeki güncellemeler korunur.

## API özeti

| İşlem | Endpoint |
| --- | --- |
| Liste/detail/oluştur/güncelle | GET /api/clubs; GET /api/clubs/{id}; POST /api/clubs; PATCH /api/clubs/{id} |
| Özel değerlendirme detayı | GET /api/clubs/{id}/review |
| Yeniden inceleme / arşivleme | POST /api/clubs/{id}/resubmit; /archive |
| ADMIN inceleme kuyruğu | GET /api/admin/clubs?status=&page=&size= |
| ADMIN durum değişimleri | POST /api/admin/clubs/{id}/approve, /reject, /suspend, /restore |
| Başvuru | POST /api/clubs/{clubId}/membership-requests; ilk gövde boş veya {} olabilir |
| Kendi üyelikleri | GET /api/me/club-memberships?status=&page=&size= |
| Yönetici üye listesi | GET /api/clubs/{clubId}/memberships?status=&page=&size= |
| Başvuru iptal / ayrılma | POST /api/clubs/{clubId}/membership/cancel, /leave |
| Üye kararları | POST /api/clubs/{clubId}/memberships/{memberId}/approve, /reject, /remove, /allow-reapplication |
| Yönetici rolü | PATCH /api/clubs/{clubId}/memberships/{memberId}/role |
| Devir | POST /api/clubs/{clubId}/ownership-transfer |
| ADMIN üyelik askı / dönüş | POST /api/admin/clubs/{clubId}/memberships/{memberId}/suspend, /restore |

Yeni state action'ları 200, kulüp/ilk başvuru oluşturma 201 döner. Hatalar 400 validation,
401 authentication, 403 yetki, 404 görünmeyen/yok kaynak ve 409 durum/version/race çakışmasıdır.
Public ClubResponse alanları: club (ClubSummary), description, status, createdAt, updatedAt, version.
Üyelik ve review yanıtları no-store; roster projeksiyonu email/hash/bio veya entity taşımaz.
Listelemeler 1..100 boyutla ve sabit id sıralamasıyla sınırlıdır; sayfalı count'lar toplu sorgulanır.

## Onayla uygulanan migration SQL'leri

- [V004 — clubs](../db/migrations/V004__add_clubs.sql)
- [V005 — club_memberships](../db/migrations/V005__add_club_memberships.sql)

V004: clubs tablosu, university/creator/reviewer FK'leri, kurum+slug unique,
name/description/slug/status/version CHECK ve listeleme indeksleri.

V005: club_memberships tablosu, club/profile/reviewer FK'leri, club+user unique,
role/status/ACTIVE-role/joinedAt/version CHECK, tek aktif OWNER partial unique indeksi
ve üye/klüp sorgu indeksleri. ACTIVE olmayan üyelik MEMBER olmalıdır.

Her dosya ayrı BEGIN/COMMIT, 5s lock_timeout ve 15s statement_timeout içerir.
Sadece 127.0.0.1:5434/campus_plus PostgreSQL 17 hedefi kabul edilir. Mevcut tablo varsa
sessiz devam edilmez. Eski users/user_roles/universities/student_profiles satırları
transaction içinde JSONB karşılaştırmasıyla korunur. V005, V004'ten sonra ve henüz
club verisi yokken çalıştırılmak üzere tasarlanmıştır. Yeni tabloya seed/rol ataması yoktur.

Her dosyanın transaction'ı ayrıdır: V005 başarısızsa V004'ün boş clubs tablosu kalabilir.
Bu durumda uygulama başlatılmaz; hata açıklanıp yeni onay olmadan destructive geri alma
veya tekrar uygulama yapılmaz. V004 ve V005 ikisi de tamamlanmadan yeni sürüm çalıştırılmaz.
Yeni tablo FK'leri RESTRICT'tir; kullanıcı/profile/kurum silmesi ilişkiler varken güvenli
409'a dönüşebilir. Migration eski kullanıcıya CLUB_ADMIN veya OWNER vermez.

İki dosya da PostgreSQL 17.11'de başarılı BEGIN/DO/COMMIT ile uygulandı. Her birinden sonra
önceden bulunan tüm satırlar tekrar karşılaştırıldı. Yeni tabloların ilk satır sayısı 0/0 idi.
Katalogdan 6 foreign key, CHECK/unique kısıtları ve tek aktif OWNER partial unique indeksi
doğrulandı. `ddl-auto=validate` korundu; context ve gerçek uygulama açılışı şemayı kabul etti.
Dosyalar manuel sürümlü SQL'dir; otomatik migration mekanizması veya yeni geçmiş tablosu eklenmedi.
Başlıktaki teklif açıklaması dosyaların hazırlanma durumunu yansıtır; uygulanan dosyaların içeriği
checksum korunması için sonradan değiştirilmedi. Yeniden çalıştırılmamalıdır.

Uygulanan dosyaların SHA-256 değerleri:

- V004: `ef2f3a5ed1705de0947dbea3753f3d81dc2fd8035340173c53221a8d675ad7fa`
- V005: `547c26d089f5f21edc7f16ba865cfa668e6380dcdfdf0be165892be42e3d3a72`

## Doğrulama sonucu

**Derleme/paketleme başarılı; tam takımda 164 test, 0 hata, 0 başarısız ve 0 atlanan test.**

- 51 yeni ClubModulesTest çalıştırması: gerçek JWT/filter zinciri, method security ve
  validation, servisler; repository ve EntityManager mock.
- Mevcut 112 izole testin tamamı başarılı. Hibernate metadata testi yeni iki entity ve
  entity/projeksiyon/count HQL sorgularını da bağlantı açmadan derliyor.
- 113. eski test contextLoads(), migration sonrasında gerçek Campus+ şemasına salt okunur
  JDBC bağlantısı ile çalıştırıldı ve geçti. Böylece önceki 163 izole teste bu kontrol eklendi.
- Senaryolar: lifecycle, gizli inceleme, cross-club IDOR, payload rol/identity enjeksiyonu,
  aynı üniversite, çoklu üyelik/yönetici, OWNER koruması/devir, bekleme/yeniden başvuru,
  rol kaldırma, arşiv/marker eşgüdümü, doğrudan servis çağrısı, lock/version/unique hataları,
  kilit beklerken rol/üyelik/sahip değişmesi ve veri gizliliği.

```sh
./mvnw test
```

Tam takım için DB_URL/DB_USERNAME/DB_PASSWORD süreç ortamından sağlandı; context bağlantısında
`options=-c%20default_transaction_read_only%3Don` kullanıldı. Maven/Mockito için mevcut izinler ve
çevrimdışı `/tmp/campus-plus-maven-repository` önbelleği kullanıldı; bağımlılıklar değiştirilmedi.

### Gerçek HTTP / PostgreSQL: 93 başarılı kontrol

[`scripts/verify_clubs_postgres.py`](../scripts/verify_clubs_postgres.py) standart Python kütüphanesi,
Java uygulaması ve psql kullanır. Açık `CAMPUS_PLUS_LIVE_TESTS=1` gerekir; yalnızca tam olarak
`jdbc:postgresql://127.0.0.1:5434/campus_plus` bağlantısını kabul eder ve sunucuyu SQL ile doğrular.
DB kimlik bilgileri ve JWT_SECRET yalnızca süreç ortamında/bellektedir; değeri yazdırılmaz.
Uygulama loopback üzerinde boş bir test portunda açıldı ve test sonunda yalnızca bu süreç durduruldu.

- Yeni hesaplarla Register → BCrypt Login → gerçek JWT → korumalı HTTP uçları.
- Kulüp onayı/ret/yeniden başvuru, askıya alma/dönüş/arşivleme; üyelik/rol/ayrılma yönetimi.
- CLUB_ADMIN'in başka kulüpte yetkisizliği, kulüpler arası membershipId, 401/403/404 ayrımı,
  profil üniversitesi kuralı, özel review/üye DTO'ları ve no-store.
- Eşzamanlı aynı kullanıcı başvurusu: 201/409; aynı sürümle kulüp düzenleme: 200/409;
  aynı üniversite/slug oluşturma: 201/409.
- İki paralel sahiplik devrinden biri başarılı, diğeri yetki/version nedeniyle reddedildi;
  tek OWNER ve eski sahibin MANAGER olması doğrulandı. Eski sahip ayrıldıktan sonra aynı JWT
  yönetim yetkisini kaybetti; yeni OWNER devir olmadan ayrılamadı.
- Başvuru/üniversite değişimi yarışında yalnızca biri başarılı; üniversiteler arası geçersiz
  PENDING/ACTIVE/SUSPENDED üyelik oluşmadığı SQL ile doğrulandı.
- Ayrı psql transaction'ı yeni test yöneticisini/kulübünü kilitlerken gerçek HTTP güncellemesi
  gönderildi. `pg_stat_activity` üzerinde Lock beklemesi görüldü. Transaction yöneticiliği kaldırıp
  commit ettiğinde bekleyen istek güncel yetkiyi okuyarak 403 döndü; kulüp değişmedi.
- PostgreSQL unique, ikinci aktif OWNER partial unique, invalid role CHECK ve FK ihlalleri
  beklenen SQLSTATE ile reddedildi; çok adımlı SQL rollback'te eski üyelikler/version'lar korundu.

Bu testler kontrollü paralel isteklerdir; uzun süreli yük testi veya kasıtlı deadlock enjeksiyonu
yapılmadı. Canlı reapplication cooldown reddi doğrulandı; sürenin dolması ve başarılı tekrar
başvuru izole servis testlerinde kapsanır. Her canlı tekrar yeni kalıcı test verisi oluşturur;
otomatik Maven takımının parçası değildir ve haber verilmeden yeniden çalıştırılmamalıdır.

### Veri koruma ve bırakılan ayrı test verileri

Migration öncesindeki **5 users, 5 user_roles, 1 universities, 1 student_profiles** satırı
migration sonrası ve canlı test sonrası JSON alanlarını kapsayan satır fingerprint'leriyle
karşılaştırıldı. Önceden bulunan hesaplara yeni rol eklenmedi; parola/hash/profiller değiştirilmedi.

- Yeni test kullanıcıları: **6–12**; hepsi sonunda yalnızca STUDENT.
- Geçici ADMIN yalnızca yeni ID 6'ya verildi ve kaldırıldı; aynı JWT ile admin isteği 403 aldı.
- Yeni test üniversiteleri **2–3** pasif; yeni test kulüpleri **1–4** arşivlenmiş durumda.
- Yeni 6 öğrenci profili ve 7 kulüp üyelik satırı silinmeden bırakıldı. Test verisi temizliği
  için mevcut veya yeni kayıtlarda toplu silme yapılmadı; sequence değerleri geri alınmadı.
- Ayrı veritabanı/şema/container açılmadı. PostgreSQL 14 veya diğer proje/servislere bağlanılmadı.

Son salt okunur kontrolde tablo toplamları users=12, user_roles=12, universities=3,
student_profiles=7, clubs=4, club_memberships=7 idi. Yeni tablo constraint'lerinin tamamı
`convalidated=true`, indeksler `indisvalid/indisready=true`; açık test uygulaması bağlantısı
ve kalıcı yükseltilmiş rol kalmadığı da doğrulandı.

## Değişen dosya grupları ve sonraki adım

- Club / ClubMembership / üç enum, iki repository ve dto/club sözleşmeleri.
- ClubController / ClubMembershipController; club servisleri/workspace/settings.
- ClubPolicy / ClubMembershipPolicy / StudentProfilePolicy.
- StudentProfileService üyelik engeli ve GlobalExceptionHandler 400 iş kuralı yanıtı.
- ClubModulesTest ve mevcut akademik testlerin yeni bağımlılık/mapping güncellemeleri.
- V004/V005 SQL ve bu inceleme belgesi.
- Açık opt-in gerektiren gerçek PostgreSQL/API test aracı.

Yeni bağımlılık eklenmedi; mevcut JWT/RBAC ve University API sözleşmeleri korunuyor.
Sırlar, kalıcı ortam değişkenleri ve PostgreSQL servis ayarları değiştirilmedi.

Club/ClubMembership aşaması yerel doğrulamayla tamamlandı. Kod, migration, test aracı ve
ilgili dokümantasyonun Git inceleme/commit/push işlemleri kullanıcı tarafından ayrıca onaylandı.
Yeni modüle geçilmedi.

Teknik kaynaklar: [PostgreSQL 17 constraints/partial unique](https://www.postgresql.org/docs/17/ddl-constraints.html),
[Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
