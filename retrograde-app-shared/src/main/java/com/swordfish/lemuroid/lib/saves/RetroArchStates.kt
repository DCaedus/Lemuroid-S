package com.swordfish.lemuroid.lib.saves

import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/**
 * Reads save states written by RetroArch (or Lemuroid) and returns the core's raw state data.
 * Handles RetroArch's compressed "rzip" files and its "RASTATE" container, and Lemuroid's gzip.
 */
object RetroArchStates {
    private const val RZIP_HEADER_SIZE = 20
    private const val RASTATE_HEADER_SIZE = 8
    private val RZIP_MAGIC = "#RZIPv".toByteArray()
    private val RASTATE_MAGIC = "RASTATE".toByteArray()

    fun decode(bytes: ByteArray): ByteArray? {
        val unpacked =
            when {
                startsWith(bytes, RZIP_MAGIC) -> decodeRzip(bytes) ?: return null
                isGzip(bytes) -> GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                else -> bytes
            }
        return if (startsWith(unpacked, RASTATE_MAGIC)) extractRastateMemory(unpacked) else unpacked
    }

    /**
     * RetroArch's compressed format (rzip): "#RZIPv<version>#", chunk size (uint32), total size
     * (uint64), then chunks of [compressed size (uint32) + zlib data].
     */
    private fun decodeRzip(bytes: ByteArray): ByteArray? {
        return runCatching {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(RZIP_HEADER_SIZE - 12)
            buffer.int // chunk size
            val totalSize = buffer.long
            val output = ByteArrayOutputStream(totalSize.toInt())
            val chunk = ByteArray(64 * 1024)

            while (buffer.remaining() >= 4) {
                val compressedSize = buffer.int
                val compressed = ByteArray(compressedSize)
                buffer.get(compressed)
                val inflater = Inflater()
                inflater.setInput(compressed)
                while (!inflater.finished()) {
                    val count = inflater.inflate(chunk)
                    if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                    output.write(chunk, 0, count)
                }
                inflater.end()
            }
            output.toByteArray().takeIf { it.size.toLong() == totalSize }
        }.onFailure { Timber.w(it, "Could not decode RetroArch state") }.getOrNull()
    }

    /**
     * Newer RetroArch versions wrap states in a "RASTATE" container: an 8-byte header ("RASTATE" +
     * version), then blocks of [4-byte id, 4-byte little-endian size, data] padded to 8 bytes.
     * The "MEM " block holds the core's own state data.
     */
    private fun extractRastateMemory(bytes: ByteArray): ByteArray? {
        for (aligned in listOf(true, false)) {
            runCatching { findRastateBlock(bytes, "MEM ", aligned) }.getOrNull()?.let { return it }
        }
        Timber.w("Could not read RetroArch RASTATE state")
        return null
    }

    private fun findRastateBlock(
        bytes: ByteArray,
        blockId: String,
        aligned: Boolean,
    ): ByteArray? {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(RASTATE_HEADER_SIZE)
        while (buffer.remaining() >= 8) {
            val id = ByteArray(4).also { buffer.get(it) }.decodeToString()
            val size = buffer.int
            if (size < 0 || size > buffer.remaining()) return null
            if (id == blockId) return ByteArray(size).also { buffer.get(it) }
            if (id == "END ") return null
            val skip = if (aligned) (size + 7) / 8 * 8 else size
            if (skip > buffer.remaining()) return null
            buffer.position(buffer.position() + skip)
        }
        return null
    }

    private fun isGzip(bytes: ByteArray) = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    private fun startsWith(
        bytes: ByteArray,
        prefix: ByteArray,
    ) = bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }
}
