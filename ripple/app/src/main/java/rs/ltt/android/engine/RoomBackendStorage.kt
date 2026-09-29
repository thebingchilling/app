/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import android.content.Context
import com.fsck.k9.backend.api.BackendFolder
import com.fsck.k9.backend.api.BackendFolderUpdater
import com.fsck.k9.backend.api.BackendStorage
import com.fsck.k9.backend.api.FolderInfo
import net.thunderbird.core.common.mail.Flag
import com.fsck.k9.mail.FolderType
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.MessageDownloadState
import java.util.Date
import org.slf4j.LoggerFactory
import rs.ltt.android.cache.BlobStorage
import rs.ltt.android.database.LttrsDatabase
import rs.ltt.android.entity.FolderStateEntity
import rs.ltt.android.entity.MailboxEntity
import rs.ltt.android.mail.model.Role

/** Receives notice of local changes so query results and notifications can be updated. */
interface StorageListener {
    fun onFolderChanged(mailboxId: String)

    fun onFoldersChanged()
}

/**
 * Lets Thunderbird's sync code store folders and messages in Ltt.rs' per account database.
 * Folder server ids are used as mailbox ids.
 */
class RoomBackendStorage(
    private val context: Context,
    private val accountId: Long,
    private val database: LttrsDatabase,
    private val cacheAttachments: Boolean,
    private val listener: StorageListener,
) : BackendStorage {

    private val dao = database.engineDao()

    override fun getFolder(folderServerId: String): BackendFolder = RoomBackendFolder(folderServerId)

    override fun getFolderServerIds(): List<String> = dao.getMailboxIds()

    override fun createFolderUpdater(): BackendFolderUpdater = FolderUpdater()

    override fun getExtraString(name: String): String? = dao.getExtraString(ACCOUNT_SCOPE, name)

    override fun setExtraString(name: String, value: String) = dao.setExtraString(ACCOUNT_SCOPE, name, value)

    override fun getExtraNumber(name: String): Long? = dao.getExtraNumber(ACCOUNT_SCOPE, name)

    override fun setExtraNumber(name: String, value: Long) = dao.setExtraNumber(ACCOUNT_SCOPE, name, value)

    fun folderState(mailboxId: String): FolderStateEntity =
        dao.getFolderState(mailboxId)
            ?: FolderStateEntity(mailboxId, DEFAULT_VISIBLE_LIMIT, BackendFolder.MoreMessages.UNKNOWN.name).also {
                dao.insert(it)
            }

    fun increaseVisibleLimit(mailboxId: String): Int {
        val state = folderState(mailboxId)
        state.visibleLimit += PAGE_SIZE
        dao.insert(state)
        return state.visibleLimit
    }

    private inner class FolderUpdater : BackendFolderUpdater {
        private var changed = false

        override fun createFolders(folders: List<FolderInfo>): Set<Long> {
            for (folder in folders) {
                val entity = MailboxEntity()
                entity.id = folder.serverId
                entity.name = displayName(folder.serverId, folder.name)
                entity.role = roleOf(folder.serverId, folder.type)
                entity.sortOrder = sortOrder(entity.role)
                entity.totalEmails = 0
                entity.unreadEmails = 0
                entity.totalThreads = 0
                entity.unreadThreads = 0
                entity.isSubscribed = true
                dao.insert(entity)
                changed = true
            }
            return folders.map { it.serverId.hashCode().toLong() }.toSet()
        }

        override fun deleteFolders(folderServerIds: List<String>) {
            for (id in folderServerIds) {
                dao.deleteMailbox(id)
                changed = true
            }
        }

        override fun changeFolder(folderServerId: String, name: String, type: FolderType) {
            val role = roleOf(folderServerId, type)
            val existing = dao.getMailbox(folderServerId)
            val displayName = displayName(folderServerId, name)
            if (existing == null || existing.name != displayName || existing.role != role) {
                dao.updateMailbox(folderServerId, displayName, role)
                changed = true
            }
        }

        override fun close() {
            if (changed) {
                listener.onFoldersChanged()
            }
        }
    }

    inner class RoomBackendFolder(private val mailboxId: String) : BackendFolder {

        private fun emailId(messageServerId: String) = MailIds.emailId(mailboxId, messageServerId)

        override val name: String
            get() = dao.getMailbox(mailboxId)?.name ?: mailboxId

        override val visibleLimit: Int
            get() = folderState(mailboxId).visibleLimit

        override fun getMessageServerIds(): Set<String> =
            dao.getEmailIds(mailboxId).map { MailIds.messageServerId(it) }.toSet()

        override fun getAllMessagesAndEffectiveDates(): Map<String, Long?> =
            dao.getEmailIdsAndTimes(mailboxId).associate {
                MailIds.messageServerId(it.id) to it.receivedAt?.toEpochMilli()
            }

        override fun destroyMessages(messageServerIds: List<String>) {
            if (messageServerIds.isEmpty()) return
            dao.deleteEmails(messageServerIds.map { emailId(it) })
            listener.onFolderChanged(mailboxId)
        }

        override fun clearAllMessages() {
            dao.clearMailbox(mailboxId)
            listener.onFolderChanged(mailboxId)
        }

        override fun getMoreMessages(): BackendFolder.MoreMessages =
            runCatching { BackendFolder.MoreMessages.valueOf(folderState(mailboxId).moreMessages) }
                .getOrDefault(BackendFolder.MoreMessages.UNKNOWN)

        override fun setMoreMessages(moreMessages: BackendFolder.MoreMessages) {
            val state = folderState(mailboxId)
            state.moreMessages = moreMessages.name
            dao.insert(state)
        }

        override fun setLastChecked(timestamp: Long) {
            val state = folderState(mailboxId)
            state.lastChecked = timestamp
            dao.insert(state)
        }

        override fun setStatus(status: String?) {
            val state = folderState(mailboxId)
            state.status = status
            dao.insert(state)
        }

        override fun isMessagePresent(messageServerId: String): Boolean = dao.emailExists(emailId(messageServerId))

        override fun getMessageFlags(messageServerId: String): Set<Flag> =
            dao.getKeywords(emailId(messageServerId)).mapNotNull { MessageMapper.flagFor(it) }.toSet()

        override fun setMessageFlag(messageServerId: String, flag: Flag, value: Boolean) {
            val keyword = MessageMapper.keywordFor(flag) ?: return
            dao.setKeyword(emailId(messageServerId), keyword, value)
            listener.onFolderChanged(mailboxId)
        }

        override suspend fun saveMessage(message: Message, downloadState: MessageDownloadState) {
            val mapped = MessageMapper.map(mailboxId, message)
            val values =
                if (downloadState != MessageDownloadState.FULL) {
                    mapped.bodyValues.onEach { it.isTruncated = true }
                } else {
                    mapped.bodyValues
                }
            dao.saveEmail(mapped.email, mapped.bodyParts, values)
            if (cacheAttachments && downloadState == MessageDownloadState.FULL) {
                cache(mapped)
            }
            listener.onFolderChanged(mailboxId)
        }

        private fun cache(mapped: MappedEmail) {
            for ((blobId, part) in mapped.attachmentBodies) {
                try {
                    val storage = BlobStorage.get(context, accountId, blobId)
                    if (!storage.file.exists()) {
                        MessageMapper.writePart(part, storage.temporaryFile)
                        storage.moveTemporaryToFile()
                    }
                } catch (e: Exception) {
                    LOGGER.warn("Unable to cache attachment {}", blobId, e)
                }
            }
        }

        override fun getOldestMessageDate(): Date? = dao.getOldestReceivedAt(mailboxId)?.let { Date.from(it) }

        override fun getFolderExtraString(name: String): String? = dao.getExtraString(mailboxId, name)

        override fun setFolderExtraString(name: String, value: String?) = dao.setExtraString(mailboxId, name, value)

        override fun getFolderExtraNumber(name: String): Long? = dao.getExtraNumber(mailboxId, name)

        override fun setFolderExtraNumber(name: String, value: Long) = dao.setExtraNumber(mailboxId, name, value)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RoomBackendStorage::class.java)
        private const val ACCOUNT_SCOPE = ""
        const val DEFAULT_VISIBLE_LIMIT = 50
        const val PAGE_SIZE = 50

        private val GMAIL_PREFIXES = listOf("[Gmail]/", "[Google Mail]/")

        fun displayName(serverId: String, name: String): String {
            if (serverId.equals("INBOX", ignoreCase = true)) return "Inbox"
            for (prefix in GMAIL_PREFIXES) {
                if (name.startsWith(prefix)) return name.removePrefix(prefix)
            }
            return name
        }

        fun roleOf(serverId: String, type: FolderType): Role? {
            val stripped = GMAIL_PREFIXES.fold(serverId) { id, prefix -> id.removePrefix(prefix) }
            val isGmail = GMAIL_PREFIXES.any { serverId.startsWith(it) }
            return when (type) {
                FolderType.INBOX -> Role.INBOX
                FolderType.DRAFTS -> Role.DRAFTS
                FolderType.SENT -> Role.SENT
                FolderType.TRASH -> Role.TRASH
                FolderType.SPAM -> Role.JUNK
                FolderType.ARCHIVE -> Role.ARCHIVE
                FolderType.OUTBOX -> null
                FolderType.REGULAR ->
                    when {
                        isGmail && stripped == "Important" -> Role.IMPORTANT
                        isGmail && stripped == "Starred" -> Role.FLAGGED
                        else -> null
                    }
            }
        }

        private fun sortOrder(role: Role?): Long =
            when (role) {
                Role.INBOX -> 0
                Role.IMPORTANT -> 1
                Role.FLAGGED -> 2
                Role.DRAFTS -> 3
                Role.SENT -> 4
                Role.ARCHIVE -> 5
                Role.JUNK -> 6
                Role.TRASH -> 7
                else -> 10
            }
    }
}
