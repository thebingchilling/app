/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.fsck.k9.backend.imap.ImapPushConfigProvider
import com.fsck.k9.backend.imap.SystemAlarmManager
import com.fsck.k9.mail.power.PowerManager
import com.fsck.k9.mail.power.WakeLock
import com.fsck.k9.mail.ssl.LocalKeyStore
import com.fsck.k9.mail.ssl.TrustManagerFactory
import com.fsck.k9.mail.ssl.TrustedSocketFactory
import com.fsck.k9.mail.store.imap.ImapClientInfo
import com.fsck.k9.mail.store.imap.ImapStoreConfig
import java.io.File
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import rs.ltt.android.BuildConfig

/** TLS sockets that verify the certificate chain and the host name (SNI enabled). */
class AndroidTrustedSocketFactory(context: Context) : TrustedSocketFactory {

    private val trustManagerFactory: TrustManagerFactory by lazy {
        val directory = File(context.filesDir, "keystore")
        TrustManagerFactory.createInstance(LocalKeyStore { directory.also { it.mkdirs() } })
    }

    override fun createSocket(
        socket: Socket?,
        host: String,
        port: Int,
        clientCertificateAlias: String?,
    ): Socket {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManagerFactory.getTrustManagerForDomain(host, port)), null)
        val factory = sslContext.socketFactory
        val sslSocket =
            if (socket == null) {
                factory.createSocket() as SSLSocket
            } else {
                factory.createSocket(socket, host, port, true) as SSLSocket
            }
        val parameters = sslSocket.sslParameters
        if (isValidSniHost(host)) {
            parameters.serverNames = listOf(SNIHostName(host))
        }
        sslSocket.sslParameters = parameters
        return sslSocket
    }

    private fun isValidSniHost(host: String): Boolean {
        return host.isNotEmpty() && !host.matches(Regex("^[0-9.]+$")) && !host.contains(':')
    }
}

class AndroidPowerManager(context: Context) : PowerManager {
    private val powerManager = context.getSystemService(android.os.PowerManager::class.java)

    override fun newWakeLock(tag: String): WakeLock {
        val wakeLock =
            powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Ripple:$tag")
        return object : WakeLock {
            override fun acquire(timeout: Long) = wakeLock.acquire(timeout)

            @SuppressLint("WakelockTimeout")
            override fun acquire() = wakeLock.acquire()

            override fun setReferenceCounted(counted: Boolean) = wakeLock.setReferenceCounted(counted)

            override fun release() {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }
}

class RippleImapStoreConfig(override val logLabel: String) : ImapStoreConfig {
    override fun isSubscribedFoldersOnly(): Boolean = false

    override fun isExpungeImmediately(): Boolean = true

    override fun clientInfo(): ImapClientInfo =
        ImapClientInfo(appName = "Ripple", appVersion = BuildConfig.VERSION_NAME)
}

class RipplePushConfigProvider : ImapPushConfigProvider {
    /** Inbox plus a few more folders is what most people watch. */
    override val maxPushFoldersFlow: Flow<Int> = flowOf(MAX_PUSH_FOLDERS)

    /** RFC 2177 asks clients to re-issue IDLE at least every 29 minutes. */
    override val idleRefreshMinutesFlow: Flow<Int> = flowOf(IDLE_REFRESH_MINUTES)

    companion object {
        const val MAX_PUSH_FOLDERS = 10
        const val IDLE_REFRESH_MINUTES = 24
    }
}

/**
 * Exact-ish alarms that also fire in Doze so the IDLE connections get refreshed. Each instance
 * owns one alarm.
 */
class AndroidSystemAlarmManager(private val context: Context) : SystemAlarmManager {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val action = "${context.packageName}.IDLE_REFRESH.${NEXT_ID.incrementAndGet()}"
    private var callback: (() -> Unit)? = null
    private var receiverRegistered = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val callback = synchronized(this@AndroidSystemAlarmManager) { this@AndroidSystemAlarmManager.callback }
                callback?.invoke()
            }
        }

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(action).setPackage(context.packageName)
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    @Synchronized
    override fun setAlarm(triggerTime: Long, callback: () -> Unit) {
        this.callback = callback
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
        }
    }

    @Synchronized
    override fun cancelAlarm() {
        callback = null
        alarmManager.cancel(pendingIntent)
    }

    override fun now(): Long = SystemClock.elapsedRealtime()

    companion object {
        private val NEXT_ID = AtomicInteger()
    }
}
