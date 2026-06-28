package com.phuchienngo.marblemarvelous.celestialBodies.shaders

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxRuntimeException
import com.phuchienngo.marblemarvelous.celestialBodies.shaders.attributes.PlanetTextureAttribute

class MoonShader : Shader {
    private lateinit var program: ShaderProgram
    private lateinit var mNormalMatrix: Matrix4
    private lateinit var mModelViewMatrix: Matrix4
    private lateinit var matrix4: Matrix4
    private lateinit var mLightDirection: Vector3
    private lateinit var mCamera: Camera
    private lateinit var mRenderContext: RenderContext

    override fun init() {
        program = ShaderProgram(Gdx.files.internal("moon/moon.vert"), Gdx.files.internal("moon/moon.frag"))
        mNormalMatrix = Matrix4()
        mModelViewMatrix = Matrix4()
        matrix4 = Matrix4()
        mLightDirection = Vector3()
        if (!program.isCompiled) throw GdxRuntimeException(program.log)
    }

    override fun compareTo(other: Shader): Int = 0

    override fun canRender(renderable: Renderable): Boolean = true

    override fun begin(
        camera: Camera,
        renderContext: RenderContext,
    ) {
        mCamera = camera
        mRenderContext = renderContext
        program.bind()
        program.setUniformMatrix("u_projectionViewTransform", camera.combined)
        program.setUniformMatrix("u_viewTransform", matrix4.set(camera.view).inv())
        renderContext.setDepthTest(GL20.GL_LEQUAL)
        renderContext.setCullFace(GL20.GL_BACK)
        renderContext.setBlending(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun render(renderable: Renderable) {
        val day = renderable.material.get(PlanetTextureAttribute.DayDiffuse) as PlanetTextureAttribute
        val normals = renderable.material.get(PlanetTextureAttribute.DayNormals) as PlanetTextureAttribute
        mNormalMatrix.set(mCamera.view).mul(renderable.worldTransform)
        if (mNormalMatrix.det() == 0.0f) return
        mNormalMatrix.inv().tra()
        mModelViewMatrix.set(mCamera.view).mul(renderable.worldTransform)
        val attr = renderable.environment.get(PointLightsAttribute.Type) as PointLightsAttribute
        val sunLight = attr.lights.first()
        mLightDirection.set(sunLight.position).nor()
        program.setUniformi("u_diffuseMap", mRenderContext.textureBinder.bind(day.textureDescription))
        program.setUniformi("u_normalMap", mRenderContext.textureBinder.bind(normals.textureDescription))
        program.setUniformf("u_lightDirection", mLightDirection)
        program.setUniformf("u_intensity", sunLight.intensity)
        program.setUniformMatrix("u_modelWorldTransform", renderable.worldTransform)
        renderable.meshPart.render(program)
    }

    override fun end() {
    }

    override fun dispose() {
        program.dispose()
    }
}
