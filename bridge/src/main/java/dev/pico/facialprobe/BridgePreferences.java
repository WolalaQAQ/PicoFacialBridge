package dev.pico.facialprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.Locale;

final class BridgePreferences {
    static final String EYE = "transmit_eye", FACE = "transmit_face", LANGUAGE = "language";
    static SharedPreferences get(Context context) { return context.getSharedPreferences("bridge", Context.MODE_PRIVATE); }
    static int selection(SharedPreferences prefs) {
        return (prefs.getBoolean(EYE, true) ? 1 : 0) | (prefs.getBoolean(FACE, true) ? 2 : 0);
    }
    static String language(Context context) {
        String fallback = context.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh") ? "zh" : "en";
        return "zh".equals(get(context).getString(LANGUAGE, fallback)) ? "zh" : "en";
    }
    static Context localized(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(language(context)));
        return context.createConfigurationContext(config);
    }
}
