package com.phuchienngo.marblemarvelous.earth.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.phuchienngo.marblemarvelous.gl.SingleFloatFrameBuffer
import com.phuchienngo.marblemarvelous.mesh.PlaneConstructor
import com.phuchienngo.marblemarvelous.utils.ShaderUtils
import com.phuchienngo.marblemarvelous.utils.Size

class Glow {
    private val mesh: Mesh
    private var idx = 0
    private val glowShader: ShaderProgram =
        ShaderUtils.load(
            ShaderUtils.loadVertexShader("glsl/base/base"),
            ShaderUtils.loadFragmentShader("marble/glow"),
        )
    private val fboSize = Size(64.0f, 64.0f)
    private var ar = 0.5625f
    private val fboRead = SingleFloatFrameBuffer(fboSize.getWidth().toInt(), fboSize.getHeight().toInt(), false)
    private val fboWrite = SingleFloatFrameBuffer(fboSize.getWidth().toInt(), fboSize.getHeight().toInt(), false)
    private var mainFbo: SingleFloatFrameBuffer = fboRead

    init {
        val cameraFragment = OrthographicCamera(1.0f, 1.0f)
        glowShader.bind()
        glowShader.setUniformMatrix("u_viewTrans", cameraFragment.view)
        glowShader.setUniformMatrix("u_projTrans", cameraFragment.projection)
        mesh = PlaneConstructor.generatePlane(1.0f, 1.0f, 0.0f, 0.0f, 1, 1, true)
    }

    fun generateGlow(
        texture: Texture,
        numTimes: Int,
    ): Texture {
        idx = 0
        var target = fboWrite
        var i = 0
        while (i < numTimes * 2) {
            val source = mainFbo
            target = if (source === fboWrite) fboRead else fboWrite
            begin(target)
            render(if (i == 0) texture else source.colorBufferTexture)
            end(target)
            mainFbo = target
            idx = (idx + 1) % 2
            i++
        }
        return if (numTimes < 1) texture else target.colorBufferTexture
    }

    private fun begin(fbo: FrameBuffer) {
        fbo.begin()
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        Gdx.gl.glClear(16384)
        Gdx.gl.glEnable(GL20.GL_DITHER)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        glowShader.bind()
    }

    private fun render(texture: Texture) {
        texture.bind(0)
        glowShader.setUniformi("u_texture", 0)
        val offsetX = (if (ar < 1.0f) 1.0f else 1.0f / ar) / fboSize.getWidth()
        val offsetY = (if (ar < 1.0f) ar else 1.0f) / fboSize.getHeight()
        glowShader.setUniformf("u_offset", if (idx == 0) offsetX else 0.0f, if (idx != 0) offsetY else 0.0f)
        mesh.render(glowShader, 4)
    }

    private fun end(fbo: FrameBuffer) {
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_DITHER)
        fbo.end()
    }

    fun dispose() {
        mesh.dispose()
        glowShader.dispose()
        fboWrite.dispose()
        fboRead.dispose()
    }

    fun resize(
        width: Int,
        height: Int,
    ) {
        if (width <= 0 || height <= 0) {
            return
        }
        ar = GlowMath.aspectRatio(width, height)
    }
}
