package com.example.data.ai

import android.util.Log
import com.example.data.model.AiChapter
import com.example.data.model.AiMediaContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AiProviderSource {
    CLOUD_GEMINI,
    LOCAL_HEURISTIC,
    DISABLED
}

data class AiExecutionResult<T>(
    val value: T,
    val source: AiProviderSource,
    val modelIdentifier: String
)

interface AiEngine {
    val modelIdentifier: String
    val providerSource: AiProviderSource
    suspend fun generateTags(context: AiMediaContext): AiExecutionResult<List<String>>
    suspend fun summarize(context: AiMediaContext, mode: String): AiExecutionResult<String> // "short" or "detailed"
    suspend fun suggestTitle(context: AiMediaContext): AiExecutionResult<String>
    suspend fun classify(context: AiMediaContext): AiExecutionResult<Pair<String, List<String>>> // Category, topics
    suspend fun generateChapters(context: AiMediaContext): AiExecutionResult<List<AiChapter>>
}

/**
 * Deterministic local heuristic engine for offline use, privacy mode, or testing.
 */
class FakeAiEngine : AiEngine {
    override val modelIdentifier: String = "Local Heuristic"
    override val providerSource: AiProviderSource = AiProviderSource.LOCAL_HEURISTIC

    override suspend fun generateTags(context: AiMediaContext): AiExecutionResult<List<String>> = withContext(Dispatchers.Default) {
        val tags = mutableListOf<String>()
        val combinedText = "${context.title} ${context.description}".lowercase(Locale.ROOT)

        if (combinedText.contains("kotlin") || combinedText.contains("android") || combinedText.contains("code") || combinedText.contains("programming") || combinedText.contains("dev")) {
            tags.add("Technology")
            tags.add("Tutorial")
        }
        if (combinedText.contains("tutorial") || combinedText.contains("course") || combinedText.contains("how to") || combinedText.contains("learn")) {
            tags.add("Tutorial")
            tags.add("Education")
        }
        if (combinedText.contains("music") || combinedText.contains("song") || combinedText.contains("lyrics") || combinedText.contains("cover") || combinedText.contains("live")) {
            tags.add("Music")
        }
        if (combinedText.contains("game") || combinedText.contains("gaming") || combinedText.contains("gameplay") || combinedText.contains("ps5") || combinedText.contains("xbox")) {
            tags.add("Gaming")
        }
        if (combinedText.contains("sport") || combinedText.contains("football") || combinedText.contains("soccer") || combinedText.contains("match") || combinedText.contains("goal")) {
            tags.add("Sports")
        }
        if (combinedText.contains("news") || combinedText.contains("update") || combinedText.contains("politics") || combinedText.contains("actu")) {
            tags.add("News")
        }

        if (tags.isEmpty()) {
            tags.add("Education")
            tags.add("General")
        }

        AiExecutionResult(tags.distinct(), providerSource, modelIdentifier)
    }

    override suspend fun summarize(context: AiMediaContext, mode: String): AiExecutionResult<String> = withContext(Dispatchers.Default) {
        val hasTranscript = !context.transcript.isNullOrBlank()
        val sourceLabel = if (hasTranscript) "Heuristique locale (Transcription)" else "Heuristique locale (Métadonnées)"

        val text = if (hasTranscript) {
            if (mode == "short") {
                "Résumé succinct issu de la transcription de '${context.title}' par ${context.creator}. Points clés et démarches extraits."
            } else {
                "Résumé détaillé de '${context.title}' basé sur la transcription :\n" +
                        "• Introduction : Présentation du sujet par ${context.creator}.\n" +
                        "• Contenu : Analyse détaillée des points et démonstrations.\n" +
                        "• Conclusion : Synthèse des étapes et récapitulatif."
            }
        } else {
            if (mode == "short") {
                "Aperçu basé sur les métadonnées de '${context.title}' (${context.platform}). Durée : ${context.duration}s."
            } else {
                val cleanDesc = if (context.description.isNotBlank()) context.description.take(150) + "..." else "Aucune description fournie."
                "Aperçu détaillé basé sur les métadonnées :\n" +
                        "• Titre : ${context.title}\n" +
                        "• Créateur : ${context.creator}\n" +
                        "• Plateforme : ${context.platform}\n" +
                        "• Description : $cleanDesc\n" +
                        "• Durée : ${context.duration}s."
            }
        }

        AiExecutionResult("[$sourceLabel]\n$text", providerSource, modelIdentifier)
    }

    override suspend fun suggestTitle(context: AiMediaContext): AiExecutionResult<String> = withContext(Dispatchers.Default) {
        var cleaned = context.title
            .replace(Regex("(?i)\\[official.*?\\s*.*?\\]"), "")
            .replace(Regex("(?i)\\(official.*?\\s*.*?\\)"), "")
            .replace(Regex("(?i)\\[hd\\]|\\(hd\\)"), "")
            .replace(Regex("(?i)\\|.*?$"), "")
            .replace(Regex("(?i) - youtube$"), "")
            .trim()

        if (cleaned.isBlank()) cleaned = context.title
        AiExecutionResult(cleaned, providerSource, modelIdentifier)
    }

    override suspend fun classify(context: AiMediaContext): AiExecutionResult<Pair<String, List<String>>> = withContext(Dispatchers.Default) {
        val tags = generateTags(context).value
        val category = tags.firstOrNull() ?: "General"

        val topics = mutableListOf<String>()
        val words = context.title.split(Regex("[\\s,\\|\\-\\(\\)\\[\\]\\.]+"))
        for (word in words) {
            val clean = word.trim().replace(Regex("[^a-zA-Z0-9]"), "")
            if (clean.length > 4 && !listOf("video", "official", "youtube", "music", "tutorial").contains(clean.lowercase())) {
                topics.add(clean)
            }
        }

        if (topics.isEmpty()) {
            topics.add("Media")
            topics.add(category)
        }

        AiExecutionResult(Pair(category, topics.distinct().take(5)), providerSource, modelIdentifier)
    }

    override suspend fun generateChapters(context: AiMediaContext): AiExecutionResult<List<AiChapter>> = withContext(Dispatchers.Default) {
        val duration = context.duration
        val chapters = mutableListOf<AiChapter>()

        if (duration <= 0) {
            chapters.add(AiChapter(0, "Introduction", "Début de la vidéo."))
            return@withContext AiExecutionResult(chapters, providerSource, modelIdentifier)
        }

        chapters.add(AiChapter(0, "Introduction & Aperçu", "Présentation générale."))
        val mid = duration / 3
        if (mid > 0) {
            chapters.add(AiChapter(mid, "Développement principal", "Explications et démonstrations."))
        }
        val end = (duration * 2) / 3
        if (end > mid && end < duration) {
            chapters.add(AiChapter(end, "Conclusion & Synthèse", "Synthèse finale."))
        }

        AiExecutionResult(chapters, providerSource, modelIdentifier)
    }
}

/**
 * Direct REST API client for Gemini with strict error propagation and privacy-safe logging.
 */
class GeminiRestAiEngine(private val apiKey: String) : AiEngine {
    override val modelIdentifier: String = "gemini-2.5-flash"
    override val providerSource: AiProviderSource = AiProviderSource.CLOUD_GEMINI

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    private suspend fun queryGemini(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw IllegalStateException("Clé API Gemini non configurée")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            }
            put("contents", contentsArray)

            if (systemInstruction != null) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("GeminiRestAiEngine", "Gemini HTTP response failure status: ${response.code}")
                throw Exception("Erreur API Gemini (${response.code})")
            }
            val bodyString = response.body?.string() ?: throw Exception("Réponse vide de Gemini")
            val responseJson = JSONObject(bodyString)
            val candidates = responseJson.optJSONArray("candidates") 
                ?: throw Exception("Aucun candidat retourné par Gemini")
            if (candidates.length() == 0) throw Exception("Réponse vide du modèle")
            val candidate = candidates.getJSONObject(0)
            val content = candidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            parts.getJSONObject(0).getString("text")
        }
    }

    override suspend fun generateTags(context: AiMediaContext): AiExecutionResult<List<String>> {
        val prompt = "Generate a short JSON array of standard normalized high-level tags for a video with " +
                "title: '${context.title}' and description: '${context.description}'. " +
                "Select from standard tags like: Technology, Education, Music, Gaming, Sports, News, Tutorial. " +
                "Return ONLY a clean JSON array of strings. Do not include markdown codeblocks."
        val response = queryGemini(prompt)
        val cleanJson = response.replace("```json", "").replace("```", "").trim()
        val array = JSONArray(cleanJson)
        val tags = mutableListOf<String>()
        for (i in 0 until array.length()) {
            tags.add(array.getString(i))
        }
        return AiExecutionResult(tags, providerSource, modelIdentifier)
    }

    override suspend fun summarize(context: AiMediaContext, mode: String): AiExecutionResult<String> {
        val hasTranscript = !context.transcript.isNullOrBlank()
        val sourceLabel = if (hasTranscript) "Gemini Cloud (Transcription)" else "Gemini Cloud (Métadonnées)"

        val inputSource = if (hasTranscript) {
            "transcript: '${context.transcript}'"
        } else {
            "title: '${context.title}', description: '${context.description}'"
        }

        val prompt = "Crée un résumé ${if (mode == "short") "concis en 2 phrases" else "détaillé avec puces structurées"} " +
                "de la vidéo suivante: $inputSource. Réponds en français."

        val response = queryGemini(prompt, "Tu es un assistant expert en synthèse vidéo.")
        return AiExecutionResult("[$sourceLabel]\n$response", providerSource, modelIdentifier)
    }

    override suspend fun suggestTitle(context: AiMediaContext): AiExecutionResult<String> {
        val prompt = "Suggest a cleaner, high-quality title for a video originally named: '${context.title}'. " +
                "Remove clickbaits, channel promotional headers, or duplicate quality flags. Return only the new title text."
        val result = queryGemini(prompt).trim()
        return AiExecutionResult(result, providerSource, modelIdentifier)
    }

    override suspend fun classify(context: AiMediaContext): AiExecutionResult<Pair<String, List<String>>> {
        val prompt = "Classify this video: title: '${context.title}', description: '${context.description}'. " +
                "Provide a single primary category (e.g. Technology, Education, Music, Gaming, Sports, News, Tutorial) " +
                "and a JSON array of up to 5 topics. Return in this exact format: " +
                "{\"category\": \"CategoryName\", \"topics\": [\"Topic1\", \"Topic2\"]}"
        val response = queryGemini(prompt)
        val cleanJson = response.replace("```json", "").replace("```", "").trim()
        val obj = JSONObject(cleanJson)
        val category = obj.getString("category")
        val topicsArray = obj.getJSONArray("topics")
        val topics = mutableListOf<String>()
        for (i in 0 until topicsArray.length()) {
            topics.add(topicsArray.getString(i))
        }
        return AiExecutionResult(Pair(category, topics), providerSource, modelIdentifier)
    }

    override suspend fun generateChapters(context: AiMediaContext): AiExecutionResult<List<AiChapter>> {
        val hasTranscript = !context.transcript.isNullOrBlank()
        val duration = context.duration

        val prompt = "Generate chronological timestamped chapters for a video " +
                "with title '${context.title}', duration: $duration seconds, " +
                "${if (hasTranscript) "transcript: '${context.transcript}'" else "description: '${context.description}'"}. " +
                "Format each chapter as a JSON object with: startTimestamp (Long, in seconds), title (String), and shortDescription (String). " +
                "Return a JSON array of these objects. Do not overlap. Ensure timestamps are between 0 and $duration in ascending order. " +
                "Return ONLY a clean JSON array."

        val response = queryGemini(prompt)
        val cleanJson = response.replace("```json", "").replace("```", "").trim()
        val array = JSONArray(cleanJson)
        val list = mutableListOf<AiChapter>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(AiChapter(
                startTimestamp = obj.getLong("startTimestamp"),
                title = obj.getString("title"),
                shortDescription = obj.getString("shortDescription")
            ))
        }
        val sorted = list.filter { it.startTimestamp >= 0 && it.startTimestamp <= duration }
            .sortedBy { it.startTimestamp }
        return AiExecutionResult(sorted, providerSource, modelIdentifier)
    }
}
