package com.phuchienngo.marblemarvelous.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater

object CompressionUtils {
    @JvmStatic
    @Throws(DataFormatException::class, IOException::class)
    fun decompress(data: ByteArray): ByteArray {
        val inflater: Inflater = Inflater()
        val outputStream: ByteArrayOutputStream = ByteArrayOutputStream(data.size)
        val dataChunk: ByteArray = ByteArray(1024)
        inflater.setInput(data)
        while (!inflater.finished()) {
            val n: Int = inflater.inflate(dataChunk)
            outputStream.write(dataChunk, 0, n)
        }
        inflater.end()
        outputStream.close()
        return outputStream.toByteArray()
    }
}
