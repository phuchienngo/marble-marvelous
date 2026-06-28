package com.phuchienngo.marblemarvelous.celestialBodies.shaders

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.phuchienngo.marblemarvelous.celestialBodies.shaders.attributes.PlanetTextureAttribute
import com.phuchienngo.marblemarvelous.utils.Console
import com.phuchienngo.marblemarvelous.utils.ShaderUtils

class EarthShaderProvider : DefaultShaderProvider() {

    override fun createShader(renderable: Renderable): Shader {
        return if (renderable.userData == "earth") EarthShader(renderable)
        else super.createShader(renderable)
    }

    private class EarthShader(private val renderable: Renderable) : BaseShader() {
        private var cloudShadowMatrix: Matrix4? = null
        private var cloudTransformMatrix: Matrix4? = null
        private var shader: ShaderProgram? = null
        private val rimColor1 = Color(0.39f, 0.44f, 0.5f, 0.8f)
        private val rimColor2 = Color(0.92941177f, 0.27450982f, 0.05882353f, 0.8f)
        private val atmosphereColor1 = Color(0.6784314f, 0.8235294f, 0.9411765f, 0.4f)
        private val atmosphereColor2 = Color(0.38039216f, 0.69411767f, 0.9490196f, 0.85f)
        private val tmpMatrix = Matrix4()
        private val tmpModelMatrix = Matrix4()
        private val tmpLightPos = Vector3()

        override fun init() {
            shader = ShaderUtils.load("earth/earth")
            cloudTransformMatrix = Matrix4()
                .rotate(Vector3(1.0f, 0.0f, 0.0f), -90.0f)
                .rotate(Vector3(0.0f, 0.0f, 1.0f), -90.0f)
            cloudShadowMatrix = Matrix4().rotate(Vector3(1.0f, 0.0f, 0.0f), 0.3f)
            init(shader, renderable)
        }

        override fun compareTo(other: Shader): Int = if (other === this) 0 else 1

        override fun canRender(renderable: Renderable): Boolean = renderable.userData == "earth"

        override fun render(renderable: Renderable) {
            val pointLights = renderable.environment.get(PointLightsAttribute.Type) as PointLightsAttribute
            val sunLight = pointLights.lights.first()
            val day = renderable.material.get(PlanetTextureAttribute.DayDiffuse) as PlanetTextureAttribute
            val night = renderable.material.get(PlanetTextureAttribute.NightDiffuse) as PlanetTextureAttribute
            val storms = renderable.material.get(PlanetTextureAttribute.StormsDiffuse) as PlanetTextureAttribute
            val clouds = renderable.environment.get(CubemapAttribute.EnvironmentMap) as CubemapAttribute
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glEnable(GL20.GL_CULL_FACE)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            Gdx.gl.glCullFace(GL20.GL_BACK)
            tmpMatrix.set(camera.view).mul(renderable.worldTransform)
            tmpModelMatrix.set(camera.view).mul(renderable.worldTransform)
            tmpLightPos.set(sunLight.position).mul(camera.view)
            if (tmpMatrix.det() == 0.0f) {
                Console.warn(TAG, "Can't invert matrix because it does not have a determinant, skip this frame")
                return
            }
            shader!!.setUniformMatrix("normalMatrix", tmpMatrix.inv().tra())
            shader!!.setUniformMatrix("modelViewMatrix", tmpModelMatrix)
            shader!!.setUniformMatrix("projectionMatrix", camera.projection)
            shader!!.setUniformMatrix("cloudTransformMatrix", cloudTransformMatrix)
            shader!!.setUniformMatrix("cloudShadowMatrix", cloudShadowMatrix)
            shader!!.setUniformf("lightPosition", tmpLightPos)
            shader!!.setUniformf("lightIntensity", sunLight.intensity)
            (day.textureDescription.texture as Cubemap).bind(4)
            shader!!.setUniformi("dayMap", 4)
            (night.textureDescription.texture as Cubemap).bind(3)
            shader!!.setUniformi("nightMap", 3)
            (storms.textureDescription.texture as Cubemap).bind(2)
            shader!!.setUniformi("stormsMap", 2)
            (clouds.textureDescription.texture as Cubemap).bind(1)
            shader!!.setUniformi("cloudMap", 1)
            shader!!.setUniformf("terminatorColor1", rimColor1)
            shader!!.setUniformf("terminatorColor2", rimColor2)
            shader!!.setUniformf("atmosphereColor1", atmosphereColor1)
            shader!!.setUniformf("atmosphereColor2", atmosphereColor2)
            super.render(renderable)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        override fun dispose() {
            shader?.let {
                it.dispose()
                shader = null
            }
            super.dispose()
        }
    }

    companion object {
        @JvmField var TAG = "EarthShader"
    }
}
