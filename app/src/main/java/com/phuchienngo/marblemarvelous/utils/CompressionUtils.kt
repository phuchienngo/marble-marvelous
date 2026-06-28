package com.phuchienngo.marblemarvelous.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater

object CompressionUtils {
    @JvmStatic
    @Throws(DataFormatException::class, IOException::class)
    fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        val outputStream = ByteArrayOutputStream(data.size)
        val dataChunk = ByteArray(1024)
        inflater.setInput(data)
        while (!inflater.finished()) {
            val n = inflater.inflate(dataChunk)
            outputStream.write(dataChunk, 0, n)
        }
        inflater.end()
        outputStream.close()
        return outputStream.toByteArray()
    }
}
