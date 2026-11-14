package com.rvmeur.glassspike

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.InputStream
import java.util.Locale
import java.util.Random
import java.util.concurrent.Executors
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Prototipe Falsifikasi Glassmorphism (Glass Spike) — Mandiri Satu Activity.
 *
 * Menguji keterbacaan tipografi dan biaya frame rate (avg, p95, jank >16,7 ms)
 * dari tiga teknik kaca Android 12+ (minSdk 31) di atas palet hangat kertas:
 * 1) Kartu Hero Status Unggah: downscale bitmap 1/8 di background thread +
 *    RenderEffect.createChainEffect(Blur 28f CLAMP, ColorMatrix saturasi 1.2) +
 *    panel Doppelrand kaca (#FAF8F3 alpha 72%, border putih alpha 55%).
 * 2) Bilah Melayang Bawah (floatingBar): tangkap-latar on-demand skala 0,25 dari
 *    konten ScrollView (bukan per-frame) + RenderEffect Blur 24f CLAMP + tint 55%.
 * 3) Sheet Kaca (Dialog): window.setBackgroundBlurRadius(80) + FLAG_BLUR_BEHIND
 *    dengan fallback otomatis ke kartu opak penuh (#FAF8F3 100%) bila blur sistem
 *    lintas-jendela nonaktif.
 */
class MainActivity : Activity() {

    companion object {
        // Token Warna Persis
        private val COLOR_GROUND = Color.parseColor("#FAF8F3")
        private val COLOR_CARD = Color.parseColor("#F6F3EB")
        private val COLOR_SHELL = Color.parseColor("#F0EBE0")
        private val COLOR_INK = Color.parseColor("#26170C")
        private val COLOR_MUTED = Color.parseColor("#4F453F")
        private val COLOR_MUTEDLIGHT = Color.parseColor("#6E625B")
        private val COLOR_BORDER = Color.parseColor("#D2C4BC")
        private val COLOR_HAIRLINE = Color.parseColor("#E8E0D8")
        private val COLOR_EMERALD = Color.parseColor("#2D4636")
        private val COLOR_OLIVE = Color.parseColor("#56642B")
        private val COLOR_BURGUNDY = Color.parseColor("#7A1A1A")

        // Warna Kaca (ARGB dari #FAF8F3 dan putih)
        private const val COLOR_GLASS_HERO_FILL = 0xB8FAF8F3.toInt()   // #FAF8F3 alpha ~72%
        private const val COLOR_GLASS_HERO_STROKE = 0x8CFFFFFF.toInt() // #FFFFFF alpha ~55%
        private const val COLOR_GLASS_BAR_TINT = 0x8CFAF8F3.toInt()    // #FAF8F3 alpha ~55%
        private const val COLOR_GLASS_SHEET_FILL = 0xE0FAF8F3.toInt()  // #FAF8F3 alpha ~88%

        private const val FRAME_HISTORY_SIZE = 240
        private const val JANK_THRESHOLD_MS = 16.7
        private const val SCROLL_DEBOUNCE_MS = 120L
        private const val UPLOAD_TICK_MS = 100L
        private const val POWER_POLL_MS = 2000L
        private const val FRAME_METER_REFRESH_MS = 500L
    }

    private enum class PhotoMode {
        HIGH_KEY,
        NEON,
        LOW_KEY
    }

    private data class PhotoPreset(
        val fileName: String,
        val displayLabel: String,
        val assetCandidates: List<String>,
        val mode: PhotoMode
    )

    private val photoPresets = listOf(
        PhotoPreset(
            fileName = "IMG_20261114_007.jpg",
            displayLabel = "IMG_20261114_007.jpg - foto uji high-key",
            assetCandidates = listOf(
                "photos/high_key.jpg",
                "photos/high-key.jpg",
                "high_key.jpg",
                "high-key.jpg",
                "IMG_20261114_007.jpg"
            ),
            mode = PhotoMode.HIGH_KEY
        ),
        PhotoPreset(
            fileName = "IMG_20261114_019.jpg",
            displayLabel = "IMG_20261114_019.jpg - foto uji neon",
            assetCandidates = listOf(
                "photos/neon.jpg",
                "neon.jpg",
                "IMG_20261114_019.jpg"
            ),
            mode = PhotoMode.NEON
        ),
        PhotoPreset(
            fileName = "IMG_20261114_042.jpg",
            displayLabel = "IMG_20261114_042.jpg - foto uji low-key",
            assetCandidates = listOf(
                "photos/low_key.jpg",
                "photos/low-key.jpg",
                "low_key.jpg",
                "low-key.jpg",
                "IMG_20261114_042.jpg"
            ),
            mode = PhotoMode.LOW_KEY
        )
    )

    private val paperCardBodies = listOf(
        "lembar kontak arsip sesi studio sore dengan catatan pencahayaan alami.\nserat kertas hangat menjaga kontras baca di bawah terik lapangan.",
        "antrean berkas mentah menunggu verifikasi checksum sebelum pengarsipan.\nstruktur tepi ganda memisahkan permukaan kerja dari kanvas dasar.",
        "catatan kalibrasi rana mekanis pada kecepatan tinggi tanpa distorsi.\npermukaan kartu bebas pantulan untuk evaluasi eksposur cepat.",
        "daftar manifestasi kurasi potret dokumenter dari kartu memori utama.\ntipografi serif dan mono dipadukan demi kejelasan hirarki visual.",
        "ringkasan telemetri transfer kabel langsung menuju penyimpanan awan.\nsetiap paket diuji ketahanannya terhadap jeda jaringan sementara.",
        "indeks urutan bingkai terpilih hasil penyaringan ketajaman mata.\ntanda batas tipis membantu orientasi mata saat menggulir daftar panjang.",
        "log sinkronisasi metadata waktu tangkap dan profil simulasi film.\nkonsistensi warna kertas dipertahankan lintas kepadatan piksel layar.",
        "verifikasi integritas folder tujuan yang telah dikunci sebelum sesi.\nmencegah salah unggah ketika fotografer berpindah lokasi pemotretan.",
        "laporan kepadatan antrean unggah latar belakang selama pemotretan aktif.\nritme tipografi tenang mengurangi kelelahan baca pada sesi panjang.",
        "arsip penutup sesi pemotretan lapangan dengan rekapitulasi penuh.\nseluruh sepuluh kartu kertas ini menjadi beban uji gulir bilah kaca."
    )

    // Tipografi dari assets/fonts/
    private lateinit var fontCormorantSemibold: Typeface
    private lateinit var fontDmSansRegular: Typeface
    private lateinit var fontDmSansMedium: Typeface
    private lateinit var fontJetBrainsMono: Typeface
    private lateinit var fontJetBrainsMonoBold: Typeface

    // Komponen UI Utama
    private lateinit var rootFrame: FrameLayout
    private lateinit var scrollView: ScrollView
    private lateinit var scrollContentColumn: LinearLayout
    private lateinit var statusSistemTextView: TextView

    // Komponen Kartu Hero Status Unggah
    private lateinit var heroCardContainer: FrameLayout
    private lateinit var heroImageView: ImageView
    private lateinit var heroFileTextView: TextView
    private lateinit var heroMbpsTextView: TextView
    private lateinit var heroProgressBar: FrameLayout
    private lateinit var heroProgressTrackView: View
    private lateinit var heroProgressFillView: View
    private lateinit var heroStateTextView: TextView

    // Tombol Baris 1 & Meter Frame
    private lateinit var btnCyclePhoto: Button
    private lateinit var btnSheet: Button
    private lateinit var frameMeterTextView: TextView

    // Bilah Melayang Bawah (floatingBar)
    private lateinit var floatingBar: FrameLayout
    private lateinit var barBgImageView: ImageView
    private lateinit var barTintView: View
    private lateinit var btnBarSheet: Button

    // Dialog Sheet Kaca Aktif (bila sedang dibuka)
    private var activeSheetDialog: Dialog? = null
    private var activeSheetCardView: LinearLayout? = null
    private var activeSheetFallbackTextView: TextView? = null

    // Layanan Sistem & Eksekutor
    private lateinit var powerManager: PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val random = Random(20261114L)
    private val localeId = Locale("id", "ID")

    // State Status Sistem
    private var isCrossWindowBlurActive: Boolean = false
    private var isPowerSaveActive: Boolean = false
    private var crossWindowBlurListener: Consumer<Boolean>? = null

    // State Foto Uji & Timer Unggah Palsu
    private var currentPhotoIndex: Int = 0
    private var photoLoadGeneration: Int = 0
    private var uploadProgress: Float = 0f
    private var uploadTickCount: Int = 0
    private var isActivityRunning: Boolean = false

    // State Meter Frame (240 durasi frame terakhir)
    private val frameDurationsMs = DoubleArray(FRAME_HISTORY_SIZE)
    private var frameWriteIndex: Int = 0
    private var frameSampleCount: Int = 0
    private var lastFrameTimeNanos: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isActivityRunning) return
            if (lastFrameTimeNanos != 0L) {
                val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000.0
                if (deltaMs in 0.1..500.0) {
                    frameDurationsMs[frameWriteIndex] = deltaMs
                    frameWriteIndex = (frameWriteIndex + 1) % FRAME_HISTORY_SIZE
                    if (frameSampleCount < FRAME_HISTORY_SIZE) {
                        frameSampleCount++
                    }
                }
            }
            lastFrameTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val frameMeterUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isActivityRunning) return
            updateFrameMeterText()
            mainHandler.postDelayed(this, FRAME_METER_REFRESH_MS)
        }
    }

    private val powerSavePollRunnable = object : Runnable {
        override fun run() {
            if (!isActivityRunning) return
            isPowerSaveActive = try {
                powerManager.isPowerSaveMode
            } catch (_: Throwable) {
                false
            }
            isCrossWindowBlurActive = try {
                windowManager.isCrossWindowBlurEnabled
            } catch (_: Throwable) {
                false
            }
            updateSystemStatusText()
            mainHandler.postDelayed(this, POWER_POLL_MS)
        }
    }

    private val uploadTimerRunnable = object : Runnable {
        override fun run() {
            if (!isActivityRunning) return
            uploadTickCount++
            if (uploadProgress < 100f) {
                val delta = 0.4f + random.nextFloat() * 1.2f
                uploadProgress = min(100f, uploadProgress + delta)
                updateHeroProgressUi(uploadProgress)

                if (uploadProgress >= 100f) {
                    heroStateTextView.text = "selesai - kaca menjernih."
                    heroMbpsTextView.text = "0,0 MB/s"
                } else {
                    val wave = sin(uploadTickCount * 0.28) * 0.75
                    val noise = (random.nextDouble() - 0.5) * 0.36
                    val mbps = max(0.1, 3.2 + wave + noise)
                    heroMbpsTextView.text = String.format(localeId, "%.1f MB/s", mbps)
                }
            } else {
                heroMbpsTextView.text = "0,0 MB/s"
            }
            mainHandler.postDelayed(this, UPLOAD_TICK_MS)
        }
    }

    private val scrollIdleRefreshRunnable = Runnable {
        if (isActivityRunning) {
            refreshBarBackground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        initFonts()

        setContentView(buildRootLayout())

        // Daftarkan listener blur lintas-jendela sekali di onCreate
        val listener = Consumer<Boolean> { enabled ->
            isCrossWindowBlurActive = enabled
            isPowerSaveActive = try {
                powerManager.isPowerSaveMode
            } catch (_: Throwable) {
                false
            }
            updateSystemStatusText()
            applyActiveDialogBlurState(enabled)
        }
        crossWindowBlurListener = listener
        try {
            windowManager.addCrossWindowBlurEnabledListener(mainExecutor, listener)
        } catch (_: Throwable) {
        }

        // Muat status sistem awal & foto uji pertama (high-key)
        isCrossWindowBlurActive = try {
            windowManager.isCrossWindowBlurEnabled
        } catch (_: Throwable) {
            false
        }
        isPowerSaveActive = try {
            powerManager.isPowerSaveMode
        } catch (_: Throwable) {
            false
        }
        updateSystemStatusText()
        loadActiveHeroPhoto(resetProgress = true)

        // Tangkap latar bilah melayang sekali setelah layout pertama selesai
        rootFrame.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (rootFrame.width > 0 && rootFrame.height > 0) {
                        rootFrame.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        updateHeroProgressUi(uploadProgress)
                        refreshBarBackground()
                    }
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        isActivityRunning = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
        mainHandler.removeCallbacks(frameMeterUpdateRunnable)
        mainHandler.postDelayed(frameMeterUpdateRunnable, FRAME_METER_REFRESH_MS)
        mainHandler.removeCallbacks(powerSavePollRunnable)
        mainHandler.post(powerSavePollRunnable)
        mainHandler.removeCallbacks(uploadTimerRunnable)
        mainHandler.postDelayed(uploadTimerRunnable, UPLOAD_TICK_MS)
    }

    override fun onStop() {
        isActivityRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        mainHandler.removeCallbacks(frameMeterUpdateRunnable)
        mainHandler.removeCallbacks(powerSavePollRunnable)
        mainHandler.removeCallbacks(uploadTimerRunnable)
        mainHandler.removeCallbacks(scrollIdleRefreshRunnable)
        super.onStop()
    }

    override fun onDestroy() {
        activeSheetDialog?.dismiss()
        activeSheetDialog = null
        crossWindowBlurListener?.let { listener ->
            try {
                windowManager.removeCrossWindowBlurEnabledListener(listener)
            } catch (_: Throwable) {
            }
        }
        crossWindowBlurListener = null
        bgExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun initFonts() {
        fontCormorantSemibold = loadTypeface("fonts/cormorant_garamond_semibold.ttf", Typeface.SERIF)
        fontDmSansRegular = loadTypeface("fonts/dm_sans_regular.ttf", Typeface.SANS_SERIF)
        fontDmSansMedium = loadTypeface("fonts/dm_sans_medium.ttf", Typeface.SANS_SERIF)
        fontJetBrainsMono = loadTypeface("fonts/jetbrains-mono-regular.ttf", Typeface.MONOSPACE)
        fontJetBrainsMonoBold = Typeface.create(fontJetBrainsMono, Typeface.BOLD)
    }

    private fun loadTypeface(assetPath: String, fallback: Typeface): Typeface {
        return try {
            Typeface.createFromAsset(assets, assetPath)
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun buildRootLayout(): View {
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(COLOR_GROUND)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1) ScrollView berisi kolom vertikal (padding 20dp, lebar maks 760dp terpusat)
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnScrollChangeListener { _, _, _, _, _ ->
                mainHandler.removeCallbacks(scrollIdleRefreshRunnable)
                mainHandler.postDelayed(scrollIdleRefreshRunnable, SCROLL_DEBOUNCE_MS)
            }
        }

        val maxWidthPx = dp(760)
        scrollContentColumn = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
                val constrainedSpec = if (measuredWidth > maxWidthPx) {
                    MeasureSpec.makeMeasureSpec(maxWidthPx, MeasureSpec.getMode(widthMeasureSpec))
                } else {
                    widthMeasureSpec
                }
                super.onMeasure(constrainedSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            )
        }

        // 1a) Judul "spike kaca." + monoLabel statusSistemTextView
        val titleTextView = TextView(this).apply {
            text = "spike kaca."
            typeface = fontCormorantSemibold
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(4))
        }
        scrollContentColumn.addView(titleTextView)

        statusSistemTextView = monoLabel("blur lintas-jendela: nonaktif  •  hemat baterai: nonaktif").apply {
            setPadding(0, 0, 0, dp(16))
        }
        scrollContentColumn.addView(statusSistemTextView)

        // 1b) Kartu Hero Status Unggah (komponen uji utama)
        scrollContentColumn.addView(buildHeroUploadCard())

        // 1c) Tombol Baris 1: btnCyclePhoto & btnSheet
        scrollContentColumn.addView(buildActionRow())

        // 1d) Panel Meter Frame (kartu Doppelrand kertas biasa)
        scrollContentColumn.addView(buildFrameMeterCard())

        // 1e) Sepuluh kartu Doppelrand kertas (statis, untuk beban scroll)
        for (i in 1..10) {
            val numberStr = String.format(localeId, "%02d", i)
            val bodyText = paperCardBodies[(i - 1) % paperCardBodies.size]
            scrollContentColumn.addView(
                buildPaperLoadCard(
                    title = "kartu kertas $numberStr.",
                    body = bodyText
                )
            )
        }

        // Ruang ekstra di bawah agar kartu ke-10 tetap dapat digulir melewati bilah melayang
        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(84)
            )
        }
        scrollContentColumn.addView(bottomSpacer)

        scrollView.addView(scrollContentColumn)
        rootFrame.addView(scrollView)

        // 2) Bilah Melayang Bawah (floatingBar)
        floatingBar = buildFloatingBar()
        rootFrame.addView(floatingBar)

        return rootFrame
    }

    private fun buildHeroUploadCard(): View {
        heroCardContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(COLOR_SHELL)
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(190)
            ).apply {
                bottomMargin = dp(14)
            }
        }

        // Lapisan terbawah: heroImageView (CENTER_CROP, memuat bitmap 1/8 + RenderEffect chain)
        heroImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        heroCardContainer.addView(heroImageView)

        // Panel dalam gaya Doppelrand kaca (margin 12dp, #FAF8F3 alpha 72%, sudut 18dp, border 1dp putih alpha 55%)
        val glassInnerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(COLOR_GLASS_HERO_FILL)
                setStroke(dp(1), COLOR_GLASS_HERO_STROKE)
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                val m = dp(12)
                setMargins(m, m, m, m)
            }
        }

        heroFileTextView = TextView(this).apply {
            text = photoPresets[0].displayLabel
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_INK)
            maxLines = 1
        }
        glassInnerPanel.addView(heroFileTextView)

        heroMbpsTextView = TextView(this).apply {
            text = "0,0 MB/s"
            typeface = fontJetBrainsMonoBold
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTextColor(COLOR_INK)
            setPadding(0, dp(4), 0, dp(8))
        }
        glassInnerPanel.addView(heroMbpsTextView)

        // ProgressBar horizontal gaya kustom: dua View persegi sudut kecil (latar HAIRLINE, isi EMERALD)
        heroProgressBar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            ).apply {
                bottomMargin = dp(8)
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateHeroProgressUi(uploadProgress)
            }
        }

        heroProgressTrackView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(COLOR_HAIRLINE)
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        heroProgressFillView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(COLOR_EMERALD)
            }
            layoutParams = FrameLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
        }

        heroProgressBar.addView(heroProgressTrackView)
        heroProgressBar.addView(heroProgressFillView)
        glassInnerPanel.addView(heroProgressBar)

        heroStateTextView = TextView(this).apply {
            text = "mengunggah ke drive - folder tujuan terkunci"
            typeface = fontJetBrainsMono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_MUTED)
            maxLines = 1
        }
        glassInnerPanel.addView(heroStateTextView)

        heroCardContainer.addView(glassInnerPanel)
        return heroCardContainer
    }

    private fun buildActionRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }

        btnCyclePhoto = capsuleButton(
            label = "simulasi jepretan ekstrem - ganti foto.",
            primary = true
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.45f
            ).apply {
                rightMargin = dp(10)
            }
            setOnClickListener {
                cycleHeroPhoto()
            }
        }

        btnSheet = capsuleButton(
            label = "buka sheet kaca.",
            primary = false
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setOnClickListener {
                showGlassSheet()
            }
        }

        row.addView(btnCyclePhoto)
        row.addView(btnSheet)
        return row
    }

    private fun buildFrameMeterCard(): View {
        val (outer, inner) = doppelrandPaperCard()

        val label = monoLabel("telemetri choreographer (240 sampel terakhir)").apply {
            setPadding(0, 0, 0, dp(6))
        }
        inner.addView(label)

        frameMeterTextView = TextView(this).apply {
            text = "frame avg: - ms\nframe p95: - ms\njank (>16,7 ms): - %"
            typeface = fontJetBrainsMono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_INK)
            setLineSpacing(dp(3).toFloat(), 1.0f)
        }
        inner.addView(frameMeterTextView)

        return outer
    }

    private fun buildPaperLoadCard(title: String, body: String): View {
        val (outer, inner) = doppelrandPaperCard()

        val titleView = TextView(this).apply {
            text = title
            typeface = fontCormorantSemibold
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(6))
        }

        val bodyView = TextView(this).apply {
            text = body
            typeface = fontDmSansRegular
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_MUTED)
            setLineSpacing(dp(2).toFloat(), 1.0f)
            minLines = 2
            maxLines = 2
        }

        inner.addView(titleView)
        inner.addView(bodyView)
        return outer
    }

    private fun buildFloatingBar(): FrameLayout {
        val bar = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(COLOR_SHELL)
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = dp(6).toFloat()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                val m = dp(16)
                setMargins(m, m, m, m)
            }
        }

        // Lapisan 1: barBgImageView (snapshot latar skala 0,25 + CENTER_CROP + RenderEffect blur 24f CLAMP)
        barBgImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setRenderEffect(
                RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        bar.addView(barBgImageView)

        // Lapisan 2: tint View bg #FAF8F3 alpha 55% + garis tepi kapsul halus
        barTintView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(COLOR_GLASS_BAR_TINT)
                setStroke(dp(1), COLOR_GLASS_HERO_STROKE)
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        bar.addView(barTintView)

        // Lapisan 3: Konten teks mono 11sp INK + tombol kecil "sheet."
        val barContentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(14), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val barLabel = TextView(this).apply {
            text = "bilah melayang - kaca tangkap-latar"
            typeface = fontJetBrainsMono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_INK)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        btnBarSheet = Button(this).apply {
            text = "sheet."
            isAllCaps = false
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_GROUND)
            minHeight = dp(36)
            minimumHeight = dp(36)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(16), 0, dp(16), 0)
            stateListAnimator = null
            elevation = 0f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(COLOR_INK)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            )
            setOnClickListener {
                showGlassSheet()
            }
        }

        barContentRow.addView(barLabel)
        barContentRow.addView(btnBarSheet)
        bar.addView(barContentRow)

        return bar
    }

    /**
     * 3) DIALOG KACA:
     * Mengatur window.setBackgroundBlurRadius(80) SEBELUM tampil + FLAG_BLUR_BEHIND.
     * Latar kartu: #FAF8F3 alpha 88% sudut 24dp border HAIRLINE.
     * Fallback: bila !windowManager.isCrossWindowBlurEnabled -> latar kartu OPAK penuh #FAF8F3 (alpha 100%)
     * + teks kecil tambahan "fallback opak - blur sistem nonaktif" VISIBLE (GONE bila blur aktif).
     */
    private fun showGlassSheet() {
        activeSheetDialog?.dismiss()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val blurEnabled = try {
            windowManager.isCrossWindowBlurEnabled
        } catch (_: Throwable) {
            false
        }
        isCrossWindowBlurActive = blurEnabled
        updateSystemStatusText()

        val dialogRoot = FrameLayout(this).apply {
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val sheetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(22))
            background = createSheetCardDrawable(blurEnabled)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val sheetTitle = TextView(this).apply {
            text = "sheet kaca."
            typeface = fontCormorantSemibold
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(6))
        }
        sheetCard.addView(sheetTitle)

        val fallbackNotice = TextView(this).apply {
            text = "fallback opak - blur sistem nonaktif"
            typeface = fontJetBrainsMono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_BURGUNDY)
            setPadding(0, 0, 0, dp(10))
            visibility = if (blurEnabled) View.GONE else View.VISIBLE
        }
        sheetCard.addView(fallbackNotice)

        val sheetBody = TextView(this).apply {
            text = "jendela dialog ini meminta blur lintas-jendela radius 80px di belakang kartu hangat. " +
                "saat penghemat daya aktif atau compositor menolak blur, permukaan otomatis kembali ke kertas opak penuh demi menjaga keterbacaan."
            typeface = fontDmSansRegular
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_MUTED)
            setLineSpacing(dp(3).toFloat(), 1.0f)
            setPadding(0, 0, 0, dp(18))
        }
        sheetCard.addView(sheetBody)

        val btnClose = capsuleButton(
            label = "tutup.",
            primary = true
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            setOnClickListener {
                dialog.dismiss()
            }
        }
        sheetCard.addView(btnClose)

        dialogRoot.addView(sheetCard)
        dialog.setContentView(dialogRoot)

        activeSheetDialog = dialog
        activeSheetCardView = sheetCard
        activeSheetFallbackTextView = fallbackNotice

        dialog.setOnDismissListener {
            if (activeSheetDialog === dialog) {
                activeSheetDialog = null
                activeSheetCardView = null
                activeSheetFallbackTextView = null
            }
        }

        // Konfigurasi jendela SEBELUM dialog.show()
        dialog.window?.let { win ->
            win.setBackgroundDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(24).toFloat()
                    setColor(Color.TRANSPARENT)
                }
            )
            win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val lp = win.attributes
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.dimAmount = 0.18f
            win.attributes = lp
            win.setBackgroundBlurRadius(80)
        }

        dialog.show()
    }

    private fun createSheetCardDrawable(blurEnabled: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(if (blurEnabled) COLOR_GLASS_SHEET_FILL else COLOR_GROUND)
            setStroke(dp(1), COLOR_HAIRLINE)
        }
    }

    private fun applyActiveDialogBlurState(blurEnabled: Boolean) {
        val card = activeSheetCardView ?: return
        val fallbackText = activeSheetFallbackTextView ?: return
        card.background = createSheetCardDrawable(blurEnabled)
        fallbackText.visibility = if (blurEnabled) View.GONE else View.VISIBLE
    }

    /**
     * Siklus foto uji berurutan: high-key -> neon -> low-key.
     * Mengatur ulang progres ke 0 dan memuat ulang blur statis di background thread.
     */
    private fun cycleHeroPhoto() {
        currentPhotoIndex = (currentPhotoIndex + 1) % photoPresets.size
        loadActiveHeroPhoto(resetProgress = true)
    }

    private fun loadActiveHeroPhoto(resetProgress: Boolean) {
        val preset = photoPresets[currentPhotoIndex]
        heroFileTextView.text = preset.displayLabel

        if (resetProgress) {
            uploadProgress = 0f
            uploadTickCount = 0
            updateHeroProgressUi(0f)
            heroMbpsTextView.text = "0,0 MB/s"
            heroStateTextView.text = "mengunggah ke drive - folder tujuan terkunci"
        }

        val generation = ++photoLoadGeneration
        bgExecutor.execute {
            val downscaledBitmap = decodeOrCreateDownscaledHeroBitmap(preset)
            mainHandler.post {
                if (!isDestroyed && generation == photoLoadGeneration) {
                    heroImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    heroImageView.setImageBitmap(downscaledBitmap)

                    val blurEffect = RenderEffect.createBlurEffect(
                        28f,
                        28f,
                        Shader.TileMode.CLAMP
                    )
                    val saturationMatrix = ColorMatrix().apply {
                        setSaturation(1.2f)
                    }
                    val colorFilterEffect = RenderEffect.createColorFilterEffect(
                        ColorMatrixColorFilter(saturationMatrix)
                    )
                    heroImageView.setRenderEffect(
                        RenderEffect.createChainEffect(blurEffect, colorFilterEffect)
                    )

                    heroImageView.post {
                        if (isActivityRunning) {
                            refreshBarBackground()
                        }
                    }
                }
            }
        }
    }

    /**
     * Dekode asset foto uji (bila tersedia di assets/) atau buat foto uji sintetis ekstrem
     * (high-key / neon / low-key), lalu perkecil ke 1/8 ukuran asli di background thread.
     */
    private fun decodeOrCreateDownscaledHeroBitmap(preset: PhotoPreset): Bitmap {
        val fullBitmap = tryDecodeAssetPhoto(preset) ?: createSyntheticExtremePhoto(preset.mode)
        val targetW = max(16, fullBitmap.width / 8)
        val targetH = max(10, fullBitmap.height / 8)
        val scaled = Bitmap.createScaledBitmap(fullBitmap, targetW, targetH, true)
        if (scaled !== fullBitmap) {
            fullBitmap.recycle()
        }
        return scaled
    }

    private fun tryDecodeAssetPhoto(preset: PhotoPreset): Bitmap? {
        for (candidate in preset.assetCandidates) {
            try {
                assets.open(candidate).use { stream ->
                    return decodeStreamSafely(stream)
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun decodeStreamSafely(stream: InputStream): Bitmap? {
        return BitmapFactory.decodeStream(stream)
    }

    private fun createSyntheticExtremePhoto(mode: PhotoMode): Bitmap {
        val width = 800
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (mode) {
            PhotoMode.HIGH_KEY -> {
                // High-key: studio overexposed putih terang, krem gading, flare kuning-sian pucat
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#FFFDF7"),
                        Color.parseColor("#FFF3D6"),
                        Color.parseColor("#E8F8FA")
                    ),
                    floatArrayOf(0f, 0.4f, 0.75f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                paint.shader = RadialGradient(
                    width * 0.28f,
                    height * 0.32f,
                    width * 0.35f,
                    Color.parseColor("#FFFFFA"),
                    Color.parseColor("#00FFFFFF"),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(width * 0.28f, height * 0.32f, width * 0.35f, paint)

                paint.shader = null
                paint.color = Color.parseColor("#F5E1A4")
                canvas.drawCircle(width * 0.76f, height * 0.26f, 110f, paint)
                paint.color = Color.parseColor("#D8EEF2")
                canvas.drawRect(width * 0.12f, height * 0.58f, width * 0.88f, height * 0.84f, paint)
            }

            PhotoMode.NEON -> {
                // Neon: kontras malam ekstrem magenta, sian elektrik, hijau asam, ungu gelap
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#14002B"),
                        Color.parseColor("#FF007F"),
                        Color.parseColor("#00F0FF"),
                        Color.parseColor("#39FF14")
                    ),
                    floatArrayOf(0f, 0.36f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                paint.shader = null
                paint.color = Color.parseColor("#FF2A85")
                canvas.drawCircle(width * 0.24f, height * 0.42f, 130f, paint)
                paint.color = Color.parseColor("#00FFFF")
                canvas.drawCircle(width * 0.62f, height * 0.34f, 120f, paint)
                paint.color = Color.parseColor("#CCFF00")
                canvas.drawCircle(width * 0.80f, height * 0.68f, 95f, paint)
                paint.color = Color.parseColor("#FF5500")
                canvas.drawRect(width * 0.08f, height * 0.70f, width * 0.52f, height * 0.90f, paint)
            }

            PhotoMode.LOW_KEY -> {
                // Low-key: bayangan chiaroscuro gelap pekat dengan sedikit aksen bara & espresso
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#050505"),
                        Color.parseColor("#120D0A"),
                        Color.parseColor("#1D1612"),
                        Color.parseColor("#090C10")
                    ),
                    floatArrayOf(0f, 0.45f, 0.8f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                paint.shader = RadialGradient(
                    width * 0.68f,
                    height * 0.44f,
                    width * 0.28f,
                    Color.parseColor("#6E3B1E"),
                    Color.parseColor("#00050505"),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(width * 0.68f, height * 0.44f, width * 0.28f, paint)

                paint.shader = null
                paint.color = Color.parseColor("#2A1B12")
                canvas.drawRect(width * 0.14f, height * 0.22f, width * 0.42f, height * 0.78f, paint)
            }
        }

        return bitmap
    }

    /**
     * 2) TEKNIK TANGKAP LATAR (refreshBarBackground):
     * Menggambar wilayah konten ScrollView yang berada tepat di bawah floatingBar
     * ke Bitmap skala 0,25, lalu memasangnya ke barBgImageView (CENTER_CROP + RenderEffect blur 24f).
     * Dipanggil hanya saat: layout pertama selesai, scroll berhenti (debounce 120ms),
     * dan saat foto hero diganti.
     */
    private fun refreshBarBackground() {
        val barWidth = floatingBar.width
        val barHeight = floatingBar.height
        if (barWidth <= 0 || barHeight <= 0 || scrollContentColumn.width <= 0) return

        val scale = 0.25f
        val bmpWidth = max(1, (barWidth * scale).toInt())
        val bmpHeight = max(1, (barHeight * scale).toInt())

        // Hitung posisi bilah relatif terhadap konten ScrollView yang terlihat
        val barTopInScroll = floatingBar.top - scrollView.top
        val barLeftInScroll = floatingBar.left - scrollView.left
        val contentX = scrollView.scrollX + barLeftInScroll - scrollContentColumn.left
        val contentY = scrollView.scrollY + barTopInScroll - scrollContentColumn.top

        val snapshot = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(snapshot)
        canvas.drawColor(COLOR_GROUND)
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-contentX.toFloat(), -contentY.toFloat())
        scrollContentColumn.draw(canvas)
        canvas.restore()

        barBgImageView.setImageBitmap(snapshot)
    }

    private fun updateHeroProgressUi(progress: Float) {
        val totalWidth = heroProgressBar.width
        if (totalWidth <= 0) return
        val clamped = progress.coerceIn(0f, 100f)
        val targetWidth = ((totalWidth * clamped) / 100f).toInt()
        val lp = heroProgressFillView.layoutParams
        if (lp.width != targetWidth) {
            lp.width = targetWidth
            heroProgressFillView.layoutParams = lp
        }
    }

    private fun updateSystemStatusText() {
        val blurText = if (isCrossWindowBlurActive) "aktif" else "nonaktif"
        val batteryText = if (isPowerSaveActive) "aktif" else "nonaktif"
        statusSistemTextView.text =
            "blur lintas-jendela: $blurText  •  hemat baterai: $batteryText"
    }

    private fun updateFrameMeterText() {
        val count = frameSampleCount
        if (count <= 0) {
            frameMeterTextView.text = "frame avg: - ms\nframe p95: - ms\njank (>16,7 ms): - %"
            return
        }

        val samples = DoubleArray(count)
        var sum = 0.0
        var jankCount = 0
        for (i in 0 until count) {
            val d = frameDurationsMs[i]
            samples[i] = d
            sum += d
            if (d > JANK_THRESHOLD_MS) {
                jankCount++
            }
        }
        samples.sort()

        val avgMs = sum / count
        val p95Index = ((count - 1) * 0.95).toInt().coerceIn(0, count - 1)
        val p95Ms = samples[p95Index]
        val jankPercent = (jankCount * 100.0) / count

        frameMeterTextView.text = String.format(
            localeId,
            "frame avg: %.2f ms\nframe p95: %.2f ms\njank (>16,7 ms): %.1f %%",
            avgMs,
            p95Ms,
            jankPercent
        )
    }

    private fun doppelrandPaperCard(): Pair<LinearLayout, LinearLayout> {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(COLOR_SHELL)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(COLOR_CARD)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        outer.addView(inner)
        return Pair(outer, inner)
    }

    private fun capsuleButton(label: String, primary: Boolean): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (primary) COLOR_GROUND else COLOR_INK)
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            stateListAnimator = null
            elevation = 0f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                if (primary) {
                    setColor(COLOR_INK)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), COLOR_BORDER)
                }
            }
        }
    }

    private fun monoLabel(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            typeface = fontJetBrainsMono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_MUTEDLIGHT)
        }
    }
}
