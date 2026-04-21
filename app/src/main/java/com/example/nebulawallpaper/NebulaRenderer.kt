package com.example.nebulawallpaper

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES30
import android.view.SurfaceHolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class NebulaRenderer(private val context: Context, private var holder: SurfaceHolder) {

    private val SAVE_FILE_NAME = "nebula_save.bin"

    // Settings
    @Volatile private var cellSize = 5
    @Volatile private var targetFps = 30
    @Volatile private var keepaliveEnabled = true
    @Volatile private var keepaliveThreshold = 0.9999f
    @Volatile private var ruleBirthMask = 8
    @Volatile private var ruleSurvivalMask = 12

    @Volatile private var running = false
    private var renderThread: Thread? = null
    @Volatile private var width = 0
    @Volatile private var height = 0

    @Volatile private var touchX = -1f
    @Volatile private var touchY = -1f
    @Volatile private var isTouching = false
    @Volatile private var requestResetRandom = false
    @Volatile private var requestResetClear = false

    fun onSurfaceChanged(w: Int, h: Int) { width = w; height = h; refreshSettings() }

    fun refreshSettings() {
        cellSize = max(1, NebulaPreferences.getCellSize(context))
        targetFps = max(0, NebulaPreferences.getFps(context))

        keepaliveEnabled = NebulaPreferences.isKeepalive(context)
        keepaliveThreshold = NebulaPreferences.getKeepaliveThreshold(context)

        val masks = NebulaPreferences.getRuleBitmasks(context)
        ruleBirthMask = masks[0]; ruleSurvivalMask = masks[1]
    }

    fun start() {
        if (running) return
        running = true
        refreshSettings()
        renderThread = Thread { renderLoop() }.apply { start() }
    }

    fun stop() {
        running = false
        try { renderThread?.join() } catch (e: Exception) {}
        renderThread = null
    }

    fun setTouch(x: Float, y: Float) { touchX = x; touchY = y; isTouching = true }
    fun requestResetRandom() { requestResetRandom = true }
    fun requestResetClear() { requestResetClear = true }

    private fun renderLoop() {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2); EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 5, EGL14.EGL_GREEN_SIZE, 6, EGL14.EGL_BLUE_SIZE, 5, EGL14.EGL_ALPHA_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, 64, EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1); val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        val eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], holder.surface, surfaceAttribs, 0)

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return

        val quadVertices = floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)
        val fullScreenQuad = ByteBuffer.allocateDirect(quadVertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fullScreenQuad.put(quadVertices).position(0)

        val programUpdate = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_UPDATE)
        val programDisplay = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_DISPLAY)

        val uFrameLoc = GLES30.glGetUniformLocation(programUpdate, "iFrame")
        val uMouseLoc = GLES30.glGetUniformLocation(programUpdate, "iMouse")
        val uResetLoc = GLES30.glGetUniformLocation(programUpdate, "uReset")
        val uTexLoc = GLES30.glGetUniformLocation(programUpdate, "iChannel0")
        val uResLoc = GLES30.glGetUniformLocation(programUpdate, "iResolution")
        val uKeepEnabledLoc = GLES30.glGetUniformLocation(programUpdate, "uKeepaliveEnabled")
        val uKeepThresholdLoc = GLES30.glGetUniformLocation(programUpdate, "uKeepaliveThreshold")
        val uBirthMaskLoc = GLES30.glGetUniformLocation(programUpdate, "uBirthMask")
        val uSurviveMaskLoc = GLES30.glGetUniformLocation(programUpdate, "uSurviveMask")

        val uTexDispLoc = GLES30.glGetUniformLocation(programDisplay, "iChannel0")

        val textures = IntArray(2); GLES30.glGenTextures(2, textures, 0)
        val fbo = IntArray(1); GLES30.glGenFramebuffers(1, fbo, 0)

        var frameCount = 0; var simWidth = 0; var simHeight = 0; var currentReadTex = 0
        val mouseArr = FloatArray(4)

        while (running) {
            val loopStart = System.currentTimeMillis()

            val w = width; val h = height
            val targetSimW = max(1, w / cellSize)
            val targetSimH = max(1, h / cellSize)

            if (targetSimW != simWidth || targetSimH != simHeight) {
                simWidth = targetSimW; simHeight = targetSimH
                setupTexture(textures[0], simWidth, simHeight)
                setupTexture(textures[1], simWidth, simHeight)
                if (loadState(textures[0], simWidth, simHeight)) {
                    currentReadTex = 0; frameCount = 1; requestResetRandom = false
                } else {
                    frameCount = 0; requestResetRandom = true
                }
            }

            var resetMode = 0
            if (requestResetRandom) { resetMode = 1; requestResetRandom = false; frameCount = 0 }
            if (requestResetClear) { resetMode = 2; requestResetClear = false }

            val scale = 1.0f / cellSize
            mouseArr[0] = touchX * scale; mouseArr[1] = (height - touchY) * scale
            mouseArr[2] = if (isTouching) 1.0f else 0.0f; mouseArr[3] = 0f
            isTouching = false

            // PASS 1: Update
            val writeTexIndex = 1 - currentReadTex
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, textures[writeTexIndex], 0)
            GLES30.glViewport(0, 0, simWidth, simHeight)

            GLES30.glUseProgram(programUpdate)
            fullScreenQuad.position(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, fullScreenQuad); GLES30.glEnableVertexAttribArray(0)
            fullScreenQuad.position(2); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, fullScreenQuad); GLES30.glEnableVertexAttribArray(1)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[currentReadTex])

            GLES30.glUniform1i(uTexLoc, 0)
            GLES30.glUniform3f(uResLoc, simWidth.toFloat(), simHeight.toFloat(), 1.0f)
            GLES30.glUniform1i(uFrameLoc, frameCount)
            GLES30.glUniform4fv(uMouseLoc, 1, mouseArr, 0)
            GLES30.glUniform1i(uResetLoc, resetMode)
            GLES30.glUniform1i(uKeepEnabledLoc, if (keepaliveEnabled) 1 else 0)
            GLES30.glUniform1f(uKeepThresholdLoc, keepaliveThreshold)
            GLES30.glUniform1i(uBirthMaskLoc, ruleBirthMask)
            GLES30.glUniform1i(uSurviveMaskLoc, ruleSurvivalMask)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

            // PASS 2: Display
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, width, height)

            GLES30.glUseProgram(programDisplay)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[writeTexIndex])
            GLES30.glUniform1i(uTexDispLoc, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            currentReadTex = writeTexIndex
            frameCount++

            // Unlimited FPS Support
            if (targetFps > 0) {
                val elapsed = System.currentTimeMillis() - loopStart
                val sleepTime = (1000L / targetFps) - elapsed
                if (sleepTime > 0) try { Thread.sleep(sleepTime) } catch (e: Exception) {}
            }
        }

        if (simWidth > 0) saveState(fbo[0], textures[currentReadTex], simWidth, simHeight)

        GLES30.glDeleteTextures(2, textures, 0)
        GLES30.glDeleteFramebuffers(1, fbo, 0)
        GLES30.glDeleteProgram(programUpdate)
        GLES30.glDeleteProgram(programDisplay)
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }

    private fun saveState(fboId: Int, texId: Int, w: Int, h: Int) {
        try {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texId, 0)
            val buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            val file = File(context.filesDir, SAVE_FILE_NAME)
            FileOutputStream(file).use { fos ->
                val header = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
                header.putInt(w).putInt(h)
                fos.write(header.array())
                val bytes = ByteArray(w * h * 4)
                buffer.position(0); buffer.get(bytes)
                fos.write(bytes)
            }
        } catch (e: Exception) { }
    }

    private fun loadState(targetTexId: Int, currentW: Int, currentH: Int): Boolean {
        val file = File(context.filesDir, SAVE_FILE_NAME)
        if (!file.exists()) return false
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(8); if (fis.read(header) != 8) return false
                val bb = ByteBuffer.wrap(header).order(ByteOrder.nativeOrder())
                val w = bb.int; val h = bb.int
                if (w != currentW || h != currentH) return false
                val dataSize = w * h * 4
                val dataBytes = ByteArray(dataSize); if (fis.read(dataBytes) != dataSize) return false
                val buffer = ByteBuffer.allocateDirect(dataSize).order(ByteOrder.nativeOrder())
                buffer.put(dataBytes).position(0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, targetTexId)
                GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
                return true
            }
        } catch (e: Exception) { return false }
    }

    private fun setupTexture(texId: Int, w: Int, h: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun createProgram(vs: String, fs: String): Int {
        val v = loadShader(GLES30.GL_VERTEX_SHADER, vs)
        val f = loadShader(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f); GLES30.glLinkProgram(p)
        return p
    }

    private fun loadShader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type); GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        return s
    }

    private val VERTEX_SHADER = """#version 300 es
        layout(location = 0) in vec4 aPos;
        layout(location = 1) in vec2 aTexCoord;
        out highp vec2 uv;
        void main() { gl_Position = aPos; uv = aTexCoord; }
    """.trimIndent()

    private val FRAGMENT_SHADER_UPDATE = """#version 300 es
        precision mediump float; precision highp int;
        uniform sampler2D iChannel0; uniform highp vec3 iResolution;
        uniform int iFrame; uniform vec4 iMouse; uniform int uReset;
        uniform int uKeepaliveEnabled; uniform float uKeepaliveThreshold;
        uniform int uBirthMask; uniform int uSurviveMask;
        in highp vec2 uv; out vec4 fragColor;
        const uint k = 1103515245U;
        highp vec3 hash(uvec3 x) {
            x = ((x>>8U)^x.yzx)*k; x = ((x>>8U)^x.yzx)*k; x = ((x>>8U)^x.yzx)*k;
            return vec3(x)*(1.0/float(0xffffffffU));
        }
        void main() {
            if (uReset == 2) { fragColor = vec4(0.0); return; }
            if (uReset == 1) { 
                bool alive = hash(uvec3(gl_FragCoord.xy, uint(iFrame) + 100U)).x > 0.6;
                fragColor = vec4(uv, alive ? 1.0 : 0.0, 1.0); return; 
            }
            highp vec2 inRes = 1.0 / iResolution.xy;
            vec2 avg = vec2(0.0); int count = 0;
            for(int x = -1; x <= 1; x++) {
                for(int y = -1; y <= 1; y++) {
                    vec3 cur = texture(iChannel0, uv + vec2(float(x), float(y)) * inRes).xyz;
                    bool curAlive = cur.z > 0.5;
                    bool shouldAdd = curAlive && (x != 0 || y != 0);
                    avg += cur.xy * float(curAlive);
                    count += int(shouldAdd);
                }
            }
            vec4 me = texture(iChannel0, uv);
            bool alive = me.z > 0.5;
            float denom = float(count + int(count == 0 || alive));
            avg /= max(denom, 0.001); 
            int neighborBit = 1 << count;
            bool isBorn = (uBirthMask & neighborBit) != 0;
            bool isSurviving = (uSurviveMask & neighborBit) != 0;
            bool nextAlive = (alive && isSurviving) || (!alive && isBorn);
            bool force = false;
            if (uKeepaliveEnabled == 1) force = hash(uvec3(gl_FragCoord.xy, uint(iFrame))).x > uKeepaliveThreshold;
            bool mouseHit = (iMouse.z > 0.5) && (distance(gl_FragCoord.xy, iMouse.xy) < 2.5);
            bool finalAlive = nextAlive || force || mouseHit;
            avg = mix(avg, uv, float(force || mouseHit));
            bool first = iFrame == 0;
            bool randomStart = hash(uvec3(gl_FragCoord.xy, 10U)).x > 0.6;
            bool b = (first && randomStart) || (!first && finalAlive);
            fragColor = vec4(mix(me.xy, avg, float(b)), float(b), 1.0);
        }
    """.trimIndent()

    private val FRAGMENT_SHADER_DISPLAY = """#version 300 es
        precision mediump float; uniform sampler2D iChannel0; in highp vec2 uv; out vec4 fragColor;
        void main() {
            vec3 data = texture(iChannel0, uv).xyz;
            float x = data.x; float y = data.y; float z = data.z;
            float r = 0.2391336 + 0.8333856 * x - 0.498348 * y;
            float g = 0.2391336 - 0.0306144 * x + 0.509652 * y;
            float b = 0.3111336 - 0.0306144 * x + 1.013652 * y;
            fragColor = vec4(clamp(vec3(r, g, b) + 0.1 * vec3(z), 0.0, 1.0), 1.0);
        }
    """.trimIndent()
}