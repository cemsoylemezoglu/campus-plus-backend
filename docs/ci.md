# Campus+ CI — hazırlanan doğrulama akışı

Durum (2026-10-30): **workflow ve araçlar hazır; GitHub'da henüz çalıştırılmadı**.
Commit/push yapılmadı. Dosyaların hazırlanması deployment veya ana DB migration onayı değildir.

[Workflow](../.github/workflows/ci.yml) GitHub-hosted Ubuntu 24.04 üzerinde iki bağımsız
iş tanımlar. Yerel Mac/Homebrew/PostgreSQL servislerini veya Docker'ı kullanmaz.
Self-hosted runner tanımı yoktur. `main` push/PR ve workflow_dispatch tetikleyicileri
vardır; ilk uzak çalışma ancak kullanıcı ayrıca push istediğinde mümkün olur.

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
6. Yalnız test hedefiyle Flyway V005 migrate; tam şema kontratı karşılaştırması;
   sonra sınırlı runner grant'leri ve `-Ppostgres-it verify` çalışır. CI işi testten
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

## Yerel doğrulama ve henüz kanıtlanmayanlar

- 23 Python testi geçti (16 şema aracı + 7 CI sınır/terminal testi).
- actionlint v1.7.12 statik workflow kontrolü başarılı. Araç /tmp'ye resmi release'ten,
  yayınlanan SHA-256 doğrulanarak alındı; projeye bağımlılık eklenmedi.
- Yerel önceki tam Java doğrulaması 193 unit + 10 PostgreSQL IT başarılıydı. Bu aşamada
  Java/uygulama kodu değişmediği için aynı paket tekrar çalıştırılmadı.
- Ubuntu paket kurulumu, özel Linux cluster başlangıcı, psql terminal akışı ve GitHub
  masking/export davranışı henüz gerçek hosted runner'da çalıştırılmadı. Mac'te bu
  job taklit edilerek servis kurulmadı. İlk uzak run sonucu ayrıca incelenecek.
- PG17 minor sürümü/locale katalog çıktısı farklıysa kontrat farkı açık hata verir;
  otomatik gevşetilmez. Ağ/apt/action indirme sorunları ürün testi geçmiş sayılmaz.

Kaynaklar: [resmi Ubuntu PGDG kurulumu](https://www.postgresql.org/download/linux/ubuntu/),
[GitHub workflow sırları](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).
