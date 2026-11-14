package com.rvmeur.transfer

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.math.abs

/**
 * Aktivitas utama TransferIn Foundation (Stage 1, Stage 2, dan Stage 3).
 *
 * Menerapkan UI polos dari kode (tanpa dependensi AndroidX), siklus hidup USB,
 * orkestrasi sesi unduh kamera Fujifilm, penjelajah folder Google Drive,
 * serta antrean unggah langsung ke folder tujuan yang dikunci.
 */
class MainActivity : Activity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.rvmeur.transfer.USB_PERMISSION"
        // Vendor ID USB Fujifilm: 0x04CB (1227)
        private const val FUJIFILM_VENDOR_ID = 0x04CB

        const val SCREEN_WIZARD = 0
        const val SCREEN_SESSION = 1
        const val SCREEN_SUMMARY = 2

        // Token Warna Legacy
        private val COLOR_GROUND = Color.parseColor("#FAF8F3")
        private val COLOR_CARD = Color.parseColor("#F6F3EB")
        private val COLOR_SHELL = Color.parseColor("#F0EBE0")
        private val COLOR_INK = Color.parseColor("#26170C")
        private val COLOR_MUTED = Color.parseColor("#4F453F")
        private val COLOR_MUTEDLIGHT = Color.parseColor("#6E625B")
        private val COLOR_BORDER = Color.parseColor("#D2C4BC")
        private val COLOR_HAIRLINE = Color.parseColor("#E8E0D8")
        private val COLOR_EMERALD = Color.parseColor("#2D4636")
        private val COLOR_EMERALDDARK = Color.parseColor("#1E3024")
        private val COLOR_OLIVE = Color.parseColor("#56642B")
        private val COLOR_BURGUNDY = Color.parseColor("#7A1A1A")
        private val COLOR_AMBER = Color.parseColor("#B8860B")
        private val COLOR_ERROR = Color.parseColor("#E53935")
        private val COLOR_MODALDARK = Color.parseColor("#12100E")
    }

    private lateinit var usbManager: UsbManager
    private val db: TransferInDb get() = Engines.db
    private val driveAuth: DriveAuth get() = Engines.driveAuth
    private val driveFolders: DriveFolders get() = Engines.driveFolders
    private val driveUploader: DriveUploader get() = Engines.driveUploader
    private val uploadQueue: UploadQueue get() = Engines.uploadQueue
    private val cullingGate: CullingGate get() = Engines.cullingGate
    private val ptpSession: PtpSession get() = Engines.ptpSession
    private val orchestrator: SessionOrchestrator get() = Engines.orchestrator

    private var lockedFolderId: String? = null
    private var lockedFolderPath: String? = null

    // Tipografi Legacy (dimuat sekali dari assets/fonts/)
    private lateinit var fontCormorantSemibold: Typeface
    private lateinit var fontCormorantMedium: Typeface
    private lateinit var fontDmSansRegular: Typeface
    private lateinit var fontDmSansMedium: Typeface
    private lateinit var fontDmSansBold: Typeface
    private lateinit var fontJetBrainsMono: Typeface

    private lateinit var statusTextView: TextView
    private lateinit var cameraInfoTextView: TextView
    private lateinit var slotTextView: TextView
    private lateinit var statsTextView: TextView
    private lateinit var uploadStatsTextView: TextView
    private lateinit var folderDestinationTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var logTextView: TextView

    // Komponen Pemilih Folder Tujuan (Halaman 1 Wizard)
    private lateinit var btnPickFolder: Button
    private lateinit var btnExplorerNew: Button
    private lateinit var pickerStatusTextView: TextView

    // Komponen Galeri Tinjau Tahan Layar Penuh (Stage 4)
    private lateinit var btnReviewHeld: Button
    private lateinit var btnBuangSisa: Button
    private lateinit var reviewOverlay: FrameLayout
    private lateinit var reviewBgImageView: ImageView
    private lateinit var reviewTintView: View
    private lateinit var reviewTitleTextView: TextView
    private lateinit var reviewStatusTextView: TextView
    private lateinit var btnReviewClose: Button
    private lateinit var reviewListContainer: LinearLayout
    private lateinit var reviewScrollView: ScrollView

    private lateinit var btnStartSession: Button
    private lateinit var btnEndSession: Button
    private lateinit var btnDriveAuth: Button
    private lateinit var btnDisconnectDrive: Button
    private lateinit var btnRetryFailed: Button
    private lateinit var btnShareLog: Button
    private lateinit var btnClearLog: Button

    // Field Baru Wizard & Navigasi Legacy
    private lateinit var headerDot: View
    private lateinit var headerRing: View
    private lateinit var wizardScreen: ScrollView
    private lateinit var sessionScreen: ScrollView
    private lateinit var summaryScreen: ScrollView
    private lateinit var pageHost: FrameLayout
    private lateinit var pageViews: List<View>
    private var currentPage: Int = 0
    private lateinit var warningPill: TextView
    private lateinit var authStatusTextView: TextView
    private lateinit var usbHintTextView: TextView
    private lateinit var summaryTextView: TextView
    private lateinit var btnBackToSetup: Button
    private lateinit var dotNavBar: FrameLayout
    private lateinit var navBgImageView: ImageView
    private lateinit var navTintView: View
    private lateinit var navDots: List<View>
    private lateinit var stepTitleText: TextView
    private val navRefreshHandler = Handler(Looper.getMainLooper())
    private val navRefreshRunnable = Runnable { refreshNavBackground() }
    private var lastDriveConnected: Boolean = false
    private var wasSessionActive: Boolean = false
    private var headerRingAnimator: ValueAnimator? = null

    // Field Baru Hibrida Kaca (G1: Kartu Hero Foto + Blur Jendela Dialog)
    private lateinit var heroCard: FrameLayout
    private lateinit var heroImageView: ImageView
    private lateinit var heroPanel: LinearLayout
    private lateinit var heroTitleTextView: TextView
    private lateinit var heroSpeedTextView: TextView
    private lateinit var heroMetaTextView: TextView
    private val heroExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastHeroBerkasSelesai: Int = 0
    private var crossWindowBlurEnabled: Boolean = false
    private val crossWindowBlurListener = Consumer<Boolean> { enabled ->
        crossWindowBlurEnabled = enabled
    }

    private val hideWarningPillRunnable = Runnable {
        if (::warningPill.isInitialized) {
            warningPill.visibility = View.GONE
        }
    }

    private val permissionRequestInFlight = AtomicBoolean(false)
    private val uiHandler = Handler(Looper.getMainLooper())
    private var isForeground = false

    private val uploadStatsPollRunnable = object : Runnable {
        override fun run() {
            if (!isForeground) return
            renderUploadStats()
            uiHandler.postDelayed(this, 2000L)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    permissionRequestInFlight.set(false)
                    val device = extractUsbDevice(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        FoundationLog.log("Izin USB diberikan untuk ${device.deviceName} (VID=0x${hex16(device.vendorId)}, PID=0x${hex16(device.productId)})")
                        val folderId = lockedFolderId ?: driveFolders.getCurrentFolder().id
                        orchestrator.start(device, folderId)
                        Engines.syncService(this@MainActivity)
                    } else {
                        FoundationLog.error("Izin USB DITOLAK untuk perangkat ${device?.deviceName ?: "(null)"}")
                        lockedFolderId = null
                        lockedFolderPath = null
                    }
                    renderState(orchestrator.state, orchestrator.statistics)
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = extractUsbDevice(intent)
                    if (device != null) {
                        FoundationLog.log("Broadcast USB_DEVICE_ATTACHED diterima: ${device.deviceName} (VID=0x${hex16(device.vendorId)})")
                    }
                    renderState(orchestrator.state, orchestrator.statistics)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    permissionRequestInFlight.set(false)
                    FoundationLog.log("[USB] Broadcast DETACHED diterima — penanganan sesi oleh layanan latar depan")
                    try {
                        renderState(orchestrator.state)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inisialisasi Logger Terpusat
        FoundationLog.init(applicationContext)
        FoundationLog.uiCallback = { line ->
            runOnUiThread {
                appendLogLineToUi(line)
            }
        }

        Engines.init(applicationContext)

        driveAuth.onAuthStatusChanged = { connected, email ->
            runOnUiThread {
                renderDriveUi(connected, email)
                renderUploadStats()
                if (!connected) {
                    handleDriveLogoutCleanup()
                } else {
                    renderFolderUi(orchestrator.state)
                }
            }
        }

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            crossWindowBlurEnabled = windowManager.isCrossWindowBlurEnabled
            windowManager.addCrossWindowBlurEnabledListener(mainExecutor, crossWindowBlurListener)
        }

        orchestrator.listener = object : SessionOrchestrator.Listener {
            override fun onStateChanged(state: SessionOrchestrator.State) {
                runOnUiThread {
                    renderState(state, orchestrator.statistics)
                    renderUploadStats()
                    Engines.syncService(this@MainActivity)
                }
            }

            override fun onStatisticsUpdated(stats: SessionOrchestrator.Statistics) {
                runOnUiThread {
                    renderState(orchestrator.state, stats)
                    renderUploadStats()
                }
            }
        }

        // 4. Bangun Tampilan UI dari Kode (Tanpa AndroidX)
        val rootView = buildRootLayout()
        setContentView(rootView)
        showScreen(SCREEN_WIZARD)
        goToPage(0)
        rootView.post { refreshNavBackground() }
        startHeaderPulseAnimation()

        // 5. Daftarkan Receiver USB
        registerUsbReceiver()

        // 6. Muat snapshot log awal & render state saat ini
        logTextView.text = FoundationLog.snapshot()
        renderState(orchestrator.state, orchestrator.statistics)
        renderDriveUi(driveAuth.isConnected(), driveAuth.accountEmail())
        renderUploadStats()
        renderFolderUi(orchestrator.state)

        FoundationLog.log("=== TransferIn Foundation MainActivity Siap (minSdk 31, targetSdk 35) ===")

        // Tangani intent awal bila aplikasi dibuka karena URI hasil picker atau OAuth redirect
        val initialUri = intent?.data
        if (initialUri != null) {
            rootView.post {
                if (handlePickerResultRedirect(initialUri)) {
                    FoundationLog.log("[FOLDER] Redirect hasil picker ditangani pada onCreate: $initialUri")
                } else {
                    val handled = driveAuth.handleRedirect(initialUri)
                    if (handled) {
                        FoundationLog.log("[AUTH] Redirect ditangani pada onCreate: $initialUri")
                    }
                }
            }
        }

        // Tangani intent awal bila aplikasi dibuka karena USB_DEVICE_ATTACHED
        val attached = intent?.let { extractUsbDevice(it) }
        if (attached != null && attached.vendorId == FUJIFILM_VENDOR_ID) {
            FoundationLog.log("Aplikasi dibuka oleh USB_DEVICE_ATTACHED: ${attached.deviceName}")
        }

        // Melanjutkan pekerjaan tertunda saat aplikasi dibuka
        Engines.syncService(this)

        // Permintaan izin notifikasi Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7331)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)

            // Tangani URI redirect hasil picker atau teruskan redirect OAuth2 ke DriveAuth
            val uri = intent.data
            if (uri != null) {
                if (handlePickerResultRedirect(uri)) {
                    FoundationLog.log("[FOLDER] Redirect hasil picker ditangani di onNewIntent: $uri")
                } else {
                    val handled = driveAuth.handleRedirect(uri)
                    if (handled) {
                        FoundationLog.log("[AUTH] Redirect ditangani di onNewIntent: $uri")
                    }
                }
            }

            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                val device = extractUsbDevice(intent)
                if (device != null && device.vendorId == FUJIFILM_VENDOR_ID) {
                    FoundationLog.log("onNewIntent USB_DEVICE_ATTACHED: ${device.deviceName}")
                }
                renderState(orchestrator.state, orchestrator.statistics)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        renderState(orchestrator.state, orchestrator.statistics)
        renderDriveUi(driveAuth.isConnected(), driveAuth.accountEmail())
        renderUploadStats()
        renderFolderUi(orchestrator.state)

        uiHandler.removeCallbacks(uploadStatsPollRunnable)
        uiHandler.postDelayed(uploadStatsPollRunnable, 2000L)
    }

    override fun onPause() {
        isForeground = false
        uiHandler.removeCallbacks(uploadStatsPollRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        isForeground = false
        uiHandler.removeCallbacks(uploadStatsPollRunnable)
        uiHandler.removeCallbacks(hideWarningPillRunnable)
        navRefreshHandler.removeCallbacks(navRefreshRunnable)
        headerRingAnimator?.cancel()
        headerRingAnimator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                windowManager.removeCrossWindowBlurEnabledListener(crossWindowBlurListener)
            } catch (_: Exception) {
            }
        }
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {
        }
        // Mesin kini milik proses (Engines) dan dijaga oleh layanan latar depan (TransferInService).
        orchestrator.listener = null
        driveAuth.onAuthStatusChanged = null
        FoundationLog.uiCallback = null
        super.onDestroy()
    }

    private fun initFonts() {
        if (::fontCormorantSemibold.isInitialized) return
        fontCormorantSemibold = try {
            Typeface.createFromAsset(assets, "fonts/cormorant_garamond_semibold.ttf")
        } catch (_: Throwable) {
            Typeface.SERIF
        }
        fontCormorantMedium = try {
            Typeface.createFromAsset(assets, "fonts/cormorant_garamond_medium.ttf")
        } catch (_: Throwable) {
            Typeface.SERIF
        }
        fontDmSansRegular = try {
            Typeface.createFromAsset(assets, "fonts/dm_sans_regular.ttf")
        } catch (_: Throwable) {
            Typeface.SANS_SERIF
        }
        fontDmSansMedium = try {
            Typeface.createFromAsset(assets, "fonts/dm_sans_medium.ttf")
        } catch (_: Throwable) {
            Typeface.SANS_SERIF
        }
        fontDmSansBold = try {
            Typeface.createFromAsset(assets, "fonts/dm_sans_bold.ttf")
        } catch (_: Throwable) {
            Typeface.DEFAULT_BOLD
        }
        fontJetBrainsMono = try {
            Typeface.createFromAsset(assets, "fonts/jetbrains-mono-regular.ttf")
        } catch (_: Throwable) {
            Typeface.MONOSPACE
        }
    }

    // (a) doppelrandCard(): LinearLayout luar (bg SHELL, sudut 24dp, padding 6dp)
    // membungkus LinearLayout dalam (bg CARD, sudut 18dp, padding 20dp vertikal isi)
    private fun doppelrandCard(): Pair<LinearLayout, LinearLayout> {
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
                bottomMargin = dp(16)
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

    // (b) kineticButton(teks, primer): Button kapsul penuh, tinggi 52dp, huruf kecil kecuali akronim;
    // primer = bg INK teks GROUND; sekunder = bg transparan border BORDER teks INK
    private fun kineticButton(teks: String, primer: Boolean = true): Button {
        return Button(this).apply {
            text = teks
            isAllCaps = false
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (primer) COLOR_GROUND else COLOR_INK)
            minHeight = dp(52)
            minimumHeight = dp(52)
            setPadding(dp(20), 0, dp(20), 0)
            stateListAnimator = null
            elevation = 0f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                if (primer) {
                    setColor(COLOR_INK)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), COLOR_BORDER)
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        }
    }

    // (c) sectionTitle(teks): TextView Cormorant semibold 20sp INK huruf kecil
    private fun sectionTitle(teks: String): TextView {
        return TextView(this).apply {
            text = teks
            typeface = fontCormorantSemibold
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(8))
        }
    }

    // (d) monoLabel(teks): TextView MONOSPACE 10-11sp MUTEDLIGHT huruf kecil
    private fun monoLabel(teks: String): TextView {
        return TextView(this).apply {
            text = teks
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_MUTEDLIGHT)
        }
    }

    private fun circleDrawable(fillColor: Int, strokeWidthPx: Int = 0, strokeColor: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
    }

    private fun createResponsiveContentContainer(): LinearLayout {
        val maxWidthPx = dp(760)
        return object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
                val spec = if (measuredWidth > maxWidthPx) {
                    MeasureSpec.makeMeasureSpec(maxWidthPx, MeasureSpec.getMode(widthMeasureSpec))
                } else {
                    widthMeasureSpec
                }
                super.onMeasure(spec, heightMeasureSpec)
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
    }

    private fun buildRootLayout(): View {
        initFonts()

        val rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(COLOR_GROUND)
        }

        val rootLL = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_GROUND)
            clipChildren = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1) headerBar horizontal 56dp padding 20dp
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(20), dp(8), dp(20), dp(8))
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val headerLeftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val brandTitle = TextView(this).apply {
            text = "rvmeur"
            typeface = fontCormorantSemibold
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(COLOR_INK)
        }
        val brandSub = monoLabel("tether fujifilm · by rvmeur.")
        headerLeftCol.addView(brandTitle)
        headerLeftCol.addView(brandSub)
        headerBar.addView(headerLeftCol)

        val headerPulseContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        headerRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
            background = circleDrawable(Color.TRANSPARENT, dp(1), COLOR_OLIVE)
        }
        headerDot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER)
            background = circleDrawable(COLOR_BURGUNDY)
        }
        headerPulseContainer.addView(headerRing)
        headerPulseContainer.addView(headerDot)
        headerBar.addView(headerPulseContainer)

        rootLL.addView(headerBar)

        // 2) screenHost FrameLayout (weight 1) berisi TIGA layar (wizardScreen, sessionScreen, summaryScreen) + dotNavBar melayang
        val screenHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        wizardScreen = buildWizardScreen()
        sessionScreen = buildSessionScreen()
        summaryScreen = buildSummaryScreen()
        dotNavBar = buildDotNavBar()

        screenHost.addView(wizardScreen)
        screenHost.addView(sessionScreen)
        screenHost.addView(summaryScreen)
        screenHost.addView(dotNavBar)

        rootLL.addView(screenHost)

        // 4) footerBar 44dp terpusat, klik membuka https://rvmeur.com
        val footerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            )
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rvmeur.com")))
                } catch (_: Exception) {
                }
            }
        }
        val footerDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(8)
            }
            background = circleDrawable(COLOR_EMERALD)
        }
        val footerLabel = monoLabel("rian-ism visual memories - rvmeur.com")
        footerBar.addView(footerDot)
        footerBar.addView(footerLabel)

        rootLL.addView(footerBar)

        rootFrame.addView(rootLL)

        // Panel Tinjau Tahan Layar Penuh (Overlay Stage 4) — saudara terakhir di root
        reviewOverlay = buildReviewOverlay()
        reviewOverlay.visibility = View.GONE
        rootFrame.addView(reviewOverlay)

        return rootFrame
    }

    private fun buildWizardScreen(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnScrollChangeListener { _, _, _, _, _ ->
                navRefreshHandler.removeCallbacks(navRefreshRunnable)
                navRefreshHandler.postDelayed(navRefreshRunnable, 120L)
            }
        }

        val content = createResponsiveContentContainer().apply {
            setPadding(dp(20), dp(20), dp(20), dp(88))
        }

        var touchDownX = 0f
        var touchDownY = 0f
        val swipeThresholdPx = dp(80).toFloat()

        pageHost = object : FrameLayout(this) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = ev.x
                        touchDownY = ev.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.x - touchDownX
                        val dy = ev.y - touchDownY
                        if (abs(dx) > swipeThresholdPx && abs(dx) > abs(dy)) {
                            return true
                        }
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }
        }.apply {
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.x
                        touchDownY = event.y
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - touchDownX
                        if (abs(dx) > swipeThresholdPx) {
                            if (dx < 0 && currentPage < pageViews.size - 1) {
                                goToPage(currentPage + 1)
                            } else if (dx > 0 && currentPage > 0) {
                                goToPage(currentPage - 1)
                            }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }

        val page1 = buildWizardPage1()
        val page2 = buildWizardPage2()
        val page3 = buildWizardPage3()

        pageViews = listOf(page1, page2, page3)
        pageHost.addView(page1)
        pageHost.addView(page2)
        pageHost.addView(page3)

        content.addView(pageHost)

        warningPill = TextView(this).apply {
            text = "selesaikan otentikasi & pilih folder terlebih dahulu."
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(COLOR_BURGUNDY)
            }
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
        content.addView(warningPill)

        scroll.addView(content)
        return scroll
    }

    // HALAMAN 1 "01 google drive"
    private fun buildWizardPage1(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Kartu A (doppelrand): akun google drive.
        val (cardAOuter, cardAInner) = doppelrandCard()
        cardAInner.addView(sectionTitle("akun google drive."))

        authStatusTextView = TextView(this).apply {
            text = "belum terhubung - masuk untuk memilih folder tujuan."
            typeface = fontDmSansRegular
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_MUTED)
            setPadding(0, 0, 0, dp(12))
        }
        cardAInner.addView(authStatusTextView)

        val rowDrive = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        btnDriveAuth = kineticButton("hubungkan google drive", primer = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
            setOnClickListener {
                if (!driveAuth.isConnected()) {
                    driveAuth.beginAuth(this@MainActivity)
                }
            }
        }

        btnDisconnectDrive = kineticButton("putuskan", primer = false).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(52)
            ).apply {
                marginStart = dp(8)
            }
            setOnClickListener {
                driveAuth.disconnect()
                handleDriveLogoutCleanup()
            }
        }

        rowDrive.addView(btnDriveAuth)
        rowDrive.addView(btnDisconnectDrive)
        cardAInner.addView(rowDrive)
        page.addView(cardAOuter)

        // Kartu B (doppelrand): folder tujuan. (Google Picker + folder baru)
        val (cardBOuter, cardBInner) = doppelrandCard()
        cardBInner.addView(sectionTitle("folder tujuan."))

        folderDestinationTextView = TextView(this).apply {
            text = if (driveFolders.hasSelectedFolder()) driveFolders.getCurrentFolder().name else "belum dipilih."
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(12))
        }
        cardBInner.addView(folderDestinationTextView)

        btnPickFolder = kineticButton("pilih folder tujuan.", primer = true).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener {
                pickFolderClicked()
            }
        }
        cardBInner.addView(btnPickFolder)

        btnExplorerNew = kineticButton("folder baru.", primer = false).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
            setOnClickListener {
                promptCreateFolder()
            }
        }
        cardBInner.addView(btnExplorerNew)

        pickerStatusTextView = monoLabel("").apply {
            setPadding(0, dp(8), 0, 0)
        }
        cardBInner.addView(pickerStatusTextView)

        page.addView(cardBOuter)

        return page
    }

    // HALAMAN 2 "02 camera tethering"
    private fun buildWizardPage2(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val (cardOuter, cardInner) = doppelrandCard()
        cardInner.addView(sectionTitle("kamera usb."))

        cameraInfoTextView = TextView(this).apply {
            text = "Kamera: - | Serial: - | Mode: Tether Auto (dari bodi)"
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(10))
        }
        cardInner.addView(cameraInfoTextView)

        usbHintTextView = TextView(this).apply {
            text = "colokkan kamera fujifilm via usb-c, nyalakan kamera, dan pastikan mode usb tether aktif. izin usb diminta saat sesi dimulai."
            typeface = fontDmSansRegular
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_MUTED)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }
        cardInner.addView(usbHintTextView)

        page.addView(cardOuter)
        return page
    }

    // HALAMAN 3 "03 session rules"
    private fun buildWizardPage3(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Kartu A: aturan sesi.
        val (cardAOuter, cardAInner) = doppelrandCard()
        cardAInner.addView(sectionTitle("aturan sesi."))

        val rules = listOf(
            "pasangan RAW+JPG · satu grup satu nomor.",
            "penamaan IMG_<tanggal>_<nomor urut>.",
            "baseline jepretan lama di kartu diabaikan.",
            "gerbang culling aktif · foto meragukan ditahan untuk tinjauan manual, tidak pernah dibuang otomatis.",
            "unggah otomatis ke folder tujuan · 3 pekerja paralel."
        )
        for ((index, ruleText) in rules.withIndex()) {
            val tv = TextView(this).apply {
                text = ruleText
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(COLOR_MUTED)
                setPadding(0, dp(6), 0, if (index == rules.lastIndex) 0 else dp(6))
            }
            cardAInner.addView(tv)
        }

        val btnShareLogWizard = monoLabel("bagikan log.").apply {
            isClickable = true
            isFocusable = true
            setPadding(0, dp(12), 0, 0)
            setOnClickListener {
                shareLogSnapshot()
            }
        }
        cardAInner.addView(btnShareLogWizard)

        page.addView(cardAOuter)

        // Kartu B: mulai sesi tethering.
        val (cardBOuter, cardBInner) = doppelrandCard()
        btnStartSession = kineticButton("mulai sesi tethering.", primer = true).apply {
            setOnClickListener {
                startSessionClicked()
            }
        }
        cardBInner.addView(btnStartSession)

        val lockNote = TextView(this).apply {
            text = "folder tujuan terkunci selama sesi berjalan."
            typeface = fontDmSansRegular
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_MUTEDLIGHT)
            setPadding(0, dp(10), 0, 0)
        }
        cardBInner.addView(lockNote)

        page.addView(cardBOuter)
        return page
    }

    // B) sessionScreen: konten vertikal padding 20dp maks 760dp terpusat
    private fun buildSessionScreen(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val content = createResponsiveContentContainer()

        // 0. Kartu hero status sesi (paling atas sebelum kartu kamera)
        heroCard = FrameLayout(this).apply {
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
                bottomMargin = dp(16)
            }
        }

        heroImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(null)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        heroCard.addView(heroImageView)

        heroTitleTextView = TextView(this).apply {
            text = "menunggu jepretan pertama."
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(4))
        }

        heroSpeedTextView = TextView(this).apply {
            text = ""
            typeface = Typeface.create(fontJetBrainsMono, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(4))
        }

        heroMetaTextView = TextView(this).apply {
            text = "foto terakhir tampil di sini sebagai latar kaca."
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_MUTED)
        }

        heroPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xB8FAF8F3.toInt())
                setStroke(dp(1), 0x8CFFFFFF.toInt())
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                setMargins(dp(12), dp(12), dp(12), dp(12))
            }
        }
        heroPanel.addView(heroTitleTextView)
        heroPanel.addView(heroSpeedTextView)
        heroPanel.addView(heroMetaTextView)
        heroCard.addView(heroPanel)
        content.addView(heroCard)

        // 1. Kartu kamera
        val (camOuter, camInner) = doppelrandCard()
        camInner.addView(sectionTitle("sesi aktif."))

        statusTextView = TextView(this).apply {
            text = "Status: Menunggu kamera"
            typeface = fontDmSansBold
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(6))
        }
        camInner.addView(statusTextView)

        slotTextView = TextView(this).apply {
            text = "Slot ruang tunggu: 12/12"
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_OLIVE)
        }
        camInner.addView(slotTextView)
        content.addView(camOuter)

        // 2. Kartu metrik
        val (metricOuter, metricInner) = doppelrandCard()
        metricInner.addView(sectionTitle("antrean & unggahan."))

        statsTextView = TextView(this).apply {
            text = "Grup selesai: 0 - Berkas: 0 - Total: 0.00 MB - Terakhir: 0.00 MB/s"
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_INK)
            setPadding(0, 0, 0, dp(6))
        }
        metricInner.addView(statsTextView)

        uploadStatsTextView = TextView(this).apply {
            text = "Unggah — antre: 0 • proses: 0 • selesai: 0 • gagal: 0"
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_MUTED)
        }
        metricInner.addView(uploadStatsTextView)
        content.addView(metricOuter)

        // 3. Kartu aksi
        val (actionOuter, actionInner) = doppelrandCard()

        btnReviewHeld = kineticButton("tinjau tahan (0)", primer = false).apply {
            isEnabled = false
            alpha = 0.4f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener {
                openReviewOverlay()
            }
        }
        actionInner.addView(btnReviewHeld)

        btnBuangSisa = kineticButton("buang sisa kamera (0)", primer = false).apply {
            isEnabled = false
            alpha = 0.4f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener {
                orchestrator.requestBuangSisaKamera()
            }
        }
        actionInner.addView(btnBuangSisa)

        btnRetryFailed = kineticButton("ulangi gagal", primer = false).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener {
                val requeued = db.requeueFailed()
                Engines.syncService(this@MainActivity)
                if (requeued > 0) {
                    uploadQueue.kick()
                }
                renderUploadStats()
            }
        }
        actionInner.addView(btnRetryFailed)

        btnEndSession = kineticButton("selesaikan sesi tethering.", primer = true).apply {
            setOnClickListener {
                orchestrator.stop()
            }
        }
        actionInner.addView(btnEndSession)
        content.addView(actionOuter)

        // 4. Kartu log
        val (logOuter, logInner) = doppelrandCard()
        logInner.addView(sectionTitle("log."))

        logTextView = TextView(this).apply {
            setTextColor(COLOR_INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = fontJetBrainsMono
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        logScrollView = ScrollView(this).apply {
            isFillViewport = true
            isNestedScrollingEnabled = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(COLOR_GROUND)
                setStroke(dp(1), COLOR_HAIRLINE)
            }
            addView(
                logTextView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220)
            ).apply {
                bottomMargin = dp(12)
            }
        }
        logInner.addView(logScrollView)

        val logBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        btnShareLog = kineticButton("bagikan log.", primer = false).apply {
            minHeight = dp(40)
            minimumHeight = dp(40)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                shareLogSnapshot()
            }
        }

        btnClearLog = kineticButton("hapus log.", primer = false).apply {
            minHeight = dp(40)
            minimumHeight = dp(40)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener {
                logTextView.text = ""
                FoundationLog.clear(this@MainActivity)
            }
        }

        logBtnRow.addView(btnShareLog)
        logBtnRow.addView(btnClearLog)
        logInner.addView(logBtnRow)
        content.addView(logOuter)

        scroll.addView(content)
        return scroll
    }

    // C) summaryScreen: Kartu ringkasan sesi.
    private fun buildSummaryScreen(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val content = createResponsiveContentContainer()

        val (cardOuter, cardInner) = doppelrandCard()
        cardInner.addView(sectionTitle("ringkasan sesi."))

        summaryTextView = TextView(this).apply {
            text = "sesi selesai."
            typeface = fontDmSansRegular
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_INK)
            setLineSpacing(dp(4).toFloat(), 1.0f)
            setPadding(0, 0, 0, dp(16))
        }
        cardInner.addView(summaryTextView)

        btnBackToSetup = kineticButton("selesai & kembali ke setup.", primer = true).apply {
            setOnClickListener {
                showScreen(SCREEN_WIZARD)
                goToPage(0)
            }
        }
        cardInner.addView(btnBackToSetup)

        content.addView(cardOuter)
        scroll.addView(content)
        return scroll
    }

    // 3) dotNavBar kapsul melayang berkaca (bg SHELL alpha, border HAIRLINE, sudut kapsul, tinggi 52dp, lebar maks 340dp)
    private fun buildDotNavBar(): FrameLayout {
        val maxWidthPx = dp(340)
        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(18), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val bar = object : FrameLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
                val spec = if (measuredWidth > maxWidthPx) {
                    MeasureSpec.makeMeasureSpec(maxWidthPx, MeasureSpec.AT_MOST)
                } else {
                    widthMeasureSpec
                }
                contentRow.measure(spec, heightMeasureSpec)
                val targetW = contentRow.measuredWidth.coerceIn(1, maxWidthPx)
                val targetH = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(dp(52))
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(targetW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY)
                )
            }
        }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(
                    Color.argb(
                        235,
                        Color.red(COLOR_SHELL),
                        Color.green(COLOR_SHELL),
                        Color.blue(COLOR_SHELL)
                    )
                )
                setStroke(dp(1), COLOR_HAIRLINE)
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(52),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(14)
            }
        }

        navBgImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        bar.addView(navBgImageView)

        navTintView = View(this).apply {
            setBackgroundColor(
                Color.argb(
                    102,
                    Color.red(COLOR_GROUND),
                    Color.green(COLOR_GROUND),
                    Color.blue(COLOR_GROUND)
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        bar.addView(navTintView)

        val dots = mutableListOf<View>()
        for (idx in 0 until 3) {
            val touchBox = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setOnClickListener {
                    goToPage(idx)
                }
            }
            val dotView = View(this).apply {
                isClickable = true
                layoutParams = FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(999).toFloat()
                    setColor(COLOR_BORDER)
                }
                setOnClickListener {
                    goToPage(idx)
                }
            }
            touchBox.addView(dotView)
            contentRow.addView(touchBox)
            dots.add(dotView)
        }
        navDots = dots

        stepTitleText = TextView(this).apply {
            text = "01 google drive"
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_INK)
            setPadding(dp(6), 0, 0, 0)
        }
        contentRow.addView(stepTitleText)
        bar.addView(contentRow)

        return bar
    }

    private fun refreshNavBackground() {
        if (!::dotNavBar.isInitialized || !::wizardScreen.isInitialized || !::navBgImageView.isInitialized) return
        if (dotNavBar.visibility != View.VISIBLE) return
        try {
            val pillW = dotNavBar.width
            val pillH = dotNavBar.height
            if (pillW <= 0 || pillH <= 0) return
            val contentView = wizardScreen.getChildAt(0) ?: return
            if (contentView.width <= 0 || contentView.height <= 0) return

            val pillLoc = IntArray(2)
            val scrollLoc = IntArray(2)
            dotNavBar.getLocationInWindow(pillLoc)
            wizardScreen.getLocationInWindow(scrollLoc)

            val offsetX = (pillLoc[0] - scrollLoc[0] + wizardScreen.scrollX - contentView.left).toFloat()
            val offsetY = (pillLoc[1] - scrollLoc[1] + wizardScreen.scrollY - contentView.top).toFloat()

            val scale = 0.25f
            val bmpW = (pillW * scale).toInt().coerceAtLeast(1)
            val bmpH = (pillH * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            canvas.translate(-offsetX, -offsetY)
            canvas.drawColor(COLOR_GROUND)
            contentView.draw(canvas)
            navBgImageView.setImageBitmap(bmp)
        } catch (_: Throwable) {
            if (::navBgImageView.isInitialized) {
                navBgImageView.setImageDrawable(null)
            }
        }
    }

    private fun startHeaderPulseAnimation() {
        if (headerRingAnimator != null || !::headerRing.isInitialized) return
        headerRingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val scale = 1.0f + fraction * 1.0f
                headerRing.scaleX = scale
                headerRing.scaleY = scale
                headerRing.alpha = 0.7f * (1.0f - fraction)
            }
            start()
        }
    }

    private fun showScreen(which: Int) {
        if (!::wizardScreen.isInitialized) return
        wizardScreen.visibility = if (which == SCREEN_WIZARD) View.VISIBLE else View.GONE
        sessionScreen.visibility = if (which == SCREEN_SESSION) View.VISIBLE else View.GONE
        summaryScreen.visibility = if (which == SCREEN_SUMMARY) View.VISIBLE else View.GONE
        dotNavBar.visibility = if (which == SCREEN_WIZARD) View.VISIBLE else View.GONE
        refreshNavBackground()
    }

    private fun step1Valid(): Boolean {
        val label = if (::folderDestinationTextView.isInitialized) {
            folderDestinationTextView.text.toString().trim()
        } else {
            ""
        }
        val folderValid = driveFolders.hasSelectedFolder() &&
            label.isNotBlank() &&
            label != "belum dipilih."
        return lastDriveConnected && folderValid
    }

    private fun goToPage(i: Int) {
        if (!::pageViews.isInitialized || i < 0 || i >= pageViews.size) return
        if (i > currentPage && currentPage == 0 && !step1Valid()) {
            if (::warningPill.isInitialized) {
                warningPill.text = "selesaikan otentikasi & pilih folder terlebih dahulu."
                warningPill.visibility = View.VISIBLE
                uiHandler.removeCallbacks(hideWarningPillRunnable)
                uiHandler.postDelayed(hideWarningPillRunnable, 3000L)
            }
            return
        }

        if (::warningPill.isInitialized) {
            warningPill.visibility = View.GONE
        }
        currentPage = i
        for ((idx, view) in pageViews.withIndex()) {
            view.visibility = if (idx == i) View.VISIBLE else View.GONE
        }
        if (::navDots.isInitialized) {
            for ((idx, dot) in navDots.withIndex()) {
                val active = (idx == i)
                val lp = dot.layoutParams
                lp.width = if (active) dp(26) else dp(10)
                lp.height = dp(10)
                dot.layoutParams = lp
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(999).toFloat()
                    setColor(if (active) COLOR_EMERALD else COLOR_BORDER)
                }
            }
        }
        if (::stepTitleText.isInitialized) {
            val stepTitles = listOf("01 google drive", "02 camera tethering", "03 session rules")
            stepTitleText.text = stepTitles.getOrElse(i) { "" }
        }
        refreshNavBackground()
    }

    private fun showScreenForState(
        state: SessionOrchestrator.State,
        stats: SessionOrchestrator.Statistics = orchestrator.statistics
    ) {
        val cameraDetected = findAttachedFujiDevice() != null
        if (::headerDot.isInitialized) {
            val isOlive = (state == SessionOrchestrator.State.MONITORING ||
                state == SessionOrchestrator.State.BASELINE ||
                cameraDetected)
            headerDot.background = circleDrawable(if (isOlive) COLOR_OLIVE else COLOR_BURGUNDY)
        }

        when (state) {
            SessionOrchestrator.State.CONNECTING,
            SessionOrchestrator.State.BASELINE,
            SessionOrchestrator.State.MONITORING,
            SessionOrchestrator.State.CLOSING -> {
                wasSessionActive = true
                showScreen(SCREEN_SESSION)
            }
            SessionOrchestrator.State.CLOSED -> {
                if (wasSessionActive) {
                    wasSessionActive = false
                    if (::summaryTextView.isInitialized) {
                        val counts = if (Engines.initialized) db.countsByStatus() else emptyMap()
                        val done = counts[ItemStatus.DONE] ?: 0
                        val failed = counts[ItemStatus.FAILED_FINAL] ?: 0
                        val held = counts[ItemStatus.CULLING_HOLD] ?: 0
                        val folderPath = lockedFolderPath
                            ?: if (driveFolders.hasSelectedFolder()) driveFolders.getCurrentFolder().name else "belum dipilih."
                        summaryTextView.text = String.format(
                            Locale.US,
                            "folder tujuan: %s\ngrup selesai diunduh: %d\nberkas diunduh: %d (%.2f MB)\nkecepatan terakhir: %.2f MB/s\nunggahan selesai: %d · gagal: %d · tahan: %d",
                            folderPath,
                            stats.grupSelesai,
                            stats.berkasSelesai,
                            stats.totalMbDownloaded,
                            stats.lastDownloadMbPerSec,
                            done,
                            failed,
                            held
                        )
                    }
                    showScreen(SCREEN_SUMMARY)
                }
            }
            SessionOrchestrator.State.IDLE -> {
                showScreen(SCREEN_WIZARD)
            }
            SessionOrchestrator.State.ERROR -> {
                if (wasSessionActive) {
                    showScreen(SCREEN_SESSION)
                }
            }
        }
    }

    private fun renderState(state: SessionOrchestrator.State, stats: SessionOrchestrator.Statistics = orchestrator.statistics) {
        val fujiDevice = findAttachedFujiDevice()

        val statusLabel = when (state) {
            SessionOrchestrator.State.IDLE -> {
                if (fujiDevice != null) "Kamera Siap (Tekan Mulai Sesi)" else "Menunggu kamera..."
            }
            SessionOrchestrator.State.CONNECTING -> "Menghubungkan ke kamera..."
            SessionOrchestrator.State.BASELINE -> "Mengambil baseline kamera..."
            SessionOrchestrator.State.MONITORING -> "Memantau kamera (Tether aktif)"
            SessionOrchestrator.State.CLOSING -> "Menutup sesi..."
            SessionOrchestrator.State.CLOSED -> "Sesi ditutup"
            SessionOrchestrator.State.ERROR -> "Terjadi kesalahan (ERROR)"
        }
        statusTextView.text = "Status: $statusLabel"

        val model = if (stats.cameraModel.isNotEmpty()) stats.cameraModel else ptpSession.lastDeviceInfo?.model ?: "-"
        val serial = if (stats.cameraSerial.isNotEmpty()) stats.cameraSerial else ptpSession.lastDeviceInfo?.serialNumber ?: "-"
        cameraInfoTextView.text = "Kamera: $model | Serial: $serial | Mode: Tether Auto (dari bodi)"

        slotTextView.text = "Slot ruang tunggu: ${stats.slotRuangTunggu}/12"

        statsTextView.text = String.format(
            Locale.US,
            "Grup selesai: %d - Berkas: %d - Total: %.2f MB - Terakhir: %.2f MB/s",
            stats.grupSelesai,
            stats.berkasSelesai,
            stats.totalMbDownloaded,
            stats.lastDownloadMbPerSec
        )

        // Aturan tombol:
        // Mulai Sesi aktif hanya bila USB tersambung dan orkestrator IDLE/CLOSED
        val canStart = (fujiDevice != null) && (state == SessionOrchestrator.State.IDLE || state == SessionOrchestrator.State.CLOSED)
        btnStartSession.isEnabled = canStart

        val isRunning = (state == SessionOrchestrator.State.CONNECTING ||
            state == SessionOrchestrator.State.BASELINE ||
            state == SessionOrchestrator.State.MONITORING ||
            state == SessionOrchestrator.State.ERROR)
        btnEndSession.isEnabled = isRunning

        if (::btnBuangSisa.isInitialized) {
            val sisa = orchestrator.sisaKameraCount
            btnBuangSisa.text = "Buang Sisa Kamera ($sisa)"
            btnBuangSisa.isEnabled = isRunning && sisa > 0
            btnBuangSisa.alpha = if (isRunning && sisa > 0) 1.0f else 0.4f
        }

        renderFolderUi(state)
        showScreenForState(state, stats)

        if (::heroSpeedTextView.isInitialized) {
            heroSpeedTextView.text = String.format(Locale.US, "%.1f MB/s", stats.lastDownloadMbPerSec).replace('.', ',')
        }
        if (::heroMetaTextView.isInitialized) {
            heroMetaTextView.text = String.format(
                Locale.US,
                "grup selesai %d · berkas %d · slot %d/12",
                stats.grupSelesai,
                stats.berkasSelesai,
                stats.slotRuangTunggu
            )
        }
        if (stats.berkasSelesai < lastHeroBerkasSelesai &&
            state != SessionOrchestrator.State.CLOSED &&
            state != SessionOrchestrator.State.IDLE
        ) {
            lastHeroBerkasSelesai = 0
        }
        if (stats.berkasSelesai > lastHeroBerkasSelesai) {
            lastHeroBerkasSelesai = stats.berkasSelesai
            refreshHeroPhoto()
        }
    }

    private fun refreshHeroPhoto() {
        if (!Engines.initialized) return
        val sessionId = orchestrator.currentSessionId ?: return
        heroExecutor.execute {
            try {
                val statuses = listOf(
                    ItemStatus.CULLING_PENDING,
                    ItemStatus.QUEUED,
                    ItemStatus.UPLOADING,
                    ItemStatus.DONE
                )
                val allItems = mutableListOf<TransferItem>()
                for (st in statuses) {
                    allItems.addAll(db.getItemsBySessionAndStatus(sessionId, st))
                }
                val candidates = allItems.filter { item ->
                    val isJpg = item.filename.endsWith(".jpg", ignoreCase = true) ||
                        item.filename.endsWith(".jpeg", ignoreCase = true)
                    if (isJpg) {
                        true
                    } else {
                        val localFile = File(item.localPath)
                        val previewFile = File(localFile.parentFile, "${localFile.nameWithoutExtension}.preview.jpg")
                        previewFile.exists() && previewFile.length() > 0L
                    }
                }
                val chosen = candidates.maxWithOrNull(
                    compareBy<TransferItem> { it.captureDate ?: "" }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                        .thenBy {
                            if (it.filename.endsWith(".jpg", ignoreCase = true) ||
                                it.filename.endsWith(".jpeg", ignoreCase = true)
                            ) 1 else 0
                        }
                ) ?: return@execute

                val decoded = decodeGroupThumbnail(listOf(chosen), 480) ?: return@execute
                val targetW = (decoded.width / 4).coerceAtLeast(1)
                val targetH = (decoded.height / 4).coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                if (scaled !== decoded) {
                    decoded.recycle()
                }

                val blurEffect = RenderEffect.createBlurEffect(26f, 26f, Shader.TileMode.CLAMP)
                val saturationMatrix = ColorMatrix().apply { setSaturation(1.2f) }
                val colorFilterEffect = RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(saturationMatrix)
                )
                val chainEffect = RenderEffect.createChainEffect(blurEffect, colorFilterEffect)
                val displayName = chosen.driveName?.takeIf { it.isNotEmpty() }
                    ?: File(chosen.localPath).name.ifEmpty { chosen.filename }

                runOnUiThread {
                    if (::heroImageView.isInitialized) {
                        heroImageView.setImageBitmap(scaled)
                        heroImageView.setRenderEffect(chainEffect)
                    }
                    if (::heroTitleTextView.isInitialized) {
                        heroTitleTextView.text = displayName
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun applyGlassToDialog(dialog: Dialog) {
        if (crossWindowBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialog.window?.setBackgroundBlurRadius(80)
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }

    private fun isSessionLocked(state: SessionOrchestrator.State = orchestrator.state): Boolean {
        return lockedFolderId != null ||
            state == SessionOrchestrator.State.CONNECTING ||
            state == SessionOrchestrator.State.BASELINE ||
            state == SessionOrchestrator.State.MONITORING ||
            state == SessionOrchestrator.State.CLOSING ||
            state == SessionOrchestrator.State.ERROR
    }

    private fun pickFolderClicked() {
        if (!driveAuth.isConnected()) {
            pickerStatusTextView.text = "hubungkan google drive dulu."
            pickerStatusTextView.setTextColor(COLOR_MUTEDLIGHT)
            refreshNavBackground()
            return
        }
        if (isSessionLocked()) {
            pickerStatusTextView.text = "folder terkunci selama sesi."
            pickerStatusTextView.setTextColor(COLOR_MUTEDLIGHT)
            refreshNavBackground()
            return
        }

        Thread({
            val token = driveAuth.getAccessToken()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (token.isNullOrEmpty()) {
                    pickerStatusTextView.text = "pemilih folder gagal: token akses tidak tersedia."
                    pickerStatusTextView.setTextColor(COLOR_ERROR)
                    refreshNavBackground()
                    return@runOnUiThread
                }

                try {
                    val pickerUri = Uri.parse("https://rvmeur.com/tether-picker.html#at=" + Uri.encode(token))
                    val intent = Intent(Intent.ACTION_VIEW, pickerUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    var isChromeAvailable = false
                    try {
                        packageManager.getPackageInfo("com.android.chrome", 0)
                        isChromeAvailable = true
                    } catch (e: PackageManager.NameNotFoundException) {
                        isChromeAvailable = false
                    } catch (t: Throwable) {
                        isChromeAvailable = false
                    }

                    if (isChromeAvailable) {
                        intent.setPackage("com.android.chrome")
                        intent.putExtra("android.support.customtabs.extra.SESSION", Bundle())
                        FoundationLog.log("[FOLDER] Membuka browser untuk pemilih folder (chrome-customtab)")
                    } else {
                        FoundationLog.log("[FOLDER] Membuka browser untuk pemilih folder (browser-umum)")
                    }

                    startActivity(intent)
                    pickerStatusTextView.text = "pemilih folder dibuka di browser — pilih folder, kamu akan kembali ke sini."
                    pickerStatusTextView.setTextColor(COLOR_MUTEDLIGHT)
                    refreshNavBackground()
                } catch (t: Throwable) {
                    FoundationLog.error("[FOLDER] Gagal membuka browser untuk pemilih folder", t)
                    pickerStatusTextView.text = "pemilih folder gagal: tidak dapat membuka browser."
                    pickerStatusTextView.setTextColor(COLOR_ERROR)
                    refreshNavBackground()
                }
            }
        }, "drive-picker-token").start()
    }

    private fun handlePickerResultRedirect(uri: Uri): Boolean {
        return try {
            val scheme = uri.scheme ?: ""
            val host = uri.host ?: ""
            val path = uri.path ?: ""
            val uriStr = uri.toString()

            val isPickerRedirect = (scheme == "com.rvmeur.transfer") &&
                (path == "/picker-result" || host == "picker-result" || uriStr.startsWith("com.rvmeur.transfer:/picker-result"))
            if (!isPickerRedirect) {
                return false
            }

            val folderId = uri.getQueryParameter("folder_id") ?: ""
            val isValidFolderId = folderId.length in 10..80 &&
                folderId.all { ch ->
                    ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '-'
                }

            if (isValidFolderId) {
                val rawName = uri.getQueryParameter("folder_name")?.trim().orEmpty()
                val clippedName = if (rawName.length > 120) rawName.substring(0, 120).trim() else rawName
                val folderName = clippedName.ifEmpty { folderId }

                driveFolders.saveSelectedFolder(folderId, folderName)
                renderFolderUi(orchestrator.state)
                if (::pickerStatusTextView.isInitialized) {
                    pickerStatusTextView.text = "folder tujuan: $folderName"
                    pickerStatusTextView.setTextColor(COLOR_MUTEDLIGHT)
                }
                val valid = step1Valid()
                if (valid && ::warningPill.isInitialized) {
                    warningPill.visibility = View.GONE
                }
                refreshNavBackground()
            } else {
                FoundationLog.error("[FOLDER] Hasil pemilih folder tidak valid pada redirect: $uri")
                if (::pickerStatusTextView.isInitialized) {
                    pickerStatusTextView.text = "hasil pemilih folder tidak valid — coba lagi."
                    pickerStatusTextView.setTextColor(COLOR_ERROR)
                }
                refreshNavBackground()
            }

            true
        } catch (t: Throwable) {
            FoundationLog.error("[FOLDER] Gagal menangani hasil picker", t)
            true
        }
    }

    private fun promptCreateFolder() {
        FoundationLog.log("[FOLDER] promptCreateFolder dibuka")
        if (isSessionLocked()) {
            pickerStatusTextView.text = "folder terkunci selama sesi."
            pickerStatusTextView.setTextColor(COLOR_MUTEDLIGHT)
            refreshNavBackground()
            return
        }
        if (!driveFolders.hasSelectedFolder()) {
            pickerStatusTextView.text = "pilih folder tujuan dulu."
            pickerStatusTextView.setTextColor(COLOR_MUTEDLIGHT)
            refreshNavBackground()
            return
        }

        val input = EditText(this).apply {
            hint = "nama folder"
            isSingleLine = true
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("folder baru.")
            .setView(input)
            .setPositiveButton("buat.") { _, _ ->
                val nama = input.text?.toString()?.trim().orEmpty()
                if (nama.isNotEmpty()) {
                    val parentId = driveFolders.getCurrentFolder().id
                    driveFolders.createFolder(nama, parentId) { success, newFolderId ->
                        FoundationLog.log("[FOLDER] callback buat folder: success=$success id=$newFolderId")
                        runOnUiThread {
                            if (success && !newFolderId.isNullOrEmpty()) {
                                driveFolders.saveSelectedFolder(newFolderId, nama)
                                renderFolderUi(orchestrator.state)
                                pickerStatusTextView.text = "folder baru jadi tujuan: $nama"
                                pickerStatusTextView.setTextColor(COLOR_MUTEDLIGHT)
                                val valid = step1Valid()
                                if (valid && ::warningPill.isInitialized) {
                                    warningPill.visibility = View.GONE
                                }
                                refreshNavBackground()
                            } else {
                                pickerStatusTextView.text = "gagal membuat folder."
                                pickerStatusTextView.setTextColor(COLOR_ERROR)
                                refreshNavBackground()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("batal.", null)
            .create()
        applyGlassToDialog(dialog)
        FoundationLog.log("[FOLDER] dialog folder baru ditampilkan")
        dialog.show()
    }

    private fun handleDriveLogoutCleanup() {
        driveFolders.clearSavedFolder()
        getSharedPreferences(DriveFolders.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(DriveFolders.KEY_TRAIL_JSON)
            .apply()
        if (::folderDestinationTextView.isInitialized) {
            folderDestinationTextView.text = "belum dipilih."
        }
        if (::pickerStatusTextView.isInitialized) {
            pickerStatusTextView.text = ""
            pickerStatusTextView.setTextColor(COLOR_MUTEDLIGHT)
        }
        renderFolderUi(orchestrator.state)
        val valid = step1Valid()
        if (!valid && currentPage > 0) {
            goToPage(0)
        }
        refreshNavBackground()
    }

    private fun renderFolderUi(state: SessionOrchestrator.State = orchestrator.state) {
        if (!::folderDestinationTextView.isInitialized) return

        val isSessionActive = (state == SessionOrchestrator.State.CONNECTING ||
            state == SessionOrchestrator.State.BASELINE ||
            state == SessionOrchestrator.State.MONITORING ||
            state == SessionOrchestrator.State.CLOSING ||
            state == SessionOrchestrator.State.ERROR)

        if (isSessionActive) {
            val currentLocked = lockedFolderPath
                ?: if (driveFolders.hasSelectedFolder()) driveFolders.getCurrentFolder().name else "belum dipilih."
            folderDestinationTextView.text = "$currentLocked (terkunci oleh sesi berjalan)"
        } else {
            lockedFolderId = null
            lockedFolderPath = null
            folderDestinationTextView.text = if (driveFolders.hasSelectedFolder()) {
                driveFolders.getCurrentFolder().name
            } else {
                "belum dipilih."
            }
        }
    }

    private fun renderDriveUi(connected: Boolean, email: String?) {
        lastDriveConnected = connected
        if (::authStatusTextView.isInitialized) authStatusTextView.text = if (connected) "terhubung - " + (email ?: "") else "belum terhubung - masuk untuk memilih folder tujuan."
        if (!::btnDriveAuth.isInitialized) return
        if (connected) {
            val mail = if (!email.isNullOrEmpty()) email else driveAuth.accountEmail() ?: "Terhubung"
            btnDriveAuth.text = "Drive: $mail"
            btnDriveAuth.isEnabled = false
            btnDisconnectDrive.visibility = View.VISIBLE
        } else {
            btnDriveAuth.text = "Hubungkan Google Drive"
            btnDriveAuth.isEnabled = true
            btnDisconnectDrive.visibility = View.GONE
        }
    }

    private fun renderUploadStats() {
        if (!::uploadStatsTextView.isInitialized || !Engines.initialized) return
        val counts = db.countsByStatus()
        val queued = counts[ItemStatus.QUEUED] ?: 0
        val uploading = (counts[ItemStatus.UPLOADING] ?: 0) + (counts[ItemStatus.VERIFYING] ?: 0)
        val done = counts[ItemStatus.DONE] ?: 0
        val failed = counts[ItemStatus.FAILED_FINAL] ?: 0

        val held = counts[ItemStatus.CULLING_HOLD] ?: 0
        uploadStatsTextView.text = "Unggah — antre: $queued • proses: $uploading • selesai: $done • gagal: $failed • tahan: $held"
        btnRetryFailed.visibility = if (failed > 0) View.VISIBLE else View.GONE

        // Perbarui label & status tombol 'Tinjau Tahan (N)' (N = jumlah GRUP tertahan)
        if (::btnReviewHeld.isInitialized) {
            val heldGroups = db.getHeldCullingGroups()
            val groupCount = heldGroups.size
            btnReviewHeld.text = "Tinjau Tahan ($groupCount)"
            btnReviewHeld.isEnabled = groupCount > 0
            btnReviewHeld.alpha = if (groupCount > 0) 1.0f else 0.4f
        }
    }

    private fun startSessionClicked() {
        val device = findAttachedFujiDevice()
        if (device == null) {
            FoundationLog.log("Mulai Sesi: Tidak ada kamera Fujifilm (VID 0x04CB) yang terdeteksi.")
            return
        }

        val activeFolder = driveFolders.getCurrentFolder()
        val activePath = if (driveFolders.hasSelectedFolder()) activeFolder.name else driveFolders.getCurrentPath()
        lockedFolderId = activeFolder.id
        lockedFolderPath = activePath
        orchestrator.targetDriveFolderId = activeFolder.id
        FoundationLog.log("[SESI] Mulai Sesi mengunci folder tujuan: '$activePath' (id=${activeFolder.id})")

        if (usbManager.hasPermission(device)) {
            permissionRequestInFlight.set(false)
            FoundationLog.log("Izin USB sudah ada untuk ${device.deviceName}; memulai sesi orkestrator...")
            orchestrator.start(device, activeFolder.id)
            Engines.syncService(this)
        } else {
            if (!permissionRequestInFlight.compareAndSet(false, true)) {
                FoundationLog.log("Permintaan izin USB sedang berlangsung...")
                return
            }
            FoundationLog.log("Meminta izin USB untuk ${device.deviceName} (VID=0x${hex16(device.vendorId)}, PID=0x${hex16(device.productId)})...")
            val explicitIntent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pendingIntent = PendingIntent.getBroadcast(this, 0, explicitIntent, flags)
            usbManager.requestPermission(device, pendingIntent)
        }
        renderState(orchestrator.state, orchestrator.statistics)
    }

    private fun shareLogSnapshot() {
        val content = FoundationLog.snapshot()
        val uri = FoundationFileProvider.logUri()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TransferIn Foundation Log (${FoundationLog.currentLogFile?.name ?: "log.txt"})")
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, "Bagikan Log Foundation"))
    }

    private fun appendLogLineToUi(line: String) {
        if (!::logTextView.isInitialized) return
        logTextView.append(line)
        logTextView.append("\n")
        logScrollView.post {
            logScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun findAttachedFujiDevice(): UsbDevice? {
        val devices = usbManager.deviceList.values
        return devices.firstOrNull { it.vendorId == FUJIFILM_VENDOR_ID }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    @Suppress("DEPRECATION")
    private fun extractUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::reviewOverlay.isInitialized && reviewOverlay.visibility == View.VISIBLE) {
            closeReviewOverlay()
            return
        }
        super.onBackPressed()
    }

    /* =========================================================================
     * Galeri Tinjau Tahan (Stage 4 Gelombang 2)
     * ========================================================================= */

    private fun buildReviewOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(COLOR_GROUND)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
        }

        reviewBgImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        overlay.addView(reviewBgImageView)

        reviewTintView = View(this).apply {
            setBackgroundColor(
                Color.argb(
                    224,
                    Color.red(COLOR_GROUND),
                    Color.green(COLOR_GROUND),
                    Color.blue(COLOR_GROUND)
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        overlay.addView(reviewTintView)

        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header: sectionTitle "tinjau tahan." + reviewTitleTextView + btnReviewClose
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val headerTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        headerTitleCol.addView(sectionTitle("tinjau tahan."))

        reviewTitleTextView = TextView(this).apply {
            text = "Galeri Tinjau Tahan (Culling)"
            typeface = fontDmSansMedium
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_INK)
        }
        headerTitleCol.addView(reviewTitleTextView)
        headerRow.addView(headerTitleCol)

        btnReviewClose = kineticButton("tutup.", primer = false).apply {
            minHeight = dp(38)
            minimumHeight = dp(38)
            setPadding(dp(16), 0, dp(16), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38)
            ).apply {
                marginStart = dp(12)
            }
            setOnClickListener {
                closeReviewOverlay()
            }
        }
        headerRow.addView(btnReviewClose)
        contentContainer.addView(headerRow)

        // Status pesan / ringkasan (mono 11sp MUTED)
        reviewStatusTextView = TextView(this).apply {
            text = "Memuat daftar grup tertahan..."
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(2), 0, dp(10))
        }
        contentContainer.addView(reviewStatusTextView)

        // Kontainer daftar grup yang dapat digulir
        reviewListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        reviewScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFillViewport = true
            addView(
                reviewListContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        contentContainer.addView(
            reviewScrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        overlay.addView(contentContainer)

        return overlay
    }

    private fun refreshReviewBackground() {
        if (!::sessionScreen.isInitialized || !::reviewBgImageView.isInitialized) return
        try {
            val viewW = sessionScreen.width
            val viewH = sessionScreen.height
            if (viewW <= 0 || viewH <= 0) {
                reviewBgImageView.setImageDrawable(null)
                return
            }
            val contentView = sessionScreen.getChildAt(0) ?: run {
                reviewBgImageView.setImageDrawable(null)
                return
            }
            val scale = 0.125f
            val bmpW = (viewW * scale).toInt().coerceAtLeast(1)
            val bmpH = (viewH * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            canvas.translate(
                (contentView.left - sessionScreen.scrollX).toFloat(),
                (contentView.top - sessionScreen.scrollY).toFloat()
            )
            canvas.drawColor(COLOR_GROUND)
            contentView.draw(canvas)
            reviewBgImageView.setImageBitmap(bmp)
        } catch (_: Throwable) {
            if (::reviewBgImageView.isInitialized) {
                reviewBgImageView.setImageDrawable(null)
            }
        }
    }

    private fun openReviewOverlay() {
        refreshReviewBackground()
        reviewOverlay.visibility = View.VISIBLE
        refreshReviewOverlay()
    }

    private fun closeReviewOverlay() {
        reviewOverlay.visibility = View.GONE
        renderUploadStats()
    }

    private fun refreshReviewOverlay() {
        if (!::reviewOverlay.isInitialized || !Engines.initialized) return
        val heldGroups = db.getHeldCullingGroups()
        reviewTitleTextView.text = "Galeri Tinjau Tahan (${heldGroups.size} grup)"

        if (heldGroups.isEmpty()) {
            reviewStatusTextView.text = "(Tidak ada foto yang sedang tertahan)"
            reviewStatusTextView.setTextColor(COLOR_MUTED)
            reviewStatusTextView.visibility = View.VISIBLE
            reviewListContainer.removeAllViews()
            return
        }

        reviewStatusTextView.text = "Terdapat ${heldGroups.size} grup foto tertahan kriteria culling. Pilih 'Unggah Tetap' atau 'Buang':"
        reviewStatusTextView.setTextColor(COLOR_MUTED)
        reviewStatusTextView.visibility = View.VISIBLE

        populateHeldGroups(heldGroups)
    }

    private fun populateHeldGroups(heldGroups: List<HeldCullingGroup>) {
        reviewListContainer.removeAllViews()

        for (group in heldGroups) {
            val (cardOuter, cardInner) = doppelrandCard()

            // Bagian Atas: Baris Gambar Kecil + Info Teks
            val rowTop = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Gambar kecil
            val thumbnailView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(90), dp(90)).apply {
                    marginEnd = dp(12)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(COLOR_SHELL)
            }
            val bmp = decodeGroupThumbnail(group.items, targetWidth = 180)
            if (bmp != null) {
                thumbnailView.setImageBitmap(bmp)
            }
            rowTop.addView(thumbnailView)

            // Kolom Informasi
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            // 1. Nama berkas grup (DM Sans medium 13sp INK)
            val namesTv = TextView(this).apply {
                text = group.items.joinToString(", ") { it.filename }
                typeface = fontDmSansMedium
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(COLOR_INK)
                setPadding(0, 0, 0, dp(4))
            }
            infoCol.addView(namesTv)

            // 2. Baris info (mono 11sp MUTED: ukuran, waktu, alasan tahan dari cullingDetail apa adanya)
            val totalBytes = group.items.sumOf { it.sizeBytes }
            val sizeMb = String.format(Locale.US, "%.2f MB", totalBytes.toDouble() / (1024.0 * 1024.0))
            val captureTime = group.items.firstOrNull { !it.captureDate.isNullOrEmpty() }?.captureDate
                ?: if (group.groupSecond.isNotEmpty()) group.groupSecond.substringBefore('#') else "-"
            val detail = group.items.firstOrNull { !it.cullingDetail.isNullOrEmpty() }?.cullingDetail
                ?: "Tertahan kriteria culling"

            val infoTv = TextView(this).apply {
                text = "$sizeMb · Waktu: $captureTime\nAlasan: $detail"
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(COLOR_MUTED)
                setLineSpacing(dp(2).toFloat(), 1.0f)
            }
            infoCol.addView(infoTv)

            rowTop.addView(infoCol)
            cardInner.addView(rowTop)

            // Baris Tombol: 'unggah tetap.' dan 'buang.'
            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                }
            }

            // Kedua aksi selalu ditawarkan. Penjaga lama menyembunyikan 'Buang'
            // (dan menolak kedua aksi) bila ada anggota grup yang sudah masuk
            // pipa unggah — itu mencabut hak user membuang sisa pasangan yatim
            // warisan bug vonis ganda. Semantik baru per item di dalam aksi:
            // hanya anggota yang SEDANG diunggah yang menghalangi.
            val btnUploadAnyway = kineticButton("unggah tetap.", primer = true).apply {
                minHeight = dp(40)
                minimumHeight = dp(40)
                setPadding(dp(16), 0, dp(16), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    marginEnd = dp(8)
                }
                setOnClickListener {
                    actionReleaseHeldGroup(group)
                }
            }
            val btnDiscard = kineticButton("buang.", primer = false).apply {
                minHeight = dp(40)
                minimumHeight = dp(40)
                setPadding(dp(16), 0, dp(16), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(COLOR_BURGUNDY)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                setOnClickListener {
                    actionDiscardHeldGroup(group)
                }
            }
            buttonRow.addView(btnUploadAnyway)
            buttonRow.addView(btnDiscard)

            cardInner.addView(buttonRow)
            reviewListContainer.addView(cardOuter)
        }
    }

    private fun actionReleaseHeldGroup(group: HeldCullingGroup) {
        val allItems = db.getItemsInGroup(group.sessionId, group.groupSecond)
        // Per item: hanya anggota yang masih TAHAN yang dilepas. Anggota yang
        // sudah QUEUED/UPLOADING/DONE (mis. pasangan warisan vonis ganda yang
        // separuhnya sudah di Drive) dibiarkan — melepaskan sisanya justru
        // menyatukan kembali pasangan itu di Drive.
        val heldItems = allItems.filter { it.status == ItemStatus.CULLING_HOLD }
        for (item in heldItems) {
            db.updateItem(
                id = item.id,
                status = ItemStatus.QUEUED,
                culling = CullingVerdict.RELEASED
            )
        }
        Engines.syncService(this)
        uploadQueue.kick()
        FoundationLog.log("[CULLING] Pengguna memilih 'Unggah Tetap' untuk grup ${group.sessionId}/${group.groupSecond} (${heldItems.joinToString { it.filename }}) -> status=QUEUED, culling=RELEASED")
        refreshReviewOverlay()
        renderUploadStats()
    }

    private fun actionDiscardHeldGroup(group: HeldCullingGroup) {
        val allItems = db.getItemsInGroup(group.sessionId, group.groupSecond)
        // Satu-satunya penghalang sejati: anggota yang SEDANG diunggah atau diverifikasi detik
        // ini (berkasnya sedang dibaca/diverifikasi pengunggah — kasus DSCF0003 dulu).
        if (allItems.any { it.status == ItemStatus.UPLOADING || it.status == ItemStatus.VERIFYING }) {
            FoundationLog.log("[CULLING] Aksi 'Buang' ditunda: ada berkas grup yang sedang diunggah/diverifikasi (UPLOADING/VERIFYING) detik ini; coba lagi sesaat lagi")
            refreshReviewOverlay()
            return
        }
        // Anggota TAHAN dan ANTRI dibuang (yang ANTRI dibatalkan bersih oleh
        // pemeriksaan DISCARDED pengunggah). Anggota DONE tidak disentuh:
        // berkasnya sudah di Drive dan menetap di sana.
        val heldItems = allItems.filter {
            it.status == ItemStatus.CULLING_HOLD || it.status == ItemStatus.QUEUED
        }
        val doneItems = allItems.filter { it.status == ItemStatus.DONE }
        if (doneItems.isNotEmpty()) {
            FoundationLog.log("[CULLING] Catatan 'Buang': ${doneItems.joinToString { it.filename }} sudah terunggah dan menetap di Drive")
        }
        for (item in heldItems) {
            db.updateItem(
                id = item.id,
                status = ItemStatus.DISCARDED,
                culling = CullingVerdict.DISCARDED
            )
            try {
                val f = File(item.localPath)
                if (f.exists()) {
                    f.delete()
                }
                if (item.filename.endsWith(".raf", ignoreCase = true)) {
                    val preview = File(f.parentFile, "${f.nameWithoutExtension}.preview.jpg")
                    if (preview.exists()) {
                        preview.delete()
                    }
                }
            } catch (e: Exception) {
                FoundationLog.error("[CULLING] Gagal menghapus berkas lokal ${item.localPath}: ${e.message}")
            }
        }
        FoundationLog.log("[CULLING] Pengguna memilih 'Buang' untuk grup ${group.sessionId}/${group.groupSecond} (${heldItems.joinToString { it.filename }}) -> status=DISCARDED, culling=DISCARDED, berkas lokal dibuang manual oleh user")
        refreshReviewOverlay()
        renderUploadStats()
    }

    private fun decodeGroupThumbnail(items: List<TransferItem>, targetWidth: Int = 180): Bitmap? {
        val jpgItem = items.firstOrNull {
            it.filename.endsWith(".jpg", ignoreCase = true) ||
                it.filename.endsWith(".jpeg", ignoreCase = true)
        }
        if (jpgItem != null) {
            val file = File(jpgItem.localPath)
            if (file.exists() && file.length() > 0L) {
                val bmp = decodeSubsampledBitmap(file, targetWidth)
                if (bmp != null) return bmp
            }
        }

        val rafItem = items.firstOrNull { it.filename.endsWith(".raf", ignoreCase = true) }
        if (rafItem != null) {
            val rafFile = File(rafItem.localPath)
            val previewFile = File(rafFile.parentFile, "${rafFile.nameWithoutExtension}.preview.jpg")
            if (previewFile.exists() && previewFile.length() > 0L) {
                val bmp = decodeSubsampledBitmap(previewFile, targetWidth)
                if (bmp != null) return bmp
            }
            if (rafFile.exists() && rafFile.length() > 0L) {
                val extracted = RafPreviewExtractor.extractAndSavePreview(rafFile, previewFile)
                if (extracted && previewFile.exists() && previewFile.length() > 0L) {
                    val bmp = decodeSubsampledBitmap(previewFile, targetWidth)
                    if (bmp != null) return bmp
                }
            }
        }

        return null
    }

    private fun decodeSubsampledBitmap(file: File, targetWidth: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            if (w <= 0) return null

            var sampleSize = 1
            while (w / (sampleSize * 2) >= targetWidth) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun hex16(v: Int): String = String.format(Locale.US, "%04X", v and 0xFFFF)
}
