package eu.kanade.translation.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.ProfileType

class PageTranslationGeometryTest {

    private fun createBlock(
        width: Float = 100f,
        height: Float = 100f,
        x: Float = 0f,
        y: Float = 0f,
        text: String = "Test",
    ): TranslationBlock {
        return TranslationBlock(
            text = text,
            translation = "Translation",
            width = width,
            height = height,
            x = x,
            y = y,
            symHeight = 10f,
            symWidth = 10f,
            angle = 0f,
        )
    }

    @Test
    fun `getAspectRatio calculates width divided by height with minimum height of 1`() {
        val normalBlock = createBlock(width = 200f, height = 100f)
        assertEquals(2.0f, normalBlock.getAspectRatio(), 0.001f)

        val zeroHeightBlock = createBlock(width = 150f, height = 0f)
        assertEquals(150.0f, zeroHeightBlock.getAspectRatio(), 0.001f)

        val negativeHeightBlock = createBlock(width = 50f, height = -10f)
        assertEquals(50.0f, negativeHeightBlock.getAspectRatio(), 0.001f)
    }

    @Test
    fun `detectShape classifies bubble shapes based on aspect ratio thresholds`() {
        // HORIZONTAL_OVAL: aspectRatio > 1.8f
        val horizOval = createBlock(width = 200f, height = 100f) // 2.0 > 1.8
        assertEquals(BubbleShape.HORIZONTAL_OVAL, horizOval.detectShape())
        assertEquals(BubbleShape.HORIZONTAL_OVAL, horizOval.bubbleShape)

        // HORIZONTAL_RECT: 1.2f < aspectRatio <= 1.8f
        val horizRect = createBlock(width = 150f, height = 100f) // 1.5
        assertEquals(BubbleShape.HORIZONTAL_RECT, horizRect.detectShape())
        assertEquals(BubbleShape.HORIZONTAL_RECT, horizRect.bubbleShape)

        val horizRectBoundary = createBlock(width = 180f, height = 100f) // 1.8 <= 1.8
        assertEquals(BubbleShape.HORIZONTAL_RECT, horizRectBoundary.detectShape())

        // SQUARE: 0.8f < aspectRatio <= 1.2f
        val square = createBlock(width = 100f, height = 100f) // 1.0
        assertEquals(BubbleShape.SQUARE, square.detectShape())
        assertEquals(BubbleShape.SQUARE, square.bubbleShape)

        val squareUpperBoundary = createBlock(width = 120f, height = 100f) // 1.2
        assertEquals(BubbleShape.SQUARE, squareUpperBoundary.detectShape())

        // VERTICAL_RECT: 0.6f < aspectRatio <= 0.8f
        val vertRect = createBlock(width = 70f, height = 100f) // 0.7
        assertEquals(BubbleShape.VERTICAL_RECT, vertRect.detectShape())
        assertEquals(BubbleShape.VERTICAL_RECT, vertRect.bubbleShape)

        val vertRectUpperBoundary = createBlock(width = 80f, height = 100f) // 0.8
        assertEquals(BubbleShape.VERTICAL_RECT, vertRectUpperBoundary.detectShape())

        // VERTICAL_OVAL: aspectRatio <= 0.6f
        val vertOval = createBlock(width = 50f, height = 100f) // 0.5
        assertEquals(BubbleShape.VERTICAL_OVAL, vertOval.detectShape())
        assertEquals(BubbleShape.VERTICAL_OVAL, vertOval.bubbleShape)

        val vertOvalUpperBoundary = createBlock(width = 60f, height = 100f) // 0.6
        assertEquals(BubbleShape.VERTICAL_OVAL, vertOvalUpperBoundary.detectShape())
    }

    @Test
    fun `getEdgeProximity classifies bubble proximity relative to image bounds`() {
        val imgHeight = 1000f

        // Near top: y < imgHeight * threshold (100f with 0.10f threshold)
        val topBlock = createBlock(y = 50f, height = 100f)
        assertEquals(EdgeProximity.NEAR_TOP, topBlock.getEdgeProximity(imgHeight))

        // Near bottom: (y + height) > imgHeight * (1 - threshold) (900f with 0.10f threshold)
        val bottomBlock = createBlock(y = 850f, height = 100f) // 950 > 900
        assertEquals(EdgeProximity.NEAR_BOTTOM, bottomBlock.getEdgeProximity(imgHeight))

        // Center: neither near top nor near bottom
        val centerBlock = createBlock(y = 300f, height = 100f) // 300 >= 100 and 400 <= 900
        assertEquals(EdgeProximity.CENTER, centerBlock.getEdgeProximity(imgHeight))

        // Custom threshold
        val customThresholdBlock = createBlock(y = 80f, height = 50f)
        // With threshold 0.05f: top limit is 50f -> y = 80f is CENTER
        assertEquals(EdgeProximity.CENTER, customThresholdBlock.getEdgeProximity(imgHeight, 0.05f))
        // With default threshold 0.10f: top limit is 100f -> y = 80f is NEAR_TOP
        assertEquals(EdgeProximity.NEAR_TOP, customThresholdBlock.getEdgeProximity(imgHeight, 0.10f))
    }

    @Test
    fun `profileType derives ProfileType from sourceLanguage correctly`() {
        val pageJa = PageTranslation(sourceLanguage = "ja")
        assertEquals(ProfileType.CJK, pageJa.profileType)

        val pageKo = PageTranslation(sourceLanguage = "ko")
        assertEquals(ProfileType.CJK, pageKo.profileType)

        val pageZh = PageTranslation(sourceLanguage = "zh")
        assertEquals(ProfileType.CJK, pageZh.profileType)

        val pageEn = PageTranslation(sourceLanguage = "en")
        assertEquals(ProfileType.LATIN, pageEn.profileType)

        val pageId = PageTranslation(sourceLanguage = "id")
        assertEquals(ProfileType.LATIN, pageId.profileType)

        val pageDefault = PageTranslation()
        assertEquals("auto", pageDefault.sourceLanguage)
        assertEquals("en", pageDefault.targetLanguage)
        assertEquals("gemini", pageDefault.translatorType)
        assertEquals(ProfileType.LATIN, pageDefault.profileType)
    }

    @Test
    fun `deleteBlock by index and object removes block and updates activeBlocks`() {
        val block1 = createBlock(text = "Block 1")
        val block2 = createBlock(text = "Block 2")
        val block3 = createBlock(text = "Block 3")

        val page = PageTranslation(
            blocks = mutableListOf(block1, block2, block3),
        )

        assertEquals(3, page.blocks.size)
        assertEquals(3, page.activeBlocks.size)

        // Delete valid index 1 (block2)
        val deleted = page.deleteBlock(1)
        assertTrue(deleted)
        assertEquals(2, page.blocks.size)
        assertEquals(2, page.activeBlocks.size)
        assertEquals(listOf(block1, block3), page.activeBlocks)

        // Delete invalid index out of bounds
        val invalidDelete = page.deleteBlock(5)
        assertFalse(invalidDelete)
        assertEquals(2, page.blocks.size)

        val negativeDelete = page.deleteBlock(-1)
        assertFalse(negativeDelete)
        assertEquals(2, page.blocks.size)

        // Delete by object
        val deletedObj = page.deleteBlock(block1)
        assertTrue(deletedObj)
        assertEquals(1, page.blocks.size)
        assertEquals(listOf(block3), page.activeBlocks)

        // Delete already removed block
        val deletedAgain = page.deleteBlock(block1)
        assertFalse(deletedAgain)
    }

    @Test
    fun `serialization preserves backwards compatibility with existing JSON`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

        // Test decoding old JSON without new fields
        val legacyJson = """{"blocks":[],"imgWidth":800.0,"imgHeight":1200.0}"""
        val page = json.decodeFromString<PageTranslation>(legacyJson)

        assertEquals(800.0f, page.imgWidth)
        assertEquals(1200.0f, page.imgHeight)
        assertEquals("auto", page.sourceLanguage)
        assertEquals("en", page.targetLanguage)
        assertEquals("gemini", page.translatorType)
        assertEquals(ProfileType.LATIN, page.profileType)

        // Test roundtrip encoding/decoding
        page.sourceLanguage = "ko"
        page.targetLanguage = "en"
        page.translatorType = "cloud"
        page.blocks.add(createBlock(text = "안녕하세요"))

        val encoded = json.encodeToString(page)
        val decoded = json.decodeFromString<PageTranslation>(encoded)

        assertEquals("ko", decoded.sourceLanguage)
        assertEquals("en", decoded.targetLanguage)
        assertEquals("cloud", decoded.translatorType)
        assertEquals(ProfileType.CJK, decoded.profileType)
        assertEquals(1, decoded.blocks.size)
        assertEquals("안녕하세요", decoded.blocks[0].text)
    }
}
