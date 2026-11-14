package com.rvmeur.transferin

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aktivitas utama TransferIn Foundation (Stage 1, Stage 2, dan Stage 3).
 *
 * Menerapkan UI polos dari kode (tanpa dependensi AndroidX), siklus hidup USB,
 * orkestrasi sesi unduh kamera Fujifilm, penjelajah folder Google Drive,
 * serta antrean unggah langsung ke folder tujuan yang dikunci.
 */
class MainActivity : Activity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.rvmeur.transferin.USB_PERMISSION"
        // Vendor ID USB Fujifilm: 0x04CB (1227)
        private const val FUJIFILM_VENDOR_ID = 0x04CB
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

    private lateinit var statusTextView: TextView
    private lateinit var cameraInfoTextView: TextView
    private lateinit var slotTextView: TextView
    private lateinit var statsTextView: TextView
    private lateinit var uploadStatsTextView: TextView
    private lateinit var folderDestinationTextView: TextView
    private lateinit var btnExploreFolders: Button
    private lateinit var logScrollView: ScrollView
    private lateinit var logTextView: TextView

    // Komponen Penjelajah Folder Layar Penuh (Stage 3)
    private lateinit var explorerOverlay: LinearLayout
    private lateinit var explorerPathTextView: TextView
    private lateinit var explorerStatusTextView: TextView
    private lateinit var btnExplorerUp: Button
    private lateinit var btnExplorerClose: Button
    private lateinit var explorerListContainer: LinearLayout
    private lateinit var explorerScrollView: ScrollView

    // Komponen Galeri Tinjau Tahan Layar Penuh (Stage 4)
    private lateinit var btnReviewHeld: Button
    private lateinit var btnBuangSisa: Button
    private lateinit var reviewOverlay: LinearLayout
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
                if (!connected && ::explorerOverlay.isInitialized && explorerOverlay.visibility == View.VISIBLE) {
                    closeFolderExplorer()
                }
                renderFolderUi(orchestrator.state)
            }
        }

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

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
        setContentView(buildRootLayout())

        // 5. Daftarkan Receiver USB
        registerUsbReceiver()

        // 6. Muat snapshot log awal & render state saat ini
        logTextView.text = FoundationLog.snapshot()
        renderState(orchestrator.state, orchestrator.statistics)
        renderDriveUi(driveAuth.isConnected(), driveAuth.accountEmail())
        renderUploadStats()
        renderFolderUi(orchestrator.state)

        FoundationLog.log("=== TransferIn Foundation MainActivity Siap (minSdk 31, targetSdk 35) ===")

        // Tangani intent awal bila aplikasi dibuka karena URI OAuth redirect
        intent?.data?.let { uri ->
            val handled = driveAuth.handleRedirect(uri)
            if (handled) {
                FoundationLog.log("[AUTH] Redirect ditangani pada onCreate: $uri")
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

            // Teruskan URI redirect OAuth2 ke DriveAuth
            val uri = intent.data
            if (uri != null) {
                val handled = driveAuth.handleRedirect(uri)
                if (handled) {
                    FoundationLog.log("[AUTH] Redirect ditangani di onNewIntent: $uri")
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

    private fun buildRootLayout(): View {
        val rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Baris status besar
        statusTextView = TextView(this).apply {
            text = "Status: Menunggu kamera"
            setTextColor(Color.parseColor("#80CBC4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(4))
        }
        contentLayout.addView(statusTextView)

        // Info kamera: model, serial dari DeviceInfo via PtpSession
        cameraInfoTextView = TextView(this).apply {
            text = "Kamera: - | Serial: - | Mode: Tether Auto (dari bodi)"
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, dp(4))
        }
        contentLayout.addView(cameraInfoTextView)

        // Indikator Slot ruang tunggu: X/12
        slotTextView = TextView(this).apply {
            text = "Slot ruang tunggu: 12/12"
            setTextColor(Color.parseColor("#FFB74D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(4))
        }
        contentLayout.addView(slotTextView)

        // Indikator Statistik Unduh: Grup selesai: N - Berkas: M - Total: ... MB - Terakhir: ... MB/s
        statsTextView = TextView(this).apply {
            text = "Grup selesai: 0 - Berkas: 0 - Total: 0.00 MB - Terakhir: 0.00 MB/s"
            setTextColor(Color.parseColor("#A5D6A7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, dp(4))
        }
        contentLayout.addView(statsTextView)

        // Baris Statistik Unggah Drive: Unggah — antre: N • proses: N • selesai: N • gagal: N
        uploadStatsTextView = TextView(this).apply {
            text = "Unggah — antre: 0 • proses: 0 • selesai: 0 • gagal: 0"
            setTextColor(Color.parseColor("#80D8FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(6))
        }
        contentLayout.addView(uploadStatsTextView)

        // Baris Folder Tujuan: Folder tujuan: <jejak> + Tombol Jelajahi (Stage 3)
        val folderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }

        folderDestinationTextView = TextView(this).apply {
            text = "Folder tujuan: ${driveFolders.getCurrentPath()}"
            setTextColor(Color.parseColor("#FFE082"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), dp(4), dp(2))
        }

        btnExploreFolders = Button(this).apply {
            text = "Jelajahi"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener {
                exploreFoldersClicked()
            }
        }

        folderRow.addView(
            folderDestinationTextView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        folderRow.addView(
            btnExploreFolders,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        contentLayout.addView(folderRow)

        // Baris Tombol 1: Mulai Sesi | Akhiri Sesi
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }

        btnStartSession = Button(this).apply {
            text = "Mulai Sesi"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener {
                startSessionClicked()
            }
        }
        btnEndSession = Button(this).apply {
            text = "Akhiri Sesi"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener {
                orchestrator.stop()
            }
        }
        row1.addView(btnStartSession, rowButtonParams())
        row1.addView(btnEndSession, rowButtonParams())
        contentLayout.addView(row1)

        // Baris Tombol 2: Hubungkan / Status Drive + Putuskan + Ulangi Gagal
        val rowDrive = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }

        btnDriveAuth = Button(this).apply {
            text = "Hubungkan Google Drive"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener {
                if (!driveAuth.isConnected()) {
                    driveAuth.beginAuth(this@MainActivity)
                }
            }
        }

        btnDisconnectDrive = Button(this).apply {
            text = "Putuskan"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
            setOnClickListener {
                driveAuth.disconnect()
            }
        }

        btnRetryFailed = Button(this).apply {
            text = "Ulangi Gagal"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
            setOnClickListener {
                val requeued = db.requeueFailed()
                Engines.syncService(this@MainActivity)
                if (requeued > 0) {
                    uploadQueue.kick()
                }
                renderUploadStats()
            }
        }

        rowDrive.addView(btnDriveAuth, rowButtonParams())
        rowDrive.addView(btnDisconnectDrive, rowButtonParams())
        rowDrive.addView(btnRetryFailed, rowButtonParams())
        contentLayout.addView(rowDrive)

        // Baris Tombol Galeri Tinjau Tahan (Stage 4)
        val rowReview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }

        btnReviewHeld = Button(this).apply {
            text = "Tinjau Tahan (0)"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isEnabled = false
            alpha = 0.4f
            setOnClickListener {
                openReviewOverlay()
            }
        }
        rowReview.addView(btnReviewHeld, rowButtonParams())

        // Buang Sisa Kamera (v0.4.2): bebaskan slot foto lama dari ruang tunggu
        // kamera tanpa unduh/unggah; dieksekusi orkestrator di sela antrean.
        btnBuangSisa = Button(this).apply {
            text = "Buang Sisa Kamera (0)"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isEnabled = false
            alpha = 0.4f
            setOnClickListener {
                orchestrator.requestBuangSisaKamera()
            }
        }
        rowReview.addView(btnBuangSisa, rowButtonParams())
        contentLayout.addView(rowReview)

        // Baris Tombol 3: Bagikan Log | Hapus Log
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }

        btnShareLog = Button(this).apply {
            text = "Bagikan Log"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener {
                shareLogSnapshot()
            }
        }
        btnClearLog = Button(this).apply {
            text = "Hapus Log"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener {
                logTextView.text = ""
                FoundationLog.clear(this@MainActivity)
            }
        }
        row2.addView(btnShareLog, rowButtonParams())
        row2.addView(btnClearLog, rowButtonParams())
        contentLayout.addView(row2)

        // Log bergulir monospace
        logTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#C8E6C9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        logScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isFillViewport = true
            addView(
                logTextView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        contentLayout.addView(
            logScrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        rootFrame.addView(contentLayout)

        // Panel Penjelajah Layar Penuh (Overlay)
        explorerOverlay = buildExplorerOverlay()
        explorerOverlay.visibility = View.GONE
        rootFrame.addView(explorerOverlay)

        // Panel Tinjau Tahan Layar Penuh (Overlay Stage 4)
        reviewOverlay = buildReviewOverlay()
        reviewOverlay.visibility = View.GONE
        rootFrame.addView(reviewOverlay)

        return rootFrame
    }

    private fun rowButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(4)
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
    }

    private fun buildExplorerOverlay(): LinearLayout {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
        }

        // Header: Judul + Tombol Tutup
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

        val titleTv = TextView(this).apply {
            text = "Penjelajah Folder Google Drive"
            setTextColor(Color.parseColor("#80CBC4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        headerRow.addView(
            titleTv,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        btnExplorerClose = Button(this).apply {
            text = "Tutup"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener {
                closeFolderExplorer()
            }
        }
        headerRow.addView(
            btnExplorerClose,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        overlay.addView(headerRow)

        // Path jejak navigasi
        explorerPathTextView = TextView(this).apply {
            text = "Jejak: Drive Saya"
            setTextColor(Color.parseColor("#FFE082"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        overlay.addView(explorerPathTextView)

        // Tombol "↑ Naik"
        btnExplorerUp = Button(this).apply {
            text = "↑ Naik"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener {
                if (driveFolders.canGoUp()) {
                    driveFolders.goUp()
                    refreshExplorerView()
                }
            }
        }
        overlay.addView(btnExplorerUp)

        // Status / pemuatan / pesan
        explorerStatusTextView = TextView(this).apply {
            text = "Memuat subfolder..."
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(6))
        }
        overlay.addView(explorerStatusTextView)

        // Kontainer daftar subfolder yang dapat digulir
        explorerListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        explorerScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
            addView(
                explorerListContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        overlay.addView(
            explorerScrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return overlay
    }

    private fun exploreFoldersClicked() {
        if (!driveAuth.isConnected()) {
            FoundationLog.log("[FOLDER] Tidak dapat membuka penjelajah: akun Google Drive belum terhubung")
            statusTextView.text = "Status: Hubungkan Google Drive terlebih dahulu untuk memilih folder"
            return
        }
        openFolderExplorer()
    }

    private fun openFolderExplorer() {
        explorerOverlay.visibility = View.VISIBLE
        refreshExplorerView()
    }

    private fun closeFolderExplorer() {
        explorerOverlay.visibility = View.GONE
        renderFolderUi(orchestrator.state)
    }

    private fun refreshExplorerView() {
        val current = driveFolders.getCurrentFolder()
        val path = driveFolders.getCurrentPath()
        explorerPathTextView.text = "Jejak: $path"
        val canUp = driveFolders.canGoUp()
        btnExplorerUp.isEnabled = canUp
        btnExplorerUp.alpha = if (canUp) 1.0f else 0.4f

        explorerStatusTextView.text = "Memuat subfolder '${current.name}'..."
        explorerStatusTextView.setTextColor(Color.parseColor("#B0BEC5"))
        explorerStatusTextView.visibility = View.VISIBLE
        explorerListContainer.removeAllViews()

        driveFolders.listSubfolders(current.id) { subfolders, errorMsg ->
            if (explorerOverlay.visibility != View.VISIBLE) return@listSubfolders

            if (errorMsg != null) {
                explorerStatusTextView.text = "Gagal memuat: $errorMsg"
                explorerStatusTextView.setTextColor(Color.parseColor("#FF8A80"))
                explorerStatusTextView.visibility = View.VISIBLE
            } else if (subfolders.isNullOrEmpty()) {
                explorerStatusTextView.text = "(Tidak ada subfolder di dalam folder ini)"
                explorerStatusTextView.setTextColor(Color.parseColor("#90A4AE"))
                explorerStatusTextView.visibility = View.VISIBLE
            } else {
                explorerStatusTextView.visibility = View.GONE
                populateSubfolders(subfolders)
            }
        }
    }

    private fun populateSubfolders(subfolders: List<DriveFolderItem>) {
        explorerListContainer.removeAllViews()
        for (folder in subfolders) {
            val tv = TextView(this).apply {
                text = "📁  ${folder.name}"
                setTextColor(Color.parseColor("#E0E0E0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isClickable = true
                isFocusable = true
                setBackgroundColor(Color.parseColor("#212121"))
                setOnClickListener {
                    driveFolders.enterFolder(folder)
                    refreshExplorerView()
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(2)
            }
            explorerListContainer.addView(tv, params)
        }
    }

    private fun renderFolderUi(state: SessionOrchestrator.State = orchestrator.state) {
        if (!::folderDestinationTextView.isInitialized || !::btnExploreFolders.isInitialized) return

        val isSessionActive = (state == SessionOrchestrator.State.CONNECTING ||
            state == SessionOrchestrator.State.BASELINE ||
            state == SessionOrchestrator.State.MONITORING ||
            state == SessionOrchestrator.State.CLOSING ||
            state == SessionOrchestrator.State.ERROR)

        if (isSessionActive) {
            btnExploreFolders.isEnabled = false
            val currentLocked = lockedFolderPath ?: driveFolders.getCurrentPath()
            folderDestinationTextView.text = "Folder tujuan: $currentLocked (terkunci oleh sesi berjalan)"
        } else {
            lockedFolderId = null
            lockedFolderPath = null
            btnExploreFolders.isEnabled = true
            folderDestinationTextView.text = "Folder tujuan: ${driveFolders.getCurrentPath()}"
        }
    }

    private fun renderDriveUi(connected: Boolean, email: String?) {
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
        val activePath = driveFolders.getCurrentPath()
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
        if (::explorerOverlay.isInitialized && explorerOverlay.visibility == View.VISIBLE) {
            closeFolderExplorer()
            return
        }
        super.onBackPressed()
    }

    /* =========================================================================
     * Galeri Tinjau Tahan (Stage 4 Gelombang 2)
     * ========================================================================= */

    private fun buildReviewOverlay(): LinearLayout {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
        }

        // Header: Judul + Tombol Tutup
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

        reviewTitleTextView = TextView(this).apply {
            text = "Galeri Tinjau Tahan (Culling)"
            setTextColor(Color.parseColor("#FFAB91"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        headerRow.addView(
            reviewTitleTextView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        btnReviewClose = Button(this).apply {
            text = "Tutup"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener {
                closeReviewOverlay()
            }
        }
        headerRow.addView(
            btnReviewClose,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        overlay.addView(headerRow)

        // Status pesan / ringkasan
        reviewStatusTextView = TextView(this).apply {
            text = "Memuat daftar grup tertahan..."
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, dp(6))
        }
        overlay.addView(reviewStatusTextView)

        // Kontainer daftar grup yang dapat digulir
        reviewListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        reviewScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
            addView(
                reviewListContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        overlay.addView(
            reviewScrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return overlay
    }

    private fun openReviewOverlay() {
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
            reviewStatusTextView.setTextColor(Color.parseColor("#90A4AE"))
            reviewStatusTextView.visibility = View.VISIBLE
            reviewListContainer.removeAllViews()
            return
        }

        reviewStatusTextView.text = "Terdapat ${heldGroups.size} grup foto tertahan kriteria culling. Pilih 'Unggah Tetap' atau 'Buang':"
        reviewStatusTextView.setTextColor(Color.parseColor("#B0BEC5"))
        reviewStatusTextView.visibility = View.VISIBLE

        populateHeldGroups(heldGroups)
    }

    private fun populateHeldGroups(heldGroups: List<HeldCullingGroup>) {
        reviewListContainer.removeAllViews()

        for (group in heldGroups) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#212121"))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            }

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
                    marginEnd = dp(10)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#121212"))
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

            // 1. Nama berkas grup
            val namesTv = TextView(this).apply {
                text = group.items.joinToString(", ") { it.filename }
                setTextColor(Color.parseColor("#FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(2))
            }
            infoCol.addView(namesTv)

            // 2. Waktu jepret
            val captureTime = group.items.firstOrNull { !it.captureDate.isNullOrEmpty() }?.captureDate
                ?: if (group.groupSecond.isNotEmpty()) group.groupSecond.substringBefore('#') else "-"
            val timeTv = TextView(this).apply {
                text = "Waktu: $captureTime"
                setTextColor(Color.parseColor("#B0BEC5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, 0, 0, dp(2))
            }
            infoCol.addView(timeTv)

            // 3. Teks cullingDetail
            val detail = group.items.firstOrNull { !it.cullingDetail.isNullOrEmpty() }?.cullingDetail
                ?: "Tertahan kriteria culling"
            val detailTv = TextView(this).apply {
                text = "Alasan: $detail"
                setTextColor(Color.parseColor("#FF8A80"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, 0, 0, dp(2))
            }
            infoCol.addView(detailTv)

            rowTop.addView(infoCol)
            card.addView(rowTop)

            // Baris Tombol: 'Unggah Tetap' dan 'Buang'
            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            }

            // Kedua aksi selalu ditawarkan. Penjaga lama menyembunyikan 'Buang'
            // (dan menolak kedua aksi) bila ada anggota grup yang sudah masuk
            // pipa unggah — itu mencabut hak user membuang sisa pasangan yatim
            // warisan bug vonis ganda. Semantik baru per item di dalam aksi:
            // hanya anggota yang SEDANG diunggah yang menghalangi.
            val btnUploadAnyway = Button(this).apply {
                text = "Unggah Tetap"
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setBackgroundColor(Color.parseColor("#2E7D32"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    actionReleaseHeldGroup(group)
                }
            }
            val btnDiscard = Button(this).apply {
                text = "Buang"
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setBackgroundColor(Color.parseColor("#C62828"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    actionDiscardHeldGroup(group)
                }
            }
            buttonRow.addView(
                btnUploadAnyway,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
            )
            buttonRow.addView(
                btnDiscard,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            card.addView(buttonRow)
            reviewListContainer.addView(card)
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
