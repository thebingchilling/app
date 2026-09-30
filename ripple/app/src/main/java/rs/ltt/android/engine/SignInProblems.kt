/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fsck.k9.mail.AuthenticationFailedException
import org.slf4j.LoggerFactory
import rs.ltt.android.R
import rs.ltt.android.database.AppDatabase
import rs.ltt.android.ui.activity.SetupActivity

/**
 * Tells the user when a server stops accepting an account's saved login (a changed password, a
 * revoked or expired Google/Microsoft sign-in). Without it sync and instant delivery just stop.
 * The notification opens [SetupActivity] to sign in again.
 */
object SignInProblems {

    private val LOGGER = LoggerFactory.getLogger(SignInProblems::class.java)
    private const val CHANNEL_ID = "sign_in"
    private const val NOTIFICATION_ID_BASE = 0x5100

    @JvmStatic
    fun isAuthFailure(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            if (current is AuthenticationFailedException) return true
            current = current.cause.takeIf { it !== current }
        }
        return false
    }

    /** Posts the notification when [throwable] is a rejected login. Returns whether it was. */
    @JvmStatic
    fun reportIfAuthFailure(context: Context, accountId: Long, throwable: Throwable?): Boolean {
        if (!isAuthFailure(throwable)) return false
        try {
            show(context.applicationContext, accountId)
        } catch (e: Exception) {
            LOGGER.warn("Unable to show sign-in notification", e)
        }
        return true
    }

    @JvmStatic
    fun clear(context: Context, accountId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(accountId))
    }

    @JvmStatic
    fun reauthIntent(context: Context, accountId: Long): Intent =
        Intent(context, SetupActivity::class.java)
            .putExtra(SetupActivity.EXTRA_REAUTH_ACCOUNT, accountId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun show(context: Context, accountId: Long) {
        val account = AppDatabase.getInstance(context).accountDao().getAccountName(accountId) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sign_in_problems_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId(accountId),
                reauthIntent(context, accountId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_push)
                .setContentTitle(context.getString(R.string.sign_in_again_title, account.name))
                .setContentText(context.getString(R.string.sign_in_again_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.sign_in_again_text)))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()
        manager.notify(notificationId(accountId), notification)
    }

    private fun notificationId(accountId: Long) = NOTIFICATION_ID_BASE + accountId.toInt()
}
