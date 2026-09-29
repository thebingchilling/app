/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import android.content.Context
import com.fsck.k9.backend.api.Backend
import com.fsck.k9.backend.api.BackendFolder
import com.fsck.k9.backend.api.BackendPusher
import com.fsck.k9.backend.api.BackendPusherCallback
import com.fsck.k9.backend.api.SyncConfig
import com.fsck.k9.backend.api.SyncListener
import com.fsck.k9.backend.imap.BackendIdleRefreshManager
import com.fsck.k9.backend.imap.ImapBackend
import com.fsck.k9.backend.pop3.Pop3Backend
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.DefaultBodyFactory
import net.thunderbird.core.common.mail.Flag
import net.thunderbird.core.common.exception.MessagingException
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.internet.BinaryTempFileBody
import com.fsck.k9.mail.internet.MimeBodyPart
import com.fsck.k9.mail.internet.MimeHeader
import com.fsck.k9.mail.oauth.OAuth2TokenProvider
import com.fsck.k9.mail.store.imap.ImapStore
import com.fsck.k9.mail.store.pop3.Pop3Store
import com.fsck.k9.mail.transport.smtp.SmtpTransport
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import rs.ltt.android.cache.BlobStorage
import rs.ltt.android.database.AppDatabase
import rs.ltt.android.database.LttrsDatabase
import rs.ltt.android.entity.AccountWithCredentials
import rs.ltt.android.entity.IdentityWithNameAndEmail
import rs.ltt.android.mail.model.Email
import rs.ltt.android.mail.model.EmailBodyPart
import rs.ltt.android.mail.model.EmailBodyValue
import rs.ltt.android.mail.model.IdentifiableEmailWithKeywords
import rs.ltt.android.mail.model.IdentifiableEmailWithMailboxIds
import rs.ltt.android.mail.model.IdentifiableIdentity
import rs.ltt.android.mail.model.IdentifiableMailboxWithRole
import rs.ltt.android.mail.model.IdentifiableMailboxWithRoleAndName
import rs.ltt.android.mail.model.Keyword
import rs.ltt.android.mail.model.Role
import rs.ltt.android.mail.model.query.EmailQuery

enum class Status {
    UNCHANGED,
    UPDATED,
    HAS_MORE,
}

/** Notified when a sync brings in new, unread mail. */
fun interface NewMailListener {
    fun onNewMail(accountId: Long, emailIds: List<String>)
}

/**
 * Ripple's mail user agent: the operations Ltt.rs' UI asks for, implemented with IMAP/POP3 and
 * SMTP (Thunderbird's engine) and the local database.
 */
class Mua @JvmOverloads constructor(
    context: Context,
    val account: AccountWithCredentials,
    tokenOverride: OAuth2TokenProvider? = null,
) : StorageListener {

    private val context: Context = context.applicationContext
    private val accountId: Long = account.id
    private val credentials = account.credentials
    private val database: LttrsDatabase = LttrsDatabase.getInstance(this.context, accountId)
    private val dao = database.engineDao()
    private val isPop3 = credentials.isPop3
    private val storage = RoomBackendStorage(this.context, accountId, database, isPop3, this)
    private val queryEngine = QueryEngine(database)
    private val tokenProvider: OAuth2TokenProvider? =
        tokenOverride
            ?: if (credentials.oauthProvider != null) AppAuthTokenProvider(this.context, credentials.id) else null
    private val socketFactory = AndroidTrustedSocketFactory(this.context)
    private val smtpTransport = SmtpTransport(outgoingSettings(), socketFactory, tokenProvider)
    private val imapStore: ImapStore? =
        if (isPop3) {
            null
        } else {
            ImapStore.create(incomingSettings(), RippleImapStoreConfig(account.accountId), socketFactory, tokenProvider)
        }
    val backend: Backend =
        if (isPop3) {
            Pop3Backend(account.accountId, storage, Pop3Store(incomingSettings(), socketFactory), smtpTransport)
        } else {
            ImapBackend(
                account.accountId,
                storage,
                imapStore!!,
                AndroidPowerManager(this.context),
                BackendIdleRefreshManager(AndroidSystemAlarmManager(this.context)),
                RipplePushConfigProvider(),
                smtpTransport,
            )
        }

    private val activeQueries = ConcurrentHashMap<String, EmailQuery>()
    private val folderLocks = ConcurrentHashMap<String, Any>()
    private val refreshScheduler = Executors.newSingleThreadScheduledExecutor()
    private var pendingRefresh: ScheduledFuture<*>? = null

    init {
        BinaryTempFileBody.setTempDirectory(this.context.cacheDir)
    }

    // ------------------------------------------------------------------ settings

    fun incomingSettings(): ServerSettings = serverSettings(
        credentials.incomingProtocol,
        credentials.incomingHost,
        credentials.incomingPort,
        credentials.incomingSecurity,
    )

    private fun outgoingSettings(): ServerSettings = serverSettings(
        "smtp",
        credentials.smtpHost,
        credentials.smtpPort,
        credentials.smtpSecurity,
    )

    private fun serverSettings(type: String, host: String, port: Int, security: String): ServerSettings =
        ServerSettings(
            type,
            host,
            port,
            ConnectionSecurity.valueOf(security),
            AuthType.valueOf(credentials.authType),
            credentials.username,
            if (credentials.oauthProvider != null) null else credentials.password,
            null,
        )

    private fun syncConfig(): SyncConfig = SyncConfig(
        expungePolicy = SyncConfig.ExpungePolicy.IMMEDIATELY,
        earliestPollDate = null,
        syncRemoteDeletions = true,
        maximumAutoDownloadMessageSize = if (isPop3) POP3_MAX_DOWNLOAD else IMAP_MAX_DOWNLOAD,
        defaultVisibleLimit = RoomBackendStorage.DEFAULT_VISIBLE_LIMIT,
        syncFlags = setOf(Flag.SEEN, Flag.FLAGGED, Flag.ANSWERED, Flag.FORWARDED, Flag.DRAFT),
    )

    // ------------------------------------------------------------------ helpers

    private fun <T> submit(block: () -> T): ListenableFuture<T> = EXECUTOR.submit<T> { block() }

    private fun mailbox(role: Role): String? = dao.getMailboxId(role)

    private fun inbox(): String = mailbox(Role.INBOX) ?: "INBOX"

    private fun lockFor(mailboxId: String): Any = folderLocks.getOrPut(mailboxId) { Any() }

    private val isAutoSavingSentMail: Boolean
        get() {
            val host = credentials.smtpHost.lowercase()
            return host.endsWith("gmail.com") || host.endsWith("googlemail.com") ||
                host.endsWith("office365.com") || host.endsWith("outlook.com")
        }

    private val isGmail: Boolean
        get() = credentials.incomingHost.lowercase().let { it.endsWith("gmail.com") || it.endsWith("googlemail.com") }

    // ------------------------------------------------------------------ sync

    /** Checks that the servers accept our credentials. */
    fun checkSettings() {
        if (isPop3) {
            Pop3Store(incomingSettings(), socketFactory).checkSettings()
        } else {
            imapStore!!.checkSettings()
        }
        smtpTransport.checkSettings()
    }

    fun refreshFolderListBlocking() {
        backend.refreshFolderList()
        dao.updateMailboxCounts()
    }

    /**
     * Downloads what changed in a folder. With [notify] new unread inbox mail is reported to the
     * [newMailListener] (instant push); periodic refreshes post their own notifications.
     */
    @JvmOverloads
    fun syncBlocking(mailboxId: String, notify: Boolean = false) {
        synchronized(lockFor(mailboxId)) {
            if (dao.getMailbox(mailboxId) == null) {
                refreshFolderListBlocking()
                if (dao.getMailbox(mailboxId) == null) {
                    throw MessagingException("Folder $mailboxId does not exist")
                }
            }
            val listener = CollectingSyncListener()
            backend.sync(mailboxId, syncConfig(), listener)
            dao.updateMailboxCounts()
            listener.failure?.let { throw it }
            if (notify && listener.newMessages.isNotEmpty() && mailboxId == inbox()) {
                val unread = listener.newMessages
                    .map { MailIds.emailId(mailboxId, it) }
                    .filter { dao.emailExists(it) && !dao.getKeywords(it).contains(Keyword.SEEN) }
                if (unread.isNotEmpty()) {
                    newMailListener?.onNewMail(accountId, unread)
                }
            }
        }
        rematerializeNow()
    }

    fun refresh(): ListenableFuture<Status> = submit {
        if (dao.getMailboxIds().isEmpty()) {
            refreshFolderListBlocking()
        }
        syncBlocking(inbox())
        Status.UPDATED
    }

    fun refreshMailboxes(): ListenableFuture<Status> = submit {
        refreshFolderListBlocking()
        Status.UPDATED
    }

    fun refreshIdentities(): ListenableFuture<Status> =
        MoreExecutors.newDirectExecutorService().submit<Status> { Status.UNCHANGED }

    fun query(query: EmailQuery): ListenableFuture<Status> = submit {
        activeQueries[query.asHash()] = query
        var failure: Exception? = null
        try {
            if (dao.getMailboxIds().isEmpty()) {
                refreshFolderListBlocking()
            }
            syncBlocking(queryEngine.mailboxOf(query) ?: inbox())
        } catch (e: Exception) {
            failure = e
        }
        queryEngine.materialize(query)
        failure?.let { throw it }
        Status.UPDATED
    }

    /** "Load more": raises the number of messages kept for the folder and syncs again. */
    fun query(query: EmailQuery, afterEmailId: String): ListenableFuture<Status> = submit {
        activeQueries[query.asHash()] = query
        val mailboxId = queryEngine.mailboxOf(query)
        if (mailboxId == null) {
            queryEngine.materialize(query)
            return@submit Status.UNCHANGED
        }
        val folder = storage.getFolder(mailboxId)
        if (folder.getMoreMessages() == BackendFolder.MoreMessages.FALSE) {
            return@submit Status.UNCHANGED
        }
        storage.increaseVisibleLimit(mailboxId)
        syncBlocking(mailboxId)
        queryEngine.materialize(query)
        if (folder.getMoreMessages() == BackendFolder.MoreMessages.TRUE) Status.HAS_MORE else Status.UPDATED
    }

    override fun onFolderChanged(mailboxId: String) = scheduleRematerialize()

    override fun onFoldersChanged() = scheduleRematerialize()

    @Synchronized
    private fun scheduleRematerialize() {
        pendingRefresh?.cancel(false)
        pendingRefresh = refreshScheduler.schedule({ rematerializeNow() }, 400, TimeUnit.MILLISECONDS)
    }

    private fun rematerializeNow() {
        for (query in activeQueries.values) {
            try {
                queryEngine.materialize(query)
            } catch (e: Exception) {
                LOGGER.warn("Unable to update query results", e)
            }
        }
    }

    private fun afterLocalChange(emailIds: Collection<String>, threadIds: Collection<String>) {
        dao.confirmOverwrites(threadIds.filterNotNull().toSet())
        dao.updateMailboxCounts()
        rematerializeNow()
    }

    // ------------------------------------------------------------------ keywords

    fun setKeyword(emails: Collection<IdentifiableEmailWithKeywords>, keyword: String): ListenableFuture<Boolean> =
        modifyKeyword(emails, keyword, true)

    fun removeKeyword(emails: Collection<IdentifiableEmailWithKeywords>, keyword: String): ListenableFuture<Boolean> =
        modifyKeyword(emails, keyword, false)

    private fun modifyKeyword(
        emails: Collection<IdentifiableEmailWithKeywords>,
        keyword: String,
        value: Boolean,
    ): ListenableFuture<Boolean> = submit {
        val changing = emails.filter { dao.getKeywords(it.id).contains(keyword) != value }
        if (changing.isEmpty()) return@submit false
        val flag = MessageMapper.flagFor(keyword)
        if (flag != null && backend.supportsFlags) {
            for ((mailboxId, ids) in changing.groupBy { dao.getMailboxOf(it.id) }) {
                if (mailboxId == null) continue
                backend.setFlag(mailboxId, ids.map { MailIds.messageServerId(it.id) }, flag, value)
            }
        }
        val threadIds = changing.mapNotNull { dao.getThreadId(it.id) }
        for (email in changing) {
            dao.setKeyword(email.id, keyword, value)
        }
        afterLocalChange(changing.map { it.id }, threadIds)
        true
    }

    // ------------------------------------------------------------------ moving

    /** Moves messages on the server and relabels the local copies with their new UIDs. */
    private fun moveBlocking(emailIds: Collection<String>, target: String): Boolean {
        var changed = false
        val threadIds = emailIds.mapNotNull { dao.getThreadId(it) }
        for ((source, ids) in emailIds.groupBy { dao.getMailboxOf(it) }) {
            if (source == null || source == target) continue
            val uids = ids.map { MailIds.messageServerId(it) }
            if (backend.supportsMove || backend.supportsCopy) {
                val mapping =
                    if (backend.supportsMove) {
                        backend.moveMessages(source, target, uids)
                    } else {
                        backend.copyMessages(source, target, uids).also { backend.deleteMessages(source, uids) }
                    }
                for (id in ids) {
                    val newUid = mapping?.get(MailIds.messageServerId(id))
                    if (newUid != null) {
                        dao.moveEmail(id, MailIds.emailId(target, newUid), source, target)
                    } else {
                        dao.deleteEmails(listOf(id))
                    }
                }
            } else {
                throw MessagingException("This account type can not move messages")
            }
            changed = true
        }
        afterLocalChange(emailIds, threadIds)
        return changed
    }

    private fun deleteBlocking(emailIds: Collection<String>) {
        val threadIds = emailIds.mapNotNull { dao.getThreadId(it) }
        for ((source, ids) in emailIds.groupBy { dao.getMailboxOf(it) }) {
            if (source == null) continue
            backend.deleteMessages(source, ids.map { MailIds.messageServerId(it) })
            if (backend.supportsExpunge) {
                backend.expunge(source)
            }
            dao.deleteEmails(ids)
        }
        afterLocalChange(emailIds, threadIds)
    }

    fun moveToTrash(emails: Collection<IdentifiableEmailWithMailboxIds>): ListenableFuture<Boolean> = submit {
        val trash = mailbox(Role.TRASH)
        val ids = emails.map { it.id }
        if (trash == null || isPop3) {
            deleteBlocking(ids)
            return@submit true
        }
        val (inTrash, elsewhere) = ids.partition { dao.getMailboxOf(it) == trash }
        if (inTrash.isNotEmpty()) deleteBlocking(inTrash)
        if (elsewhere.isNotEmpty()) moveBlocking(elsewhere, trash)
        true
    }

    fun archive(emails: Collection<IdentifiableEmailWithMailboxIds>): ListenableFuture<Boolean> = submit {
        val inbox = inbox()
        val ids = emails.map { it.id }.filter { dao.getMailboxOf(it) == inbox }
        if (ids.isEmpty()) return@submit false
        val archive = mailbox(Role.ARCHIVE) ?: createArchiveFolder()
        moveBlocking(ids, archive)
    }

    private fun createArchiveFolder(): String {
        val store = imapStore ?: throw MessagingException("This account has no archive folder")
        val prefix = store.combinedPrefix ?: ""
        val name = prefix + "Archive"
        val folder = store.getFolder(name)
        if (!folder.exists()) {
            folder.create()
        }
        refreshFolderListBlocking()
        dao.setRole(name, Role.ARCHIVE)
        return name
    }

    fun moveToInbox(emails: Collection<IdentifiableEmailWithMailboxIds>): ListenableFuture<Boolean> = submit {
        moveBlocking(emails.map { it.id }, inbox())
    }

    fun removeFromMailbox(
        emails: Collection<IdentifiableEmailWithMailboxIds>,
        mailboxId: String,
    ): ListenableFuture<Boolean> = submit { removeFromMailboxBlocking(emails.map { it.id }, mailboxId) }

    private fun removeFromMailboxBlocking(emailIds: Collection<String>, mailboxId: String): Boolean {
        val ids = emailIds.filter { dao.getMailboxOf(it) == mailboxId }
        if (ids.isEmpty()) return false
        if (isGmail && mailbox(Role.ARCHIVE) != null) {
            // Gmail folders are labels: deleting from one keeps the message in All Mail.
            deleteBlocking(ids)
            return true
        }
        val target = mailbox(Role.ARCHIVE) ?: inbox()
        if (target == mailboxId) return false
        return moveBlocking(ids, target)
    }

    fun copyToMailbox(
        emails: Collection<IdentifiableEmailWithMailboxIds>,
        mailbox: IdentifiableMailboxWithRole,
    ): ListenableFuture<Boolean> = submit { copyBlocking(emails.map { it.id }, mailbox.id) }

    private fun copyBlocking(emailIds: Collection<String>, target: String): Boolean {
        var changed = false
        for ((source, ids) in emailIds.groupBy { dao.getMailboxOf(it) }) {
            if (source == null || source == target) continue
            backend.copyMessages(source, target, ids.map { MailIds.messageServerId(it) })
            changed = true
        }
        if (changed) {
            try {
                syncBlocking(target)
            } catch (e: Exception) {
                LOGGER.warn("Unable to sync {} after copying", target, e)
            }
        }
        afterLocalChange(emailIds, emailIds.mapNotNull { dao.getThreadId(it) })
        return changed
    }

    fun copyToImportant(emails: Collection<IdentifiableEmailWithMailboxIds>): ListenableFuture<Boolean> = submit {
        val important = mailbox(Role.IMPORTANT) ?: return@submit false
        copyBlocking(emails.map { it.id }, important)
    }

    fun modifyLabels(
        emails: Collection<IdentifiableEmailWithMailboxIds>,
        add: Collection<IdentifiableMailboxWithRoleAndName>,
        remove: Collection<IdentifiableMailboxWithRoleAndName>,
    ): ListenableFuture<Boolean> = submit {
        val ids = emails.map { it.id }
        var changed = false
        for (mailbox in add) {
            changed = copyBlocking(ids, mailbox.id) || changed
        }
        for (mailbox in remove) {
            changed = removeFromMailboxBlocking(ids, mailbox.id) || changed
        }
        changed
    }

    fun setRole(mailbox: IdentifiableMailboxWithRole, role: Role): ListenableFuture<Boolean> = submit {
        dao.clearRole(role, mailbox.id)
        dao.setRole(mailbox.id, role)
        true
    }

    fun emptyTrash(): ListenableFuture<Boolean> = submit {
        val trash = mailbox(Role.TRASH) ?: return@submit false
        backend.deleteAllMessages(trash)
        if (backend.supportsExpunge) backend.expunge(trash)
        dao.clearMailbox(trash)
        dao.updateMailboxCounts()
        rematerializeNow()
        true
    }

    // ------------------------------------------------------------------ drafts and sending

    private fun attachmentFiles(): MimeComposer.AttachmentFiles =
        MimeComposer.AttachmentFiles { blobId -> downloadBlocking(blobId) }

    /** Saves a draft to the Drafts folder and returns its local email id. */
    fun draft(email: Email): ListenableFuture<String> = submit {
        val drafts = mailbox(Role.DRAFTS) ?: throw MessagingException("This account has no Drafts folder")
        val draft = email.toBuilder().keyword(Keyword.DRAFT, true).keyword(Keyword.SEEN, true).build()
        val message = MimeComposer.compose(draft, attachmentFiles())
        val uid = backend.uploadMessage(drafts, message)
            ?: backend.findByMessageId(drafts, message.messageId)
        syncBlocking(drafts)
        uid?.let { MailIds.emailId(drafts, it) } ?: findByMessageId(message.messageId) ?: ""
    }

    fun send(email: Email, identity: IdentifiableIdentity): ListenableFuture<String> = submit {
        sendBlocking(email)
    }

    private fun sendBlocking(email: Email): String {
        val files = attachmentFiles()
        val outgoing = MimeComposer.compose(email, files)
        val messageId = outgoing.messageId
        backend.sendMessage(outgoing)
        markRepliedBlocking(email.inReplyTo.orEmpty())
        val sent = mailbox(Role.SENT)
        if (sent != null && !isPop3) {
            try {
                if (!isAutoSavingSentMail) {
                    backend.uploadMessage(sent, MimeComposer.compose(email, files, messageId))
                }
                syncBlocking(sent)
            } catch (e: Exception) {
                LOGGER.warn("Unable to store sent message", e)
            }
        }
        return findByMessageId(messageId) ?: ""
    }

    private fun markRepliedBlocking(inReplyTo: List<String>) {
        if (inReplyTo.isEmpty()) return
        val ids = dao.getEmailIdsByMessageIds(inReplyTo.map { it.removePrefix("<").removeSuffix(">") })
        for ((mailboxId, emailIds) in ids.groupBy { dao.getMailboxOf(it) }) {
            if (mailboxId == null) continue
            try {
                if (backend.supportsFlags) {
                    backend.setFlag(mailboxId, emailIds.map { MailIds.messageServerId(it) }, Flag.ANSWERED, true)
                }
                emailIds.forEach { dao.setKeyword(it, Keyword.ANSWERED, true) }
            } catch (e: Exception) {
                LOGGER.warn("Unable to mark as answered", e)
            }
        }
    }

    private fun findByMessageId(messageId: String?): String? {
        if (messageId == null) return null
        return dao.getEmailIdsByMessageIds(listOf(messageId.removePrefix("<").removeSuffix(">"))).firstOrNull()
    }

    /** Sends an existing draft as it is and removes it from the Drafts folder. */
    fun submit(emailId: String, identity: IdentityWithNameAndEmail): ListenableFuture<Boolean> = submit {
        val draft = database.threadAndEmailDao().getEmailWithReferences(accountId, emailId).get()
            ?: return@submit false
        val textPart = EmailBodyPart.builder().partId(MessageMapper.TEXT_PART_ID).type("text/plain").build()
        val attachments = draft.attachments.map {
            EmailBodyPart.builder().blobId(it.blobId).type(it.type).name(it.name).size(it.size).build()
        }
        val email = Email.builder()
            .from(identity.emailAddress)
            .to(draft.to)
            .cc(draft.cc)
            .bcc(draft.bcc)
            .subject(draft.subject)
            .inReplyTo(draft.inReplyTo.orEmpty())
            .bodyValue(MessageMapper.TEXT_PART_ID, EmailBodyValue.builder().value(draft.text ?: "").build())
            .textBody(textPart)
            .attachments(attachments)
            .build()
        sendBlocking(email)
        deleteBlocking(listOf(emailId))
        true
    }

    fun discardDraft(email: IdentifiableEmailWithKeywords): ListenableFuture<Boolean> = submit {
        if (!dao.emailExists(email.id)) return@submit false
        deleteBlocking(listOf(email.id))
        true
    }

    // ------------------------------------------------------------------ attachments

    fun verifyAttachmentsDoNotExceedLimit(
        attachments: Collection<rs.ltt.android.mail.model.Attachment>,
    ): ListenableFuture<Void?> = submit {
        rs.ltt.android.mail.util.AttachmentUtil.verifyAttachmentsDoNotExceedLimit(attachments)
        null
    }

    fun download(blobId: String): ListenableFuture<File> = submit { downloadBlocking(blobId) }

    /** Returns the cached file for a blob, fetching the MIME part from the server if needed. */
    fun downloadBlocking(blobId: String): File {
        val storage = BlobStorage.get(context, accountId, blobId)
        if (storage.file.exists()) return storage.file
        val part = dao.getBodyPart(blobId) ?: throw MessagingException("Unknown attachment $blobId")
        val mailboxId = dao.getMailboxOf(part.emailId) ?: throw MessagingException("Message is gone")
        val uid = MailIds.messageServerId(part.emailId)
        if (isPop3) {
            // POP3 downloads whole messages; attachments are cached while syncing.
            backend.downloadMessage(syncConfig(), mailboxId, uid)
            if (storage.file.exists()) return storage.file
            throw MessagingException("Attachment is not available")
        }
        val mimePart = MimeBodyPart()
        mimePart.serverExtra = part.partId
        part.encoding?.let { mimePart.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, it) }
        part.type?.let { mimePart.setHeader(MimeHeader.HEADER_CONTENT_TYPE, it) }
        backend.fetchPart(mailboxId, uid, mimePart, DefaultBodyFactory())
        MessageMapper.writePart(mimePart, storage.temporaryFile)
        if (!storage.moveTemporaryToFile()) {
            throw MessagingException("Unable to store attachment")
        }
        return storage.file
    }

    // ------------------------------------------------------------------ push

    val isPushCapable: Boolean
        get() = backend.isPushCapable

    fun createPusher(callback: BackendPusherCallback): BackendPusher = backend.createPusher(callback)

    fun foldersToPush(): List<String> = listOfNotNull(mailbox(Role.INBOX) ?: "INBOX")

    fun close() {
        imapStore?.closeAllConnections()
        refreshScheduler.shutdown()
    }

    private inner class CollectingSyncListener : SyncListener {
        val newMessages = ArrayList<String>()
        var failure: Exception? = null

        override fun syncStarted(folderServerId: String) = Unit
        override fun syncAuthenticationSuccess() = Unit
        override fun syncHeadersStarted(folderServerId: String) = Unit
        override fun syncHeadersProgress(folderServerId: String, completed: Int, total: Int) = Unit
        override fun syncHeadersFinished(folderServerId: String, totalMessagesInMailbox: Int, numNewMessages: Int) = Unit
        override fun syncProgress(folderServerId: String, completed: Int, total: Int) = Unit

        override fun syncNewMessage(folderServerId: String, messageServerId: String, isOldMessage: Boolean) {
            if (!isOldMessage) newMessages.add(messageServerId)
        }

        override fun syncRemovedMessage(folderServerId: String, messageServerId: String) = Unit
        override fun syncFlagChanged(folderServerId: String, messageServerId: String) = Unit
        override fun syncFinished(folderServerId: String) = Unit

        override fun syncFailed(folderServerId: String, message: String, exception: Exception?) {
            failure = exception ?: MessagingException(message)
        }

        override fun folderStatusChanged(folderServerId: String) = Unit
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(Mua::class.java)
        private const val IMAP_MAX_DOWNLOAD = 128 * 1024
        private const val POP3_MAX_DOWNLOAD = 32 * 1024 * 1024
        private val EXECUTOR: ListeningExecutorService =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4))

        @JvmStatic
        @Volatile
        var newMailListener: NewMailListener? = null

        /** Logs in to the incoming and outgoing server without touching any database. */
        @JvmStatic
        @JvmOverloads
        fun checkSettings(
            context: Context,
            credentials: AccountWithCredentials.Credentials,
            tokenProvider: OAuth2TokenProvider? = null,
        ) {
            val serverSettings = { type: String, host: String, port: Int, security: String ->
                ServerSettings(
                    type,
                    host,
                    port,
                    ConnectionSecurity.valueOf(security),
                    AuthType.valueOf(credentials.authType),
                    credentials.username,
                    if (tokenProvider != null) null else credentials.password,
                    null,
                )
            }
            val socketFactory = AndroidTrustedSocketFactory(context.applicationContext)
            val incoming = serverSettings(
                credentials.incomingProtocol,
                credentials.incomingHost,
                credentials.incomingPort,
                credentials.incomingSecurity,
            )
            if (credentials.isPop3) {
                Pop3Store(incoming, socketFactory).checkSettings()
            } else {
                val store = ImapStore.create(incoming, RippleImapStoreConfig(credentials.username), socketFactory, tokenProvider)
                try {
                    store.checkSettings()
                } finally {
                    store.closeAllConnections()
                }
            }
            val outgoing = serverSettings("smtp", credentials.smtpHost, credentials.smtpPort, credentials.smtpSecurity)
            SmtpTransport(outgoing, socketFactory, tokenProvider).checkSettings()
        }
    }
}
