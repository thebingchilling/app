/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets

/**
 * Ids used in the local database. A mailbox id is the folder's server id. An email id combines a
 * short hash of the folder with the message's server id (IMAP UID or POP3 UIDL).
 */
object MailIds {

    @JvmStatic
    fun folderKey(mailboxId: String): String = hash(mailboxId).substring(0, 10)

    @JvmStatic
    fun emailId(mailboxId: String, messageServerId: String): String =
        "${folderKey(mailboxId)}-$messageServerId"

    @JvmStatic
    fun messageServerId(emailId: String): String = emailId.substringAfter('-')

    @JvmStatic
    fun belongsTo(emailId: String, mailboxId: String): Boolean =
        emailId.startsWith(folderKey(mailboxId) + "-")

    @JvmStatic
    fun blobId(emailId: String, partId: String): String =
        "b" + hash("$emailId|$partId").substring(0, 32)

    @JvmStatic
    fun threadId(seed: String): String = "t" + hash(seed).substring(0, 24)

    private fun hash(value: String): String =
        Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString()
}
