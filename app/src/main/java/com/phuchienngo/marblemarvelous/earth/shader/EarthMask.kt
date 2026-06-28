package com.phuchienngo.marblemarvelous.earth.shader

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.RenderableProvider
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.FlushablePool
import com.phuchienngo.marblemarvelous.utils.ShaderUtils
import com.phuchienngo.marblemarvelous.utils.Size

class EarthMask {
    private var mAod = 0.0f
    private val mLightDirection = Vector3()
    private val mModelViewMatrix = Matrix4()
    private val mNormalMatrix = Matrix4()
    private var rimFadeStart = 0.98f
    private var rimFadeEnd = 1.0f
    private var dFov = 0.0f
    private val maskShader: ShaderProgram = ShaderUtils.load("marble/earthMask")
    private val renderablesPool = RenderablePool()
    private val renderables = Array<Renderable>()
    private val fboSize = Size(256.0f, 256.0f)
    private val mCamera = PerspectiveCamera()
    private val fbo = FrameBuffer(Pixmap.Format.RGBA8888, fboSize.getWidth().toInt(), fboSize.getHeight().toInt(), false)

    init {
        fbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    fun begin(
        camera: PerspectiveCamera,
        renderContext: RenderContext?,
    ) {
        mCamera.far = camera.far
        mCamera.near = camera.near
        mCamera.position.set(camera.position)
        mCamera.direction.set(camera.direction)
        mCamera.up.set(camera.up)
        mCamera.viewportHeight = camera.viewportHeight
        mCamera.viewportWidth = camera.viewportWidth
        mCamera.fieldOfView = camera.fieldOfView + dFov
        mCamera.update()
        fbo.begin()
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        Gdx.gl.glClear(16384)
        maskShader.bind()
        if (renderContext != null) {
            renderContext.setDepthTest(GL20.GL_LEQUAL)
            renderContext.setCullFace(GL20.GL_BACK)
            renderContext.setBlending(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            Gdx.gl.glCullFace(GL20.GL_BACK)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }
    }

    fun render(renderable: Renderable) {
        mNormalMatrix.set(mCamera.view).mul(renderable.worldTransform)
        if (mNormalMatrix.det() == 0.0f) return
        mNormalMatrix.inv().tra()
        mModelViewMatrix.set(mCamera.view).mul(renderable.worldTransform)
        val attr = renderable.environment.get(PointLightsAttribute.Type) as PointLightsAttribute
        val sunLight = attr.lights.first()
        mLightDirection.set(sunLight.position).mul(mCamera.view).nor()
        maskShader.setUniformf("u_lightDirection", mLightDirection)
        maskShader.setUniformf("u_aod", mAod)
        maskShader.setUniformf("u_resolution", fboSize.getWidth(), fboSize.getHeight())
        maskShader.setUniformf("u_rim_fade_start", rimFadeStart)
        maskShader.setUniformf("u_rim_fade_end", rimFadeEnd)
        maskShader.setUniformMatrix("u_projViewTrans", mCamera.combined)
        maskShader.setUniformMatrix("u_modelViewTrans", mModelViewMatrix)
        maskShader.setUniformMatrix("u_worldTrans", renderable.worldTransform)
        maskShader.setUniformMatrix("u_normalMatrix", mNormalMatrix)
        renderable.meshPart.render(maskShader)
    }

    fun render(
        renderableProvider: RenderableProvider,
        environment: Environment,
    ) {
        val offset = renderables.size
        renderableProvider.getRenderables(renderables, renderablesPool)
        var i = offset
        while (i < renderables.size) {
            val r = renderables.get(i)
            r.environment = environment
            render(r)
            i++
        }
        renderablesPool.flush()
        renderables.clear()
    }

    fun end() {
        Gdx.gl.glDisable(GL20.GL_BLEND)
        fbo.end()
    }

    fun dispose() {
        maskShader.dispose()
        fbo.dispose()
    }

    fun getFboTexture(): Texture = fbo.colorBufferTexture

    fun setAOD(aodValue: Float) {
        mAod = aodValue
    }

    fun setRimFade(
        rimFadeStart: Float,
        rimFadeEnd: Float,
    ) {
        this.rimFadeStart = rimFadeStart
        this.rimFadeEnd = rimFadeEnd
    }

    fun setFieldOfViewDifference(dFov: Float) {
        this.dFov = dFov
    }

    private class RenderablePool : FlushablePool<Renderable>() {
        override fun newObject(): Renderable = Renderable()

        override fun obtain(): Renderable =
            super.obtain().apply {
                environment = null
                material = null
                meshPart.set("", null, 0, 0, 0)
                shader = null
                userData = null
            }
    }
}
