package com.phuchienngo.marblemarvelous.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Guards against the precompiled `.filamat` packages under assets drifting from
 * their `.mat` sources under materials. `matc` is run manually (see the
 * `compileFilamentMaterials` Gradle task), so this test — which needs no matc —
 * fails when a `.mat` is edited without regenerating its `.filamat` and updating
 * the recorded checksum.
 */
class FilamentMaterialSourceChecksumTest {
  @Test
  fun materialSourcesMatchRecordedChecksums() {
    val materialsDir: File = materialsDirectory()
    val checksumFile = File(materialsDir, CHECKSUM_FILE)
    assertTrue("Missing $CHECKSUM_FILE", checksumFile.isFile)

    val recorded: Map<String, String> =
      checksumFile
        .readLines()
        .filter { line -> line.isNotBlank() }
        .associate { line ->
          val separator: Int = line.indexOf('=')
          line.substring(0, separator) to line.substring(separator + 1)
        }

    val matFiles: List<File> =
      materialsDir
        .listFiles { candidate -> candidate.extension == "mat" }
        ?.sortedBy { it.name }
        .orEmpty()
    assertTrue("No .mat sources found in $materialsDir", matFiles.isNotEmpty())

    for (matFile: File in matFiles) {
      val expected: String? = recorded[matFile.name]
      assertNotNull(
        "No recorded checksum for ${matFile.name}; run ./gradlew compileFilamentMaterials",
        expected
      )
      assertEquals(
        "${matFile.name} changed but ${matFile.nameWithoutExtension}.filamat was not recompiled. " +
          "Run ./gradlew compileFilamentMaterials -Pfilament.matc=/path/to/matc and commit the " +
          "updated .filamat plus $CHECKSUM_FILE.",
        expected,
        sha256(matFile)
      )
    }
  }

  private fun sha256(file: File): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(file.readBytes())
      .joinToString("") { byte -> "%02x".format(byte) }

  private fun materialsDirectory(): File {
    val candidates: List<File> =
      listOf(
        File("app/src/main/materials"),
        File("src/main/materials")
      )
    return candidates.firstOrNull { candidate -> candidate.isDirectory }
      ?: error("Unable to find materials directory from ${File(".").absolutePath}")
  }

  private companion object {
    const val CHECKSUM_FILE: String = "checksums.sha256"
  }
}
