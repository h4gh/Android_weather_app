package cz.kisiala.pocasi.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import cz.kisiala.pocasi.PocasiApp
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.AppLanguage
import java.util.Locale

/**
 * Jazyk aplikace. Od Androidu 13 ho drží systém (per-app language – volba je vidět i v nastavení systému
 * a po změně systém sám znovu vytvoří aktivity). Na starších verzích ho ukládáme sami a uplatňujeme
 * při startu procesu ([applyLegacy]) a v každé aktivitě ([wrap]).
 */
object AppLocales {
    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    fun current(context: Context): AppLanguage {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeUnless { it.isEmpty }?.get(0)?.language
        } else {
            legacyTag(context)
        }
        return AppLanguage.entries.firstOrNull { it.tag != null && it.tag == tag } ?: AppLanguage.System
    }

    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TAG, language.tag).commit()
            applyLegacy(activity.applicationContext)
            (activity.application as PocasiApp).onLanguageChanged()
            activity.recreate()
        }
    }

    /**
     * Jazyk, ve kterém aplikace skutečně zobrazuje texty (bez překladu do jazyka systému je to čeština) –
     * pro jazyk vyhledávání míst, geokódování a popisků mapy.
     */
    fun contentLanguage(context: Context): String = context.getString(R.string.content_language)

    /** Do Androidu 12: kontext aktivity se zvoleným jazykem (volat z `attachBaseContext`). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val locale = legacyTag(base)?.let(Locale::forLanguageTag) ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** Do Androidu 12: zvolený jazyk pro celý proces (texty widgetů a upozornění, formát čísel). */
    fun applyLegacy(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val locale = legacyTag(context)?.let(Locale::forLanguageTag)
            ?: Resources.getSystem().configuration.locales[0]
        Locale.setDefault(locale)
        val resources = context.applicationContext.resources
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun legacyTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
}
