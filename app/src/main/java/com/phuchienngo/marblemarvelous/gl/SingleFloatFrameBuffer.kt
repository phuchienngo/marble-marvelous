package com.phuchienngo.marblemarvelous.gl

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer

// Single-channel 32-bit float (R32F) framebuffer. libGDX 1.14 reworked FrameBuffer to a
// builder API, so the old custom createColorTexture/SingleFloatTextureData is replaced by
// a GLFrameBufferBuilder float attachment.
class SingleFloatFrameBuffer(width: Int, height: Int, hasDepth: Boolean) :
    FrameBuffer(buildSpec(width, height)) {

    init {
        colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        colorBufferTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
    }

    companion object {
        private fun buildSpec(w: Int, h: Int): GLFrameBuffer.FrameBufferBuilder {
            val builder = GLFrameBuffer.FrameBufferBuilder(w, h)
            builder.addFloatAttachment(GL30.GL_R32F, GL30.GL_RED, GL20.GL_FLOAT, true)
            return builder
        }
    }
}
