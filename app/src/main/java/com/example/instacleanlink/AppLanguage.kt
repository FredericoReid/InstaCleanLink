package com.example.instacleanlink

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

enum class AppLanguage(val code: String, val locale: Locale) {
    ENGLISH("en", Locale.ENGLISH),
    PORTUGUESE("pt", Locale.forLanguageTag("pt-BR"));

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: ENGLISH
        }
    }
}

object AppLanguageManager {

    fun getAppLanguage(context: Context): AppLanguage {
        return AppLanguage.fromCode(PrefsManager.getAppLanguageCode(context))
    }

    fun setAppLanguage(context: Context, language: AppLanguage) {
        PrefsManager.setAppLanguageCode(context, language.code)
    }

    fun getLocalizedContext(
        context: Context,
        language: AppLanguage = getAppLanguage(context)
    ): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(language.locale)
        configuration.setLayoutDirection(language.locale)
        return context.createConfigurationContext(configuration)
    }

    fun getString(
        context: Context,
        @StringRes resId: Int,
        vararg formatArgs: Any
    ): String {
        return getString(context, getAppLanguage(context), resId, *formatArgs)
    }

    fun getString(
        context: Context,
        language: AppLanguage,
        @StringRes resId: Int,
        vararg formatArgs: Any
    ): String {
        val localizedContext = getLocalizedContext(context, language)
        return if (formatArgs.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *formatArgs)
        }
    }

    fun getQuantityString(
        context: Context,
        language: AppLanguage,
        @PluralsRes resId: Int,
        quantity: Int,
        vararg formatArgs: Any
    ): String {
        return getLocalizedContext(context, language)
            .resources
            .getQuantityString(resId, quantity, *formatArgs)
    }
}