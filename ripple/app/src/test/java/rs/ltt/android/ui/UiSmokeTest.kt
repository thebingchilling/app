package rs.ltt.android.ui

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import rs.ltt.android.R
import rs.ltt.android.database.AppDatabase
import rs.ltt.android.entity.CredentialsEntity
import rs.ltt.android.repository.MainRepository
import rs.ltt.android.ui.activity.LttrsActivity
import rs.ltt.android.ui.activity.SetupActivity
import rs.ltt.android.ui.model.SetupViewModel

/** Starts the real activities to catch layout, data binding and navigation mistakes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {

    private lateinit var context: Context
    private var greenMail: GreenMail? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        greenMail?.stop()
    }

    private fun idle(ms: Long = 200) {
        repeat(20) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(ms / 20 + 1)
        }
    }

    @Test
    fun setupScreensInflate() {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val activity = controller.get()
        idle()
        assertNotNull(activity.findViewById<View>(R.id.email_address))
        assertNotNull(activity.findViewById<View>(R.id.sign_in_google))
        screenshot(activity, "setup_sign_in")

        val viewModel = androidx.lifecycle.ViewModelProvider(activity)[SetupViewModel::class.java]
        viewModel.emailAddress.value = "someone@example.com"
        viewModel.openServerSettings()
        idle()
        assertNotNull("server settings shown", activity.findViewById<View>(R.id.incoming_security))
        screenshot(activity, "setup_server_settings")
        controller.pause().stop().destroy()
    }

    @Test
    fun inboxShowsSyncedMail() {
        val server = GreenMail(ServerSetupTest.SMTP_IMAP.map { it.dynamicPort() }.toTypedArray())
        greenMail = server
        server.start()
        server.setUser("erin@example.com", "erin", "pw")
        val message = MimeMessage(Session.getInstance(Properties()))
        message.setFrom(InternetAddress("frank@example.com", "Frank"))
        message.setRecipient(Message.RecipientType.TO, InternetAddress("erin@example.com"))
        message.subject = "Hello from the smoke test"
        message.setText("Body text")
        message.saveChanges()
        server.userManager.getUserByEmail("erin@example.com").deliver(message)

        val credentials = CredentialsEntity().apply {
            incomingProtocol = "imap"
            incomingHost = "127.0.0.1"
            incomingPort = server.imap.port
            incomingSecurity = "NONE"
            smtpHost = "127.0.0.1"
            smtpPort = server.smtp.port
            smtpSecurity = "NONE"
            authType = "PLAIN"
            username = "erin"
            password = "pw"
        }
        val application = context as android.app.Application
        val accountId = MainRepository(application)
            .insertAccount(credentials, "erin@example.com", "Erin")
            .get(30, TimeUnit.SECONDS)
        AppDatabase.getInstance(context).accountDao().selectAccount(accountId)

        val intent = Intent(context, LttrsActivity::class.java).putExtra(LttrsActivity.EXTRA_ACCOUNT_ID, accountId)
        val controller = Robolectric.buildActivity(LttrsActivity::class.java, intent).setup()
        val activity = controller.get()
        var found = false
        val deadline = System.currentTimeMillis() + 30_000
        while (!found && System.currentTimeMillis() < deadline) {
            idle(500)
            found = findText(activity.window.decorView, "Hello from the smoke test")
        }
        assertTrue("the synced message is listed", found)
        screenshot(activity, "inbox")
        val list = findRecyclerView(activity.window.decorView)
        assertNotNull(list)
        controller.pause().stop().destroy()
    }

    /** Saves what the activity shows to build/screenshots (for README and review). */
    private fun screenshot(activity: android.app.Activity, name: String) {
        try {
            val view = activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots")
            dir.mkdirs()
            java.io.FileOutputStream(java.io.File(dir, "$name.png")).use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (e: Throwable) {
            println("screenshot $name failed: $e")
        }
    }

    private fun findText(view: View, text: String): Boolean {
        if (view is android.widget.TextView && view.text?.toString()?.contains(text) == true) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (findText(view.getChildAt(i), text)) return true
            }
        }
        return false
    }

    private fun findRecyclerView(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findRecyclerView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
