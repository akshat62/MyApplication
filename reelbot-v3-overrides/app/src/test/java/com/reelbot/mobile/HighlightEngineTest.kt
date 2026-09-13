package com.reelbot.mobile

import com.reelbot.mobile.ai.HighlightEngine
import com.reelbot.mobile.data.model.TranscriptSegment
import org.junit.Assert.*
import org.junit.Test

class HighlightEngineTest {
    @Test fun sentenceBoundariesDoNotCollapseEveryCandidateToOneShortSentence() {
        val speech = (0..35).map { i -> TranscriptSegment(i * 5000L, (i + 1) * 5000L, "Why is topic${i} important? Detail${i} evidence${i} lesson${i} finding${i}.") }
        val clips = HighlightEngine().select(speech, 3, 30000)
        assertEquals(3, clips.size)
        assertTrue(clips.all { it.endMs - it.startMs >= 15000 })
        assertTrue(clips.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue(clips.all { clip -> clip.segments.all { it in speech } })
    }
    @Test fun emptySpeechFailsInsteadOfFabricatingTranscript() {
        assertThrows(Exception::class.java) { HighlightEngine().select(emptyList(), 3, 30000) }
    }
}
