# Počasí – Android aplikace s předpovědí pro ČR

Nativní Android aplikace s předpovědí počasí pro Českou republiku z dat ČHMÚ.
UI je česky a anglicky (menu ⚙ → Jazyk). Testovací zařízení: Google Pixel 10 Pro XL, vývoj na Windows 11 (Android Studio).

## Obrazovky (spodní Navigation Bar)

1. **Dnes** – aktuální stav (teplota, popis, pocitová, max/min, ikona), karta „Nejbližší srážky“
   (z radaru), meteogram „Dalších 24 hodin“ (teplota křivkou, srážky sloupci, vítr po 3 h)
   a tři malé karty Vítr / Vlhkost / Tlak.
2. **Výhled** – výhled na 5 dní od zítřka: nahoře napevno karta „Přehled na 5 dní“ (teplota křivkou, srážky po 3 h, klepnutí na den rozbalí a odroluje jeho kartu pod přehled), pod ní dny (rozbalitelný den s hodnotami po 3 h a chipy nárazy/vlhkost/tlak,
   srážky + pravděpodobnost, vítr, pruh min–max na společné stupnici; dny 4–5 označené jako ECMWF).
3. **Radar** – mapa s radarovými kompozity ČHMÚ, animace posledních 2 h, nejbližší déšť.

## Technologie a architektura

- Kotlin, Jetpack Compose, Material 3. AGP 9 (vestavěný Kotlin), Kotlin 2.4, Gradle 9.8,
  verze knihoven v `gradle/libs.versions.toml`. minSdk 26, compile/target 37.
- Síť: Retrofit + kotlinx.serialization (OkHttp).
- Poloha: systémový `LocationManager` přes `LocationManagerCompat` (bez Play Services),
  název místa přes `Geocoder` (při selhání „Moje poloha“). Bez polohy → záložní místo Frýdek-Místek.
- Grafy kreslit přes Compose `Canvas` – žádné grafové knihovny. Kreslení ikon a meteogramu je v `DrawScope`
  funkcích (`drawWeatherIcon`, `drawMeteogram`), které sdílí aplikace i widgety (ty je kreslí do bitmapy).
- Widgety: Jetpack Glance (+ WorkManager); obnova `updatePeriodMillis` 30 min.
- Ruční DI: `AppContainer` (vytváří `PocasiApp`), ViewModely přes `viewModelFactory`.
- Vrstvy (balíček `cz.kisiala.pocasi`):
  - `model/` – doménové třídy: `Place`, `Forecast` (+ `CurrentWeather`, `HourlyWeather`, `DailyWeather`,
    odvození `WeatherCondition` z oblačnosti a srážek, tendence tlaku).
  - `data/remote/` – `OpenMeteoApi`, `GeocodingApi` (Retrofit) a DTO; `data/WeatherRepository` mapuje DTO → doména,
    `data/PlaceSearchRepository` hledá místa (jen CZ, SK, PL, AT, DE; česká první).
  - `data/PlaceRepository` – zdroj pravdy o místech: `places` (poloha, oblíbená, vybrané hledané místo – jen dokud
    je vybrané, záložní Frýdek-Místek jen bez polohy i oblíbených), `selectedPlace`, přidání/odebrání/pořadí
    oblíbených. `data/PlaceStore` je trvale ukládá do DataStore (JSON) i s naposledy vybraným místem.
  - `data/location/LocationProvider` – poloha a reverzní geokódování.
  - `data/SettingsRepository` – nastavení v DataStore (`themeMode`: Světlý / Tmavý / Podle systému; `blackBackground`: čistě černé pozadí a karty s obrysem v tmavém režimu pro OLED),
    `MainActivity` z něj odvozuje `darkTheme` pro `PocasiTheme` i styl systémových lišt.
  - `ui/AppRoot` – Scaffold s Navigation Barem a `HorizontalPager` (swipe mezi obrazovkami, na Radaru vypnutý –
    gesta patří mapě), dialog Vzhled a stránka O aplikaci, žádost o oprávnění k poloze; vytváří jednu instanci
    `ForecastViewModel`, kterou sdílí Dnes i Výhled (jeden dotaz na API).
  - `ui/ForecastViewModel` – UiState: loading / data / error, pull-to-refresh, výběr místa.
  - `ui/common/` – `LoadingState`, `ErrorState`, `PlacesMenu`, `AppMenu` (menu pod ozubeným kolem ⚙ na všech obrazovkách: panel přes celou šířku s rychlou volbou Vzhled + OLED a Jazyk, souhrn Upozornění, O aplikaci).
  - `ui/about/AboutScreen` – celoobrazovková stránka O aplikaci: verze, novinky ve verzích (`RELEASES` + pole `changelog_…` v obou jazycích), autor a kontakt, zdroje dat s atribucemi.
  - `ui/search/` – `PlaceSearchScreen` + `PlaceSearchViewModel` (celoobrazovkové hledání, ☆ u výsledků).
  - `ui/favorites/FavoritesScreen` – úprava oblíbených: tažení za úchyt (řádky s `key`), odebrání s „Vrátit“.
  - `model/Radar` – geometrie kompozitu (`RadarGrid`, ořez na datovou oblast), škála ČHMÚ (`RadarScale`),
    hledání nejbližších srážek (`NearbyPrecipitation.find`, okolí 100 km, odfiltrování osamocených pixelů).
  - `data/radar/RadarRepository` – stahování a dekódování PNG (OkHttp), cache snímků v paměti.
  - `ui/radar/` – `RadarViewModel` (sdílený Dnes i Radarem, vytváří `AppRoot`; nejnovější snímek hned,
    animace až při otevření Radaru, obnova po 4 min), `RadarScreen`, `RadarMap` (MapLibre přes `AndroidView`).
  - `ui/outlook/` – `OutlookScreen` (seznam dní, rozbalení jednoho dne klepnutím kamkoli do něj, v detailu
    `Meteogram` průběhu dne ze `ui/today`, pruh teplot přes Canvas).
  - `ui/today/` – `TodayScreen`,
    `Meteogram` (Canvas), `WeatherIcon` (Canvas ikony), `Format` (formátování, české popisky).
  - `ui/theme/` – `PocasiTheme`, barvy, vlastní barva `WeatherTheme.colors.temperature`; `appColorScheme()`
    a `weatherColors()` používá i widget.
  - `widget/` – `WeatherWidget` (Exact velikost: kompaktní / úzký s meteogramem / široký / široký s meteogramem),
    `OutlookWidget` (5 dní), `WidgetConfigActivity` (výběr místa při přidání, přenastavitelné),
    `WidgetUpdater` + `WidgetState` (místo a poslední data v Glance stavu, obnova jen když jsou data starší
    než 25 min), `WidgetDrawing` (bitmapy ze sdíleného kreslení). „Moje poloha“ = poslední poloha z aplikace
    (`PlaceStore.lastLocation`); `AppContainer` po nové poloze obnoví widgety s polohou, po změně vzhledu je překreslí.
  - `notifications/` – upozornění s počasím v nastavené časy: `NotificationScheduler` (vždy jeden budík AlarmManageru
    na nejbližší čas, přesný jen s oprávněním; `AppContainer` přeplánuje po každé změně nastavení),
    `NotificationAlarmReceiver` (goAsync: předpověď + radar → upozornění, další budík), `NotificationRescheduleReceiver`
    (restart, aktualizace, změna času), `WeatherNotifier` (obsah, kanál `scheduled_forecast`). Rada k deštníku
    `model/UmbrellaAdvice` z radaru (v místě / do 10 km) a srážek po 15 min (`minutely_15`, jen pro upozornění).
    Nastavení (`NotificationSettings`: časy s dny v týdnu, místo) v `SettingsRepository`; obrazovka
    `ui/notifications/NotificationSettingsScreen` z menu ⋮ → Upozornění.
- Každá obrazovka má vlastní top app bar (vnořený `Scaffold`), vnější `Scaffold` řeší jen Navigation Bar.

## Pravidla designu

- **Návrh je ve složce `design/`** (světlá i tmavá verze všech 3 obrazovek) – drž se ho co nejvěrněji.
- Material Design 3 / Material You: dynamic color (Android 12+), fallback vlastní modré M3 schéma
  (seed/primary `#415F91`) v `ui/theme/Color.kt`.
- Světlý i tmavý režim podle systému – vždy ověřit obojí.
- Tónované karty `surfaceContainer`, rohy 12 dp (`WeatherCard`), typografie M3, dotykové plochy ≥ 48 dp.
- Teplota (a zvýraznění silného větru ≥ 20 km/h) teplou oranžovou `WeatherTheme.colors.temperature`,
  srážky barvou `primary` (slabé srážky `inversePrimary`).
- Texty v `res/values/strings.xml` česky (výchozí) a `res/values-en/strings.xml` anglicky – každý nový text do obou. Jazyk drží `data/AppLocales` (Android 13+ systémový per-app jazyk, starší SharedPreferences); vyhledávání, geokodér a popisky mapy berou jazyk z `R.string.content_language`.

## Zdroje dat

- **Předpověď:** Open-Meteo, model `chmi_aladin_seamless` (ALADIN cca 3 dny, dál dopočítává ECMWF).
  `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&models=chmi_aladin_seamless&timezone=auto&forecast_days=6`
  - `current` + `hourly`: temperature_2m, apparent_temperature, relative_humidity_2m, dew_point_2m,
    precipitation, cloud_cover, pressure_msl, wind_speed_10m, wind_direction_10m, wind_gusts_10m, is_day
  - `daily`: temperature_2m_max/min, precipitation_sum, precipitation_probability_max (může chybět → % se
    nezobrazí), wind_speed_10m_max, wind_gusts_10m_max, wind_direction_10m_dominant
  - jednotky °C, km/h, mm, hPa; dokumentace https://open-meteo.com/en/docs/chmi-api
  - ALADIN nedává symbol počasí → ikona/popis se odvozuje z `cloud_cover` a `precipitation`.
  - Zdarma pro nekomerční použití, bez API klíče. Atribuce v menu „O aplikaci“.
- **Radar:** hotové PNG kompozity ČHMÚ
  https://opendata.chmi.cz/meteorology/weather/radar/composite/maxz/png_masked/
  (Mercator EPSG:3857, každých 5 min, popis:
  https://opendata.chmi.cz/meteorology/weather/radar/radar_description_en.pdf)
  - soubory `pacz2gmaps3.z_max3d.YYYYMMDD.hhmm.0.png` (čas UTC), 680 × 460 px s paletou; nejnovější se hledá
    od teď zpět po 5 min. Data jen v oblasti E 11,267–19,624°, N 48,047–51,458° – pravý a horní pruh
    jsou boční průměty, proto se ořezávají. Černá (rámečky, popisek) a šedá C4C4C4 (mimo dosah) → průhledné.
  - sytě vykreslené barvy = srážky dopadající na zem; přesně odpovídají škále
    `https://opendata.chmi.cz/meteorology/weather/radar/scl/scl-dbz-mmh.png` (15 barev po 4 dBZ),
    světlejší barvy = odraz ve výšce (zobrazují se poloprůhledně, do nejbližšího deště se nepočítají).
- **Mapa:** MapLibre Android, styly OpenFreeMap `positron` (světlý) a `dark` (tmavý), bez API klíče;
  popisky přepnuté na `name:cs`. Atribuce OSM v mapě a v „O aplikaci“.

## Plán kroků

1. **Dnes** – kostra aplikace + obrazovka Dnes ✅ hotovo
2. **Výhled na 5 dní** ✅ hotovo
3. **Radar** s animací posledních 2 h + karta „Nejbližší srážky“ na obrazovce Dnes ✅ hotovo
4. **Úpravy (říjen 2026)** – postupně, po každé build + instalace release na telefon, potvrzení, commit:
   1. Výhled – v rozbaleném dni „Průběh dne“: meteogram 00–24 h (stejná komponenta jako Dnes) ✅
   2. Navigace swipem mezi Dnes / Výhled / Radar (HorizontalPager + spodní lišta); na Radaru gesta patří mapě ✅
   3. Menu ⋮ – Vzhled: Světlý / Tmavý / Podle systému (výchozí), uložení v DataStore, platí i pro styl mapy ✅
   4. Menu ⋮ – O aplikaci: název, verze, zdroje dat s atribucemi a licencemi ✅
   5. O aplikaci – autor Ondřej Kisiala, klikací kontakt o.kisiala@gmail.com (připomínky, návrhy, žádosti o update) ✅
   6. Výhled – rozbalený den se sbalí klepnutím kamkoli do jeho otevřené karty (nejen na hlavičku) ✅
   7. Výhled – připnutá karta „Přehled na 5 dní“ (teplota + srážky, bez větru), klepnutím na den v grafu se rozbalí jeho karta a odroluje pod přehled; odebrán podnadpis s místem, jednotkami a modely ✅
5. **Oblíbená místa** – dva kroky, po každém build + instalace release, potvrzení, commit:
   1. ✅ Vyhledávání míst: lupa na Dnes (a „Přidat místo…“ v menu míst) otevře hledání přes Open-Meteo Geocoding
      (Česko a okolní státy, výsledky z Česka první); výběr místa → předpověď. Radar: tečka a nejbližší déšť
      pro vybrané místo. Záložní místo bez polohy: Frýdek-Místek (místo Prahy), v menu jen když chybí poloha.
   2. ✅ Oblíbená místa v DataStore: ☆ ve výsledcích hledání a u názvu místa na Dnes; menu míst = Moje poloha,
      oblíbená, „Přidat místo…“, „Upravit oblíbená“ (odebrání, pořadí tažením); pamatuje si vybrané místo.
6. ✅ **Widgety** (Jetpack Glance) – dva kroky, po každém build + instalace release, potvrzení, commit:
   1. Widget „Počasí“ přepínaný podle velikosti: 2×2 kompaktní (místo, teplota, ikona, popis, max/min),
      od 4×2 navíc meteogram na 24 h. Ikony a meteogram se kreslí stejným kódem jako v aplikaci (DrawScope do
      bitmapy). Místo se volí při přidání widgetu (Moje poloha / oblíbené / hledat), obnova po 30 min,
      klepnutí otevře aplikaci na Dnes.
. ✅ **Upozornění s počasím** – menu ⋮ → Upozornění: místo (Moje poloha / oblíbené), libovolné časy s dny v týdnu;
   upozornění = teplota, stav, rada k deštníku na příští hodinu (radar + předpověď), později dnes srážky, max/min.

## Stav

Hotovo:
- Kostra: Navigation Bar (Dnes / Výhled / Radar) + přepínání swipem (kromě Radaru).
- Obrazovka Dnes kompletně dle návrhu, načítání, chybový stav se „Zkusit znovu“, pull-to-refresh,
  snackbar při chybě obnovy nad zobrazenými daty.
- Obrazovka Výhled dle návrhu: 5 dní od zítřka, zítřek rozbalený, rozbalený den ve vlastní kartě,
  dny 4–5 pod nadpisem „Obecnější předpověď (model ECMWF)“. Ikona dne z průměrné denní oblačnosti
  (7–20 h), déšť až od 1 mm za den. Ikona místa v top app baru otevírá stejné menu míst jako Dnes.
- Poloha z GPS/sítě s fallbackem na Frýdek-Místek, reverzní geokódování, menu míst v top app baru.
- Vyhledávání míst a oblíbená místa (hvězdička na Dnes a ve výsledcích, úprava pořadí, pamatuje si vybrané místo);
  Radar ukazuje značku a nejbližší déšť pro vybrané místo.
- Menu ⋮ na všech obrazovkách: Vzhled (uložený v DataStore, platí i pro mapu) a O aplikaci (verze, autor
  s klikacím e-mailem – předmět v mailto URI kvůli Gmailu i v EXTRA_SUBJECT, zdroje dat, atribuce a licence).
- Obrazovka Radar: celoobrazovková mapa, plovoucí lišta s časem zobrazeného snímku a menu Vrstvy
  (přepínač průhledného radaru), chip s nejbližším deštěm, FAB „Moje poloha“ (modrá tečka),
  spodní panel s přehráváním 13 snímků po 10 min, posuvníkem a legendou škály ČHMÚ.
- Karta „Nejbližší srážky“ na Dnes: vzdálenost, směr (šipka míří ke srážkám) a intenzita pro vybrané
  místo; tlačítko „Mapa“ přepne na Radar.

Zbývá / známá omezení:
- Widget s „Moje poloha“ ukazuje poslední polohu zjištěnou v aplikaci (bez oprávnění „Povolit vždy“).
- Widget přebírá světlý/tmavý vzhled při vykreslení – po přepnutí systému se srovná při další obnově.
- R8: pravidla pro WorkManager/Room v `app/proguard-rules.pro` (bez nich release padá / widgety se nenačtou).
- Předpověď se necachuje (každé spuštění = nový požadavek), radarové snímky jen v paměti; poloha se zjišťuje při startu a při pull-to-refresh.
- Žádné testy zatím nejsou.

## Sestavení

- `gradlew.bat assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `gradlew.bat installDebug` nainstaluje do připojeného zařízení (USB ladění).
- Release je jen pro `arm64-v8a` a s komprimovanými nativními knihovnami (MapLibre) → APK ~7 MB. Kopie k rozeslání
  se srozumitelným názvem: `app/build/outputs/share/app-pocasi_release.apk`. Do telefonu:
  `adb install -r app/build/outputs/apk/release/app-release.apk`.
- Verze: `versionName`/`versionCode` v `app/build.gradle.kts`. Před vydáním nové verze zvýšit `versionCode` o 1
  (jinak Android aktualizaci odmítne) a `versionName` (MAJOR.MINOR.PATCH). Vydané APK se nahrává na Google Drive
  `G:/My Drive/03_Development/Android_release/` jako `Pocasi-v<versionName>.apk`. Vedle něj je úplný changelog `pocasi-changelog.md` (nejnovější verze nahoře) – při každém vydání
  doplnit novou verzi tam i do novinek v O aplikaci (`RELEASES` v `AboutScreen` + `changelog_…` v obou `strings.xml`).
- Release build (`assembleRelease`) je s R8 a podepsaný vlastním klíčem `C:/Users/ondre/keys/pocasi-release.jks`
  (alias `pocasi`). Cesta a hesla jsou v `keystore.properties` v kořeni projektu – **není v gitu**.
  Bez tohoto souboru vznikne nepodepsané APK. Debug build dál používá debug klíč – release a debug
  nejdou nainstalovat přes sebe (jiný podpis), je nutné nejdřív odinstalovat.
