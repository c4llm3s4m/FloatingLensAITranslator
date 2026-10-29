package com.floatinglens.floatinglensaitranslator.plugin;

import com.floatinglens.floatinglensaitranslator.plugin.ITranslationCallback;

interface ITranslationPlugin {
    String getPluginName();
    int getPluginVersion();
    boolean isAvailable();
    String translate(in String text, in String sourceLang, in String targetLang);
    void translateAsync(in String text, in String sourceLang, in String targetLang, in ITranslationCallback callback);
}
