package rs.ltt.android.engine

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import rs.ltt.android.database.AppDatabase
import rs.ltt.android.database.LttrsDatabase
import rs.ltt.android.entity.CredentialsEntity
import rs.ltt.android.mail.util.StandardQueries
import rs.ltt.android.ui.activity.SetupActivity

/** A login the server rejects is reported, and signing in again replaces it in place. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignInProblemsTest {

    private lateinit var greenMail: GreenMail
    private lateinit var context: Context
    private var accountId = 0L

    @Before
    fun setUp() {
        greenMail = GreenMail(ServerSetupTest.SMTP_IMAP.map { it.dynamicPort() }.toTypedArray())
        greenMail.start()
        greenMail.setUser("carol@example.com", "carol", "new-password")
        context = ApplicationProvider.getApplicationContext()
        val credentials = CredentialsEntity().apply {
            incomingProtocol = "imap"
            incomingHost = "127.0.0.1"
            incomingPort = greenMail.imap.port
            incomingSecurity = "NONE"
            smtpHost = "127.0.0.1"
            smtpPort = greenMail.smtp.port
            smtpSecurity = "NONE"
            authType = "PLAIN"
            username = "carol"
            password = "old-password"
        }
        accountId = onBackground {
            AppDatabase.getInstance(context).accountDao().insert(credentials, "carol@example.com").id
        }
    }

    @After
    fun tearDown() {
        LttrsDatabase.close(accountId)
        greenMail.stop()
    }

    private fun <T> onBackground(block: () -> T): T {
        var result: Result<T>? = null
        val thread = Thread { result = runCatching(block) }
        thread.start()
        thread.join(30_000)
        return result!!.getOrThrow()
    }

    private fun syncInbox() {
        val account = onBackground { AppDatabase.getInstance(context).accountDao().getAccount(accountId) }
        val mua = Mua(context, account)
        try {
            mua.query(StandardQueries.mailbox("INBOX")).get(30, TimeUnit.SECONDS)
        } finally {
            mua.close()
        }
    }

    @Test
    fun rejectedLoginIsReportedAndReplacedBySigningInAgain() {
        val failure = try {
            syncInbox()
            fail("the old password was accepted")
            return
        } catch (e: ExecutionException) {
            e
        }
        assertTrue(SignInProblems.isAuthFailure(failure))
        assertTrue(onBackground { SignInProblems.reportIfAuthFailure(context, accountId, failure) })

        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, notifications.size)
        val intent = shadowOf(notifications[0].contentIntent).savedIntent
        assertEquals(SetupActivity::class.java.name, intent.component?.className)
        assertEquals(accountId, intent.getLongExtra(SetupActivity.EXTRA_REAUTH_ACCOUNT, -1L))

        onBackground {
            AppDatabase.getInstance(context).accountDao()
                .updateLogin(accountId, "PLAIN", "carol", "new-password", null, null)
        }
        syncInbox()
        val account = onBackground { AppDatabase.getInstance(context).accountDao().getAccount(accountId) }
        assertEquals("carol@example.com", account.name)
        assertNotNull(account.credentials)

        SignInProblems.clear(context, accountId)
        assertTrue(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }

    @Test
    fun networkErrorsAreNotSignInProblems() {
        assertFalse(SignInProblems.isAuthFailure(ExecutionException(SocketTimeoutException("slow"))))
        assertFalse(SignInProblems.reportIfAuthFailure(context, accountId, SocketTimeoutException("slow")))
    }
}
