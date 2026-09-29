/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.push

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import rs.ltt.android.database.AppDatabase
import rs.ltt.android.ui.notification.EmailNotification
import rs.ltt.android.worker.MainMailboxQueryRefreshWorker

/**
 * Decides whether the instant push service runs and keeps a periodic refresh as fallback for
 * servers without IDLE, POP3 accounts, or when the service gets killed.
 */
object PushController {

    private val LOGGER = LoggerFactory.getLogger(PushController::class.java)
    private val EXECUTOR = Executors.newSingleThreadExecutor()

    const val PREFERENCE_INSTANT_DELIVERY = "instant_delivery"

    @JvmStatic
    fun isInstantDeliveryEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE_INSTANT_DELIVERY, true)

    /** Call when accounts were added or removed, or the setting changed. */
    @JvmStatic
    fun onAccountsChanged(context: Context) {
        val appContext = context.applicationContext
        EXECUTOR.execute {
            val accounts = AppDatabase.getInstance(appContext).accountDao().accountsSync
            for (account in accounts) {
                EmailNotification.createChannel(appContext, account)
            }
            schedulePeriodicRefresh(appContext, accounts.map { it.id })
            val wantsPush = isInstantDeliveryEnabled(appContext) && accounts.any { !it.credentials.isPop3 }
            if (wantsPush) {
                start(appContext)
            } else {
                appContext.stopService(Intent(appContext, PushService::class.java))
            }
        }
    }

    @JvmStatic
    fun start(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PushService::class.java))
        } catch (e: Exception) {
            // Android 12+ refuses to start foreground services from the background; the periodic
            // refresh keeps mail flowing until the app is opened or the phone restarts.
            LOGGER.warn("Unable to start push service", e)
        }
    }

    private fun schedulePeriodicRefresh(context: Context, accountIds: List<Long>) {
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        for (accountId in accountIds) {
            val request =
                PeriodicWorkRequest.Builder(MainMailboxQueryRefreshWorker::class.java, 15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setInputData(MainMailboxQueryRefreshWorker.data(accountId, true))
                    .build()
            workManager.enqueueUniquePeriodicWork(
                MainMailboxQueryRefreshWorker.uniquePeriodicName(accountId),
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    @JvmStatic
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    @JvmStatic
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}
