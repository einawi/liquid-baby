package com.liquid.liquidbaby

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.io.ByteArrayOutputStream

/**
 * Child unit: opens the camera and hands out JPEG pictures (about 7 to 11 per second).
 * Every picture starts with one byte: how many quarter turns the parent must rotate it.
 */
class CameraStreamer(
    private val ctx: Context,
    private val onFrame: (ByteArray) -> Unit,
    private val fail: (String) -> Unit,
    private val ready: () -> Unit
) {
    private val thread = HandlerThread("LiquidBaby-camera")
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var afModes: IntArray? = null

    @Volatile
    var hasFlash = false
        private set

    private var front = false
    private var quality = 1
    private var flash = false
    private var night = false
    private var exposureComp = 0
    private var fpsRanges: Array<android.util.Range<Int>>? = null
    private var rotation = 1
    private var lastFrame = 0L
    private var closed = false
    private var generation = 0

    /** Low battery: fewer pictures per second to save power. */
    @Volatile
    var slowMode = false

    fun start(front: Boolean, quality: Int, flash: Boolean, night: Boolean = false) {
        thread.start()
        handler = Handler(thread.looper)
        handler?.post {
            this.front = front
            this.quality = quality
            this.flash = flash
            this.night = night
            open()
        }
    }

    fun apply(front: Boolean, quality: Int, flash: Boolean, night: Boolean = false) {
        handler?.post {
            val reopen = front != this.front || quality != this.quality
            this.front = front
            this.quality = quality
            this.flash = flash
            this.night = night
            if (reopen) {
                closeCamera()
                open()
            } else {
                repeat()
            }
        }
    }

    fun stop() {
        handler?.post {
            closed = true
            closeCamera()
            thread.quitSafely()
        }
    }

    private fun findCamera(cm: CameraManager, wantFront: Boolean): String? {
        val want = if (wantFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        return cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want }
    }

    @SuppressLint("MissingPermission")
    private fun open() {
        if (closed) return
        val myGen = ++generation
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = findCamera(cm, front) ?: findCamera(cm, !front)
            if (id == null) {
                fail("No camera was found on the child phone")
                return
            }
            val ch = cm.getCameraCharacteristics(id)
            rotation = ((ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90) / 90) % 4
            hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            afModes = ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            fpsRanges = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            exposureComp = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.upper ?: 0
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.map { intArrayOf(it.width, it.height) } ?: emptyList()
            val size = Yuv.pickSize(sizes, quality)
            if (size == null) {
                fail("The camera has no usable picture size")
                return
            }
            val r = ImageReader.newInstance(size[0], size[1], ImageFormat.YUV_420_888, 2)
            r.setOnImageAvailableListener({ rd -> handleImage(rd) }, handler)
            reader = r
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (closed || myGen != generation) {
                        camera.close()
                        return
                    }
                    device = camera
                    startSession(camera, r)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (!closed && myGen == generation) fail("The camera was disconnected (maybe another app uses it)")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (!closed && myGen == generation) fail("Camera error $error")
                }
            }, handler)
        } catch (e: Exception) {
            Diag.e("Could not open the camera", e)
            fail("Could not open the camera: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    @Suppress("DEPRECATION")
    private fun startSession(cam: CameraDevice, r: ImageReader) {
        try {
            cam.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (closed) {
                        s.close()
                        return
                    }
                    session = s
                    repeat()
                    ready()
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    fail("The camera could not be set up")
                }
            }, handler)
        } catch (e: Exception) {
            Diag.e("Camera session failed", e)
            fail("The camera could not be set up")
        }
    }

    private fun repeat() {
        val cam = device ?: return
        val s = session ?: return
        val r = reader ?: return
        try {
            val b = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            b.addTarget(r.surface)
            val af = afModes
            if (af != null && af.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            if (night) {
                b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                val slow = fpsRanges?.minByOrNull { it.lower }
                if (slow != null) b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, slow)
                b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureComp)
                b.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                b.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            }
            b.set(
                CaptureRequest.FLASH_MODE,
                if (flash) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
            )
            s.setRepeatingRequest(b.build(), null, handler)
        } catch (e: Exception) {
            Diag.e("Camera request failed", e)
            fail("The camera stopped: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun handleImage(rd: ImageReader) {
        val img = try {
            rd.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            val gap = if (slowMode) 400 else when (quality) {
                0 -> 140
                1 -> 90
                2 -> 110
                else -> 130
            }
            if (now - lastFrame < gap) return
            lastFrame = now
            val w = img.width
            val h = img.height
            val p = img.planes
            val nv = Yuv.toNv21(
                w, h, p[0].buffer, p[0].rowStride,
                p[1].buffer, p[1].rowStride, p[1].pixelStride,
                p[2].buffer, p[2].rowStride, p[2].pixelStride
            )
            val out = ByteArrayOutputStream(w * h / 4)
            out.write(rotation)
            YuvImage(nv, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), when (quality) { 0 -> 50; 1 -> 60; else -> 70 }, out)
            onFrame(out.toByteArray())
        } catch (e: Exception) {
            Diag.limited("picture", "Picture problem: " + e.message)
        } finally {
            img.close()
        }
    }

    private fun closeCamera() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        try {
            device?.close()
        } catch (_: Exception) {
        }
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        session = null
        device = null
        reader = null
    }

    companion object {
        /** Lists the phone's cameras for the Diagnostics report (needs no camera permission). */
        fun dump(ctx: Context): String {
            val sb = StringBuilder("Cameras:\n")
            try {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val ids = cm.cameraIdList
                if (ids.isEmpty()) sb.append("  (none)\n")
                for (id in ids) {
                    val ch = cm.getCameraCharacteristics(id)
                    val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "front"
                        CameraCharacteristics.LENS_FACING_BACK -> "back"
                        else -> "other"
                    }
                    val flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                    val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(ImageFormat.YUV_420_888)?.map { intArrayOf(it.width, it.height) } ?: emptyList()
                    val low = Yuv.pickSize(sizes, 0)
                    val normal = Yuv.pickSize(sizes, 1)
                    sb.append("  id ").append(id).append(": ").append(facing)
                        .append(", flash=").append(flash)
                        .append(", sensor=").append(ch.get(CameraCharacteristics.SENSOR_ORIENTATION))
                        .append(", low=").append(low?.joinToString("x"))
                        .append(", normal=").append(normal?.joinToString("x")).append("\n")
                }
            } catch (e: Exception) {
                sb.append("  error: ").append(e.message).append("\n")
            }
            return sb.toString()
        }
    }
}
