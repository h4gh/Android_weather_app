# Počasí – Android aplikace s předpovědí pro ČR

Nativní Android aplikace s předpovědí počasí pro Českou republiku z dat ČHMÚ.
UI je česky. Testovací zařízení: Google Pixel 10 Pro XL, vývoj na Windows 11 (Android Studio).

## Obrazovky (spodní Navigation Bar)

1. **Dnes** – aktuální stav (teplota, popis, pocitová, max/min, ikona), karta „Nejbližší srážky“
   (z radaru), meteogram „Dalších 24 hodin“ (teplota křivkou, srážky sloupci, vítr po 3 h)
   a tři malé karty Vítr / Vlhkost / Tlak.
2. **Výhled** – výhled na 5 dní (rozbalitelný den s hodnotami po 3 h, srážky, vítr, rozpětí teplot).
3. **Radar** – mapa s radarovými kompozity ČHMÚ, animace posledních 2 h, nejbližší déšť.

## Technologie a architektura

- Kotlin, Jetpack Compose, Material 3. AGP 9 (vestavěný Kotlin), Kotlin 2.4, Gradle 9.8,
  verze knihoven v `gradle/libs.versions.toml`. minSdk 26, compile/target 37.
- Síť: Retrofit + kotlinx.serialization (OkHttp).
- Poloha: systémový `LocationManager` přes `LocationManagerCompat` (bez Play Services),
  název místa přes `Geocoder` (při selhání „Moje poloha“). Bez oprávnění → výchozí Praha.
- Grafy kreslit přes Compose `Canvas` – žádné grafové knihovny.
- Ruční DI: `AppContainer` (vytváří `PocasiApp`), ViewModely přes `viewModelFactory`.
- Vrstvy (balíček `cz.kisiala.pocasi`):
  - `model/` – doménové třídy: `Place`, `Forecast` (+ `CurrentWeather`, `HourlyWeather`,
    odvození `WeatherCondition` z oblačnosti a srážek, tendence tlaku).
  - `data/remote/` – `OpenMeteoApi` (Retrofit) a DTO; `data/WeatherRepository` mapuje DTO → doména.
  - `data/PlaceRepository` – zdroj pravdy o místech: `places` (aktuální poloha + uložená místa)
    a `selectedPlace`. **Oblíbená místa** se doplní nahrazením `savedPlaces` tokem z DataStore/Room;
    UI (rozbalovací menu v top app baru) už pracuje se seznamem `places`.
  - `data/location/LocationProvider` – poloha a reverzní geokódování.
  - `ui/AppRoot` – Scaffold s Navigation Barem, žádost o oprávnění k poloze.
  - `ui/today/` – `TodayScreen`, `TodayViewModel` (UiState: loading / data / error, pull-to-refresh),
    `Meteogram` (Canvas), `WeatherIcon` (Canvas ikony), `Format` (formátování, české popisky).
  - `ui/theme/` – `PocasiTheme`, barvy, vlastní barva `WeatherTheme.colors.temperature`.
- Každá obrazovka má vlastní top app bar (vnořený `Scaffold`), vnější `Scaffold` řeší jen Navigation Bar.

## Pravidla designu

- **Návrh je ve složce `design/`** (světlá i tmavá verze všech 3 obrazovek) – drž se ho co nejvěrněji.
- Material Design 3 / Material You: dynamic color (Android 12+), fallback vlastní modré M3 schéma
  (seed/primary `#415F91`) v `ui/theme/Color.kt`.
- Světlý i tmavý režim podle systému – vždy ověřit obojí.
- Tónované karty `surfaceContainer`, rohy 12 dp (`WeatherCard`), typografie M3, dotykové plochy ≥ 48 dp.
- Teplota (a zvýraznění silného větru ≥ 20 km/h) teplou oranžovou `WeatherTheme.colors.temperature`,
  srážky barvou `primary` (slabé srážky `inversePrimary`).
- Texty v `res/values/strings.xml`, česky.

## Zdroje dat

- **Předpověď:** Open-Meteo, model `chmi_aladin_seamless` (ALADIN cca 3 dny, dál dopočítává ECMWF).
  `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&models=chmi_aladin_seamless&timezone=auto&forecast_days=3`
  - `current` + `hourly`: temperature_2m, apparent_temperature, relative_humidity_2m, dew_point_2m,
    precipitation, cloud_cover, pressure_msl, wind_speed_10m, wind_direction_10m, wind_gusts_10m, is_day
  - jednotky °C, km/h, mm, hPa; dokumentace https://open-meteo.com/en/docs/chmi-api
  - ALADIN nedává symbol počasí → ikona/popis se odvozuje z `cloud_cover` a `precipitation`.
  - Zdarma pro nekomerční použití, bez API klíče. Atribuce v menu „O aplikaci“.
- **Radar (krok 3):** hotové PNG kompozity ČHMÚ
  https://opendata.chmi.cz/meteorology/weather/radar/composite/maxz/png_masked/
  (Mercator EPSG:3857, každých 5 min, popis:
  https://opendata.chmi.cz/meteorology/weather/radar/radar_description_en.pdf)

## Plán kroků

1. **Dnes** – kostra aplikace + obrazovka Dnes ✅ hotovo
2. **Výhled na 5 dní** (`forecast_days` zvýšit, přidat denní agregace)
3. **Radar** s animací posledních 2 h (+ naplnit kartu „Nejbližší srážky“ na obrazovce Dnes)
4. Později: oblíbená místa (vyhledávání + uložení, přepínání v top app baru) a widget

## Stav

Hotovo:
- Kostra: Navigation Bar (Dnes / Výhled / Radar), Výhled a Radar zatím „Připravujeme“.
- Obrazovka Dnes kompletně dle návrhu, načítání, chybový stav se „Zkusit znovu“, pull-to-refresh,
  snackbar při chybě obnovy nad zobrazenými daty.
- Poloha z GPS/sítě s fallbackem na Prahu, reverzní geokódování, menu míst v top app baru.
- Menu „Další možnosti“ → „O aplikaci“ s atribucí dat.

Zbývá / známá omezení:
- Karta „Nejbližší srážky“ je placeholder (tlačítko „Mapa“ přepne na záložku Radar).
- Tlačítko Hledat ukazuje jen snackbar „připravujeme“.
- Data se necachují (každé spuštění = nový požadavek); poloha se zjišťuje při startu a při pull-to-refresh.
- Žádné testy zatím nejsou.

## Sestavení

- `gradlew.bat assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `gradlew.bat installDebug` nainstaluje do připojeného zařízení (USB ladění).
- Release build (`assembleRelease`) je s R8 a podepsaný debug klíčem – jen pro osobní použití.
