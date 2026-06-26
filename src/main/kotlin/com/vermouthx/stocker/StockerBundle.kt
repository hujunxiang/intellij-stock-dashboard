package com.vermouthx.stocker

import com.intellij.DynamicBundle
import com.vermouthx.stocker.settings.StockerSetting
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.text.MessageFormat
import java.util.*

private const val BUNDLE = "messages.StockerBundle"

object StockerBundle : DynamicBundle(BUNDLE) {

    private fun getPreferredLocale(): Locale {
        return try {
            val languageOverride = StockerSetting.instance.languageOverride
            when {
                languageOverride.isEmpty() -> Locale.getDefault()
                languageOverride == "zh_CN" -> Locale.SIMPLIFIED_CHINESE
                languageOverride == "en" -> Locale.ENGLISH
                else -> Locale.getDefault()
            }
        } catch (_: Exception) {
            Locale.getDefault()
        }
    }

    private fun getBundle(): ResourceBundle {
        return ResourceBundle.getBundle(BUNDLE, getPreferredLocale(), StockerBundle::class.java.classLoader)
    }

    @Nls
    @JvmStatic
    fun msg(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): String {
        val template = getBundle().getString(key)
        return if (params.isEmpty()) template else MessageFormat.format(template, *params)
    }

    @Nls
    @JvmStatic
    fun msgPointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?) =
        getLazyMessage(key, *params)
}
