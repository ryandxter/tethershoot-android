package com.rvmeur.transferin

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Satu-satunya pemilik koneksi USB PTP untuk TransferIn Foundation (Stage 1).
 *
 * Batasan keras arsitektur & transport (dipanen dari Stage 0 Probe v4/v5):
 * 1. Satu executor thread khusus ([bulkExecutor]) bernama [BULK_THREAD_NAME].
 *    Semua transaksi bulk (command + data + response) dieksekusi serial lewat executor ini.
 *    API publik kelas aman dipanggil dari thread orkestrator; panggilan akan dikirim ke
 *    executor dan hasilnya dikembalikan secara tersinkronisasi.
 * 2. Thread pembaca interrupt ([interruptThread]) terpisah dan HANYA membaca endpoint
 *    interrupt IN (timeout 1000 ms) serta mem-parse event container; thread ini TIDAK PERNAH mengirim apa pun.
 *    Bila terdeteksi event ObjectAdded (0x4002), panggil callback [onObjectAddedHint].
 * 3. Opcode berpagar ketat (ALLOWLIST): 0x1001, 0x1002, 0x1003, 0x1004, 0x1005, 0x1007, 0x1008, 0x1009, 0x100B, 0x1014, 0x1015.
 *    Opcode vendor dan opcode modifikasi properti dilarang keras.
 * 4. Urutan sambung terkunci: Diagnostik EP0 -> CLEAR_FEATURE halt -> setInterface -> jeda settle ->
 *    OpenSession didahulukan (tid pra-sesi 0; direset saat OpenSession) -> GetDeviceInfo.
 * 5. Baca paket kontrol/respons: potongan 512B.
 *    Unduh berkas GetObject: streaming potongan default 16384B langsung ke berkas (tanpa memuat seluruh
 *    objek ke RAM), hitung MD5 on-the-fly, dan verifikasi byte-exact terhadap ObjectInfo.
 * 6. Tangga pemulihan stall v4 dipertahankan: diagnostik EP0 pasca-gagal-baca; bila halt terdeteksi,
 *    Still-Image Device_Reset kelas 0x66 -> CLEAR_FEATURE halt 0x81 -> jeda 150ms -> retry transaksi 1x.
 * 7. Teardown koneksi sopan: hentikan interrupt reader, CloseSession bila terbuka dan bersih,
 *    selalu releaseInterface dan close UsbDeviceConnection.
 */
class PtpSession(
    private val usbManager: UsbManager,
    @Volatile var onObjectAddedHint: ((handle: Int) -> Unit)? = null
) {
    /**
     * Konstruktor praktis berbasis [Context] framework Android.
     */
    constructor(
        context: Context,
        onObjectAddedHint: ((handle: Int) -> Unit)? = null
    ) : this(
        context.getSystemService(Context.USB_SERVICE) as UsbManager,
        onObjectAddedHint
    )

    data class PtpResult(
        val opcode: Int,
        val transactionId: Int,
        val responseCode: Int,
        val responseParams: IntArray,
        val data: ByteArray,
        val elapsedMs: Long
    )

    data class DownloadResult(
        val success: Boolean,
        val path: String,
        val sizeBytes: Long,
        val md5Hex: String,
        val elapsedMs: Long,
        val bytesPerSecond: Long
    ) {
        val mbPerSec: Double
            get() = bytesPerSecond.toDouble() / (1024.0 * 1024.0)
    }

    data class ObjectInfoResult(
        val handle: Int,
        val filename: String,
        val sizeBytes: Long,
        val captureDateString: String,
        val formatCode: Int,
        val storageId: Int = 0,
        val parentObject: Int = 0,
        val modificationDateString: String = "",
        val protectionStatus: Int = 0
    )

    data class StorageInfoResult(
        val storageType: Int,
        val filesystemType: Int,
        val accessCapability: Int,
        val maxCapacity: Long,
        val freeSpaceInBytes: Long,
        val freeSpaceInImages: Long,
        val storageDescription: String,
        val volumeLabel: String = ""
    )

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val deviceVersion: String,
        val serialNumber: String,
        val standardVersion: Int,
        val vendorExtensionId: Int,
        val vendorExtensionVersion: Int,
        val vendorExtensionDesc: String,
        val functionalMode: Int,
        val operationsSupported: IntArray,
        val eventsSupported: IntArray,
        val devicePropertiesSupported: IntArray,
        val captureFormats: IntArray,
        val imageFormats: IntArray
    )

    companion object {
        private const val BULK_THREAD_NAME = "ptp-bulk-exec"
        private const val INTERRUPT_THREAD_NAME = "ptp-intr-reader"

        private const val HEADER_SIZE = 12
        private const val BULK_TIMEOUT_MS = 5000
        private const val DOWNLOAD_TIMEOUT_MS = 15000
        private const val INTERRUPT_TIMEOUT_MS = 1000
        private const val PRE_DRAIN_TIMEOUT_MS = 50
        private const val PRE_DRAIN_MAX_READS = 4
        private const val EP0_TIMEOUT_MS = 1000
        private const val EP0_RESET_TIMEOUT_MS = 2000
        private const val RUNG_A_SETTLE_MS = 100L
        private const val RUNG_B_SETTLE_MS = 150L
        private const val RECONNECT_SETTLE_MS = 200L
        private const val BULK_CHUNK_SIZE = 65536

        // Max packet size Bulk IN host USB Android
        private const val DATA_READ_CHUNK = 512

        // Default ukuran potongan streaming unduhan Stage 1
        const val DEFAULT_DOWNLOAD_CHUNK_SIZE = 16384

        // Konstanta USB Control Transfer (EP0 Standard & Still Image Class)
        const val USB_REQ_TYPE_EP_STATUS_IN = 0x82
        const val USB_REQ_TYPE_EP_CLEAR_OUT = 0x02
        const val USB_REQ_TYPE_CLASS_INTF_IN = 0xA1
        const val USB_REQ_TYPE_CLASS_INTF_OUT = 0x21

        const val USB_REQ_GET_STATUS = 0x00
        const val USB_REQ_CLEAR_FEATURE = 0x01
        const val USB_FEATURE_ENDPOINT_HALT = 0x0000

        const val USB_CLASS_REQ_DEVICE_RESET = 0x66
        const val USB_CLASS_REQ_GET_DEVICE_STATUS = 0x67

        // Sentinel kode error transport lokal (negatif, dibedakan tegas dari 16-bit PTP ResponseCode)
        const val RC_TRANSPORT_WRITE_FAILED = -1
        const val RC_TRANSPORT_READ_FAILED = -2
        const val RC_TRANSPORT_NOT_READY = -3
        const val RC_TRANSPORT_BAD_CONTAINER = -4

        // USB Class 6 = Still Image Capture Device (PTP/MTP)
        const val USB_CLASS_PTP = UsbConstants.USB_CLASS_STILL_IMAGE

        // Tipe Container PTP (ISO 15740)
        const val CONTAINER_UNDEFINED = 0x0000
        const val CONTAINER_COMMAND = 0x0001
        const val CONTAINER_DATA = 0x0002
        const val CONTAINER_RESPONSE = 0x0003
        const val CONTAINER_EVENT = 0x0004

        // Opcode PTP Standar ALLOWLIST Stage 1
        const val OC_GET_DEVICE_INFO = 0x1001
        const val OC_OPEN_SESSION = 0x1002
        const val OC_CLOSE_SESSION = 0x1003
        const val OC_GET_STORAGE_IDS = 0x1004
        const val OC_GET_STORAGE_INFO = 0x1005
        const val OC_GET_OBJECT_HANDLES = 0x1007
        const val OC_GET_OBJECT_INFO = 0x1008
        const val OC_GET_OBJECT = 0x1009
        const val OC_DELETE_OBJECT = 0x100B
        const val OC_GET_DEVICE_PROP_DESC = 0x1014
        const val OC_GET_DEVICE_PROP_VALUE = 0x1015

        // Response Code PTP Standar
        const val RC_OK = 0x2001
        const val RC_GENERAL_ERROR = 0x2002
        const val RC_SESSION_NOT_OPEN = 0x2003
        const val RC_OPERATION_NOT_SUPPORTED = 0x2005
        const val RC_PARAMETER_NOT_SUPPORTED = 0x2006
        const val RC_DEVICE_BUSY = 0x2019
        const val RC_SESSION_ALREADY_OPEN = 0x201E

        // Event Code PTP Standar
        const val EC_CANCEL_TRANSACTION = 0x4001
        const val EC_OBJECT_ADDED = 0x4002
        const val EC_OBJECT_REMOVED = 0x4003
        const val EC_STORE_ADDED = 0x4004
        const val EC_STORE_REMOVED = 0x4005
        const val EC_DEVICE_PROP_CHANGED = 0x4006
        const val EC_OBJECT_INFO_CHANGED = 0x4007
        const val EC_DEVICE_INFO_CHANGED = 0x4008
        const val EC_REQUEST_OBJECT_TRANSFER = 0x4009
        const val EC_STORE_FULL = 0x400A
        const val EC_DEVICE_RESET = 0x400B
        const val EC_STORAGE_INFO_CHANGED = 0x400C
        const val EC_CAPTURE_COMPLETE = 0x400D

        // Device Property Code Fujifilm (Read-Only di Stage 1)
        const val PROP_FUJI_MEDIA_RECORD = 0xD20C
        const val PROP_FUJI_FREE_SDRAM_IMAGES = 0xD20E
        const val PROP_FUJI_MEDIA_STATUS = 0xD211
        const val PROP_FUJI_CURRENT_STATE = 0xD212

        // Tipe Data PTP
        const val DTC_UNDEF = 0x0000
        const val DTC_INT8 = 0x0001
        const val DTC_UINT8 = 0x0002
        const val DTC_INT16 = 0x0003
        const val DTC_UINT16 = 0x0004
        const val DTC_INT32 = 0x0005
        const val DTC_UINT32 = 0x0006
        const val DTC_INT64 = 0x0007
        const val DTC_UINT64 = 0x0008
        const val DTC_STR = 0xFFFF
        const val DTC_ARRAY_MASK = 0x4000

        // Strict Allowlist Opcode Stage 1 di transact()
        val ALLOWED_OPCODES = setOf(
            OC_GET_DEVICE_INFO,
            OC_OPEN_SESSION,
            OC_CLOSE_SESSION,
            OC_GET_STORAGE_IDS,
            OC_GET_STORAGE_INFO,
            OC_GET_OBJECT_HANDLES,
            OC_GET_OBJECT_INFO,
            OC_GET_OBJECT,
            OC_DELETE_OBJECT,
            OC_GET_DEVICE_PROP_DESC,
            OC_GET_DEVICE_PROP_VALUE
        )

        private val DATA_IN_OPCODES = setOf(
            OC_GET_DEVICE_INFO,
            OC_GET_STORAGE_IDS,
            OC_GET_STORAGE_INFO,
            OC_GET_OBJECT_HANDLES,
            OC_GET_OBJECT_INFO,
            OC_GET_OBJECT,
            OC_GET_DEVICE_PROP_DESC,
            OC_GET_DEVICE_PROP_VALUE
        )
    }

    // Single-thread executor untuk SEMUA transaksi bulk serial
    private val bulkExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, BULK_THREAD_NAME).apply {
            isDaemon = true
        }
    }

    // State koneksi USB (dimutasi hanya di bulkExecutor)
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var epBulkIn: UsbEndpoint? = null
    private var epBulkOut: UsbEndpoint? = null
    private var epInterruptIn: UsbEndpoint? = null

    private var transactionIdCounter: Int = 0
    private var lastBulkOutHaltBit: Int = -1

    private val usbClaimed = AtomicBoolean(false)
    private val ptpSessionOpen = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    private val sessionBroken = AtomicBoolean(false)
    private val interruptRunning = AtomicBoolean(false)

    @Volatile
    private var interruptThread: Thread? = null

    @Volatile
    var lastDeviceInfo: DeviceInfo? = null
        private set

    @Volatile
    var stillStorageId: Int = 0x10000001
        private set

    fun isConnected(): Boolean = ptpSessionOpen.get() && !sessionBroken.get()
    fun isUsbClaimed(): Boolean = usbClaimed.get()
    fun isConnecting(): Boolean = connecting.get()
    fun isBroken(): Boolean = sessionBroken.get()

    private fun ensureBulkThread() {
        check(Thread.currentThread().name == BULK_THREAD_NAME) {
            "Pelanggaran batasan keras: transaksi bulk dipanggil dari thread '${Thread.currentThread().name}', harus '$BULK_THREAD_NAME'"
        }
    }

    /**
     * Memastikan blok dieksekusi di [bulkExecutor].
     * Bila dipanggil dari thread luar (misal thread orkestrator), blok disubmit ke executor
     * dan hasilnya ditunggu secara tersinkronisasi.
     */
    private fun <T> runOnBulk(block: () -> T): T {
        if (Thread.currentThread().name == BULK_THREAD_NAME) {
            return block()
        }
        val future = bulkExecutor.submit(Callable { block() })
        return try {
            future.get()
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            if (cause is RuntimeException) throw cause
            throw RuntimeException(cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while waiting for bulk transaction", e)
        }
    }

    // =========================================================================
    // API Publik Siklus Hidup & Koneksi Sesi
    // =========================================================================

    /**
     * Membuka koneksi USB PTP menggunakan urutan sambung terkunci Stage 0 Probe v4/v5.
     * Aman dipanggil dari thread orkestrator; memblokir hingga sesi siap atau gagal.
     */
    fun open(device: UsbDevice): Boolean = runOnBulk {
        ensureBulkThread()
        if (!connecting.compareAndSet(false, true)) {
            FoundationLog.log("PtpSession: percobaan koneksi diabaikan karena proses open sedang berjalan")
            return@runOnBulk false
        }
        try {
            if (usbClaimed.get() || ptpSessionOpen.get() || usbConnection != null) {
                FoundationLog.log("Sesi/klaim USB sebelumnya masih aktif; menutup sesi lama terlebih dahulu...")
                closeSessionInternal(preserveBrokenFlag = false)
                sleepQuietly(RECONNECT_SETTLE_MS, "jeda pasca-tutup sesi lama")
            }

            sessionBroken.set(false)
            transactionIdCounter = 0
            lastBulkOutHaltBit = -1
            lastDeviceInfo = null
            stillStorageId = 0x10000001

            FoundationLog.log("=== MULAI URUTAN CONNECT TERKUNCI: ${device.deviceName} (VID=0x${hex16(device.vendorId)}, PID=0x${hex16(device.productId)}) ===")

            // Langkah 1: Klaim interface PTP
            if (!claimPtpInterface(device)) {
                sessionBroken.set(true)
                closeSessionInternal(preserveBrokenFlag = true)
                return@runOnBulk false
            }

            val conn = usbConnection
            val targetIntf = claimedInterface
            val outEp = epBulkOut
            val inEp = epBulkIn
            val intrEp = epInterruptIn
            if (conn == null || targetIntf == null || outEp == null || inEp == null || intrEp == null) {
                FoundationLog.error("State koneksi tidak lengkap sesudah claimPtpInterface")
                sessionBroken.set(true)
                closeSessionInternal(preserveBrokenFlag = true)
                return@runOnBulk false
            }

            // Diagnostik EP0 pra-tulis
            logInitialEp0Diagnostics(conn, targetIntf, outEp, inEp, intrEp)

            // Pre-drain interrupt loop (maks 4 bacaan)
            preDrainInterruptEndpoint(conn, intrEp)
            startInterruptReader()

            // Buka sesi lewat tangga pemulihan terkunci (Rung C Utama, Rung A & B Fallback)
            if (!runRecoveryLadderAndOpenSession(conn, targetIntf, outEp, inEp, intrEp)) {
                sessionBroken.set(true)
                closeSessionInternal(preserveBrokenFlag = true)
                return@runOnBulk false
            }

            // GetDeviceInfo (0x1001) segera sesudah sesi terbuka
            FoundationLog.log("--- GetDeviceInfo (0x1001) pasca-OpenSession ---")
            val devInfoRes = transactInternal(OC_GET_DEVICE_INFO, markBrokenOnTimeout = false)
            if (devInfoRes.responseCode == RC_OK && devInfoRes.data.isNotEmpty()) {
                val parsed = parseAndLogDeviceInfo(devInfoRes.data)
                lastDeviceInfo = parsed
            } else {
                FoundationLog.error("GetDeviceInfo gagal pasca-OpenSession: ${formatResultStatus(devInfoRes.responseCode)}")
            }

            // Enumerasi Storage PTP (GetStorageIDs 0x1004 & GetStorageInfo 0x1005)
            FoundationLog.log("--- Enumerasi Storage PTP pasca-GetDeviceInfo ---")
            selectStillStorage()

            startInterruptReader()
            FoundationLog.log("=== SESI PTP TERBUKA & SIAP (Tether Auto) ===")
            true
        } finally {
            connecting.set(false)
        }
    }

    /**
     * Alias konseptual untuk [open].
     */
    fun connect(device: UsbDevice): Boolean = open(device)

    /**
     * Menutup sesi PTP dengan sopan (CloseSession + releaseInterface + close connection).
     * Aman dipanggil dari thread mana pun.
     */
    fun closeSession() = runOnBulk {
        ensureBulkThread()
        closeSessionInternal(preserveBrokenFlag = false)
    }

    /**
     * Menutup sesi dan mematikan [bulkExecutor].
     */
    fun shutdown() {
        closeSession()
        bulkExecutor.shutdown()
    }

    // =========================================================================
    // API Publik Operasi PTP (Aman untuk Orkestrator)
    // =========================================================================

    /**
     * Membaca DeviceInfo (0x1001) segar dari kamera.
     */
    fun getDeviceInfo(): DeviceInfo? = runOnBulk {
        ensureBulkThread()
        val res = transactInternal(OC_GET_DEVICE_INFO, markBrokenOnTimeout = false)
        if (res.responseCode == RC_OK && res.data.isNotEmpty()) {
            val parsed = parseAndLogDeviceInfo(res.data)
            lastDeviceInfo = parsed
            parsed
        } else {
            lastDeviceInfo
        }
    }

    /**
     * Membaca daftar ID storage (0x1004) yang tersedia di kamera.
     */
    fun getStorageIDs(): List<Long> = runOnBulk {
        ensureBulkThread()
        val res = transactInternal(OC_GET_STORAGE_IDS, markBrokenOnTimeout = false)
        if (res.responseCode == RC_OK && res.data.size >= 4) {
            val bb = ByteBuffer.wrap(res.data).order(ByteOrder.LITTLE_ENDIAN)
            val count = bb.int.coerceAtLeast(0)
            val available = bb.remaining() / 4
            val n = count.coerceAtMost(available)
            val list = ArrayList<Long>(n)
            for (i in 0 until n) {
                list.add(bb.int.toLong() and 0xFFFFFFFFL)
            }
            list
        } else {
            emptyList()
        }
    }

    /**
     * Membaca metadata StorageInfo (0x1005) untuk storage tertentu.
     */
    fun getStorageInfo(storageId: Long): StorageInfoResult? = runOnBulk {
        ensureBulkThread()
        val res = transactInternal(OC_GET_STORAGE_INFO, intArrayOf(storageId.toInt()), markBrokenOnTimeout = false)
        if (res.responseCode == RC_OK && res.data.isNotEmpty()) {
            parseAndLogStorageInfo(storageId, res.data)
        } else {
            FoundationLog.error("GetStorageInfo (0x1005) gagal untuk storage 0x${hex32(storageId.toInt())}: ${formatResultStatus(res.responseCode)}")
            null
        }
    }

    /**
     * Overload [getStorageInfo] berbasis ID [Int].
     */
    fun getStorageInfo(storageId: Int): StorageInfoResult? = getStorageInfo(storageId.toLong() and 0xFFFFFFFFL)

    /**
     * Mengambil daftar handle objek (0x1007) pada storage tertentu (default [stillStorageId]).
     */
    fun getObjectHandles(
        storageId: Int = stillStorageId,
        formatCode: Int = 0,
        parentHandle: Int = -1
    ): IntArray = runOnBulk {
        ensureBulkThread()
        val res = transactInternal(OC_GET_OBJECT_HANDLES, intArrayOf(storageId, formatCode, parentHandle), markBrokenOnTimeout = false)
        if (res.responseCode == RC_OK && res.data.size >= 4) {
            parseUint32Array(res.data)
        } else {
            IntArray(0)
        }
    }

    /**
     * Membaca metadata objek (0x1008) untuk handle yang diminta.
     */
    fun getObjectInfo(handle: Int): ObjectInfoResult? = runOnBulk {
        ensureBulkThread()
        val res = transactInternal(OC_GET_OBJECT_INFO, intArrayOf(handle))
        if (res.responseCode == RC_OK && res.data.isNotEmpty()) {
            parseAndLogObjectInfo(handle, res.data)
        } else {
            FoundationLog.error("GetObjectInfo (0x1008) gagal untuk handle 0x${hex32(handle)}: ${formatResultStatus(res.responseCode)}")
            null
        }
    }

    /**
     * Mengunduh berkas objek (0x1009) langsung secara streaming ke [destinationFile].
     * Potongan unduh default 16384B, menghitung MD5 on-the-fly, dan memverifikasi byte-exact
     * terhadap [expectedSize].
     */
    fun streamGetObjectToFile(
        handle: Int,
        destinationFile: File,
        expectedSize: Long = -1L,
        chunkSize: Int = DEFAULT_DOWNLOAD_CHUNK_SIZE
    ): DownloadResult? = runOnBulk {
        ensureBulkThread()
        streamGetObjectToFileInternal(handle, destinationFile, expectedSize, chunkSize)
    }

    /**
     * Alias untuk [streamGetObjectToFile].
     */
    fun downloadObject(
        handle: Int,
        destinationFile: File,
        expectedSize: Long = -1L,
        chunkSize: Int = DEFAULT_DOWNLOAD_CHUNK_SIZE
    ): DownloadResult? = streamGetObjectToFile(handle, destinationFile, expectedSize, chunkSize)

    /**
     * Menghapus objek dari storage kamera (0x100B) lewat jalur checkout orkestrator.
     */
    fun deleteObject(handle: Int): Boolean = runOnBulk {
        ensureBulkThread()
        val res = transactInternal(OC_DELETE_OBJECT, intArrayOf(handle))
        val ok = (res.responseCode == RC_OK)
        if (ok) {
            FoundationLog.log("DeleteObject (0x100B) untuk handle 0x${hex32(handle)} SUKSES")
        } else {
            FoundationLog.error("DeleteObject (0x100B) untuk handle 0x${hex32(handle)} GAGAL: ${formatResultStatus(res.responseCode)}")
        }
        ok
    }

    /**
     * Membaca nilai properti perangkat (0x1015).
     */
    fun getDevicePropValue(propCode: Int): ByteArray? = runOnBulk {
        ensureBulkThread()
        val res = transactInternal(OC_GET_DEVICE_PROP_VALUE, intArrayOf(propCode), markBrokenOnTimeout = false)
        if (res.responseCode == RC_OK) {
            res.data
        } else {
            null
        }
    }

    /**
     * Membaca nilai properti skalar sebagai Integer (misalnya untuk D20E FreeSDRAMImages).
     */
    fun getDevicePropValueAsInt(propCode: Int): Int? = runOnBulk {
        ensureBulkThread()
        val raw = getDevicePropValue(propCode) ?: return@runOnBulk null
        if (raw.isEmpty()) return@runOnBulk null
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        when (raw.size) {
            1 -> raw[0].toInt() and 0xFF
            2 -> bb.short.toInt() and 0xFFFF
            4 -> bb.int
            else -> null
        }
    }

    /**
     * Membaca deskripsi properti perangkat (0x1014).
     */
    fun getDevicePropDesc(propCode: Int): ByteArray? = runOnBulk {
        ensureBulkThread()
        val res = transactInternal(OC_GET_DEVICE_PROP_DESC, intArrayOf(propCode), markBrokenOnTimeout = false)
        if (res.responseCode == RC_OK) {
            res.data
        } else {
            null
        }
    }

    /**
     * Menjalankan transaksi PTP umum berpagar ketat (ALLOWLIST).
     */
    fun transact(
        opcode: Int,
        params: IntArray = IntArray(0),
        dataOut: ByteArray? = null,
        expectDataIn: Boolean = opcode in DATA_IN_OPCODES,
        markBrokenOnTimeout: Boolean = true,
        overrideTid: Int? = null
    ): PtpResult = runOnBulk {
        ensureBulkThread()
        transactInternal(opcode, params, dataOut, expectDataIn, markBrokenOnTimeout, overrideTid)
    }

    // =========================================================================
    // Implementasi Internal Transaksi Bulk & Streaming
    // =========================================================================

    private fun transactInternal(
        opcode: Int,
        params: IntArray = IntArray(0),
        dataOut: ByteArray? = null,
        expectDataIn: Boolean = opcode in DATA_IN_OPCODES,
        markBrokenOnTimeout: Boolean = true,
        overrideTid: Int? = null
    ): PtpResult {
        ensureBulkThread()
        require(opcode in ALLOWED_OPCODES) {
            "Opcode 0x${hex16(opcode)} dilarang oleh SPEC Stage 1"
        }
        require(dataOut == null) {
            "dataOut dilarang; Stage 1 tidak mengizinkan operasi bulk data-out"
        }

        val conn = usbConnection
        val outEp = epBulkOut
        val inEp = epBulkIn
        if (conn == null || outEp == null || inEp == null || !usbClaimed.get()) {
            FoundationLog.error("transact(0x${hex16(opcode)}) gagal: koneksi/klaim USB belum siap")
            sessionBroken.set(true)
            return PtpResult(opcode, -1, RC_TRANSPORT_NOT_READY, IntArray(0), ByteArray(0), 0L)
        }

        val tid: Int = when {
            opcode == OC_OPEN_SESSION -> {
                transactionIdCounter = 0
                overrideTid ?: 0
            }
            overrideTid != null -> overrideTid
            !ptpSessionOpen.get() -> 0
            else -> ++transactionIdCounter
        }
        val txStartMs = System.currentTimeMillis()

        // 1. Bangun Command Container (length, type=1, code, transactionId, params)
        val cmdLen = HEADER_SIZE + params.size * 4
        val cmdBuf = ByteBuffer.allocate(cmdLen).order(ByteOrder.LITTLE_ENDIAN)
        cmdBuf.putInt(cmdLen)
        cmdBuf.putShort(CONTAINER_COMMAND.toShort())
        cmdBuf.putShort(opcode.toShort())
        cmdBuf.putInt(tid)
        for (p in params) {
            cmdBuf.putInt(p)
        }
        val cmdBytes = cmdBuf.array()
        val paramsHex = if (params.isEmpty()) "[]" else params.joinToString(", ", "[", "]") { "0x${hex32(it)}" }
        val intfId = claimedInterface?.id ?: 0
        val txDesc = "opcode=0x${hex16(opcode)} (${opcodeName(opcode)}), tid=$tid"

        var recoveryAttempted = false

        txAttemptLoop@ while (true) {
            if (recoveryAttempted) {
                FoundationLog.log("[PEMULIHAN-STALL] Mengulangi transaksi dari Command Container (retry 1x) untuk $txDesc")
            }

            val cmdStartMs = System.currentTimeMillis()
            if (!bulkWriteAll(conn, outEp, cmdBytes, BULK_TIMEOUT_MS)) {
                val elapsed = System.currentTimeMillis() - txStartMs
                FoundationLog.error("Gagal mengirim Command Container $txDesc (${elapsed}ms) [TRANSPORT_ERROR_BULK_OUT]")
                sessionBroken.set(true)
                return PtpResult(opcode, tid, RC_TRANSPORT_WRITE_FAILED, IntArray(0), ByteArray(0), elapsed)
            }
            val cmdElapsedMs = System.currentTimeMillis() - cmdStartMs
            FoundationLog.log(
                "CMD OUT${if (recoveryAttempted) " [RETRY]" else ""}: opcode=0x${hex16(opcode)} (${opcodeName(opcode)}), " +
                    "tid=$tid, params=$paramsHex, len=$cmdLen, waktu=${cmdElapsedMs}ms"
            )

            var dataPayload = ByteArray(0)
            var pendingResponsePacket: ByteArray? = null

            // 2. Baca fase data (jika operasi data-in) via 512B reader
            if (expectDataIn) {
                val dataStartMs = System.currentTimeMillis()
                val chunkBuf = ByteArray(DATA_READ_CHUNK)
                val initialAccum = ByteArrayOutputStream(DATA_READ_CHUNK)

                while (initialAccum.size() < HEADER_SIZE) {
                    val r = conn.bulkTransfer(inEp, chunkBuf, DATA_READ_CHUNK, BULK_TIMEOUT_MS)
                    if (r <= 0) {
                        val elapsed = System.currentTimeMillis() - txStartMs
                        FoundationLog.error("Timeout/error membaca header paket IN untuk $txDesc, ret=$r [TRANSPORT_ERROR_BULK_IN]")
                        val stallDetected = logBulkInStatusAfterReadFailure(conn, inEp, intfId)
                        if (stallDetected && !recoveryAttempted) {
                            recoveryAttempted = true
                            performBulkInStallRecovery(conn, inEp, intfId, txDesc)
                            continue@txAttemptLoop
                        }
                        if (markBrokenOnTimeout) {
                            sessionBroken.set(true)
                        }
                        return PtpResult(opcode, tid, RC_TRANSPORT_READ_FAILED, IntArray(0), ByteArray(0), elapsed)
                    }
                    initialAccum.write(chunkBuf, 0, r)
                }

                val firstBuf = initialAccum.toByteArray()
                val firstRead = firstBuf.size
                val firstHeader = ByteBuffer.wrap(firstBuf, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                val totalContainerLen = firstHeader.int
                val containerType = firstHeader.short.toInt() and 0xFFFF
                val containerCode = firstHeader.short.toInt() and 0xFFFF
                val containerTid = firstHeader.int

                if (containerType == CONTAINER_RESPONSE || containerType == CONTAINER_DATA) {
                    if (containerTid != tid) {
                        val elapsed = System.currentTimeMillis() - txStartMs
                        FoundationLog.error(
                            "[PTP] TID-BASI: kontainer bertid $containerTid diterima saat menunggu tid $tid (opcode 0x${hex16(opcode)}) — transaksi digagalkan agar tangga pemulihan bekerja"
                        )
                        sessionBroken.set(true)
                        return PtpResult(opcode, tid, RC_TRANSPORT_BAD_CONTAINER, IntArray(0), ByteArray(0), elapsed)
                    }
                }

                if (containerType == CONTAINER_RESPONSE) {
                    // Kamera langsung mengirim Response Container tanpa fase data
                    val targetRespLen = totalContainerLen.coerceAtLeast(HEADER_SIZE)
                    while (initialAccum.size() < targetRespLen) {
                        val r = conn.bulkTransfer(inEp, chunkBuf, DATA_READ_CHUNK, BULK_TIMEOUT_MS)
                        if (r <= 0) {
                            val elapsed = System.currentTimeMillis() - txStartMs
                            FoundationLog.error("Timeout/error melengkapi Response langsung pada fase DATA IN untuk $txDesc, ret=$r")
                            val stallDetected = logBulkInStatusAfterReadFailure(conn, inEp, intfId)
                            if (stallDetected && !recoveryAttempted) {
                                recoveryAttempted = true
                                performBulkInStallRecovery(conn, inEp, intfId, txDesc)
                                continue@txAttemptLoop
                            }
                            if (markBrokenOnTimeout) {
                                sessionBroken.set(true)
                            }
                            return PtpResult(opcode, tid, RC_TRANSPORT_READ_FAILED, IntArray(0), ByteArray(0), elapsed)
                        }
                        initialAccum.write(chunkBuf, 0, r)
                    }
                    pendingResponsePacket = initialAccum.toByteArray()
                    FoundationLog.log(
                        "DATA IN dilewati oleh kamera (langsung Response type=3): opcode=0x${hex16(opcode)}, " +
                            "rc=0x${hex16(containerCode)} (${rcName(containerCode)}), tid=$containerTid, " +
                            "waktu=${System.currentTimeMillis() - dataStartMs}ms"
                    )
                } else if (containerType == CONTAINER_DATA) {
                    val expectedPayloadLen = (totalContainerLen - HEADER_SIZE).coerceAtLeast(0)
                    val outPayload = ByteArray(expectedPayloadLen)
                    var copied = 0

                    val initialDataBytes = (firstRead - HEADER_SIZE).coerceAtMost(expectedPayloadLen)
                    if (initialDataBytes > 0) {
                        System.arraycopy(firstBuf, HEADER_SIZE, outPayload, 0, initialDataBytes)
                        copied += initialDataBytes
                    }

                    if (firstRead > totalContainerLen) {
                        pendingResponsePacket = firstBuf.copyOfRange(totalContainerLen, firstRead)
                    }

                    while (copied < expectedPayloadLen) {
                        val r = conn.bulkTransfer(inEp, chunkBuf, DATA_READ_CHUNK, BULK_TIMEOUT_MS)
                        if (r <= 0) {
                            val elapsed = System.currentTimeMillis() - txStartMs
                            FoundationLog.error("Timeout/error membaca payload DATA IN untuk $txDesc, terbaca=$copied/$expectedPayloadLen, ret=$r")
                            val stallDetected = logBulkInStatusAfterReadFailure(conn, inEp, intfId)
                            if (stallDetected && !recoveryAttempted) {
                                recoveryAttempted = true
                                performBulkInStallRecovery(conn, inEp, intfId, txDesc)
                                continue@txAttemptLoop
                            }
                            if (markBrokenOnTimeout) {
                                sessionBroken.set(true)
                            }
                            return PtpResult(opcode, tid, RC_TRANSPORT_READ_FAILED, IntArray(0), outPayload.copyOfRange(0, copied), elapsed)
                        }
                        val toCopy = r.coerceAtMost(expectedPayloadLen - copied)
                        System.arraycopy(chunkBuf, 0, outPayload, copied, toCopy)
                        copied += toCopy
                        if (r > toCopy) {
                            pendingResponsePacket = chunkBuf.copyOfRange(toCopy, r)
                        }
                    }

                    dataPayload = outPayload
                    val dataElapsedMs = System.currentTimeMillis() - dataStartMs
                    FoundationLog.log(
                        "DATA IN${if (recoveryAttempted) " [RETRY]" else ""}: opcode=0x${hex16(opcode)} (${opcodeName(opcode)}), tid=$containerTid, " +
                            "payload=${dataPayload.size}B (totalContainer=$totalContainerLen), waktu=${dataElapsedMs}ms"
                    )
                } else {
                    val elapsed = System.currentTimeMillis() - txStartMs
                    FoundationLog.error("Tipe container tidak dikenal saat menunggu DATA IN: type=0x${hex16(containerType)} [TRANSPORT_BAD_CONTAINER]")
                    if (markBrokenOnTimeout) {
                        sessionBroken.set(true)
                    }
                    return PtpResult(opcode, tid, RC_TRANSPORT_BAD_CONTAINER, IntArray(0), ByteArray(0), elapsed)
                }
            }

            // 3. Baca Response Container (type=3)
            val respStartMs = System.currentTimeMillis()
            val respPacket: ByteArray = if (pendingResponsePacket != null) {
                pendingResponsePacket
            } else {
                var respBuf = ByteArray(DATA_READ_CHUNK)
                val r = conn.bulkTransfer(inEp, respBuf, DATA_READ_CHUNK, BULK_TIMEOUT_MS)
                if (r < HEADER_SIZE) {
                    val elapsed = System.currentTimeMillis() - txStartMs
                    FoundationLog.error("Timeout/error membaca Response Container untuk $txDesc, ret=$r")
                    val stallDetected = logBulkInStatusAfterReadFailure(conn, inEp, intfId)
                    if (stallDetected && !recoveryAttempted) {
                        recoveryAttempted = true
                        performBulkInStallRecovery(conn, inEp, intfId, txDesc)
                        continue@txAttemptLoop
                    }
                    if (markBrokenOnTimeout) {
                        sessionBroken.set(true)
                    }
                    return PtpResult(opcode, tid, RC_TRANSPORT_READ_FAILED, IntArray(0), dataPayload, elapsed)
                }
                val declaredRespLen = ByteBuffer.wrap(respBuf, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).int
                if (declaredRespLen < HEADER_SIZE || declaredRespLen > 4096) {
                    val elapsed = System.currentTimeMillis() - txStartMs
                    FoundationLog.error("[PTP] Panjang Response Container tidak wajar: $declaredRespLen byte — kontainer ditolak")
                    sessionBroken.set(true)
                    return PtpResult(opcode, tid, RC_TRANSPORT_BAD_CONTAINER, IntArray(0), dataPayload, elapsed)
                }
                if (declaredRespLen > respBuf.size) {
                    respBuf = respBuf.copyOf(declaredRespLen)
                }
                var totalRead = r
                var lastRet = r
                while (totalRead < declaredRespLen) {
                    val ret = conn.bulkTransfer(inEp, respBuf, totalRead, respBuf.size - totalRead, BULK_TIMEOUT_MS)
                    lastRet = ret
                    if (ret <= 0) {
                        break
                    }
                    totalRead += ret
                }
                if (lastRet < 0 || totalRead < declaredRespLen) {
                    val elapsed = System.currentTimeMillis() - txStartMs
                    FoundationLog.error("Timeout/error membaca Response Container untuk $txDesc, terbaca=$totalRead/$declaredRespLen, ret=$lastRet")
                    val stallDetected = logBulkInStatusAfterReadFailure(conn, inEp, intfId)
                    if (stallDetected && !recoveryAttempted) {
                        recoveryAttempted = true
                        performBulkInStallRecovery(conn, inEp, intfId, txDesc)
                        continue@txAttemptLoop
                    }
                    if (markBrokenOnTimeout) {
                        sessionBroken.set(true)
                    }
                    return PtpResult(opcode, tid, RC_TRANSPORT_READ_FAILED, IntArray(0), dataPayload, elapsed)
                }
                val finalLen = if (declaredRespLen <= r) r else declaredRespLen
                respBuf.copyOfRange(0, finalLen)
            }

            val respBb = ByteBuffer.wrap(respPacket).order(ByteOrder.LITTLE_ENDIAN)
            val respLen = respBb.int
            val respType = respBb.short.toInt() and 0xFFFF
            val respCode = respBb.short.toInt() and 0xFFFF
            val respTid = respBb.int
            val respParamCount = ((respPacket.size - HEADER_SIZE).coerceAtLeast(0)) / 4
            val respParams = IntArray(respParamCount) { respBb.int }
            val respElapsedMs = System.currentTimeMillis() - respStartMs
            val totalElapsedMs = System.currentTimeMillis() - txStartMs

            if (respTid != tid) {
                FoundationLog.error(
                    "[PTP] TID-BASI: kontainer bertid $respTid diterima saat menunggu tid $tid (opcode 0x${hex16(opcode)}) — transaksi digagalkan agar tangga pemulihan bekerja"
                )
                sessionBroken.set(true)
                return PtpResult(opcode, respTid, RC_TRANSPORT_BAD_CONTAINER, IntArray(0), dataPayload, totalElapsedMs)
            }

            if (respType != CONTAINER_RESPONSE) {
                FoundationLog.error("Container bukan RESPONSE (type=0x${hex16(respType)}) untuk $txDesc, code=0x${hex16(respCode)}")
                sessionBroken.set(true)
                return PtpResult(opcode, respTid, RC_TRANSPORT_BAD_CONTAINER, respParams, dataPayload, totalElapsedMs)
            }

            val rParamsHex = if (respParams.isEmpty()) "[]" else respParams.joinToString(", ", "[", "]") { "0x${hex32(it)}" }
            FoundationLog.log(
                "RESP IN${if (recoveryAttempted) " [RETRY]" else ""}: opcode=0x${hex16(opcode)}, " +
                    "rc=0x${hex16(respCode)} (${rcName(respCode)}), tid=$respTid, len=$respLen, " +
                    "params=$rParamsHex, waktuResp=${respElapsedMs}ms, total=${totalElapsedMs}ms"
            )

            return PtpResult(
                opcode = opcode,
                transactionId = respTid,
                responseCode = respCode,
                responseParams = respParams,
                data = dataPayload,
                elapsedMs = totalElapsedMs
            )
        }
    }

    private fun streamGetObjectToFileInternal(
        handle: Int,
        destinationFile: File,
        expectedSize: Long,
        chunkSize: Int
    ): DownloadResult? {
        ensureBulkThread()
        val conn = usbConnection ?: return null
        val outEp = epBulkOut ?: return null
        val inEp = epBulkIn ?: return null

        val tid = ++transactionIdCounter
        val startMs = System.currentTimeMillis()

        val cmdBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        cmdBuf.putInt(16)
        cmdBuf.putShort(CONTAINER_COMMAND.toShort())
        cmdBuf.putShort(OC_GET_OBJECT.toShort())
        cmdBuf.putInt(tid)
        cmdBuf.putInt(handle)
        val cmdBytes = cmdBuf.array()

        val intfId = claimedInterface?.id ?: 0
        val txDesc = "opcode=0x1009 (GetObject), tid=$tid, handle=0x${hex32(handle)}"
        var recoveryAttempted = false

        downloadAttemptLoop@ while (true) {
            if (recoveryAttempted) {
                FoundationLog.log("[PEMULIHAN-STALL] Mengulangi GetObject dari Command Container (retry 1x) untuk $txDesc")
            }

            if (!bulkWriteAll(conn, outEp, cmdBytes, BULK_TIMEOUT_MS)) {
                FoundationLog.error("GetObject (0x1009) gagal mengirim Command Container untuk handle=0x${hex32(handle)}")
                sessionBroken.set(true)
                return null
            }
            FoundationLog.log("CMD OUT${if (recoveryAttempted) " [RETRY]" else ""}: opcode=0x1009 (GetObject), tid=$tid, handle=0x${hex32(handle)}")

            val buf = ByteArray(chunkSize)
            val initialAccum = ByteArrayOutputStream(chunkSize)
            while (initialAccum.size() < HEADER_SIZE) {
                val r = conn.bulkTransfer(inEp, buf, chunkSize, DOWNLOAD_TIMEOUT_MS)
                if (r <= 0) {
                    FoundationLog.error("GetObject timeout/error pada paket header (chunk=${chunkSize}B): ret=$r")
                    val stallDetected = logBulkInStatusAfterReadFailure(conn, inEp, intfId)
                    if (stallDetected && !recoveryAttempted) {
                        recoveryAttempted = true
                        performBulkInStallRecovery(conn, inEp, intfId, txDesc)
                        continue@downloadAttemptLoop
                    }
                    sessionBroken.set(true)
                    return null
                }
                initialAccum.write(buf, 0, r)
            }

            val firstBuf = initialAccum.toByteArray()
            val firstRead = firstBuf.size
            val hdr = ByteBuffer.wrap(firstBuf, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val containerLenUnsigned = hdr.int.toLong() and 0xFFFFFFFFL
            val containerType = hdr.short.toInt() and 0xFFFF
            val containerCode = hdr.short.toInt() and 0xFFFF
            val containerTid = hdr.int

            if (containerTid != tid) {
                FoundationLog.error(
                    "[PTP] TID-BASI: kontainer bertid $containerTid diterima saat menunggu tid $tid (opcode 0x${hex16(OC_GET_OBJECT)}) — transaksi digagalkan agar tangga pemulihan bekerja"
                )
                sessionBroken.set(true)
                return null
            }

            if (containerType == CONTAINER_RESPONSE) {
                FoundationLog.error("GetObject ditolak kamera tanpa data: rc=0x${hex16(containerCode)} (${rcName(containerCode)}), tid=$containerTid")
                return null
            }
            if (containerType != CONTAINER_DATA) {
                FoundationLog.error("GetObject menerima container tidak dikenal: type=0x${hex16(containerType)}")
                sessionBroken.set(true)
                return null
            }

            val expectedPayloadBytes = if (containerLenUnsigned == 0xFFFFFFFFL && expectedSize > 0) {
                expectedSize
            } else {
                (containerLenUnsigned - HEADER_SIZE).coerceAtLeast(0L)
            }

            val parentDir = destinationFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            var totalWritten = 0L
            var coalescedResp: ByteArray? = null
            var triggerStallRetryMidStream = false
            val md5 = MessageDigest.getInstance("MD5")

            try {
                FileOutputStream(destinationFile).use { fos ->
                    val initialPayload = (firstRead - HEADER_SIZE).toLong().coerceAtMost(expectedPayloadBytes).toInt()
                    if (initialPayload > 0) {
                        fos.write(firstBuf, HEADER_SIZE, initialPayload)
                        md5.update(firstBuf, HEADER_SIZE, initialPayload)
                        totalWritten += initialPayload
                    }
                    if (firstRead - HEADER_SIZE > initialPayload) {
                        coalescedResp = firstBuf.copyOfRange(HEADER_SIZE + initialPayload, firstRead)
                    }

                    while (totalWritten < expectedPayloadBytes) {
                        val r = conn.bulkTransfer(inEp, buf, chunkSize, DOWNLOAD_TIMEOUT_MS)
                        if (r <= 0) {
                            FoundationLog.error("GetObject timeout/error di tengah unduhan (chunk=${chunkSize}B): terunduh=$totalWritten/$expectedPayloadBytes, ret=$r")
                            val stallDetected = logBulkInStatusAfterReadFailure(conn, inEp, intfId)
                            if (stallDetected && !recoveryAttempted) {
                                triggerStallRetryMidStream = true
                                return@use
                            }
                            sessionBroken.set(true)
                            return null
                        }
                        val remaining = expectedPayloadBytes - totalWritten
                        val toWrite = r.toLong().coerceAtMost(remaining).toInt()
                        fos.write(buf, 0, toWrite)
                        md5.update(buf, 0, toWrite)
                        totalWritten += toWrite
                        if (r > toWrite) {
                            coalescedResp = buf.copyOfRange(toWrite, r)
                        }
                    }
                    fos.flush()
                }
            } catch (e: Exception) {
                FoundationLog.error("Gagal menulis file unduhan ${destinationFile.name}", e)
                return null
            }

            if (triggerStallRetryMidStream) {
                recoveryAttempted = true
                performBulkInStallRecovery(conn, inEp, intfId, txDesc)
                continue@downloadAttemptLoop
            }

            // Baca Response Container akhir
            val respPacket = coalescedResp ?: run {
                val rBuf = ByteArray(chunkSize)
                val r = conn.bulkTransfer(inEp, rBuf, chunkSize, DOWNLOAD_TIMEOUT_MS)
                if (r < HEADER_SIZE) {
                    FoundationLog.error("GetObject gagal membaca Response Container akhir: ret=$r")
                    val stallDetected = logBulkInStatusAfterReadFailure(conn, inEp, intfId)
                    if (stallDetected && !recoveryAttempted) {
                        recoveryAttempted = true
                        performBulkInStallRecovery(conn, inEp, intfId, txDesc)
                        continue@downloadAttemptLoop
                    }
                    sessionBroken.set(true)
                    return null
                }
                rBuf.copyOfRange(0, r)
            }

            val respBb = ByteBuffer.wrap(respPacket).order(ByteOrder.LITTLE_ENDIAN)
            respBb.int // len
            val respType = respBb.short.toInt() and 0xFFFF
            val respCode = respBb.short.toInt() and 0xFFFF
            val respTid = respBb.int

            if (respTid != tid) {
                FoundationLog.error(
                    "[PTP] TID-BASI: kontainer bertid $respTid diterima saat menunggu tid $tid (opcode 0x${hex16(OC_GET_OBJECT)}) — transaksi digagalkan agar tangga pemulihan bekerja"
                )
                sessionBroken.set(true)
                return null
            }

            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val bytesPerSecond = (totalWritten * 1000L) / elapsedMs
            val mbPerSec = bytesPerSecond.toDouble() / (1024.0 * 1024.0)

            val digestBytes = md5.digest()
            val hexChars = "0123456789abcdef"
            val sbMd5 = StringBuilder(digestBytes.size * 2)
            for (b in digestBytes) {
                val v = b.toInt() and 0xFF
                sbMd5.append(hexChars[v ushr 4])
                sbMd5.append(hexChars[v and 0x0F])
            }
            val md5Hex = sbMd5.toString()

            val sizeMatch = (expectedSize <= 0L || totalWritten == expectedSize)
            val success = (respCode == RC_OK && sizeMatch)

            if (!sizeMatch) {
                FoundationLog.error("Verifikasi ukuran GAGAL untuk handle 0x${hex32(handle)}: terunduh=$totalWritten vs expected=$expectedSize")
            } else if (expectedSize > 0L) {
                FoundationLog.log("Verifikasi ukuran SUKSES: $totalWritten byte byte-exact")
            }

            FoundationLog.log(
                String.format(
                    Locale.US,
                    "[UNDUH] handle=0x%s, rc=0x%s (%s, tid=%d), bytes=%d, md5=%s, durasi=%d ms, speed=%.2f MB/s, chunk=%d B, path=%s",
                    hex32(handle),
                    hex16(respCode),
                    rcName(respCode),
                    respTid,
                    totalWritten,
                    md5Hex,
                    elapsedMs,
                    mbPerSec,
                    chunkSize,
                    destinationFile.name
                )
            )

            return DownloadResult(
                success = success,
                path = destinationFile.absolutePath,
                sizeBytes = totalWritten,
                md5Hex = md5Hex,
                elapsedMs = elapsedMs,
                bytesPerSecond = bytesPerSecond
            )
        }
    }

    private fun bulkWriteAll(
        conn: UsbDeviceConnection,
        outEp: UsbEndpoint,
        bytes: ByteArray,
        timeoutMs: Int
    ): Boolean {
        var offset = 0
        while (offset < bytes.size) {
            val chunkLen = (bytes.size - offset).coerceAtMost(BULK_CHUNK_SIZE)
            val slice = if (offset == 0 && chunkLen == bytes.size) {
                bytes
            } else {
                bytes.copyOfRange(offset, offset + chunkLen)
            }
            val written = conn.bulkTransfer(outEp, slice, slice.size, timeoutMs)
            if (written <= 0) {
                FoundationLog.error("bulkTransfer OUT gagal pada offset=$offset/${bytes.size}, ret=$written [TRANSPORT_ERROR_BULK_OUT]")
                logBulkOutStatusAfterWriteFailure(conn, outEp)
                return false
            }
            offset += written
        }
        return true
    }

    // =========================================================================
    // Tangga Pemulihan Stall & Diagnostik EP0
    // =========================================================================

    private fun logInitialEp0Diagnostics(
        conn: UsbDeviceConnection,
        targetIntf: UsbInterface,
        outEp: UsbEndpoint,
        inEp: UsbEndpoint,
        intrEp: UsbEndpoint
    ) {
        ensureBulkThread()
        FoundationLog.log("=== DIAGNOSTIK EP0 PRA-TULIS ===")
        lastBulkOutHaltBit = queryAndLogEndpointStatus(conn, outEp.address, "BulkOUT", "PRA-TULIS")
        queryAndLogEndpointStatus(conn, inEp.address, "BulkIN", "PRA-TULIS")
        queryAndLogEndpointStatus(conn, intrEp.address, "InterruptIN", "PRA-TULIS")
        queryAndLogStillImageDeviceStatus(conn, targetIntf.id, "PRA-TULIS")
    }

    private fun queryAndLogEndpointStatus(
        conn: UsbDeviceConnection,
        epAddress: Int,
        epLabel: String,
        stageLabel: String
    ): Int {
        val buf = ByteArray(2)
        val ret = conn.controlTransfer(
            USB_REQ_TYPE_EP_STATUS_IN,
            USB_REQ_GET_STATUS,
            0,
            epAddress,
            buf,
            buf.size,
            EP0_TIMEOUT_MS
        )
        if (ret < 2) {
            FoundationLog.log("EP0 GET_STATUS [$stageLabel] ep=0x${hex8(epAddress)} ($epLabel): GAGAL ret=$ret")
            return -1
        }
        val statusWord = (buf[0].toInt() and 0xFF) or ((buf[1].toInt() and 0xFF) shl 8)
        val haltBit = statusWord and 0x0001
        val haltStr = if (haltBit == 1) "HALTED (STALL=1)" else "CLEAR (HALT=0)"
        FoundationLog.log(
            "EP0 GET_STATUS [$stageLabel] ep=0x${hex8(epAddress)} ($epLabel): ret=$ret, " +
                "status=0x${hex16(statusWord)} [$haltStr]"
        )
        return haltBit
    }

    private fun queryAndLogStillImageDeviceStatus(
        conn: UsbDeviceConnection,
        interfaceId: Int,
        stageLabel: String
    ): IntArray {
        val buf = ByteArray(32)
        val ret = conn.controlTransfer(
            USB_REQ_TYPE_CLASS_INTF_IN,
            USB_CLASS_REQ_GET_DEVICE_STATUS,
            0,
            interfaceId,
            buf,
            buf.size,
            EP0_TIMEOUT_MS
        )
        if (ret < 0) {
            FoundationLog.log("EP0 Class Get_Device_Status [$stageLabel] (intf=$interfaceId): GAGAL/STALL ret=$ret")
            return IntArray(0)
        }
        val slice = buf.copyOfRange(0, ret)
        if (ret < 4) {
            FoundationLog.log("EP0 Class Get_Device_Status [$stageLabel] (intf=$interfaceId): ret=$ret (pendek <4B)")
            return IntArray(0)
        }
        val bb = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN)
        val wLength = bb.short.toInt() and 0xFFFF
        val statusCode = bb.short.toInt() and 0xFFFF
        val paramCount = bb.remaining() / 4
        val params = IntArray(paramCount) { bb.int }
        val paramsHex = if (params.isEmpty()) "[]" else params.joinToString(", ", "[", "]") { "0x${hex32(it)}" }
        FoundationLog.log(
            "EP0 Class Get_Device_Status [$stageLabel] (intf=$interfaceId): ret=$ret, " +
                "wLength=$wLength, code=0x${hex16(statusCode)} (${rcName(statusCode)}), haltedEpParams=$paramsHex"
        )
        return params
    }

    private fun clearAllEndpointHalts(
        conn: UsbDeviceConnection,
        outEp: UsbEndpoint,
        inEp: UsbEndpoint,
        intrEp: UsbEndpoint,
        stageLabel: String
    ) {
        val endpoints = listOf(
            outEp.address to "BulkOUT",
            inEp.address to "BulkIN",
            intrEp.address to "InterruptIN"
        )
        for ((epAddr, epLabel) in endpoints) {
            val ret = conn.controlTransfer(
                USB_REQ_TYPE_EP_CLEAR_OUT,
                USB_REQ_CLEAR_FEATURE,
                USB_FEATURE_ENDPOINT_HALT,
                epAddr,
                null,
                0,
                EP0_TIMEOUT_MS
            )
            FoundationLog.log(
                "EP0 CLEAR_FEATURE(ENDPOINT_HALT) [$stageLabel] ep=0x${hex8(epAddr)} ($epLabel): ret=$ret (${if (ret >= 0) "OK" else "GAGAL"})"
            )
        }
        lastBulkOutHaltBit = queryAndLogEndpointStatus(conn, outEp.address, "BulkOUT", "$stageLabel pasca-CLEAR_FEATURE")
    }

    private fun logBulkOutStatusAfterWriteFailure(conn: UsbDeviceConnection, outEp: UsbEndpoint) {
        val prevHalt = lastBulkOutHaltBit
        val newHalt = queryAndLogEndpointStatus(conn, outEp.address, "BulkOUT", "PASCA-GAGAL-TULIS")
        FoundationLog.log("DIAGNOSTIK HALT 0x01 PASCA-GAGAL-TULIS: prev=$prevHalt -> new=$newHalt")
        lastBulkOutHaltBit = newHalt
    }

    private fun logBulkInStatusAfterReadFailure(
        conn: UsbDeviceConnection,
        inEp: UsbEndpoint,
        interfaceId: Int
    ): Boolean {
        val haltBit = queryAndLogEndpointStatus(conn, inEp.address, "BulkIN", "PASCA-GAGAL-BACA")
        val haltedEpParams = queryAndLogStillImageDeviceStatus(conn, interfaceId, "PASCA-GAGAL-BACA")
        val inEpHaltedInClassStatus = haltedEpParams.any { (it and 0xFF) == (inEp.address and 0xFF) }
        val verdict = when {
            haltBit == 1 && inEpHaltedInClassStatus -> "HALT=1 & class haltedEpParams memuat 0x${hex8(inEp.address)}"
            haltBit == 1 -> "HALT=1 pada endpoint GET_STATUS"
            inEpHaltedInClassStatus -> "class Get_Device_Status memuat 0x${hex8(inEp.address)} di haltedEpParams"
            haltBit == 0 -> "BulkIN 0x${hex8(inEp.address)} TIDAK halt"
            else -> "HALT=ERROR ($haltBit)"
        }
        FoundationLog.log("DIAGNOSTIK BULK-IN 0x${hex8(inEp.address)} [PASCA-GAGAL-BACA]: haltBit=$haltBit -> $verdict")
        return (haltBit == 1) || inEpHaltedInClassStatus
    }

    private fun performBulkInStallRecovery(
        conn: UsbDeviceConnection,
        inEp: UsbEndpoint,
        interfaceId: Int,
        txDesc: String
    ) {
        FoundationLog.log(
            "=== [PEMULIHAN-STALL] Memulai pemulihan stall untuk $txDesc " +
                "(Device_Reset 0x66 -> CLEAR_FEATURE 0x${hex8(inEp.address)} -> sleep ${RUNG_B_SETTLE_MS}ms -> retry 1x) ==="
        )
        val classResetRet = conn.controlTransfer(
            USB_REQ_TYPE_CLASS_INTF_OUT,
            USB_CLASS_REQ_DEVICE_RESET,
            0,
            interfaceId,
            null,
            0,
            EP0_RESET_TIMEOUT_MS
        )
        FoundationLog.log(
            "[PEMULIHAN-STALL] EP0 Class Device_Reset (0x21, 0x66, intf=$interfaceId): " +
                "ret=$classResetRet (${if (classResetRet >= 0) "OK" else "GAGAL"})"
        )
        val clearRet = conn.controlTransfer(
            USB_REQ_TYPE_EP_CLEAR_OUT,
            USB_REQ_CLEAR_FEATURE,
            USB_FEATURE_ENDPOINT_HALT,
            inEp.address,
            null,
            0,
            EP0_TIMEOUT_MS
        )
        FoundationLog.log(
            "[PEMULIHAN-STALL] EP0 CLEAR_FEATURE ep=0x${hex8(inEp.address)} (BulkIN): " +
                "ret=$clearRet (${if (clearRet >= 0) "OK" else "GAGAL"})"
        )
        sleepQuietly(RUNG_B_SETTLE_MS, "jeda settle pemulihan stall")
        queryAndLogEndpointStatus(conn, inEp.address, "BulkIN", "PEMULIHAN-STALL pasca-reset")
        queryAndLogStillImageDeviceStatus(conn, interfaceId, "PEMULIHAN-STALL pasca-reset")
    }

    private fun preDrainInterruptEndpoint(conn: UsbDeviceConnection, intrEp: UsbEndpoint) {
        ensureBulkThread()
        FoundationLog.log("=== PRE-DRAIN INTERRUPT ENDPOINT 0x${hex8(intrEp.address)} (maks $PRE_DRAIN_MAX_READS bacaan) ===")
        val buf = ByteArray(maxOf(intrEp.maxPacketSize, 64))
        var drainedCount = 0
        for (i in 1..PRE_DRAIN_MAX_READS) {
            val readBytes = try {
                conn.bulkTransfer(intrEp, buf, buf.size, PRE_DRAIN_TIMEOUT_MS)
            } catch (e: Exception) {
                FoundationLog.error("PRE-DRAIN bacaan #$i exception", e)
                break
            }
            if (readBytes > 0) {
                drainedCount++
                val pkt = buf.copyOfRange(0, readBytes)
                FoundationLog.log("PRE-DRAIN bacaan #$i: paket tertunda diterima (${readBytes}B)")
                parseAndLogInterruptPacket(pkt)
            } else {
                FoundationLog.log("PRE-DRAIN selesai pada bacaan #$i (totalDrained=$drainedCount)")
                return
            }
        }
    }

    private fun runRecoveryLadderAndOpenSession(
        conn: UsbDeviceConnection,
        targetIntf: UsbInterface,
        outEp: UsbEndpoint,
        inEp: UsbEndpoint,
        intrEp: UsbEndpoint
    ): Boolean {
        ensureBulkThread()

        // =====================================================================
        // JALUR UTAMA — RUNG C (OpenSession-first):
        // CLEAR_FEATURE 3 EP + setInterface + sleep 100ms -> OpenSession DULU (tid=0, param=1)
        // =====================================================================
        FoundationLog.log("=== RECOVERY LADDER [RUNG C - JALUR UTAMA]: OpenSession DULU (tid=0, param=1) ===")
        clearAllEndpointHalts(conn, outEp, inEp, intrEp, "Rung C Utama")
        val setIntfOkC = conn.setInterface(targetIntf)
        FoundationLog.log("Rung C Utama: conn.setInterface(id=${targetIntf.id}) -> berhasil=$setIntfOkC")
        sleepQuietly(RUNG_A_SETTLE_MS, "Rung C Utama settle delay")
        startInterruptReader()

        sessionBroken.set(false)
        val openC = transactInternal(OC_OPEN_SESSION, intArrayOf(1), overrideTid = 0)
        if (openC.responseCode == RC_OK || openC.responseCode == RC_SESSION_ALREADY_OPEN) {
            ptpSessionOpen.set(true)
            sessionBroken.set(false)
            transactionIdCounter = 0
            FoundationLog.log("Rung C (Utama) SUKSES: OpenSession(tid=0, param=1) berhasil (${formatResultStatus(openC.responseCode)})")
            return true
        }
        FoundationLog.error("Rung C (Utama) GAGAL: ${formatResultStatus(openC.responseCode)}; mencoba fallback Rung A...")

        // =====================================================================
        // FALLBACK 1 — RUNG A (hanya jika Rung C gagal):
        // CLEAR_FEATURE 3 EP + setInterface + sleep 100ms -> GetDeviceInfo(tid=0) -> OpenSession(tid=0, param=1)
        // =====================================================================
        FoundationLog.log("=== RECOVERY LADDER [RUNG A - FALLBACK 1]: GetDeviceInfo(tid=0) + OpenSession(tid=0) ===")
        clearAllEndpointHalts(conn, outEp, inEp, intrEp, "Rung A Fallback")
        val setIntfOkA = conn.setInterface(targetIntf)
        FoundationLog.log("Rung A Fallback: conn.setInterface(id=${targetIntf.id}) -> berhasil=$setIntfOkA")
        sleepQuietly(RUNG_A_SETTLE_MS, "Rung A Fallback settle delay")

        sessionBroken.set(false)
        val devInfoA = transactInternal(OC_GET_DEVICE_INFO, markBrokenOnTimeout = false, overrideTid = 0)
        if (devInfoA.responseCode == RC_OK && devInfoA.data.isNotEmpty()) {
            FoundationLog.log("Rung A Fallback: GetDeviceInfo(tid=0) BERHASIL (${devInfoA.data.size}B)")
            parseAndLogDeviceInfo(devInfoA.data)
        } else {
            FoundationLog.error("Rung A Fallback: GetDeviceInfo gagal (${formatResultStatus(devInfoA.responseCode)}); tetap mencoba OpenSession...")
            clearAllEndpointHalts(conn, outEp, inEp, intrEp, "Rung A pra-OpenSession")
        }

        sessionBroken.set(false)
        val openA = transactInternal(OC_OPEN_SESSION, intArrayOf(1), overrideTid = 0)
        if (openA.responseCode == RC_OK || openA.responseCode == RC_SESSION_ALREADY_OPEN) {
            ptpSessionOpen.set(true)
            sessionBroken.set(false)
            transactionIdCounter = 0
            FoundationLog.log("Rung A Fallback SUKSES: OpenSession(tid=0, param=1) berhasil")
            return true
        }
        FoundationLog.error("Rung A Fallback GAGAL: ${formatResultStatus(openA.responseCode)}; mencoba fallback Rung B...")

        // =====================================================================
        // FALLBACK 2 — RUNG B (hanya jika Rung A juga gagal):
        // setConfiguration + re-claim + Class Device_Reset + CLEAR_FEATURE + sleep 150ms -> retry OpenSession
        // =====================================================================
        FoundationLog.log("=== RECOVERY LADDER [RUNG B - FALLBACK 2]: setConfiguration + re-claim + Device_Reset(0x66) ===")
        stopInterruptReaderInternal()
        val activeConfig = try {
            usbDevice?.getConfiguration(0)
        } catch (e: Exception) {
            FoundationLog.error("Rung B Fallback: gagal membaca konfigurasi aktif", e)
            null
        }
        val resetOk = if (activeConfig != null) conn.setConfiguration(activeConfig) else false
        FoundationLog.log("Rung B Fallback: conn.setConfiguration -> berhasil=$resetOk")
        val reclaimed = conn.claimInterface(targetIntf, true)
        usbClaimed.set(reclaimed)
        FoundationLog.log("Rung B Fallback: re-claimInterface -> berhasil=$reclaimed")
        if (!reclaimed) {
            FoundationLog.error("Rung B Fallback: gagal mengklaim ulang interface")
            return false
        }
        val classResetRet = conn.controlTransfer(
            USB_REQ_TYPE_CLASS_INTF_OUT,
            USB_CLASS_REQ_DEVICE_RESET,
            0,
            targetIntf.id,
            null,
            0,
            EP0_RESET_TIMEOUT_MS
        )
        FoundationLog.log("Rung B Fallback: Still-Image Class Device_Reset (0x66) -> ret=$classResetRet")
        clearAllEndpointHalts(conn, outEp, inEp, intrEp, "Rung B Fallback")
        sleepQuietly(RUNG_B_SETTLE_MS, "Rung B Fallback settle delay")
        startInterruptReader()

        sessionBroken.set(false)
        val devInfoB = transactInternal(OC_GET_DEVICE_INFO, markBrokenOnTimeout = false, overrideTid = 0)
        if (devInfoB.responseCode == RC_OK && devInfoB.data.isNotEmpty()) {
            FoundationLog.log("Rung B Fallback: GetDeviceInfo(tid=0) BERHASIL (${devInfoB.data.size}B)")
            parseAndLogDeviceInfo(devInfoB.data)
        }

        sessionBroken.set(false)
        val openB = transactInternal(OC_OPEN_SESSION, intArrayOf(1), overrideTid = 0)
        if (openB.responseCode == RC_OK || openB.responseCode == RC_SESSION_ALREADY_OPEN) {
            ptpSessionOpen.set(true)
            sessionBroken.set(false)
            transactionIdCounter = 0
            FoundationLog.log("Rung B Fallback SUKSES: OpenSession(tid=0, param=1) berhasil")
            return true
        }

        FoundationLog.error("RECOVERY LADDER HABIS: Jalur utama Rung C serta fallback Rung A & B gagal membuka sesi")
        return false
    }

    private fun selectStillStorage() {
        var chosenStorageId: Int? = null
        var reason: String = ""

        try {
            val storageIds = getStorageIDs()
            FoundationLog.log(
                "[SESI] GetStorageIDs mengembalikan ${storageIds.size} storage: " +
                    storageIds.joinToString(", ") { "0x${hex32(it.toInt())}" }
            )

            if (storageIds.isNotEmpty()) {
                val storageInfos = mutableMapOf<Long, StorageInfoResult>()
                for (id in storageIds) {
                    val info = getStorageInfo(id)
                    if (info != null) {
                        storageInfos[id] = info
                    }
                }

                // 1. Pilih storage yang deskripsinya mengandung 'Still' (abaikan huruf besar/kecil)
                for (id in storageIds) {
                    val info = storageInfos[id]
                    if (info != null && info.storageDescription.contains("Still", ignoreCase = true)) {
                        chosenStorageId = id.toInt()
                        reason = "deskripsi storage mengandung 'Still' ('${info.storageDescription}')"
                        break
                    }
                }

                // 2. Bila tidak ada yang cocok, pilih storage pertama yang GetObjectHandles-nya berhasil
                if (chosenStorageId == null) {
                    for (id in storageIds) {
                        val handlesRes = transactInternal(
                            OC_GET_OBJECT_HANDLES,
                            intArrayOf(id.toInt(), 0, -1),
                            markBrokenOnTimeout = false
                        )
                        if (handlesRes.responseCode == RC_OK) {
                            chosenStorageId = id.toInt()
                            reason = "storage pertama yang GetObjectHandles-nya berhasil (rc=0x${hex16(handlesRes.responseCode)})"
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            FoundationLog.error("[SESI] Exception saat enumerasi storage", e)
        }

        // 3. Simpan sebagai stillStorageId, fallback terakhir 0x10000001 bila enumerasi storage gagal total
        if (chosenStorageId != null) {
            stillStorageId = chosenStorageId
            FoundationLog.log("[SESI] stillStorageId dipilih: 0x${hex32(stillStorageId)} (alasan: $reason)")
        } else {
            stillStorageId = 0x10000001
            FoundationLog.log("[SESI] Enumerasi storage gagal total; fallback stillStorageId ditetapkan ke hardcode 0x${hex32(stillStorageId)}")
        }
    }

    private fun claimPtpInterface(device: UsbDevice): Boolean {
        var bestInterface: UsbInterface? = null
        var bestBulkIn: UsbEndpoint? = null
        var bestBulkOut: UsbEndpoint? = null
        var bestIntrIn: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var bIn: UsbEndpoint? = null
            var bOut: UsbEndpoint? = null
            var iIn: UsbEndpoint? = null

            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) bIn = ep
                    else if (ep.direction == UsbConstants.USB_DIR_OUT) bOut = ep
                } else if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == UsbConstants.USB_DIR_IN) {
                    iIn = ep
                }
            }

            if (intf.interfaceClass == USB_CLASS_PTP && bIn != null && bOut != null && iIn != null) {
                bestInterface = intf
                bestBulkIn = bIn
                bestBulkOut = bOut
                bestIntrIn = iIn
                break
            }
            if (bestInterface == null && bIn != null && bOut != null && iIn != null) {
                bestInterface = intf
                bestBulkIn = bIn
                bestBulkOut = bOut
                bestIntrIn = iIn
            }
        }

        val targetIntf = bestInterface
        if (targetIntf == null || bestBulkIn == null || bestBulkOut == null || bestIntrIn == null) {
            FoundationLog.error("Tidak ditemukan interface PTP dengan endpoint Bulk IN + Bulk OUT + Interrupt IN")
            return false
        }

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            FoundationLog.error("usbManager.openDevice() mengembalikan null (izin USB belum diberikan atau perangkat dicabut)")
            return false
        }

        if (!conn.claimInterface(targetIntf, true)) {
            conn.close()
            FoundationLog.error("claimInterface(id=${targetIntf.id}, force=true) gagal")
            return false
        }

        usbDevice = device
        usbConnection = conn
        claimedInterface = targetIntf
        epBulkIn = bestBulkIn
        epBulkOut = bestBulkOut
        epInterruptIn = bestIntrIn
        usbClaimed.set(true)
        ptpSessionOpen.set(false)

        FoundationLog.log(
            "Interface PTP diklaim: id=${targetIntf.id}, alt=${targetIntf.alternateSetting} | " +
                "BulkIN=0x${hex8(bestBulkIn.address)}, BulkOUT=0x${hex8(bestBulkOut.address)}, InterruptIN=0x${hex8(bestIntrIn.address)}"
        )
        return true
    }

    private fun startInterruptReader() {
        if (interruptThread?.isAlive == true) {
            FoundationLog.log("[PTP] Pembaca interrupt lama masih hidup — thread baru tidak dibuat")
            return
        }
        if (interruptRunning.getAndSet(true)) return
        val conn = usbConnection ?: run {
            interruptRunning.set(false)
            return
        }
        val intrEp = epInterruptIn ?: run {
            interruptRunning.set(false)
            return
        }

        val thread = Thread({
            val buf = ByteArray(maxOf(intrEp.maxPacketSize, 64))
            FoundationLog.log("Thread pembaca interrupt dimulai (ep=0x${hex8(intrEp.address)}, timeout=${INTERRUPT_TIMEOUT_MS}ms)")
            while (interruptRunning.get() && usbClaimed.get()) {
                val readBytes = try {
                    conn.bulkTransfer(intrEp, buf, buf.size, INTERRUPT_TIMEOUT_MS)
                } catch (e: Exception) {
                    if (interruptRunning.get()) {
                        FoundationLog.error("Exception pada pembacaan interrupt", e)
                    }
                    break
                }

                if (readBytes > 0) {
                    val packet = buf.copyOfRange(0, readBytes)
                    parseAndLogInterruptPacket(packet)
                }
            }
            FoundationLog.log("Thread pembaca interrupt berhenti")
        }, INTERRUPT_THREAD_NAME)

        thread.isDaemon = true
        interruptThread = thread
        thread.start()
    }

    private fun stopInterruptReaderInternal() {
        interruptRunning.set(false)
        val intr = interruptThread
        if (intr != null) {
            FoundationLog.log("Menunggu thread pembaca interrupt berhenti (join maks 2000ms)...")
            try {
                intr.join(2000L)
            } catch (e: InterruptedException) {
                FoundationLog.error("Join thread pembaca interrupt terinterupsi", e)
            }
            if (!intr.isAlive) {
                interruptThread = null
            } else {
                FoundationLog.log("[PTP] Pembaca interrupt belum mati sesudah join — referensi dipertahankan agar tidak ada pembaca ganda")
            }
        }
    }

    private fun parseAndLogInterruptPacket(packet: ByteArray) {
        if (packet.size < HEADER_SIZE) {
            FoundationLog.log("EVENT INTERRUPT MENTAH (<12B, len=${packet.size})")
            return
        }
        val bb = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val totalLen = bb.int
        val containerType = bb.short.toInt() and 0xFFFF
        val eventCode = bb.short.toInt() and 0xFFFF
        val txId = bb.int
        val paramCount = (packet.size - HEADER_SIZE) / 4
        val params = IntArray(paramCount) { bb.int }

        val paramsFormatted = if (params.isEmpty()) "[]" else params.joinToString(", ", "[", "]") { "0x${hex32(it)}" }
        FoundationLog.log(
            "EVENT INTERRUPT: len=$totalLen (read=${packet.size}), type=0x${hex16(containerType)}, " +
                "code=0x${hex16(eventCode)} (${eventName(eventCode)}), tid=$txId, params=$paramsFormatted"
        )

        // Panggil callback petunjuk jika event adalah ObjectAdded (0x4002)
        if (eventCode == EC_OBJECT_ADDED) {
            val handleHint = if (params.isNotEmpty()) params[0] else 0
            FoundationLog.log("EVENT INTERRUPT ObjectAdded petunjuk: handle=0x${hex32(handleHint)}")
            try {
                onObjectAddedHint?.invoke(handleHint)
            } catch (e: Exception) {
                FoundationLog.error("Exception pada onObjectAddedHint callback", e)
            }
        }
    }

    private fun closeSessionInternal(preserveBrokenFlag: Boolean = false) {
        ensureBulkThread()
        FoundationLog.log("=== MULAI TUTUP SESI PTP (ptpSessionOpen=${ptpSessionOpen.get()}, usbClaimed=${usbClaimed.get()}, broken=${sessionBroken.get()}) ===")

        stopInterruptReaderInternal()

        if (usbConnection != null && usbClaimed.get() && ptpSessionOpen.get() && !sessionBroken.get()) {
            val closeRes = transactInternal(OC_CLOSE_SESSION, markBrokenOnTimeout = false)
            FoundationLog.log("CloseSession (0x1003) selesai: ${formatResultStatus(closeRes.responseCode)}")
        } else {
            FoundationLog.log("CloseSession (0x1003) dilewati (sesi tidak terbuka bersih)")
        }

        val conn = usbConnection
        val intf = claimedInterface
        if (conn != null && intf != null) {
            val released = try {
                conn.releaseInterface(intf)
            } catch (e: Exception) {
                FoundationLog.error("Exception saat releaseInterface", e)
                false
            }
            FoundationLog.log("releaseInterface(id=${intf.id}): berhasil=$released")
        }
        if (conn != null) {
            try {
                conn.close()
                FoundationLog.log("UsbDeviceConnection ditutup")
            } catch (e: Exception) {
                FoundationLog.error("Exception saat UsbDeviceConnection.close()", e)
            }
        }

        usbConnection = null
        claimedInterface = null
        epBulkIn = null
        epBulkOut = null
        epInterruptIn = null
        usbDevice = null
        lastBulkOutHaltBit = -1
        usbClaimed.set(false)
        ptpSessionOpen.set(false)
        if (!preserveBrokenFlag) {
            sessionBroken.set(false)
        }
        FoundationLog.log("=== SESI PTP DITUTUP TUNTAS ===")
    }

    private fun sleepQuietly(ms: Long, reason: String) {
        FoundationLog.log("Jeda $ms ms ($reason)...")
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // =========================================================================
    // Parser & Logger Struktur Data PTP
    // =========================================================================

    private fun parseAndLogDeviceInfo(data: ByteArray): DeviceInfo? {
        return try {
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val standardVersion = bb.short.toInt() and 0xFFFF
            val vendorExtId = bb.int
            val vendorExtVersion = bb.short.toInt() and 0xFFFF
            val vendorExtDesc = readPtpString(bb)
            val functionalMode = bb.short.toInt() and 0xFFFF
            val operations = readUint16Array(bb)
            val events = readUint16Array(bb)
            val deviceProps = readUint16Array(bb)
            val captureFormats = readUint16Array(bb)
            val imageFormats = readUint16Array(bb)
            val manufacturer = readPtpString(bb)
            val model = readPtpString(bb)
            val deviceVersion = readPtpString(bb)
            val serialNumber = readPtpString(bb)

            FoundationLog.log(
                "DeviceInfo (0x1001): manufacturer='$manufacturer', model='$model', " +
                    "deviceVersion='$deviceVersion', serialNumber='$serialNumber', " +
                    "standardVersion=$standardVersion, vendorExtensionId=0x${hex32(vendorExtId)}"
            )
            FoundationLog.log("DeviceInfo Operations (${operations.size}): " + operations.joinToString(", ") { "0x${hex16(it)}" })
            FoundationLog.log("DeviceInfo Properties (${deviceProps.size}): " + deviceProps.joinToString(", ") { "0x${hex16(it)}" })

            DeviceInfo(
                manufacturer = manufacturer,
                model = model,
                deviceVersion = deviceVersion,
                serialNumber = serialNumber,
                standardVersion = standardVersion,
                vendorExtensionId = vendorExtId,
                vendorExtensionVersion = vendorExtVersion,
                vendorExtensionDesc = vendorExtDesc,
                functionalMode = functionalMode,
                operationsSupported = operations,
                eventsSupported = events,
                devicePropertiesSupported = deviceProps,
                captureFormats = captureFormats,
                imageFormats = imageFormats
            )
        } catch (e: Exception) {
            FoundationLog.error("Gagal mem-parse DeviceInfo (0x1001)", e)
            null
        }
    }

    private fun parseAndLogStorageInfo(storageId: Long, data: ByteArray): StorageInfoResult? {
        return try {
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val storageType = bb.short.toInt() and 0xFFFF
            val filesystemType = bb.short.toInt() and 0xFFFF
            val accessCapability = bb.short.toInt() and 0xFFFF
            val maxCapacity = bb.long
            val freeSpaceInBytes = bb.long
            val freeSpaceInImages = bb.int.toLong() and 0xFFFFFFFFL
            val storageDescription = readPtpString(bb)
            val volumeLabel = if (bb.hasRemaining()) readPtpString(bb) else ""

            FoundationLog.log(
                "StorageInfo (0x1005) [storageId=0x${hex32(storageId.toInt())}]: type=0x${hex16(storageType)}, " +
                    "fs=0x${hex16(filesystemType)}, access=0x${hex16(accessCapability)}, " +
                    "maxCapacity=$maxCapacity, freeSpace=$freeSpaceInBytes, freeImages=$freeSpaceInImages, " +
                    "desc='$storageDescription', label='$volumeLabel'"
            )

            StorageInfoResult(
                storageType = storageType,
                filesystemType = filesystemType,
                accessCapability = accessCapability,
                maxCapacity = maxCapacity,
                freeSpaceInBytes = freeSpaceInBytes,
                freeSpaceInImages = freeSpaceInImages,
                storageDescription = storageDescription,
                volumeLabel = volumeLabel
            )
        } catch (e: Exception) {
            FoundationLog.error("Gagal mem-parse StorageInfo untuk storage 0x${hex32(storageId.toInt())}", e)
            null
        }
    }

    private fun parseAndLogObjectInfo(handle: Int, data: ByteArray): ObjectInfoResult? {
        return try {
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val storageId = bb.int
            val objectFormat = bb.short.toInt() and 0xFFFF
            val protectionStatus = bb.short.toInt() and 0xFFFF
            val compressedSize = bb.int.toLong() and 0xFFFFFFFFL
            val thumbFormat = bb.short.toInt() and 0xFFFF
            val thumbCompressedSize = bb.int.toLong() and 0xFFFFFFFFL
            val thumbPixWidth = bb.int.toLong() and 0xFFFFFFFFL
            val thumbPixHeight = bb.int.toLong() and 0xFFFFFFFFL
            val imagePixWidth = bb.int.toLong() and 0xFFFFFFFFL
            val imagePixHeight = bb.int.toLong() and 0xFFFFFFFFL
            val imageBitDepth = bb.int.toLong() and 0xFFFFFFFFL
            val parentObject = bb.int
            val associationType = bb.short.toInt() and 0xFFFF
            val associationDesc = bb.int
            val sequenceNumber = bb.int.toLong() and 0xFFFFFFFFL
            val filename = readPtpString(bb)
            val captureDate = readPtpString(bb)
            val modificationDate = readPtpString(bb)
            val keywords = readPtpString(bb)

            FoundationLog.log(
                "ObjectInfo (0x1008) [handle=0x${hex32(handle)}]: nama='$filename', ukuran=$compressedSize byte, " +
                    "format=0x${hex16(objectFormat)}, storageId=0x${hex32(storageId)}, parent=0x${hex32(parentObject)}, " +
                    "dimensi=${imagePixWidth}x${imagePixHeight}, captureDate='$captureDate'"
            )

            ObjectInfoResult(
                handle = handle,
                filename = filename,
                sizeBytes = compressedSize,
                captureDateString = captureDate,
                formatCode = objectFormat,
                storageId = storageId,
                parentObject = parentObject,
                modificationDateString = modificationDate,
                protectionStatus = protectionStatus
            )
        } catch (e: Exception) {
            FoundationLog.error("Gagal mem-parse ObjectInfo untuk handle 0x${hex32(handle)}", e)
            null
        }
    }

    private fun parseUint32Array(data: ByteArray): IntArray {
        if (data.size < 4) return IntArray(0)
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.int.coerceAtLeast(0)
        val available = bb.remaining() / 4
        val n = count.coerceAtMost(available)
        return IntArray(n) { bb.int }
    }

    private fun readUint16Array(bb: ByteBuffer): IntArray {
        if (bb.remaining() < 4) return IntArray(0)
        val count = bb.int.coerceAtLeast(0)
        val available = bb.remaining() / 2
        val n = count.coerceAtMost(available)
        return IntArray(n) { bb.short.toInt() and 0xFFFF }
    }

    private fun readPtpString(bb: ByteBuffer): String {
        if (bb.remaining() < 1) return ""
        val numChars = bb.get().toInt() and 0xFF
        if (numChars == 0) return ""
        val chars = CharArray(numChars)
        var actualLen = 0
        for (i in 0 until numChars) {
            if (bb.remaining() < 2) break
            val c = (bb.short.toInt() and 0xFFFF).toChar()
            if (c != '\u0000') {
                chars[actualLen++] = c
            }
        }
        return String(chars, 0, actualLen)
    }

    private fun hex8(v: Int): String = String.format(Locale.US, "%02X", v and 0xFF)
    private fun hex16(v: Int): String = String.format(Locale.US, "%04X", v and 0xFFFF)
    private fun hex32(v: Int): String = String.format(Locale.US, "%08X", v)

    private fun opcodeName(opcode: Int): String = when (opcode) {
        OC_GET_DEVICE_INFO -> "GetDeviceInfo"
        OC_OPEN_SESSION -> "OpenSession"
        OC_CLOSE_SESSION -> "CloseSession"
        OC_GET_STORAGE_IDS -> "GetStorageIDs"
        OC_GET_STORAGE_INFO -> "GetStorageInfo"
        OC_GET_OBJECT_HANDLES -> "GetObjectHandles"
        OC_GET_OBJECT_INFO -> "GetObjectInfo"
        OC_GET_OBJECT -> "GetObject"
        OC_DELETE_OBJECT -> "DeleteObject"
        OC_GET_DEVICE_PROP_DESC -> "GetDevicePropDesc"
        OC_GET_DEVICE_PROP_VALUE -> "GetDevicePropValue"
        else -> "Opcode_0x${hex16(opcode)}"
    }

    private fun formatResultStatus(rc: Int): String = when (rc) {
        RC_TRANSPORT_WRITE_FAILED -> "TRANSPORT_ERROR_BULK_OUT (ret=-1)"
        RC_TRANSPORT_READ_FAILED -> "TRANSPORT_ERROR_BULK_IN (timeout/error)"
        RC_TRANSPORT_NOT_READY -> "TRANSPORT_NOT_READY (koneksi belum siap)"
        RC_TRANSPORT_BAD_CONTAINER -> "TRANSPORT_BAD_CONTAINER (kontainer tidak valid)"
        else -> if (rc < 0) "TRANSPORT_ERROR ($rc)" else "rc=0x${hex16(rc)} (${rcName(rc)})"
    }

    private fun rcName(rc: Int): String = when (rc) {
        RC_TRANSPORT_WRITE_FAILED -> "TRANSPORT_ERROR_BULK_OUT"
        RC_TRANSPORT_READ_FAILED -> "TRANSPORT_ERROR_BULK_IN"
        RC_TRANSPORT_NOT_READY -> "TRANSPORT_NOT_READY"
        RC_TRANSPORT_BAD_CONTAINER -> "TRANSPORT_BAD_CONTAINER"
        RC_OK -> "OK"
        RC_GENERAL_ERROR -> "GeneralError"
        RC_SESSION_NOT_OPEN -> "SessionNotOpen"
        RC_OPERATION_NOT_SUPPORTED -> "OperationNotSupported"
        RC_PARAMETER_NOT_SUPPORTED -> "ParameterNotSupported"
        RC_DEVICE_BUSY -> "DeviceBusy"
        RC_SESSION_ALREADY_OPEN -> "SessionAlreadyOpen"
        else -> if (rc < 0) "TRANSPORT_ERROR_$rc" else "RC_0x${hex16(rc)}"
    }

    private fun eventName(ec: Int): String = when (ec) {
        EC_CANCEL_TRANSACTION -> "CancelTransaction"
        EC_OBJECT_ADDED -> "ObjectAdded"
        EC_OBJECT_REMOVED -> "ObjectRemoved"
        EC_STORE_ADDED -> "StoreAdded"
        EC_STORE_REMOVED -> "StoreRemoved"
        EC_DEVICE_PROP_CHANGED -> "DevicePropChanged"
        EC_OBJECT_INFO_CHANGED -> "ObjectInfoChanged"
        EC_DEVICE_INFO_CHANGED -> "DeviceInfoChanged"
        EC_REQUEST_OBJECT_TRANSFER -> "RequestObjectTransfer"
        EC_STORE_FULL -> "StoreFull"
        EC_DEVICE_RESET -> "DeviceReset"
        EC_STORAGE_INFO_CHANGED -> "StorageInfoChanged"
        EC_CAPTURE_COMPLETE -> "CaptureComplete"
        else -> "Event_0x${hex16(ec)}"
    }
}
