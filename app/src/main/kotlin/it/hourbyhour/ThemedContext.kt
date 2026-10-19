package it.hourbyhour

import android.content.Context
import android.content.res.Configuration

/** [this] context in the light or dark theme, whatever the theme of the phone. */
fun Context.inTheme(dark: Boolean): Context {
    val configuration = Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    return createConfigurationContext(configuration)
}
