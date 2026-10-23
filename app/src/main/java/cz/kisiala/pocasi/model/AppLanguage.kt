package cz.kisiala.pocasi.model

/** Jazyk aplikace zvolený v menu ⋮ → Jazyk; [tag] `null` = podle systému. */
enum class AppLanguage(val tag: String?) {
    System(null),
    Czech("cs"),
    English("en"),
}
