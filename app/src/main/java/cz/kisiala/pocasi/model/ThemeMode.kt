package cz.kisiala.pocasi.model

/** Vzhled aplikace zvolený v menu ⋮ → Vzhled. */
enum class ThemeMode {
    System,
    Light,
    Dark;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }
}
