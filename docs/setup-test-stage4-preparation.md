# Kurulum / test altyapısı — 4. aşama hazırlığı

> Bu, 2026-10-30 hazırlık anının tarihsel kaydıdır. Baseline 2026-10-31 kullanıcı
> onayıyla uygulandı; [güncel sonuç raporu](setup-test-stage4-review.md).

Tarih: 2026-10-30. **Ana DB baseline onay bekliyor; SQL uygulanmadı.**

- [Baseline planı](baseline-adoption-plan.md) ve
  [metadata SQL önizlemesi](../db/review/baseline-v005-metadata.sql) hazır.
  Mevcut şema sürüm 5 olarak kabul edilince history tablosu, PK/success indeksleri
  ve tek BASELINE satırı oluşacak. V001–V005/V005 başlangıç SQL'i çalışmayacak.
- Salt okunur `baseline_preflight.sql` sonucu gerçek PG17.11/Campus+ hedefi;
  history yok, etkin event trigger yok, cem için otomatik default table grant yok.
- İki işli GitHub Actions ve yalnız disposable hosted Linux'ta çalışabilen özel PG17
  cluster aracı hazır; [CI tasarımı ve sınırları](ci.md).
- 23 Python testi ve actionlint başarılı; eski migration checksum'ları aynı;
  git diff whitespace kontrolü temiz. Java kodu değişmedi; son 203 Java testi başarılı
  olan 3. aşama sonucu geçerli. Bu aşamada Java paketi yeniden çalıştırılmadı.
- GitHub workflow henüz çalıştırılmadı; apt/cluster akışı ilk onaylı push sonrasında
  uzak ortamda doğrulanacak. Commit/push yapılmadı.
- Yerel DB/rol/parola/yapılandırma değişikliği yapılmadı. Diğer projeler, PostgreSQL 14,
  Docker, Supabase ve Render kullanılmadı. Event kodlamasına başlanmadı.

Sonraki uygulama için istenen onay yalnız ana Campus+ PostgreSQL 17 üzerinde
belirtilen Flyway history metadata'sının oluşturulması ve ardından salt okunur uygulama
validate/şema/veri koruma kontrolleridir. Başka migration veya iş verisi değişikliği içermez.
