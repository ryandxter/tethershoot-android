package com.rvmeur.fcmprobe

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.google.firebase.FirebaseApp
import com.google.firebase.dynamicloading.ComponentLoader
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * MainActivity untuk FCM Probe.
 *
 * Menjalankan urutan probe diagnostik 7 langkah untuk membuktikan apakah ketiadaan
 * komponen FirebaseMessaging disebabkan oleh pendaftaran lazy registrar yang belum
 * dipicu via ComponentLoader.discoverComponents().
 */
class MainActivity : Activity() {

    private var logFile: File? = null
    private var statusTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "fcm probe berjalan — lihat probe-log.txt"
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
        statusTextView = textView
        setContentView(textView)

        initLogFile()
        runProbeSequence()
    }

    private fun initLogFile() {
        try {
            val baseDir = getExternalFilesDir(null) ?: filesDir
            val file = File(baseDir, "probe-log.txt")
            // Tulis ulang di awal (overwrite / truncate)
            FileOutputStream(file, false).use { fos ->
                fos.write("".toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            logFile = file
            Log.i("PROBE", "[PROBE] Berkas probe-log.txt disiapkan di: ${file.absolutePath}")
        } catch (t: Throwable) {
            Log.e("PROBE", "[PROBE] Gagal inisialisasi probe-log.txt: ${t.message}", t)
        }
    }

    private fun log(message: String) {
        val line = if (message.startsWith("[PROBE]")) message else "[PROBE] $message"
        Log.i("PROBE", line)
        try {
            logFile?.let { file ->
                FileOutputStream(file, true).use { fos ->
                    fos.write((line + "\n").toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
            }
        } catch (t: Throwable) {
            Log.e("PROBE", "[PROBE] Gagal menulis ke probe-log.txt: ${t.message}", t)
        }
    }

    private fun runProbeSequence() {
        log("MEMULAI URUTAN PROBE DIAGNOSTIK FCM (7 LANGKAH)")

        // 1. FirebaseApp.getInstance() -> catat nama aplikasi + options.projectId
        try {
            val app = FirebaseApp.getInstance()
            val projId = app.options.projectId
            log("LANGKAH 1 - FirebaseApp.getInstance(): nama=${app.name}, projectId=$projId")
        } catch (t: Throwable) {
            log("LANGKAH 1 - FirebaseApp.getInstance(): [EXCEPTION] ${t.javaClass.name}: ${t.message}")
        }

        // 2. DUMP CONTAINER #1: refleksi - ambil field componentRuntime, lalu components (Map)
        // dan untuk tiap key Component catat semua tipe dari getProvidedInterfaces() serta jumlah total
        try {
            dumpContainer("LANGKAH 2 - DUMP CONTAINER #1")
        } catch (t: Throwable) {
            log("LANGKAH 2 - DUMP CONTAINER #1: [EXCEPTION] ${t.javaClass.name}: ${t.message}")
        }

        // 3. FirebaseMessaging.getInstance() -> catat "MESSAGING: OK" atau kelas exception + pesannya
        try {
            FirebaseMessaging.getInstance()
            log("LANGKAH 3 - MESSAGING: OK")
        } catch (t: Throwable) {
            log("LANGKAH 3 - MESSAGING: ${t.javaClass.name}: ${t.message}")
        }

        // 4. FirebaseInstallations.getInstance() -> catat "INSTALLATIONS: OK" atau exception-nya
        try {
            FirebaseInstallations.getInstance()
            log("LANGKAH 4 - INSTALLATIONS: OK")
        } catch (t: Throwable) {
            log("LANGKAH 4 - INSTALLATIONS: ${t.javaClass.name}: ${t.message}")
        }

        // 5. PEMICU: FirebaseApp.getInstance().get(ComponentLoader::class.java) -> catat hadir/tidak;
        // bila hadir, panggil discoverComponents() lalu catat "DISCOVER: terpanggil"
        try {
            val loader = FirebaseApp.getInstance().get(ComponentLoader::class.java)
            if (loader != null) {
                log("LANGKAH 5 - PEMICU: ComponentLoader hadir")
                loader.discoverComponents()
                log("LANGKAH 5 - DISCOVER: terpanggil")
            } else {
                log("LANGKAH 5 - PEMICU: ComponentLoader tidak hadir")
            }
        } catch (t: Throwable) {
            log("LANGKAH 5 - PEMICU: [EXCEPTION] ${t.javaClass.name}: ${t.message}")
        }

        // 6. Ulangi langkah 3 (catat sebagai "MESSAGING SESUDAH DISCOVER") dan langkah 2 sebagai DUMP CONTAINER #2
        // (jumlah + apakah com.google.firebase.messaging.FirebaseMessaging kini ada di daftar)
        try {
            try {
                FirebaseMessaging.getInstance()
                log("LANGKAH 6 - MESSAGING SESUDAH DISCOVER: OK")
            } catch (t: Throwable) {
                log("LANGKAH 6 - MESSAGING SESUDAH DISCOVER: ${t.javaClass.name}: ${t.message}")
            }

            val typesAfter = dumpContainer("LANGKAH 6 - DUMP CONTAINER #2")
            val hasMessaging = typesAfter.contains("com.google.firebase.messaging.FirebaseMessaging") ||
                    typesAfter.any { it.contains("FirebaseMessaging") }
            log("LANGKAH 6 - DUMP CONTAINER #2: jumlah=${typesAfter.size}, com.google.firebase.messaging.FirebaseMessaging ada di daftar: $hasMessaging")
        } catch (t: Throwable) {
            log("LANGKAH 6: [EXCEPTION] ${t.javaClass.name}: ${t.message}")
        }

        // 7. Catat "PROBE SELESAI"
        try {
            log("LANGKAH 7 - PROBE SELESAI")
            statusTextView?.append("\n\nPROBE SELESAI.\nBerkas: ${logFile?.absolutePath}")
        } catch (t: Throwable) {
            log("LANGKAH 7 - PROBE SELESAI: [EXCEPTION] ${t.javaClass.name}: ${t.message}")
        }
    }

    private fun dumpContainer(title: String): Set<String> {
        val allProvidedTypes = mutableSetOf<String>()
        log("$title: mulai dump container refleksi")
        val app = FirebaseApp.getInstance()
        val runtime = findComponentRuntime(app)
        val componentsMap = findComponentsMap(runtime)

        log("$title: jumlah total komponen = ${componentsMap.size}")
        var index = 0
        for ((key, _) in componentsMap) {
            index++
            if (key == null) {
                log("$title: [$index] component=<null>")
                continue
            }
            val types = getProvidedInterfacesFromComponent(key)
            allProvidedTypes.addAll(types)
            log("$title: [$index] ${key.javaClass.simpleName} -> provided=[${types.joinToString(", ")}]")
        }
        return allProvidedTypes
    }

    private fun findComponentRuntime(app: FirebaseApp): Any {
        try {
            val field = getField(app.javaClass, "componentRuntime")
            val runtime = field.get(app)
            if (runtime != null) return runtime
        } catch (_: Throwable) {}

        // Fallback: telusuri field dengan tipe ComponentRuntime / ComponentContainer
        var cls: Class<*>? = app.javaClass
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (f.type.name.contains("ComponentRuntime") || f.type.name.contains("ComponentContainer")) {
                    f.isAccessible = true
                    val obj = f.get(app)
                    if (obj != null) return obj
                }
            }
            cls = cls.superclass
        }
        throw IllegalStateException("Field componentRuntime tidak ditemukan pada FirebaseApp")
    }

    private fun findComponentsMap(runtime: Any): Map<*, *> {
        try {
            val field = getField(runtime.javaClass, "components")
            val map = field.get(runtime) as? Map<*, *>
            if (map != null) return map
        } catch (_: Throwable) {}

        // Fallback: telusuri field bertipe Map
        var cls: Class<*>? = runtime.javaClass
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (Map::class.java.isAssignableFrom(f.type)) {
                    f.isAccessible = true
                    val map = f.get(runtime) as? Map<*, *>
                    if (map != null) return map
                }
            }
            cls = cls.superclass
        }
        throw IllegalStateException("Field components (Map) tidak ditemukan pada runtime ${runtime.javaClass.name}")
    }

    private fun getField(targetClass: Class<*>, fieldName: String): Field {
        var cls: Class<*>? = targetClass
        while (cls != null && cls != Any::class.java) {
            try {
                val field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException("Field $fieldName tidak ditemukan pada ${targetClass.name}")
    }

    private fun getProvidedInterfacesFromComponent(component: Any): List<String> {
        val results = mutableListOf<String>()
        var method: Method? = null
        var cls: Class<*>? = component.javaClass
        while (cls != null && cls != Any::class.java) {
            try {
                method = cls.getDeclaredMethod("getProvidedInterfaces")
                method.isAccessible = true
                break
            } catch (_: NoSuchMethodException) {
                cls = cls.superclass
            }
        }

        if (method == null) {
            for (m in component.javaClass.methods) {
                if (m.name.equals("getProvidedInterfaces", ignoreCase = true) && m.parameterCount == 0) {
                    m.isAccessible = true
                    method = m
                    break
                }
            }
        }

        if (method != null) {
            val set = method.invoke(component) as? Iterable<*>
            if (set != null) {
                for (item in set) {
                    if (item != null) {
                        results.add(extractInterfaceName(item))
                    }
                }
            }
        }
        return results
    }

    private fun extractInterfaceName(item: Any?): String {
        if (item == null) return "<null>"
        if (item is Class<*>) return item.name
        try {
            val getTypeMethod = item.javaClass.getMethod("getType")
            val type = getTypeMethod.invoke(item)
            if (type is Class<*>) return type.name
            if (type != null) return type.toString()
        } catch (_: Throwable) {}
        return item.toString()
    }
}
