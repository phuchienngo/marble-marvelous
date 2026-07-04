package com.phuchienngo.marblemarvelous.gl

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.utils.BufferUtils
import java.nio.IntBuffer

object FramebufferHints {
  private val COLOR_ATTACHMENT: IntBuffer =
    BufferUtils.newIntBuffer(1).apply {
      put(GL20.GL_COLOR_ATTACHMENT0)
      flip()
    }

  fun discardPreviousColorAttachment() {
    val gl30: GL30 = Gdx.gl30 ?: return
    COLOR_ATTACHMENT.position(0)
    gl30.glInvalidateFramebuffer(GL20.GL_FRAMEBUFFER, COLOR_ATTACHMENT.limit(), COLOR_ATTACHMENT)
  }
}
