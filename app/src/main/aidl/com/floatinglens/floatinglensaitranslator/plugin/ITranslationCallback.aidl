package com.floatinglens.floatinglensaitranslator.plugin;

interface ITranslationCallback {
    void onSuccess(in String translatedText);
    void onError(in int errorCode, in String errorMessage);
}
