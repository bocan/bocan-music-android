package io.cloudcauldron.bocan.app.player

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.cloudcauldron.bocan.playback.audio.WaveformSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tanh

/**
 * A permanently running oscilloscope for the Now Playing screen: it renders the live output
 * waveform (tapped off the audio thread by [WaveformSource]) as a thin ribbon whose hue
 * scrolls a full rainbow every twenty seconds. All rendering runs on the GLSurfaceView's own
 * GL thread, so nothing here touches the main thread. It is purely decorative: no controls,
 * no overlays, transparent so the ambient background shows through. The GL thread pauses with
 * the screen's lifecycle and when the composable leaves the tree, so it never renders unseen.
 */
@Composable
fun Oscilloscope(source: WaveformSource, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val glView = remember(source) { OscilloscopeSurfaceView(context, source) }
    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
        }
    }
    AndroidView(factory = { glView }, modifier = modifier.clearAndSetSemantics {})
}

/**
 * A transparent [GLSurfaceView] hosting the [OscilloscopeRenderer]. An RGBA config, a
 * translucent holder, and top z-order give it real per-pixel alpha, so the cleared
 * background is transparent and the ambient gradient shows through the trace. It only ever
 * renders on the bare Now Playing view (the caller drops it while a sheet is open), so
 * drawing on top is safe.
 */
private class OscilloscopeSurfaceView(context: Context, source: WaveformSource) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(RGBA_BITS, RGBA_BITS, RGBA_BITS, RGBA_BITS, DEPTH_BITS, STENCIL_BITS)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(OscilloscopeRenderer(source))
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private companion object {
        const val RGBA_BITS = 8
        const val DEPTH_BITS = 0
        const val STENCIL_BITS = 0
    }
}

/**
 * Builds the ribbon geometry on the GL thread each frame from the latest waveform snapshot
 * and draws it as a triangle strip, two vertices per point offset vertically for thickness.
 * The rainbow hue is a per-vertex value cycled by a time uniform in the fragment shader.
 */
private class OscilloscopeRenderer(private val source: WaveformSource) : GLSurfaceView.Renderer {
    private val points = source.pointCount
    private val wave = FloatArray(points)
    private val vertices = FloatArray(points * VERTS_PER_POINT * FLOATS_PER_VERT)
    private val vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(vertices.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private var program = 0
    private var positionHandle = 0
    private var hueHandle = 0
    private var timeHandle = 0
    private var halfThicknessNdc = DEFAULT_HALF_THICKNESS
    private var startNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = buildProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "aPos")
        hueHandle = GLES20.glGetAttribLocation(program, "aHue")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        startNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        // Keep the ribbon a constant pixel thickness regardless of the strip height.
        halfThicknessNdc = if (height > 0) LINE_THICKNESS_PX / height else DEFAULT_HALF_THICKNESS
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (!source.copyInto(wave)) wave.fill(0f)
        fillVertices()
        vertexBuffer.clear()
        vertexBuffer.put(vertices).position(0)

        GLES20.glUseProgram(program)
        val elapsed = (System.nanoTime() - startNanos) / NANOS_PER_SECOND
        GLES20.glUniform1f(timeHandle, elapsed)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, POSITION_SIZE, GLES20.GL_FLOAT, false, STRIDE, vertexBuffer)
        vertexBuffer.position(POSITION_SIZE)
        GLES20.glEnableVertexAttribArray(hueHandle)
        GLES20.glVertexAttribPointer(hueHandle, HUE_SIZE, GLES20.GL_FLOAT, false, STRIDE, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, points * VERTS_PER_POINT)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(hueHandle)
    }

    private fun fillVertices() {
        var v = 0
        var i = 0
        while (i < points) {
            val x = -1f + 2f * i / (points - 1)
            // Drive the amplitude for a bold trace, then let tanh roll off smoothly near the
            // edges so loud passages fill the strip without hard-clipping into flat tops.
            val y = tanh(wave[i] * DRIVE) * Y_SCALE
            val hue = i.toFloat() / (points - 1)
            vertices[v++] = x
            vertices[v++] = y - halfThicknessNdc
            vertices[v++] = hue
            vertices[v++] = x
            vertices[v++] = y + halfThicknessNdc
            vertices[v++] = hue
            i++
        }
    }

    private fun buildProgram(): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        return GLES20.glCreateProgram().also { handle ->
            GLES20.glAttachShader(handle, vertex)
            GLES20.glAttachShader(handle, fragment)
            GLES20.glLinkProgram(handle)
        }
    }

    private fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
    }

    private companion object {
        const val VERTS_PER_POINT = 2
        const val FLOATS_PER_VERT = 3
        const val POSITION_SIZE = 2
        const val HUE_SIZE = 1
        const val BYTES_PER_FLOAT = 4
        const val STRIDE = FLOATS_PER_VERT * BYTES_PER_FLOAT
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val LINE_THICKNESS_PX = 2.5f
        const val DEFAULT_HALF_THICKNESS = 0.02f
        const val Y_SCALE = 0.92f
        const val DRIVE = 3.2f

        val VERTEX_SHADER = """
            attribute vec2 aPos;
            attribute float aHue;
            varying float vHue;
            void main() {
                vHue = aHue;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """.trimIndent()

        // Full rainbow across the trace, scrolling one complete cycle every 20 seconds.
        val FRAGMENT_SHADER = """
            precision mediump float;
            varying float vHue;
            uniform float uTime;
            vec3 hsv2rgb(vec3 c) {
                vec4 k = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + k.xyz) * 6.0 - k.www);
                return c.z * mix(k.xxx, clamp(p - k.xxx, 0.0, 1.0), c.y);
            }
            void main() {
                float hue = fract(vHue + uTime / 20.0);
                gl_FragColor = vec4(hsv2rgb(vec3(hue, 0.9, 1.0)), 1.0);
            }
        """.trimIndent()
    }
}
