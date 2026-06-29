package com.phuchienngo.marblemarvelous.earth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EarthShaderSourceTest {
    @Test
    fun cloudsCombineWeatherMaskWithBundledDetailTexture() {
        val source: String = File("src/main/assets/earth/earth.frag").readText()

        assertTrue(source.contains("uniform samplerCube cloudMaskMap;"))
        assertTrue(source.contains("uniform samplerCube cloudDetailMap;"))
        assertTrue(source.contains("float sampleCloudMask(vec3 normal)"))
        assertTrue(source.contains("float sampleCloudDetail(vec3 normal)"))
        assertTrue(source.contains("const float cloudBlurOffset = 0.0075;"))
        assertTrue(source.contains("const float cloudMaskBoost = 1.85;"))
        assertTrue(source.contains("const float cloudThinBoostMix = 0.65;"))
        assertTrue(source.contains("float boostThinCloudMask(float mask)"))
        assertTrue(source.contains("return mix(mask, sqrt(mask), cloudThinBoostMix);"))
        assertTrue(source.contains("textureCube( cloudMaskMap, n ).r * .48"))
        assertTrue(source.contains("textureCube( cloudDetailMap, n ).r * .48"))
        assertTrue(source.contains(") ).r * .13"))
        assertTrue(source.contains("float weatherMask = sampleCloudMask(vCloudNormal);"))
        assertTrue(source.contains("float boostedWeatherMask = boostThinCloudMask(weatherMask);"))
        assertTrue(source.contains("float cloudDetail = sampleCloudDetail(vCloudNormal);"))
        assertTrue(source.contains("float cloudColor = clamp(boostedWeatherMask * mix(0.58, 1.0, cloudDetail) * cloudMaskBoost, 0., 1.);"))
        assertTrue(source.contains("float cloudShadow = clamp(boostedWeatherShadowMask * mix(0.58, 1.0, cloudShadowDetail) * cloudMaskBoost, 0., 1.);"))
        assertTrue(source.contains("float cloudCoverage = smoothstep(0.04, 0.64, cloudColor);"))
        assertTrue(source.contains("(cloudColor - cloudShadow) * 2.0 + .50"))
        assertTrue(source.contains("nightCloudColor * .78"))
    }

    @Test
    fun shaderProviderBindsWeatherMaskAndBundledCloudDetail() {
        val source: String =
            File("src/main/kotlin/com/phuchienngo/marblemarvelous/earth/shader/EarthShaderProvider.kt")
                .readText()

        assertTrue(source.contains("EarthTextureAttribute.CLOUD_DETAIL"))
        assertTrue(source.contains("val cloudDetail: EarthTextureAttribute"))
        assertTrue(source.contains("shader!!.setUniformi(\"cloudMaskMap\", 1)"))
        assertTrue(source.contains("shader!!.setUniformi(\"cloudDetailMap\", 2)"))
    }
}
