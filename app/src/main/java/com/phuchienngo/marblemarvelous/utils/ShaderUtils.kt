package com.phuchienngo.marblemarvelous.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.GdxRuntimeException
import java.util.regex.Matcher
import java.util.regex.Pattern

object ShaderUtils {
    const val METHOD_INCLUDE_REGEX: String = "#include\\s+(<([^>]+)>|\"([^\"]+)\")"
    const val METHOD_REGEX: String = "#include\\s+SHADER_METHODS"

    @JvmStatic
    fun loadShaderWithMethods(
        shaderPath: String,
        vararg methodsPaths: String
    ): String {
        val methodsToAdd: StringBuilder = StringBuilder()
        for (methodPath in methodsPaths) {
            methodsToAdd.append("\n")
            methodsToAdd.append(loadShader(methodPath))
            methodsToAdd.append("\n")
        }
        val shaderContent: String = replaceShaderMethods(loadShader(shaderPath), methodsToAdd.toString())
        Console.log("ShaderUtil", shaderContent)
        return shaderContent
    }

    @JvmStatic
    fun loadVertexShader(basePath: String): String = loadShader("$basePath.vert")

    @JvmStatic
    fun loadFragmentShader(basePath: String): String = loadShader("$basePath.frag")

    @JvmStatic
    fun loadGLSLCode(basePath: String): String = loadShader("$basePath.glsl")

    @JvmStatic
    fun loadShader(shaderPath: String): String =
        try {
            Gdx.files.internal(shaderPath).readString()
        } catch (exception: GdxRuntimeException) {
            exception.toString()
        }

    @JvmStatic
    fun load(basePath: String): ShaderProgram = load(loadVertexShader(basePath), loadFragmentShader(basePath))

    @JvmStatic
    fun load(
        vertexShaderContent: String,
        fragmentShaderContent: String
    ): ShaderProgram {
        val shader: ShaderProgram =
            ShaderProgram(
                replaceIncludeFiles(vertexShaderContent),
                replaceIncludeFiles(fragmentShaderContent)
            )
        if (!shader.isCompiled) {
            throw RuntimeException(shader.log)
        }
        return shader
    }

    @JvmStatic
    fun replaceIncludeFiles(shaderContent: String): String {
        val pattern: Pattern = Pattern.compile(METHOD_INCLUDE_REGEX)
        val matcher: Matcher = pattern.matcher(shaderContent)
        val sb: StringBuffer = StringBuffer(shaderContent.length)
        while (matcher.find()) {
            val angleBrackets: String? = matcher.group(2)
            val quotationMarks: String? = matcher.group(3)
            val file: String = angleBrackets ?: (quotationMarks ?: "")
            matcher.appendReplacement(sb, Matcher.quoteReplacement(loadShader(file)))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    @JvmStatic
    fun replaceShaderMethods(
        shaderContent: String,
        shaderFunctionsContent: String
    ): String {
        val pattern: Pattern = Pattern.compile(METHOD_REGEX)
        val matcher: Matcher = pattern.matcher(shaderContent)
        val sb: StringBuffer = StringBuffer(shaderContent.length)
        while (matcher.find()) {
            matcher.group()
            matcher.appendReplacement(sb, Matcher.quoteReplacement(shaderFunctionsContent))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }
}
