package com.phuchienngo.marblemarvelous.weather

import android.content.Context
import android.util.Log
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.net.HttpRequestHeader
import com.phuchienngo.marblemarvelous.di.IoDispatcher
import com.phuchienngo.marblemarvelous.di.MainDispatcher
import com.phuchienngo.marblemarvelous.utils.CompressionUtils
import com.phuchienngo.marblemarvelous.utils.ConnectionUtils
import com.phuchienngo.marblemarvelous.utils.Console
import com.phuchienngo.marblemarvelous.utils.TimeUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.util.zip.DataFormatException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StormsProvider(
    private val context: Context,
    mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val callback: Callback
) {
    private var dataUpdated = true
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    interface Callback {
        fun onStormsUpdated(stormLocations: StormProtos.StormLocations?)
    }

    fun requestStormsUpdate() {
        dataUpdated = false
        if (ConnectionUtils.hasConnection(context)) {
            val dateString: String = TimeUtils.getTimeString()
            val lastCached: String? = getLastCache()
            if (dateString != lastCached) {
                runStormsUpdate()
            }
        }
    }

    fun getLatestStorms(): StormProtos.StormLocations? {
        return try {
            val stormsFile: File = Gdx
                .files
                .local(FILENAME)
                .file()
            if (stormsFile.exists()) {
                FileInputStream(stormsFile).use parseStorms@{ inputStream: FileInputStream ->
                    return@parseStorms StormProtos.StormLocations.parseFrom(inputStream)
                }
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Cannot load storms cache", e)
            null
        }
    }

    fun isDataUpdated(): Boolean = dataUpdated

    fun setNeedsUpdate() {
        dataUpdated = false
    }

    private fun runStormsUpdate() {
        scope.launch updateStorms@{
            Console.info(TAG, "Downloading new Storms...")
            val status: Int = withContext(ioDispatcher) stormDownload@{
                return@stormDownload downloadStorms()
            }
            if (status == 0) {
                dataUpdated = true
                saveNewCache()
                callback.onStormsUpdated(getLatestStorms())
            } else {
                Console.info(TAG, "Can't download storms, retrying soon")
            }
            return@updateStorms
        }
    }

    fun dispose() {
        scope.cancel()
    }

    class Factory @Inject constructor(
        private val context: Context,
        @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
    ) {
        fun create(callback: Callback): StormsProvider {
            return StormsProvider(context, mainDispatcher, ioDispatcher, callback)
        }
    }

    private fun downloadStorms(): Int {
        val result: File = downloadFile(STORMS_URL, FILENAME_TMP) ?: return 1
        val destination: File = Gdx.files.local(FILENAME).file()
        if (!result.renameTo(destination)) {
            Log.e(TAG, "Can't save storms file into final destination.")
            return 1
        }
        if (!result.delete()) {
            Console.info(TAG, "Couldn't delete temporary file storms_tmp.pb")
        }
        Console.info(TAG, "Storms updated successfully.")
        return 0
    }

    private fun downloadFile(urlString: String, filename: String): File? {
        return try {
            val url: URL = URL(urlString)
            val urlConnection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
            urlConnection.setRequestProperty(HttpRequestHeader.AcceptEncoding, "gzip")
            if (urlConnection.responseCode != 200) {
                Console.warn(TAG, "Response code for file: " + urlConnection.responseCode + ", aborting.")
                return null
            }
            val inputStream: InputStream = openResponseStream(urlConnection)
            val file: File = File.createTempFile(filename, null, context.cacheDir)
            try {
                writeDownloadToFile(inputStream, file, urlString)
                file
            } catch (e: DataFormatException) {
                Log.e(TAG, "Cannot decompress file", e)
                null
            }
        } catch (e: MalformedURLException) {
            Console.warn(TAG, "Malformed storms URL")
            null
        } catch (e: IOException) {
            Log.e(TAG, "Cannot download storms file", e)
            null
        }
    }

    private fun openResponseStream(urlConnection: HttpsURLConnection): InputStream {
        val encoding: String? = urlConnection.contentEncoding
        return if ("gzip" == encoding) {
            GZIPInputStream(urlConnection.inputStream)
        } else {
            urlConnection.inputStream
        }
    }

    @Throws(IOException::class, DataFormatException::class)
    private fun writeDownloadToFile(inputStream: InputStream, file: File, urlString: String) {
        BufferedInputStream(inputStream).use readStorms@{ bufferedInputStream: BufferedInputStream ->
            BufferedOutputStream(FileOutputStream(file)).use writeStorms@{ outputStream: BufferedOutputStream ->
                if (urlString != STORMS_URL_COMPRESSED) {
                    copyStorms(bufferedInputStream, outputStream)
                } else {
                    writeCompressedStorms(bufferedInputStream, outputStream)
                }
                outputStream.flush()
                return@writeStorms
            }
            return@readStorms
        }
    }

    @Throws(IOException::class)
    private fun copyStorms(inputStream: BufferedInputStream, outputStream: BufferedOutputStream) {
        var inByte: Int = inputStream.read()
        while (inByte != -1) {
            outputStream.write(inByte)
            inByte = inputStream.read()
        }
    }

    @Throws(IOException::class, DataFormatException::class)
    private fun writeCompressedStorms(inputStream: BufferedInputStream, outputStream: BufferedOutputStream) {
        val stream: ByteArrayOutputStream = ByteArrayOutputStream()
        val byteArray: ByteArray = ByteArray(2048)
        var read: Int = inputStream.read(byteArray)
        while (read > 0) {
            stream.write(byteArray, 0, read)
            read = inputStream.read(byteArray)
        }
        outputStream.write(CompressionUtils.decompress(stream.toByteArray()))
    }

    private fun getLastCache(): String? {
        val secureContext: Context = context.createDeviceProtectedStorageContext()
        return secureContext
            .getSharedPreferences("earth", 0)
            .getString("storms_update_time", null)
    }

    private fun saveNewCache() {
        val secureContext: Context = context.createDeviceProtectedStorageContext()
        val committed: Boolean = secureContext
            .getSharedPreferences("earth", 0)
            .edit()
            .putString("storms_update_time", TimeUtils.getTimeString())
            .commit()
        if (!committed) {
            Console.warn(TAG, "Couldn't save storms update timestamp")
        }
    }

    companion object {
        private const val FILENAME: String = "storms.pb"
        private const val FILENAME_TMP: String = "storms_tmp.pb"
        private const val STORMS_URL: String = "https://www.gstatic.com/pixel/livewallpaper/myworld/storm_locations"
        private const val STORMS_URL_COMPRESSED: String =
            "https://www.gstatic.com/pixel/livewallpaper/temp/storm_locations.compressed"
        private const val TAG: String = "StormsProvider"
    }
}
