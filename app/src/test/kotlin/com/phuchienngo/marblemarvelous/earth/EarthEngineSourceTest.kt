package com.phuchienngo.marblemarvelous.earth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EarthEngineSourceTest {
    @Test
    fun initialCloudMapIsAppliedEvenDuringResumeWarmup() {
        val source: String = File("src/main/kotlin/com/phuchienngo/marblemarvelous/earth/EarthEngine.kt").readText()

        assertTrue(
            "First cloud cubemap must be bound before rendering; resume warmup may only defer swaps after a cloud texture exists.",
            source.contains("cloudsTexture == null || !isResumeWarmupActive()")
        )
    }

    @Test
    fun bundledCloudTextureIsLoadedAsRealtimeDetailLayer() {
        val source: String = File("src/main/kotlin/com/phuchienngo/marblemarvelous/earth/EarthEngine.kt").readText()

        assertTrue(source.contains("assetManager!!.load(\"earth/clouds.ktx\", Cubemap::class.java)"))
        assertTrue(source.contains("private var cloudDetailTexture: Cubemap? = null"))
        assertTrue(source.contains("cloudDetailTexture = assetManager!!.get(\"earth/clouds.ktx\", Cubemap::class.java)"))
        assertTrue(source.contains("EarthTextureAttribute.createCloudDetail(loadedCloudDetail)"))
    }

    @Test
    fun earthTextureAttributeSupportsCloudDetailCubemap() {
        val source: String =
            File("src/main/kotlin/com/phuchienngo/marblemarvelous/earth/shader/attributes/EarthTextureAttribute.kt")
                .readText()

        assertTrue(source.contains("val CLOUD_DETAIL: Long = register(\"CloudDetail\")"))
        assertTrue(source.contains("or CLOUD_DETAIL"))
        assertTrue(source.contains("fun createCloudDetail(texture: Cubemap): EarthTextureAttribute"))
    }
}
