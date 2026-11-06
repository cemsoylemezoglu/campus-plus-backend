# Kulüp etkileşimleri kaynak ve transaction temeli

> Bu rapor önceki temel altyapı adımının tarihsel sonucudur. Sonraki entity/API/inbox
> kodu ve V011 onay noktası [güncel rapordadır](club-interactions-phase1-review.md).

Tarih: 2026-11-05. Bu rapor Event/Announcement yorum ve beğeni modülünün
**altyapı adımını** kapsar; yeni API'lerin tamamlandığı anlamına gelmez.

## Değişiklikler

- `dto/clubinteraction/ClubSourceKind`, `ClubSourceRef`: açık kaynak türü ve pozitif id.
- `service/clubinteraction/ClubInteractionSource`: Event/Announcement için ayrı
  adapter; mevcut ClubInteractionPolicy üzerinden normal okuma/yazma/yerel yönetim.
- `ClubInteractionWorkspace`: transaction zorunluluğu, ön kontrol ve kilit sonrası
  yeniden yetkilendirme; mevcut Event/Announcement kilit sırasıyla uyum.
- `ClubCommentCursor`: kaynak türü/id ve thread'e bağlı keyset cursor; Post cursor'ı
  değiştirilmedi. Kaynak/actor/context log çıktıları içerik taşımıyor.

Yeni entity eklememek bu adımda bilinçli bir sınırdır: mevcut V010 şemasıyla
`ddl-auto=validate` çalışmaya devam eder. Henüz var olmayan tabloları JPA taramasına
ekleyerek mevcut uygulama başlangıcını bozmadık. Yeni tablolar sonraki V011
hazırlığında, kesin SQL ve test planıyla birlikte ele alınacak.

## Doğrulama

Yeni Java testleri:

- `ClubCommentCursorTest`: kaynaklar/kimlikler/thread'ler arası cursor reddi,
  sürüm/tür, canonical Base64URL, zaman/nanos/id sınırları ve round-trip kontrolleri.
- `ClubInteractionWorkspaceTest`: normal kaynak ayrımı, kilit sırası, kilit bekleme
  sırasında iptal/geri çekme, üyelik/rol kaybı, pasif üniversite, kaynak kulübü
  uyuşmazlığı, ADMIN/marker bypass reddi ve gerçek Spring proxy üzerinden çağıran
  transaction'ın zorunlu olması. İptal edilen Event yerel moderasyona açık kalır.

İlk hedefli denemede bir test fixture'ının yıl 10000 için ISO işaretini içermemesi
nedeniyle hata alındı; `+10000` biçimine düzeltildi. Sonrasında **tüm izole Java
testleri birlikte** çalıştırıldı:

```sh
mvn -o test
python3 -m unittest discover -s scripts/tests
```

- **514 Java başarılı** (önceki 476 + yeni 38); 0 failure/error/skipped.
- **40 Python başarılı**.
- İlk offline denemelerde eski geçici Maven önbelleği eksikti; standart yerel Maven
  önbelleğindeki eksik bağımlılıklar indirildikten sonra tam test offline geçti.
- Bu oturumda PostgreSQL'e bağlanılmadı; contextLoads ve gerçek PostgreSQL/API/
  eşzamanlılık testleri yeniden çalıştırılmadı. Mock tabanlı değişim senaryoları,
  gerçek PostgreSQL kilit yarışlarının yerine geçmez. Son tam V010 sonucu tarihsel
  olarak 521 Java (449 izole +72 PG) /40 Python'dur; güncel toplam diye toplanmaz.

## Git ve sınırlar

Oturum başında main çalışma ağacı temizdi. Önceki `c5e291a` için
[GitHub Actions çalışması](https://github.com/cemsoylemezoglu/campus-plus-backend/actions/runs/41521151993)
completed/success olarak doğrulandı. Bu sonuç önceki push'a aittir; burada eklenen
38 testin sonucu yereldir.

Bu oturumda commit, push, force push, SQL, rol/yetki veya servis değişikliği yapılmadı.
V001–V010 ve mevcut authentication/Post/Event/Announcement API kodları değiştirilmedi.
Diğer projelere veya veritabanlarına dokunulmadı.

Sıradaki adım: yeni kulüp yorum/beğeni/audit persistence, servisler ve API'ler;
ardından notification entegrasyonu ve V011 review SQL/test/grant hazırlığı. Test
ve ana veritabanına migration uygulamak için ayrı, açık onay alınacak.
