package me.rerere.rikkahub.data.ai.tools.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomToolImageValidatorTest {

    @Test
    fun acceptsSupportedRasterFormatsAndDetectsTheirActualMimeTypes() {
        assertEquals("image/png", CustomToolImageValidator.validate(png(2, 3), "image/png"))
        assertEquals("image/gif", CustomToolImageValidator.validate(gif(4, 5), "image/gif"))
        assertEquals("image/jpeg", CustomToolImageValidator.validate(jpeg(6, 7), "image/jpeg"))
        assertEquals("image/webp", CustomToolImageValidator.validate(webp(8, 9), "image/webp"))
    }

    @Test
    fun rejectsMismatchedTypesOversizedImagesAndActiveFormats() {
        assertTrue(runCatching { CustomToolImageValidator.validate(png(2, 3), "image/jpeg") }.isFailure)
        assertTrue(runCatching { CustomToolImageValidator.validate(png(100_000, 100_000), "image/png") }.isFailure)
        assertTrue(runCatching {
            CustomToolImageValidator.validate("<svg></svg>".toByteArray(), "image/svg+xml")
        }.isFailure)
        assertTrue(runCatching {
            CustomToolImageValidator.validate(ByteArray(CustomToolImageValidator.MAX_IMAGE_BYTES + 1), "image/png")
        }.isFailure)
    }

    private fun png(width: Int, height: Int): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) +
            ByteArray(8) +
            int32(width) +
            int32(height)

    private fun gif(width: Int, height: Int): ByteArray =
        "GIF89a".toByteArray() + byteArrayOf(
            width.toByte(), (width shr 8).toByte(),
            height.toByte(), (height shr 8).toByte(),
        )

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(21)
        bytes[0] = 0xff.toByte()
        bytes[1] = 0xd8.toByte()
        bytes[2] = 0xff.toByte()
        bytes[3] = 0xc0.toByte()
        bytes[4] = 0
        bytes[5] = 17
        bytes[6] = 8
        bytes[7] = (height shr 8).toByte()
        bytes[8] = height.toByte()
        bytes[9] = (width shr 8).toByte()
        bytes[10] = width.toByte()
        return bytes
    }

    private fun webp(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(30)
        "RIFF".toByteArray().copyInto(bytes, 0)
        "WEBP".toByteArray().copyInto(bytes, 8)
        "VP8X".toByteArray().copyInto(bytes, 12)
        val widthMinusOne = width - 1
        val heightMinusOne = height - 1
        bytes[24] = widthMinusOne.toByte()
        bytes[25] = (widthMinusOne shr 8).toByte()
        bytes[26] = (widthMinusOne shr 16).toByte()
        bytes[27] = heightMinusOne.toByte()
        bytes[28] = (heightMinusOne shr 8).toByte()
        bytes[29] = (heightMinusOne shr 16).toByte()
        return bytes
    }

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )
}
