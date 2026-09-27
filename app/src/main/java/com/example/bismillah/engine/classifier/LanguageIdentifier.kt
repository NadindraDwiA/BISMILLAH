package com.example.bismillah.engine.classifier

import com.example.bismillah.data.model.LangCode
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LanguageIdentifier {
    /** Maps ML Kit language code to FLORES-200 code for the translator. */
    suspend fun identify(text: String): String {
        return try {
            val code = suspendCancellableCoroutine { cont ->
                LanguageIdentification.getClient().identifyLanguage(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume("ja") }
            }
            LangCode.fromMlKit(code)
        } catch (_: Exception) {
            LangCode.JAPAN
        }
    }
}
