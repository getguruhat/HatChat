package com.getguruhat.hatchat

import android.content.Context
import android.content.res.Configuration

/** The Appearance setting: follow the phone, or force light or dark. */
object AppTheme {
    private const val PREFS = "settings"
    private const val KEY = "theme"

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "system") ?: "system"

    fun write(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply()
    }

    /** A context whose night-mode bit matches the setting; "system" leaves it as the phone has it. */
    fun wrap(base: Context): Context {
        val night = when (read(base)) {
            "light" -> Configuration.UI_MODE_NIGHT_NO
            "dark" -> Configuration.UI_MODE_NIGHT_YES
            else -> return base
        }
        val config = Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(config)
    }
}
