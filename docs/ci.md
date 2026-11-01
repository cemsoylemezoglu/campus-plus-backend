> Post test DB aşaması (2026-11-01): workflow V005–V009 boş kurulum, V009 kontratı
> ve sınırlı Post/audit grant'i için hazır. Yerelde 451 Java/38 Python ve actionlint
> geçti. Bu workflow henüz push edilmedi/uzakta çalıştırılmadı.
> [Güncel sonuç](post-test-validation.md). Aşağıdaki sürüm sonuçları tarihseldir.

> Notification test DB aşaması (2026-11-01): workflow V005–V008 kurulum, V008 kontratı
> ve sınırlı notification grant'i için hazır. 374 Java/36 Python ve actionlint başarılı.
> Yeni workflow henüz push edilmedi/uzakta çalıştırılmadı. [Güncel sonuç](notification-test-validation.md).

> Announcement test DB aşaması: workflow V005–V007 kurulum, V007 kontratı ve sınırlı
> yeni tablo grant'i için hazırlandı. Yerelde 328 Java/34 Python testi ve actionlint
> başarılı. V007 workflow'u henüz GitHub'a push edilmedi/uzakta çalıştırılmadı.
> [Güncel doğrulama](announcement-test-validation.md).

# Campus+ CI — hazırlanan doğrulama akışı

Durum (2026-10-31): **ilk GitHub çalışması başarılı; unit ve postgres işleri geçti**.
`d0b250e` için [run 41144506168](https://github.com/cemsoylemezoglu/campus-plus-backend/actions/runs/41144506168)
salt okunur GitHub API üzerinden doğrulandı. Bu, önceki V005 sürümünün sonucudur.
V008 workflow güncellemesi henüz commit/push edilmedi ve GitHub üzerinde çalıştırılmadı.

[Workflow](../.github/workflows/ci.yml) GitHub-hosted Ubuntu 24.04 üzerinde iki bağımsız
iş tanımlar. Yerel Mac/Homebrew/PostgreSQL servislerini veya Docker'ı kullanmaz.
Self-hosted runner tanımı yoktur. `main` push/PR ve workflow_dispatch tetikleyicileri
vardır; ilk çalışma kullanıcının ayrıca onayladığı push sonrasında gerçekleşti.

## İzole test işi

Java 21 / Python 3.12 ile `./mvnw clean test`, Python araç testleri ve tarihsel
V001–V005 checksum kontrolü. DB/JWT/uygulama sırları gerekmez. Maven bağımlılık cache'i
kullanılır; sır dosyaları veya test çıktıları cache'e konmaz.

## PostgreSQL işi

1. Resmi PGDG paketlerinden PostgreSQL 17 binary'leri sağlanır (runner'da varsa yeniden
   kurulmaz). Kurulu farklı PostgreSQL cluster'ı durdurulmaz/yeniden yapılandırılmaz.
2. `scripts/ci_postgres.py` yalnız GitHub-hosted Linux işinde, RUNNER_TEMP altında yeni
   0700 dizinde `initdb` yapar. Mevcut dizini tekrar kullanmaz/silmez. Bu script Mac'te,
   normal terminal ortamında ve self-hosted runner'da kurulumdan önce hata verir.
   Bu kontroller yanlışlıkla çalıştırmaya karşı sınırdır; bir OS sandbox'ı değildir.
3. Yeni özel süreç yalnız loopback `127.0.0.1:5434` dinler; kendi data/log/socket
   dizinini kullanır. Host bağlantıları SCRAM; local socket yalnız disposable cluster'ın
   bootstrap yönetimi için trust'tır. `campus_ci_owner` yalnız bu yeni cluster'dadır;
   kullanıcı makinesindeki `cem` hesabı veya sırları CI'ya taşınmaz.
4. Oluşturulmuş data directory, gerçek server identity/role/PG17/port ile doğrulanır.
   Sadece bu özel cluster'daki varsayılan `postgres` maintenance DB üzerinden yeni iki
   test rolü ve `campus_plus_test` oluşturulur. Sonraki bütün işlemler test DB'sindedir.
   CI'da `campus_plus` isimli ana uygulama DB'si yaratılmaz/ona bağlanılmaz.
5. Yönetici/migrator/runner parolaları her çalışmada yeni 48 rastgele bayttan üretilir.
   Yönetici parolası yalnız private pwfile ile initdb'ye verilir; rol parolaları psql
   gizli terminal istemleriyle SCRAM olarak atanır. SQL/komut argümanı literal'i yoktur.
   Workflow masking kayıtları ve GITHUB_ENV üzerinden aynı job'a aktarılır; repo veya
   kalıcı GitHub secret gerekmez. Yerel sır dosyaları okunmaz.
6. Yalnız test hedefiyle Flyway V005→V009 migrate; V009 şema kontratı ve
   managed SQL SHA-256 karşılaştırması; sonra temel/Event/Announcement/Notification/Post sınırlı runner
   grant'leri ve `-Ppostgres-it verify` çalışır. CI işi testten
   önce her seferinde boş DB kurduğu için kurulum yolunu da doğrular.

Cluster yalnız disposable runner'ın ömrü kadardır. Otomatik DROP/clean/repair yoktur;
başarısızlıkta sonraki step'ler çalışmaz. Kayıtlar/cluster logları artifact olarak
yüklenmez. Tekrar çalıştırma yeni runner üzerinde başlar.

## Yetki ve sınırlar

- GITHUB_TOKEN yalnız contents:read; checkout kalıcı credential bırakmaz.
- Resmi checkout/setup-java/setup-python action'ları tam commit SHA'sına sabitlendi;
  sürüm yükseltmeleri bilinçli diff incelemesiyle yapılır.
- pull_request_target, repository secret, deploy, push, release, uzak migration veya
  production bağlantısı yoktur. PR kodu yalnız geçici test ortamını kullanır.
- Çalışan Java/Spring Boot/Security sürümleri veya runtime bağımlılıkları değişmedi.
- Normal uygulama başlangıcı ve unit job otomatik migration çalıştırmaz.

## Doğrulama ve sınırlar

- 36 Python testi geçti; hedef sınırları, sürümlü şema, SQL checksum ve CI testlerini içerir.
- actionlint v1.7.12 statik workflow kontrolü başarılı. Araç /tmp'ye resmi release'ten,
  yayınlanan SHA-256 doğrulanarak alındı; projeye bağımlılık eklenmedi.
- Güncel yerel test sonuçları [duyuru canlı doğrulama raporundadır](announcement-test-validation.md).
  Önceki V005 sonucu 193 unit + 10 PostgreSQL IT başarılıydı.
- Önceki gönderilmiş V005 workflow'unda özel Linux cluster başlangıcı, rol/password kurulum adımları, V005 migrate, şema
  kontratı, runner grant ve gerçek HTTP/transaction testlerini içeren postgres işi
  hosted runner'da başarılı tamamlandı. Paket kurulumu binary yoksa çalışan fallback'tir;
  başarılı job tek başına bu fallback dalının çalıştığı iddiası değildir.
  Mac'te bu job taklit edilerek servis kurulmadı. Masking/export kullanılan workflow
  tamamlandı; bu sonuç tüm olası log/sır sızıntılarına karşı genel güvenlik kanıtı değildir.
- PG17 minor sürümü/locale katalog çıktısı farklıysa kontrat farkı açık hata verir;
  otomatik gevşetilmez. Ağ/apt/action indirme sorunları ürün testi geçmiş sayılmaz.

Kaynaklar: [resmi Ubuntu PGDG kurulumu](https://www.postgresql.org/download/linux/ubuntu/),
[GitHub workflow sırları](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).
