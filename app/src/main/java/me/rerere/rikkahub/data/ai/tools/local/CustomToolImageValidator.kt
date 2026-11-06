package me.rerere.rikkahub.data.ai.tools.local

/**
 * Validates bounded raster images before they are written to conversation-owned storage.
 * SVG and unknown formats are deliberately rejected; rendering untrusted SVG would add a
 * separate active-content surface.
 */
internal object CustomToolImageValidator {
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_DIMENSION = 8_192
    private const val MAX_PIXELS = 40_000_000L

    fun validate(bytes: ByteArray, declaredMimeType: String): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) {
            "Image output exceeds the 8 MiB size limit"
        }
        val detected = detect(bytes) ?: throw IllegalArgumentException("Image format is invalid or unsupported")
        val declared = declaredMimeType.substringBefore(';').trim().lowercase()
        require(declared == detected.mimeType || declared == "application/octet-stream") {
            "Image content type does not match its file data"
        }
        require(
            detected.width in 1..MAX_DIMENSION &&
                detected.height in 1..MAX_DIMENSION &&
                detected.width.toLong() * detected.height.toLong() <= MAX_PIXELS
        ) {
            "Image dimensions exceed the safe display limit"
        }
        return detected.mimeType
    }

    private data class Dimensions(val mimeType: String, val width: Int, val height: Int)

    private fun detect(bytes: ByteArray): Dimensions? {
        if (bytes.startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) && bytes.size >= 24) {
            return Dimensions("image/png", bytes.readInt32BigEndian(16), bytes.readInt32BigEndian(20))
        }
        if (bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a")) {
            if (bytes.size < 10) return null
            return Dimensions(
                "image/gif",
                bytes.readUInt16LittleEndian(6),
                bytes.readUInt16LittleEndian(8),
            )
        }
        if (bytes.startsWith(0xff, 0xd8)) return jpegDimensions(bytes)
        if (bytes.startsWithAscii("RIFF") && bytes.size >= 30 &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        ) {
            return webpDimensions(bytes)
        }
        return null
    }

    private fun jpegDimensions(bytes: ByteArray): Dimensions? {
        val startOfFrame = setOf(
            0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7,
            0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf,
        )
        var index = 2
        while (index + 3 < bytes.size) {
            if ((bytes[index].toInt() and 0xff) != 0xff) {
                index++
                continue
            }
            while (index < bytes.size && (bytes[index].toInt() and 0xff) == 0xff) index++
            if (index >= bytes.size) return null
            val marker = bytes[index++].toInt() and 0xff
            if (marker == 0xd9 || marker == 0xda) return null
            if (marker == 0x01 || marker in 0xd0..0xd7) continue
            if (index + 1 >= bytes.size) return null
            val segmentLength = bytes.readUInt16BigEndian(index)
            if (segmentLength < 2 || index + segmentLength > bytes.size) return null
            if (marker in startOfFrame) {
                if (segmentLength < 7) return null
                val height = bytes.readUInt16BigEndian(index + 3)
                val width = bytes.readUInt16BigEndian(index + 5)
                return Dimensions("image/jpeg", width, height)
            }
            index += segmentLength
        }
        return null
    }

    private fun webpDimensions(bytes: ByteArray): Dimensions? {
        return when (bytes.copyOfRange(12, 16).toString(Charsets.US_ASCII)) {
            "VP8X" -> Dimensions(
                "image/webp",
                1 + bytes.readUInt24LittleEndian(24),
                1 + bytes.readUInt24LittleEndian(27),
            )
            "VP8 " -> {
                if (bytes.size < 30 || !bytes.startsWithAt(23, 0x9d, 0x01, 0x2a)) return null
                Dimensions(
                    "image/webp",
                    bytes.readUInt16LittleEndian(26) and 0x3fff,
                    bytes.readUInt16LittleEndian(28) and 0x3fff,
                )
            }
            "VP8L" -> {
                if (bytes.size < 25 || (bytes[20].toInt() and 0xff) != 0x2f) return null
                val b1 = bytes[21].toInt() and 0xff
                val b2 = bytes[22].toInt() and 0xff
                val b3 = bytes[23].toInt() and 0xff
                val b4 = bytes[24].toInt() and 0xff
                Dimensions(
                    "image/webp",
                    1 + ((b1 and 0x3f) or ((b2 and 0x3f) shl 8)),
                    1 + (((b2 and 0xc0) shr 6) or (b3 shl 2) or ((b4 and 0x0f) shl 10)),
                )
            }
            else -> null
        }
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { (this[it].toInt() and 0xff) == expected[it] }

    private fun ByteArray.startsWithAt(offset: Int, vararg expected: Int): Boolean =
        offset >= 0 && size >= offset + expected.size &&
            expected.indices.all { (this[offset + it].toInt() and 0xff) == expected[it] }

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && copyOfRange(0, value.length).toString(Charsets.US_ASCII) == value

    private fun ByteArray.readUInt16BigEndian(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)

    private fun ByteArray.readUInt16LittleEndian(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.readUInt24LittleEndian(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16)

    private fun ByteArray.readInt32BigEndian(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)
}
