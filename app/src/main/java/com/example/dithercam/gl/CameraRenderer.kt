package com.example.dithercam.gl

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "CameraRenderer"
        private const val MAX_COLORS = 8

        const val SHAPE_SQUARE = 0
        const val SHAPE_CIRCLE = 1
        const val SHAPE_PLUS = 2
        const val SHAPE_CROSS = 3
        const val SHAPE_DIAMOND = 4
        const val SHAPE_DOT = 5

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;

            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;

            uniform samplerExternalOES uTexture;
            uniform float uPixelSize;
            uniform float uDitherStrength;
            uniform vec3 uPalette[8];
            uniform int uPaletteSize;
            uniform vec2 uViewportSize;
            uniform int uMatrixSize;
            uniform int uShape;

            varying vec2 vTexCoord;

            float bayer2Index(vec2 p) {
                float x = mod(floor(p.x), 2.0);
                float y = mod(floor(p.y), 2.0);

                if (y < 0.5) {
                    if (x < 0.5) return 0.0;
                    return 2.0;
                }

                if (x < 0.5) return 3.0;
                return 1.0;
            }

            float bayer4Index(vec2 p) {
                vec2 local = mod(floor(p), 2.0);
                vec2 coarse = floor(mod(floor(p), 4.0) / 2.0);
                return 4.0 * bayer2Index(local) + bayer2Index(coarse);
            }

            float bayer8Index(vec2 p) {
                vec2 local4 = mod(floor(p), 4.0);
                vec2 coarse2 = floor(mod(floor(p), 8.0) / 4.0);
                return 4.0 * bayer4Index(local4) + bayer2Index(coarse2);
            }

            float ditherThreshold(vec2 logicalPixel) {
                if (uMatrixSize <= 2) {
                    return bayer2Index(logicalPixel) / 4.0;
                }

                if (uMatrixSize >= 8) {
                    return bayer8Index(logicalPixel) / 64.0;
                }

                return bayer4Index(logicalPixel) / 16.0;
            }

            vec3 paletteAt(int index) {
                if (index == 0) return uPalette[0];
                if (index == 1) return uPalette[1];
                if (index == 2) return uPalette[2];
                if (index == 3) return uPalette[3];
                if (index == 4) return uPalette[4];
                if (index == 5) return uPalette[5];
                if (index == 6) return uPalette[6];
                return uPalette[7];
            }

            float shapeMask(vec2 cell) {
                vec2 p = cell - vec2(0.5);
                vec2 a = abs(p);

                if (uShape == 1) {
                    // Circle
                    return step(length(p), 0.46);
                }

                if (uShape == 2) {
                    // Plus
                    float vertical = step(a.x, 0.15) * step(a.y, 0.46);
                    float horizontal = step(a.y, 0.15) * step(a.x, 0.46);
                    return max(vertical, horizontal);
                }

                if (uShape == 3) {
                    // X / cross
                    float d1 = abs(p.x - p.y);
                    float d2 = abs(p.x + p.y);
                    return max(step(d1, 0.13), step(d2, 0.13));
                }

                if (uShape == 4) {
                    // Diamond
                    return step(a.x + a.y, 0.48);
                }

                if (uShape == 5) {
                    // Small dot
                    return step(length(p), 0.27);
                }

                // Square = exact original behavior.
                return 1.0;
            }

            void main() {
                vec2 baseResolution;

                if (uViewportSize.x > uViewportSize.y) {
                    baseResolution = vec2(320.0, 240.0);
                } else {
                    baseResolution = vec2(240.0, 320.0);
                }

                vec2 logicalResolution;

                if (uPixelSize < 0.30) {
                    logicalResolution = baseResolution * 2.0;
                } else if (uPixelSize < 0.40) {
                    logicalResolution = baseResolution * 1.5;
                } else if (uPixelSize < 0.75) {
                    logicalResolution = baseResolution * 1.25;
                } else if (uPixelSize < 1.5) {
                    logicalResolution = baseResolution;
                } else if (uPixelSize < 2.5) {
                    logicalResolution = baseResolution * 0.90;
                } else if (uPixelSize < 3.5) {
                    logicalResolution = baseResolution * 0.80;
                } else if (uPixelSize < 4.5) {
                    logicalResolution = baseResolution * 0.70;
                } else if (uPixelSize < 5.5) {
                    logicalResolution = baseResolution * 0.60;
                } else if (uPixelSize < 6.5) {
                    logicalResolution = baseResolution * 0.50;
                } else if (uPixelSize < 7.5) {
                    logicalResolution = baseResolution * 0.45;
                } else {
                    logicalResolution = baseResolution * 0.40;
                }

                logicalResolution = floor(logicalResolution);

                vec2 logicalCoord = vTexCoord * logicalResolution;
                vec2 logicalPixel = floor(logicalCoord);
                vec2 cell = fract(logicalCoord);

                vec2 sampleUV =
                    (logicalPixel + vec2(0.5)) /
                    logicalResolution;

                vec4 camera = texture2D(uTexture, sampleUV);

                float gray = dot(
                    camera.rgb,
                    vec3(0.299, 0.587, 0.114)
                );

                float levels = float(uPaletteSize - 1);
                float scaled = clamp(gray, 0.0, 1.0) * levels;
                float lowerFloat = floor(scaled);
                float fraction = fract(scaled);

                float threshold = ditherThreshold(logicalPixel);
                threshold = mix(0.5, threshold, uDitherStrength);

                int lower = int(lowerFloat);
                int upper = lower + 1;

                if (upper >= uPaletteSize) {
                    upper = uPaletteSize - 1;
                }

                int chosen = lower;
                if (fraction > threshold) {
                    chosen = upper;
                }

                vec3 finalColor = paletteAt(chosen);

                if (uShape != 0 && chosen > lower) {
                    vec3 baseColor = paletteAt(lower);
                    float mask = shapeMask(cell);
                    finalColor = mix(baseColor, finalColor, mask);
                }

                gl_FragColor = vec4(finalColor, 1.0);
            }
        """
    }

    private val vertices = floatArrayOf(
        -1f, -1f,
         1f, -1f,
        -1f,  1f,
         1f,  1f
    )

    // Known-good orientation. Do not manually flip Y.
    private val textureCoordinates = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )

    private val vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

    private val textureBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(textureCoordinates.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(textureCoordinates)
                position(0)
            }

    private val textureMatrix = FloatArray(16)

    private var program = 0
    private var cameraTextureId = 0

    private var positionHandle = -1
    private var texCoordHandle = -1
    private var texMatrixHandle = -1
    private var textureHandle = -1
    private var pixelSizeHandle = -1
    private var ditherStrengthHandle = -1
    private var paletteHandle = -1
    private var paletteSizeHandle = -1
    private var viewportSizeHandle = -1
    private var matrixSizeHandle = -1
    private var shapeHandle = -1

    private var surfaceTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null

    private var viewportWidth = 0
    private var viewportHeight = 0

    @Volatile
    private var ready = false

    @Volatile
    private var pixelSize = 2f

    @Volatile
    private var ditherStrength = 1f

    @Volatile
    private var matrixSize = 4

    @Volatile
    private var shape = SHAPE_SQUARE

    @Volatile
    private var palette = floatArrayOf(
        0f, 0f, 0f,
        1f, 1f, 1f
    )

    @Volatile
    private var paletteSize = 2

    @Volatile
    private var captureRequested = false

    @Volatile
    private var captureCallback: ((Bitmap) -> Unit)? = null

    private var onReadyListener: (() -> Unit)? = null

    fun setOnReadyListener(listener: () -> Unit) {
        onReadyListener = listener
        if (ready) listener()
    }

    fun setPixelSize(value: Float) {
        pixelSize = value.coerceIn(0.25f, 8f)
    }

    fun setDitherStrength(value: Float) {
        ditherStrength = value.coerceIn(0f, 1f)
    }

    fun setDitherMatrix(value: Int) {
        matrixSize = when (value) {
            2 -> 2
            8 -> 8
            else -> 4
        }
    }

    fun setDitherShape(value: Int) {
        shape = value.coerceIn(SHAPE_SQUARE, SHAPE_DOT)
    }

    fun setPalette(hexColors: List<String>): Boolean {
        if (hexColors.size !in 2..MAX_COLORS) return false

        val values = FloatArray(hexColors.size * 3)

        hexColors.forEachIndexed { index, hex ->
            val color = parseHexColor(hex) ?: return false
            values[index * 3] = Color.red(color) / 255f
            values[index * 3 + 1] = Color.green(color) / 255f
            values[index * 3 + 2] = Color.blue(color) / 255f
        }

        palette = values
        paletteSize = hexColors.size
        return true
    }

    fun requestCapture(callback: (Bitmap) -> Unit) {
        captureCallback = callback
        captureRequested = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        pixelSizeHandle = GLES20.glGetUniformLocation(program, "uPixelSize")
        ditherStrengthHandle = GLES20.glGetUniformLocation(program, "uDitherStrength")
        paletteHandle = GLES20.glGetUniformLocation(program, "uPalette[0]")
        paletteSizeHandle = GLES20.glGetUniformLocation(program, "uPaletteSize")
        viewportSizeHandle = GLES20.glGetUniformLocation(program, "uViewportSize")
        matrixSizeHandle = GLES20.glGetUniformLocation(program, "uMatrixSize")
        shapeHandle = GLES20.glGetUniformLocation(program, "uShape")

        cameraTextureId = createExternalTexture()

        surfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setOnFrameAvailableListener {
                // Continuous rendering is enabled.
            }
        }

        cameraSurface = Surface(surfaceTexture)
        ready = true
        Log.d(TAG, "Camera GL ready")
        onReadyListener?.invoke()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val texture = surfaceTexture ?: return

        try {
            texture.updateTexImage()
            texture.getTransformMatrix(textureMatrix)
        } catch (e: RuntimeException) {
            Log.e(TAG, "updateTexImage failed", e)
            return
        }

        GLES20.glUseProgram(program)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        textureBuffer.position(0)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(
            texCoordHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            textureBuffer
        )

        GLES20.glUniformMatrix4fv(
            texMatrixHandle,
            1,
            false,
            textureMatrix,
            0
        )

        GLES20.glUniform1f(pixelSizeHandle, pixelSize)
        GLES20.glUniform1f(ditherStrengthHandle, ditherStrength)
        GLES20.glUniform2f(
            viewportSizeHandle,
            viewportWidth.toFloat(),
            viewportHeight.toFloat()
        )
        GLES20.glUniform1i(matrixSizeHandle, matrixSize)
        GLES20.glUniform1i(shapeHandle, shape)

        val currentPalette = palette
        GLES20.glUniform3fv(
            paletteHandle,
            paletteSize,
            currentPalette,
            0
        )
        GLES20.glUniform1i(paletteSizeHandle, paletteSize)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            cameraTextureId
        )
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(
            GLES20.GL_TRIANGLE_STRIP,
            0,
            4
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        if (captureRequested) {
            captureRequested = false
            val callback = captureCallback
            captureCallback = null

            if (callback != null && viewportWidth > 0 && viewportHeight > 0) {
                try {
                    callback(readCurrentFrame())
                } catch (e: Exception) {
                    Log.e(TAG, "Processed capture failed", e)
                }
            }
        }
    }

    fun setCameraBufferSize(width: Int, height: Int) {
        surfaceTexture?.setDefaultBufferSize(width, height)
        Log.d(TAG, "Camera buffer configured: ${width}x${height}")
    }

    fun getCameraSurface(): Surface {
        return requireNotNull(cameraSurface) {
            "Camera surface is not ready"
        }
    }

    fun isReady(): Boolean = ready

    fun release() {
        ready = false

        cameraSurface?.release()
        cameraSurface = null

        surfaceTexture?.release()
        surfaceTexture = null

        if (cameraTextureId != 0) {
            GLES20.glDeleteTextures(
                1,
                intArrayOf(cameraTextureId),
                0
            )
            cameraTextureId = 0
        }

        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun readCurrentFrame(): Bitmap {
        val width = viewportWidth
        val height = viewportHeight
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
        buffer.order(ByteOrder.nativeOrder())

        GLES20.glReadPixels(
            0,
            0,
            width,
            height,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )

        buffer.rewind()
        val source = ByteArray(width * height * 4)
        buffer.get(source)

        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val sourceY = height - 1 - y

            for (x in 0 until width) {
                val src = (sourceY * width + x) * 4
                val r = source[src].toInt() and 0xFF
                val g = source[src + 1].toInt() and 0xFF
                val b = source[src + 2].toInt() and 0xFF
                val a = source[src + 3].toInt() and 0xFF

                pixels[y * width + x] =
                    (a shl 24) or
                    (r shl 16) or
                    (g shl 8) or
                    b
            }
        }

        return Bitmap.createBitmap(
            pixels,
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun parseHexColor(value: String): Int? {
        return try {
            var hex = value.trim().uppercase()
            if (!hex.startsWith("#")) hex = "#$hex"

            if (!Regex("^#[0-9A-F]{6}$").matches(hex)) {
                return null
            }

            Color.parseColor(hex)
        } catch (_: Exception) {
            null
        }
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        GLES20.glBindTexture(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            textures[0]
        )

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        return textures[0]
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String
    ): Int {
        val vertexShader = compileShader(
            GLES20.GL_VERTEX_SHADER,
            vertexSource
        )
        val fragmentShader = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            fragmentSource
        )

        val createdProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(createdProgram, vertexShader)
        GLES20.glAttachShader(createdProgram, fragmentShader)
        GLES20.glLinkProgram(createdProgram)

        val status = IntArray(1)
        GLES20.glGetProgramiv(
            createdProgram,
            GLES20.GL_LINK_STATUS,
            status,
            0
        )

        if (status[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(createdProgram)
            GLES20.glDeleteProgram(createdProgram)
            throw RuntimeException("Program link failed: $info")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return createdProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(
            shader,
            GLES20.GL_COMPILE_STATUS,
            status,
            0
        )

        if (status[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $info")
        }

        return shader
    }
}
