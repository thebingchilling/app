package rs.ltt.android.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fsck.k9.backend.api.BackendPusherCallback
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.activation.DataHandler
import jakarta.mail.Flags
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rs.ltt.android.database.LttrsDatabase
import rs.ltt.android.entity.AccountWithCredentials
import rs.ltt.android.entity.EmailWithKeywords
import rs.ltt.android.mail.model.Email
import rs.ltt.android.mail.model.EmailAddress
import rs.ltt.android.mail.model.EmailBodyPart
import rs.ltt.android.mail.model.EmailBodyValue
import rs.ltt.android.mail.model.Identity
import rs.ltt.android.mail.model.Keyword
import rs.ltt.android.mail.util.StandardQueries

/** Runs Ripple's engine against GreenMail, a real IMAP/SMTP server running in the test JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineIntegrationTest {

    private lateinit var greenMail: GreenMail
    private lateinit var context: Context
    private lateinit var mua: Mua
    private lateinit var database: LttrsDatabase
    private var testAccountId = 0L
    private val session = Session.getInstance(Properties())

    @Before
    fun setUp() {
        testAccountId = NEXT_ACCOUNT_ID.incrementAndGet()
        greenMail = GreenMail(ServerSetupTest.SMTP_IMAP.map { it.dynamicPort() }.toTypedArray())
        greenMail.start()
        greenMail.setUser("alice@example.com", "alice", "secret")
        context = ApplicationProvider.getApplicationContext()
        val account = AccountWithCredentials().apply {
            id = testAccountId
            credentialsId = 1
            accountId = "alice@example.com"
            name = "alice@example.com"
            incomingProtocol = "imap"
            incomingHost = "127.0.0.1"
            incomingPort = greenMail.imap.port
            incomingSecurity = "NONE"
            smtpHost = "127.0.0.1"
            smtpPort = greenMail.smtp.port
            smtpSecurity = "NONE"
            authType = "PLAIN"
            username = "alice"
            password = "secret"
        }
        mua = Mua(context, account)
        database = LttrsDatabase.getInstance(context, testAccountId)
    }

    @After
    fun tearDown() {
        if (this::mua.isInitialized) mua.close()
        LttrsDatabase.close(testAccountId)
        greenMail.stop()
    }

    private fun deliver(
        subject: String,
        body: String,
        messageId: String,
        inReplyTo: String? = null,
        from: String = "bob@example.com",
    ): MimeMessage {
        val message = object : MimeMessage(session) {
            override fun updateMessageID() {
                setHeader("Message-ID", "<$messageId>")
            }
        }
        message.setFrom(InternetAddress(from, "Bob"))
        message.setRecipient(Message.RecipientType.TO, InternetAddress("alice@example.com"))
        message.subject = subject
        message.setText(body)
        if (inReplyTo != null) {
            message.setHeader("In-Reply-To", "<$inReplyTo>")
            message.setHeader("References", "<$inReplyTo>")
        }
        message.saveChanges()
        greenMail.getUserManager().getUserByEmail("alice@example.com").deliver(message)
        return message
    }

    private fun inboxItems(): List<String> {
        val query = StandardQueries.mailbox("INBOX")
        mua.query(query).get(30, TimeUnit.SECONDS)
        return onBackground { database.queryDao().getEmailIds(query.asHash()) }
    }

    private fun <T> onBackground(block: () -> T): T {
        var result: Result<T>? = null
        val thread = Thread { result = runCatching(block) }
        thread.start()
        thread.join(30_000)
        return result!!.getOrThrow()
    }

    @Test
    fun syncsInboxAndGroupsRepliesIntoThreads() {
        deliver("Lunch?", "Shall we have lunch tomorrow?", "a1@example.com")
        deliver("Re: Lunch?", "Yes, at noon.", "a2@example.com", inReplyTo = "a1@example.com")
        deliver("Invoice", "Your invoice is attached.", "b1@example.com")

        val items = inboxItems()
        assertEquals("two threads expected", 2, items.size)

        val dao = database.engineDao()
        val ids = onBackground { dao.getEmailIds("INBOX") }
        assertEquals(3, ids.size)
        val threads = onBackground { ids.map { dao.getThreadId(it) }.toSet() }
        assertEquals(2, threads.size)

        val lunch = onBackground { dao.getEmailIdsByMessageIds(listOf("a1@example.com", "a2@example.com")) }
        assertEquals(2, lunch.size)
        assertEquals(onBackground { dao.getThreadId(lunch[0]) }, onBackground { dao.getThreadId(lunch[1]) })

        val text = onBackground { dao.getEmailIdsByText("%invoice is attached%") }
        assertEquals(1, text.size)
    }

    @Test
    fun replyArrivingFirstStillJoinsThread() {
        deliver("Re: Plans", "Sounds good", "c2@example.com", inReplyTo = "c1@example.com")
        inboxItems()
        deliver("Plans", "What are the plans?", "c1@example.com")
        val items = inboxItems()
        assertEquals(1, items.size)
    }

    @Test
    fun markingAsReadSetsTheFlagOnTheServer() {
        deliver("Hello", "Hi Alice", "d1@example.com")
        inboxItems()
        val emailId = onBackground { database.engineDao().getEmailIds("INBOX").first() }
        val email = onBackground { database.threadAndEmailDao().getEmailWithKeyword(emailId) }
        assertTrue(mua.setKeyword(listOf<EmailWithKeywords>(email), Keyword.SEEN).get(30, TimeUnit.SECONDS))
        val messages = greenMail.getReceivedMessagesForDomain("example.com")
        val inbox = greenMail.managers.imapHostManager.getInbox(greenMail.userManager.getUserByEmail("alice@example.com"))
        assertTrue(inbox.messages.all { it.flags.contains(Flags.Flag.SEEN) })
        assertTrue(messages.isNotEmpty())
        assertTrue(onBackground { database.engineDao().getKeywords(emailId) }.contains(Keyword.SEEN))
    }

    @Test
    fun deletingWithoutTrashFolderRemovesTheMessage() {
        deliver("Spam", "Buy now", "e1@example.com")
        deliver("Keep", "Important", "e2@example.com")
        inboxItems()
        val spam = onBackground { database.engineDao().getEmailIdsByMessageIds(listOf("e1@example.com")).first() }
        val email = onBackground { database.threadAndEmailDao().getEmailsWithMailboxes(database.engineDao().getThreadId(spam)) }
        assertTrue(mua.moveToTrash(email).get(30, TimeUnit.SECONDS))
        val inbox = greenMail.managers.imapHostManager.getInbox(greenMail.userManager.getUserByEmail("alice@example.com"))
        assertEquals(1, inbox.messageCount)
        assertEquals(1, inboxItems().size)
    }

    @Test
    fun sendingDeliversOverSmtp() {
        val email = Email.builder()
            .from(EmailAddress.builder().email("alice@example.com").name("Alice").build())
            .to(listOf(EmailAddress.builder().email("carol@example.org").build()))
            .subject("Greetings from Ripple")
            .bodyValue("0", EmailBodyValue.builder().value("Sent through SMTP").build())
            .textBody(EmailBodyPart.builder().partId("0").type("text/plain").build())
            .build()
        val identity = Identity.builder().id("primary").email("alice@example.com").build()
        mua.send(email, identity).get(30, TimeUnit.SECONDS)
        assertTrue(greenMail.waitForIncomingEmail(10_000, 1))
        val received = greenMail.receivedMessages.first { it.subject == "Greetings from Ripple" }
        assertTrue(GreenMailUtil.getBody(received).contains("Sent through SMTP"))
        assertEquals("carol@example.org", (received.getRecipients(Message.RecipientType.TO)[0] as InternetAddress).address)
    }

    @Test
    fun largeAttachmentIsFetchedOnDemand() {
        val payload = ByteArray(400 * 1024) { (it % 251).toByte() }
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("bob@example.com"))
        message.setRecipient(Message.RecipientType.TO, InternetAddress("alice@example.com"))
        message.subject = "Photos"
        val text = MimeBodyPart().apply { setText("See attachment") }
        val attachment = MimeBodyPart().apply {
            dataHandler = DataHandler(ByteArrayDataSource(payload, "application/octet-stream"))
            fileName = "data.bin"
            disposition = "attachment"
        }
        message.setContent(MimeMultipart(text, attachment))
        message.saveChanges()
        greenMail.userManager.getUserByEmail("alice@example.com").deliver(message)

        inboxItems()
        val emailId = onBackground { database.engineDao().getEmailIds("INBOX").first() }
        val emails = onBackground { database.threadAndEmailDao().getEmails(listOf(emailId)) }
        val attachments = emails.first().attachments
        assertEquals(1, attachments.size)
        assertEquals("data.bin", attachments[0].name)
        assertTrue(emails.first().textBodies.joinToString().contains("See attachment"))
        val file = mua.download(attachments[0].blobId).get(30, TimeUnit.SECONDS)
        assertEquals(payload.size.toLong(), file.length())
        assertTrue(payload.contentEquals(file.readBytes()))
    }

    @Test
    fun localSearchFindsSyncedMail() {
        deliver("Quarterly report", "The numbers look great", "f1@example.com")
        deliver("Holiday", "Beach time", "f2@example.com")
        inboxItems()
        val query = StandardQueries.search("numbers", arrayOf())
        mua.query(query).get(30, TimeUnit.SECONDS)
        val ids = onBackground { database.queryDao().getEmailIds(query.asHash()) }
        assertEquals(1, ids.size)
    }

    @Test
    fun idlePushReportsNewMail() {
        inboxItems()
        val latch = CountDownLatch(1)
        val pusher = mua.createPusher(
            object : BackendPusherCallback {
                override fun onPushEvent(folderServerId: String) {
                    latch.countDown()
                }

                override fun onPushError(exception: Exception) = Unit

                override suspend fun onPushNotSupported() = Unit
            },
        )
        pusher.start()
        pusher.updateFolders(listOf("INBOX"))
        Thread.sleep(1500)
        deliver("Instant", "This should arrive via IDLE", "g1@example.com")
        val pushed = latch.await(20, TimeUnit.SECONDS)
        pusher.stop()
        assertTrue("expected an IDLE push event", pushed)
        assertNotEquals(0, inboxItems().size)
    }

    private fun createFolder(name: String) {
        val user = greenMail.userManager.getUserByEmail("alice@example.com")
        greenMail.managers.imapHostManager.createMailbox(user, name)
    }

    private fun serverFolder(name: String) =
        greenMail.managers.imapHostManager.getFolder(
            greenMail.userManager.getUserByEmail("alice@example.com"),
            name,
        )

    @Test
    fun trashArchiveAndBackToInbox() {
        createFolder("Trash")
        createFolder("Archive")
        deliver("Newsletter", "Weekly news", "h1@example.com")
        deliver("Receipt", "Thanks for your order", "h2@example.com")
        inboxItems()
        val dao = database.engineDao()
        val newsletter = onBackground { dao.getEmailIdsByMessageIds(listOf("h1@example.com")).first() }
        val receipt = onBackground { dao.getEmailIdsByMessageIds(listOf("h2@example.com")).first() }
        val newsletterThread = onBackground { database.threadAndEmailDao().getEmailsWithMailboxes(dao.getThreadId(newsletter)) }
        val receiptThread = onBackground { database.threadAndEmailDao().getEmailsWithMailboxes(dao.getThreadId(receipt)) }

        assertTrue(mua.moveToTrash(newsletterThread).get(30, TimeUnit.SECONDS))
        assertTrue(mua.archive(receiptThread).get(30, TimeUnit.SECONDS))

        assertEquals(0, serverFolder("INBOX").messageCount)
        assertEquals(1, serverFolder("Trash").messageCount)
        assertEquals(1, serverFolder("Archive").messageCount)
        assertEquals(0, inboxItems().size)
        val movedReceipt = onBackground { dao.getEmailIdsByMessageIds(listOf("h2@example.com")).first() }
        assertEquals("Archive", onBackground { dao.getMailboxOf(movedReceipt) })

        val archived = onBackground {
            database.threadAndEmailDao().getEmailsWithMailboxes(dao.getThreadId(movedReceipt))
        }
        assertTrue(mua.moveToInbox(archived).get(30, TimeUnit.SECONDS))
        assertEquals(1, serverFolder("INBOX").messageCount)
        assertEquals(1, inboxItems().size)
    }

    @Test
    fun draftsAreStoredOnTheServer() {
        createFolder("Drafts")
        mua.refreshMailboxes().get(30, TimeUnit.SECONDS)
        val email = Email.builder()
            .from(EmailAddress.builder().email("alice@example.com").build())
            .to(listOf(EmailAddress.builder().email("dave@example.org").build()))
            .subject("Unfinished thoughts")
            .bodyValue("0", EmailBodyValue.builder().value("To be continued").build())
            .textBody(EmailBodyPart.builder().partId("0").type("text/plain").build())
            .build()
        val id = mua.draft(email).get(30, TimeUnit.SECONDS)
        assertTrue(id.isNotEmpty())
        val drafts = serverFolder("Drafts")
        assertEquals(1, drafts.messageCount)
        assertTrue(drafts.messages.first().flags.contains(Flags.Flag.DRAFT))
        assertTrue(onBackground { database.engineDao().getKeywords(id) }.contains(Keyword.DRAFT))

        val stored = onBackground { database.threadAndEmailDao().getEmailWithKeyword(id) }
        assertTrue(mua.discardDraft(stored).get(30, TimeUnit.SECONDS))
        assertEquals(0, serverFolder("Drafts").messageCount)
    }

    @Test
    fun copyingToAFolderAddsALabel() {
        createFolder("Work")
        deliver("Project", "Kickoff on Monday", "i1@example.com")
        inboxItems()
        val dao = database.engineDao()
        val id = onBackground { dao.getEmailIds("INBOX").first() }
        val emails = onBackground { database.threadAndEmailDao().getEmailsWithMailboxes(dao.getThreadId(id)) }
        val work = onBackground { database.mailboxDao().getMailbox("Work") }
        assertTrue(mua.copyToMailbox(emails, work).get(30, TimeUnit.SECONDS))
        assertEquals(1, serverFolder("Work").messageCount)
        assertEquals(1, serverFolder("INBOX").messageCount)
        // both copies belong to the same conversation
        val ids = onBackground { dao.getEmailIdsByMessageIds(listOf("i1@example.com")) }
        assertEquals(2, ids.size)
        assertEquals(onBackground { dao.getThreadId(ids[0]) }, onBackground { dao.getThreadId(ids[1]) })
    }

    @Test
    fun checkSettingsAcceptsGoodAndRejectsBadPasswords() {
        val good = AccountWithCredentials.Credentials(
            -1L, "imap", "127.0.0.1", greenMail.imap.port, "NONE",
            "127.0.0.1", greenMail.smtp.port, "NONE", "PLAIN", "alice", "secret", null,
        )
        Mua.checkSettings(context, good)
        val bad = AccountWithCredentials.Credentials(
            -1L, "imap", "127.0.0.1", greenMail.imap.port, "NONE",
            "127.0.0.1", greenMail.smtp.port, "NONE", "PLAIN", "alice", "wrong", null,
        )
        try {
            Mua.checkSettings(context, bad)
            throw AssertionError("wrong password was accepted")
        } catch (e: com.fsck.k9.mail.AuthenticationFailedException) {
            // expected
        }
    }

    companion object {
        private val NEXT_ACCOUNT_ID = java.util.concurrent.atomic.AtomicLong(100)
    }
}
