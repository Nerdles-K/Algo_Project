package com.synchplay.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the YouTube id extraction used when publishing a video
 * (VideosController). Pure string parsing — no Spring context / DB.
 */
class VideosControllerTest {

    @Test
    @DisplayName("extracts id from common YouTube URL forms")
    void parsesUrlForms() {
        assertEquals("dQw4w9WgXcQ",
            VideosController.extractYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ",
            VideosController.extractYouTubeId("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ",
            VideosController.extractYouTubeId("https://www.youtube.com/embed/dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ",
            VideosController.extractYouTubeId("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ",
            VideosController.extractYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"));
    }

    @Test
    @DisplayName("accepts a bare 11-char id and trims whitespace")
    void parsesBareId() {
        assertEquals("dQw4w9WgXcQ", VideosController.extractYouTubeId("dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", VideosController.extractYouTubeId("  dQw4w9WgXcQ  "));
    }

    @Test
    @DisplayName("ids with underscores and hyphens are preserved")
    void preservesUnderscoreHyphen() {
        assertEquals("D_CZ8v9uZJY", VideosController.extractYouTubeId("https://youtu.be/D_CZ8v9uZJY"));
        assertEquals("a-b_c-d_e-f", VideosController.extractYouTubeId("a-b_c-d_e-f"));
    }

    @Test
    @DisplayName("returns null for blanks and non-YouTube / wrong-length input")
    void rejectsInvalid() {
        assertNull(VideosController.extractYouTubeId(null));
        assertNull(VideosController.extractYouTubeId(""));
        assertNull(VideosController.extractYouTubeId("not a link"));
        assertNull(VideosController.extractYouTubeId("https://example.com/video/123"));
        assertNull(VideosController.extractYouTubeId("tooShort"));       // < 11 bare chars
    }
}
