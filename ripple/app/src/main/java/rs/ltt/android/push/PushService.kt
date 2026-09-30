/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fsck.k9.backend.api.BackendPusher
import com.fsck.k9.backend.api.BackendPusherCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.slf4j.LoggerFactory
import rs.ltt.android.MuaPool
import rs.ltt.android.R
import rs.ltt.android.database.AppDatabase
import rs.ltt.android.engine.Mua
import rs.ltt.android.engine.SignInProblems
import rs.ltt.android.entity.AccountWithCredentials
import rs.ltt.android.ui.activity.MainActivity

/**
 * Keeps one IMAP IDLE connection per account (Thunderbird's pusher) so the server can tell us
 * about new mail the moment it arrives. On a push event the folder is synced and new mail is
 * shown as a notification.
 */
class PushService : Service() {

    private val pushers = ConcurrentHashMap<Long, AccountPusher>()
    private val executor = Executors.newSingleThreadExecutor()
    private val syncExecutor = Executors.newFixedThreadPool(2)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(getString(R.string.push_notification_connecting))
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(getString(R.string.push_notification_connecting))
        val restart = intent?.getLongExtra(EXTRA_RESTART_ACCOUNT, -1L) ?: -1L
        executor.execute {
            if (restart >= 0) {
                // The login changed: reconnect with the new one.
                pushers.remove(restart)?.stop()
            }
            updatePushers()
        }
        return START_STICKY
    }

    private fun updatePushers() {
        val accounts =
            AppDatabase.getInstance(this).accountDao().accountsSync.filter { !it.credentials.isPop3 }
        if (accounts.isEmpty() || !PushController.isInstantDeliveryEnabled(this)) {
            stopSelf()
            return
        }
        val ids = accounts.map { it.id }.toSet()
        for ((id, pusher) in pushers) {
            if (id !in ids) {
                pusher.stop()
                pushers.remove(id)
            }
        }
        for (account in accounts) {
            val existing = pushers[account.id]
            if (existing != null && existing.account == account) {
                continue
            }
            existing?.stop()
            try {
                val pusher = AccountPusher(account)
                pushers[account.id] = pusher
                pusher.start()
            } catch (e: Exception) {
                LOGGER.warn("Unable to start push for account {}", account.id, e)
            }
        }
        startInForeground(summary(accounts))
    }

    private fun summary(accounts: List<AccountWithCredentials>): String =
        if (accounts.size == 1) {
            getString(R.string.push_notification_text_one_account, accounts[0].name)
        } else {
            getString(R.string.push_notification_text_x_accounts, accounts.size)
        }

    private inner class AccountPusher(val account: AccountWithCredentials) : BackendPusherCallback {
        private val mua: Mua = MuaPool.getInstance(this@PushService, account)
        private var pusher: BackendPusher? = null

        fun start() {
            // catch up on what arrived while we were not connected
            sync(null)
            if (!mua.isPushCapable) {
                return
            }
            val pusher = mua.createPusher(this)
            this.pusher = pusher
            pusher.start()
            pusher.updateFolders(mua.foldersToPush())
        }

        fun stop() {
            pusher?.stop()
            pusher = null
        }

        fun reconnect() {
            pusher?.reconnect()
        }

        private fun sync(folderServerId: String?) {
            syncExecutor.execute {
                try {
                    if (folderServerId == null) {
                        mua.refreshFolderListBlocking()
                        pusher?.updateFolders(mua.foldersToPush())
                    }
                    mua.syncBlocking(folderServerId ?: mua.foldersToPush().first(), true)
                } catch (e: Exception) {
                    LOGGER.warn("Sync after push event failed for {}", account.id, e)
                    SignInProblems.reportIfAuthFailure(this@PushService, account.id, e)
                }
            }
        }

        override fun onPushEvent(folderServerId: String) {
            LOGGER.info("Push event for account {} folder {}", account.id, folderServerId)
            sync(folderServerId)
        }

        override fun onPushError(exception: Exception) {
            LOGGER.warn("Push error for account {}", account.id, exception)
            SignInProblems.reportIfAuthFailure(this@PushService, account.id, exception)
        }

        override suspend fun onPushNotSupported() {
            LOGGER.info("Server of account {} does not support IDLE; relying on periodic refresh", account.id)
            stop()
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    LOGGER.info("Network available; reconnecting push")
                    for (pusher in pushers.values) {
                        pusher.reconnect()
                    }
                }
            }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            LOGGER.warn("Unable to watch network changes", e)
        }
    }

    override fun onDestroy() {
        networkCallback?.let {
            try {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
            } catch (e: Exception) {
                LOGGER.debug("Unable to unregister network callback", e)
            }
        }
        for (pusher in pushers.values) {
            pusher.stop()
        }
        pushers.clear()
        executor.shutdown()
        syncExecutor.shutdown()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, getString(R.string.push_channel_name), NotificationManager.IMPORTANCE_MIN)
        channel.description = getString(R.string.push_channel_description)
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startInForeground(text: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.push_notification_title))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification_push)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build()
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(PushService::class.java)
        private const val CHANNEL_ID = "push"
        private const val NOTIFICATION_ID = 0x7075
        const val EXTRA_RESTART_ACCOUNT = "restart_account"
    }
}
