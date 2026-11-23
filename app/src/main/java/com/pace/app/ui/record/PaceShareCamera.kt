package com.pace.app.ui.record

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pace.app.R

@Composable
internal fun PaceShareCamera(distance: String, duration: String, onCapture: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val texture = remember { TextureView(context) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    DisposableEffect(texture, owner) {
        val camera = ShareCamera(texture) { success -> ready = success; error = !success }
        val lifecycle = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> camera.resume()
                Lifecycle.Event.ON_PAUSE -> { ready = false; camera.pause() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(lifecycle)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) camera.resume()
        onDispose {
            owner.lifecycle.removeObserver(lifecycle)
            camera.dispose()
        }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (error) Text(
                stringResource(R.string.pace_share_camera_failed), color = Color.White, modifier = Modifier.align(Alignment.Center),
            ) else AndroidView(factory = { texture }, modifier = Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xD9000010))
                    .padding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.asPaddingValues())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Pace", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("$distance  ·  $duration", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                com.pace.app.ui.components.PacePrimaryButton(
                    label = stringResource(R.string.pace_share_capture),
                    enabled = ready && !error,
                    onClick = {
                        val image = texture.bitmap
                        if (image == null) error = true else onCapture(image)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pace_cancel), color = Color.White)
                }
            }
        }
    }
}

private class ShareCamera(private val texture: TextureView, private val onReady: (Boolean) -> Unit) :
    TextureView.SurfaceTextureListener {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = texture.context.getSystemService(CameraManager::class.java)
    private var active = false
    private var generation = 0
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null

    fun resume() {
        if (active) return
        active = true
        texture.surfaceTextureListener = this
        texture.surfaceTexture?.let(::open)
    }

    fun pause() {
        active = false
        close()
    }

    fun dispose() {
        pause()
        texture.surfaceTextureListener = null
    }

    private fun close() {
        generation++
        session?.close()
        session = null
        device?.close()
        device = null
        surface?.release()
        surface = null
    }

    @SuppressLint("MissingPermission")
    private fun open(output: SurfaceTexture) {
        val request = ++generation
        runCatching {
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.first()
            val configuration = manager.getCameraCharacteristics(id)
            val sizes = configuration.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
            val size = sizes.filter { it.width <= 1920 && it.height <= 1080 }
                .minByOrNull { kotlin.math.abs(it.width - 1280) } ?: sizes.first()
            output.setDefaultBufferSize(size.width, size.height)
            val screenRotation = when (texture.display?.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val facing = configuration.get(CameraCharacteristics.LENS_FACING)
            val sensor = configuration.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val rotation = if (facing == CameraCharacteristics.LENS_FACING_FRONT)
                (sensor + screenRotation) % 360 else (sensor - screenRotation + 360) % 360
            transformPreview(size.width, size.height, rotation)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (!active || request != generation) { camera.close(); return }
                    device = camera
                    startPreview(camera, output, request)
                }

                override fun onDisconnected(camera: CameraDevice) = cameraFailed(camera, request)
                override fun onError(camera: CameraDevice, error: Int) = cameraFailed(camera, request)
            }, handler)
        }.onFailure { if (active && request == generation) onReady(false) }
    }

    private fun transformPreview(width: Int, height: Int, rotation: Int) {
        val viewWidth = texture.width.toFloat()
        val viewHeight = texture.height.toFloat()
        if (viewWidth == 0f || viewHeight == 0f) return
        val buffer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val rotate = Matrix().apply { setRotate(rotation.toFloat(), buffer.centerX(), buffer.centerY()) }
        val bounds = RectF(buffer).also { rotate.mapRect(it) }
        val scale = maxOf(viewWidth / bounds.width(), viewHeight / bounds.height())
        val matrix = Matrix().apply {
            setScale(width / viewWidth, height / viewHeight)
            postConcat(rotate)
            postScale(scale, scale, bounds.centerX(), bounds.centerY())
            postTranslate(viewWidth / 2 - bounds.centerX(), viewHeight / 2 - bounds.centerY())
        }
        texture.setTransform(matrix)
    }

    @Suppress("DEPRECATION")
    private fun startPreview(camera: CameraDevice, output: SurfaceTexture, request: Int) {
        val target = Surface(output)
        surface = target
        runCatching {
            camera.createCaptureSession(listOf(target), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (!active || request != generation) { configured.close(); return }
                    session = configured
                    runCatching {
                        val preview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(target)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        configured.setRepeatingRequest(preview.build(), null, handler)
                    }.onFailure { cameraFailed(camera, request) }
                }

                override fun onConfigureFailed(configured: CameraCaptureSession) {
                    configured.close()
                    cameraFailed(camera, request)
                }
            }, handler)
        }.onFailure { cameraFailed(camera, request) }
    }

    private fun cameraFailed(camera: CameraDevice, request: Int) {
        camera.close()
        if (active && request == generation) {
            close()
            onReady(false)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { if (active) open(surface) }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { close(); return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { if (active && session != null) onReady(true) }
}
