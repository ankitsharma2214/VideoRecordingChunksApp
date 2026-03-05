package com.service.videorecordchunks

import android.annotation.SuppressLint
import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import com.google.common.collect.ImmutableList
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.*
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.media3.common.MediaItem
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.*
import java.io.File
import java.nio.ByteBuffer

class VideoRecordingService : LifecycleService() {

    companion object {
        private const val TAG = "VIDEO_DEBUG"
        private const val CHANNEL_ID = "video_channel"
        private const val TOTAL_CHUNKS = 4
        private var cameraId: String? = null
    }

    private val prefs by lazy {
        getSharedPreferences("recorder_prefs", MODE_PRIVATE)
    }

    inner class LocalBinder : Binder() {
        fun getService(): VideoRecordingService = this@VideoRecordingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    @SuppressLint("MissingSuperCall")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cameraId = intent?.getStringExtra("camera_id")
        return START_STICKY
    }

    private lateinit var cameraProvider: ProcessCameraProvider
    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var activeRecording: Recording? = null
    private var tempFile: File? = null


    override fun onCreate() {
        super.onCreate()

        startForegroundService()

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({

            cameraProvider = future.get()
            setupCamera()

            if (prefs.getBoolean("is_recording", false)) {
                startRecording()
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun setupCamera() {

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        preview = Preview.Builder().build()

        val selector = when (cameraId) {
            "1" -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        cameraProvider.unbindAll()

        cameraProvider.bindToLifecycle(
            this,
            selector,
            preview,
            videoCapture
        )
    }


    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        preview?.setSurfaceProvider(surfaceProvider)
    }


    @SuppressLint("MissingPermission")
    fun startRecording() {

        if (videoCapture == null || activeRecording != null) return

        prefs.edit().putBoolean("is_recording", true).apply()

        tempFile = File(cacheDir, "FULL_${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(tempFile!!).build()

        activeRecording = videoCapture!!.output
            .prepareRecording(this, options)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->

                when (event) {

                    is VideoRecordEvent.Start -> {
                        Handler(Looper.getMainLooper()).postDelayed({
                            stopRecording()
                        }, 2 * 60 * 1000)
                    }

                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        prefs.edit().putBoolean("is_recording", false).apply()

                        if (!event.hasError()) {
                            processVideo(tempFile!!)
                        } else {
                            tempFile?.delete()
                        }
                    }
                }
            }
    }


    fun stopRecording() {
        prefs.edit().putBoolean("is_recording", false).apply()

        activeRecording?.stop()
        activeRecording = null

        videoCapture = null
        cameraProvider.unbindAll()

        stopForeground(true)
        stopSelf()
    }


    private fun processVideo(originalFile: File) {
        val watermarkedFile =
            File(cacheDir, "WATERMARKED_${System.currentTimeMillis()}.mp4")

        addWatermark(originalFile, watermarkedFile) {
            continueProcessing(originalFile, watermarkedFile)
        }
    }


    private fun getVideoDuration(file: File): Long {

        val extractor = MediaExtractor()
        var duration = 0L

        try {
            extractor.setDataSource(file.absolutePath)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                if (mime != null && mime.startsWith("video/")) {

                    duration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else {
                        0L
                    }

                    break
                }
            }

        } catch (e: Exception) {
            Log.e("VIDEO_DEBUG", "Duration error: ${e.message}")
        } finally {
            extractor.release()
        }

        return duration
    }


    private fun continueProcessing(originalFile: File, watermarkedFile: File) {

        Thread {

            val duration = getVideoDuration(watermarkedFile)
            val chunkDuration = duration / TOTAL_CHUNKS

            saveToGallery(watermarkedFile, "FULL_VIDEO")

            var startUs = 0L

            for (i in 1..TOTAL_CHUNKS) {

                val endUs =
                    if (i == TOTAL_CHUNKS) duration
                    else startUs + chunkDuration

                val chunkFile = File(cacheDir, "chunk_$i.mp4")

                extractChunk(watermarkedFile, startUs, endUs, chunkFile)

                saveToGallery(chunkFile, "CHUNK_$i")

                chunkFile.delete()
                startUs += chunkDuration
            }

            originalFile.delete()
            watermarkedFile.delete()


        }.start()
    }


    private fun saveToGallery(file: File, prefix: String) {

        try {

            val resolver = contentResolver

            val values = ContentValues().apply {
                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    "${prefix}_${System.currentTimeMillis()}.mp4"
                )
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

            if (uri != null) {

                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                    output.flush()
                }

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)

                resolver.update(uri, values, null, null)

            } else {
                Log.e(TAG, "MediaStore insert failed")
            }

        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery error: ${e.message}")
        }
    }

    @SuppressLint("WrongConstant")
    private fun extractChunk(
        sourceFile: File,
        startUs: Long,
        endUs: Long,
        outputFile: File
    ) {

        val extractor = MediaExtractor()
        extractor.setDataSource(sourceFile.absolutePath)

        val muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        val trackMap = HashMap<Int, Int>()

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            trackMap[i] = muxer.addTrack(format)
        }

        muxer.start()

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        for (i in 0 until extractor.trackCount) {

            extractor.selectTrack(i)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            while (true) {

                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break

                if (sampleTime < startUs) {
                    extractor.advance()
                    continue
                }

                if (sampleTime >= endUs) break

                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize <= 0) {
                    extractor.advance()
                    continue
                }

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(trackMap[i]!!, buffer, bufferInfo)
                extractor.advance()
            }

            extractor.unselectTrack(i)
        }

        muxer.stop()
        muxer.release()
        extractor.release()
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun addWatermark(
        input: File,
        output: File,
        onComplete: () -> Unit
    ) {

        val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(input))

        val watermarkText = SpannableString("My Watermark").apply {
            setSpan(AbsoluteSizeSpan(60, true), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val textOverlay = TextOverlay.createStaticTextOverlay(watermarkText)
        val overlayEffect = OverlayEffect(ImmutableList.of(textOverlay))

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), listOf(overlayEffect)))
            .build()

        val transformer = Transformer.Builder(this).build()

        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                onComplete()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exception: ExportException
            ) {
                Log.e(TAG, "Watermark error: ${exception.message}")
            }
        })

        transformer.start(editedMediaItem, output.absolutePath)
    }


    @SuppressLint("MissingPermission")
    override fun onTaskRemoved(rootIntent: Intent?) {

        val restartIntent = Intent(applicationContext, VideoRecordingService::class.java)
        restartIntent.putExtra("camera_id", cameraId)

        val pendingIntent = PendingIntent.getService(
            this,
            1001,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager =
            getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 500,
            pendingIntent
        )

        super.onTaskRemoved(rootIntent)
    }


    private fun startForegroundService() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Recording",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Recording")
            .setContentText("Recording in background...")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(1, notification)
    }
}