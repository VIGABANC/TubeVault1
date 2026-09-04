package com.example

import com.example.data.ai.FakeAiEngine
import com.example.data.model.AiChapter
import com.example.data.model.AiMediaContext
import com.example.data.model.AiTranscript
import com.example.util.TranscriptFixture
import com.example.util.TranscriptParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiFeaturesTest {

    @Test
    fun testTranscriptParserSrt() {
        // Test parsing of standard SRT subtitle file format
        val transcript = TranscriptParser.parse(TranscriptFixture.SRT_FIXTURE, "fr")
        
        assertNotNull(transcript)
        assertEquals("fr", transcript.language)
        assertTrue(transcript.segments.isNotEmpty())
        
        // Check first segment properties
        val firstSegment = transcript.segments.first()
        assertEquals(0.0, firstSegment.startTime, 0.01)
        assertEquals(5.0, firstSegment.endTime, 0.01)
        assertTrue(firstSegment.text.contains("Bonjour et bienvenue dans ce tutoriel de TubeVault"))
        
        // Serialize and deserialize JSON
        val jsonStr = transcript.toJsonString()
        assertNotNull(jsonStr)
        
        val parsedFromJson = AiTranscript.fromJsonString(jsonStr)
        assertNotNull(parsedFromJson)
        assertEquals(transcript.language, parsedFromJson?.language)
        assertEquals(transcript.segments.size, parsedFromJson?.segments?.size)
    }

    @Test
    fun testTranscriptParserVtt() {
        // Test parsing of WebVTT subtitle file format
        val vttFixture = """
            WEBVTT

            1
            00:00:01.200 --> 00:00:05.400
            Bonjour à tous et bienvenue !

            2
            00:00:06.000 --> 00:00:10.150
            Aujourd'hui nous allons parler d'IA.
        """.trimIndent()

        val transcript = TranscriptParser.parse(vttFixture, "fr")
        assertNotNull(transcript)
        assertEquals(2, transcript.segments.size)
        
        val first = transcript.segments.first()
        assertEquals(1.2, first.startTime, 0.01)
        assertEquals(5.4, first.endTime, 0.01)
        assertEquals("Bonjour à tous et bienvenue !", first.text)
    }

    @Test
    fun testChaptersJsonSerialization() {
        val chapters = listOf(
            AiChapter(0L, "Introduction", "Présentation de l'application et mise en route"),
            AiChapter(120L, "Installation", "Comment installer et configurer les paramètres de base")
        )

        val jsonStr = AiChapter.listToJsonString(chapters)
        assertNotNull(jsonStr)

        val parsedList = AiChapter.listFromJsonString(jsonStr)
        assertEquals(2, parsedList.size)
        assertEquals("Introduction", parsedList[0].title)
        assertEquals(120L, parsedList[1].startTimestamp)
    }

    @Test
    fun testFakeAiEngineGenerations() = runBlocking {
        val engine = FakeAiEngine()
        assertTrue(engine.modelIdentifier.contains("Local Heuristic") || engine.modelIdentifier.contains("FakeAiEngine"))

        val context = AiMediaContext(
            mediaId = "123",
            title = "Apprendre le Kotlin et Compose de zéro",
            creator = "DevTutorials",
            platform = "YouTube",
            description = "Un cours complet pour les débutants.",
            duration = 180L,
            existingTags = emptyList()
        )

        // Test Summary
        val summary = engine.summarize(context, "short")
        assertNotNull(summary.value)
        assertTrue(summary.value.contains("Apprendre le Kotlin"))

        // Test Title suggestions
        val suggestedTitle = engine.suggestTitle(context)
        assertNotNull(suggestedTitle.value)
        assertTrue(suggestedTitle.value.isNotBlank())

        // Test Tag suggestions
        val suggestedTags = engine.generateTags(context)
        assertTrue(suggestedTags.value.isNotEmpty())
        assertTrue(suggestedTags.value.contains("Tutorial"))

        // Test Chapter suggestions
        val suggestedChapters = engine.generateChapters(context)
        assertTrue(suggestedChapters.value.isNotEmpty())
        assertTrue(suggestedChapters.value.first().title.isNotBlank())
    }
}
