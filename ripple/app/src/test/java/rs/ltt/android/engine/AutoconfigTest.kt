package rs.ltt.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoconfigTest {

    private val ispdb = """
        <?xml version="1.0" encoding="UTF-8"?>
        <clientConfig version="1.1">
          <emailProvider id="example.net">
            <domain>example.net</domain>
            <incomingServer type="pop3">
              <hostname>pop.example.net</hostname>
              <port>995</port>
              <socketType>SSL</socketType>
              <username>%EMAILADDRESS%</username>
            </incomingServer>
            <incomingServer type="imap">
              <hostname>imap.example.net</hostname>
              <port>143</port>
              <socketType>STARTTLS</socketType>
              <username>%EMAILLOCALPART%</username>
            </incomingServer>
            <outgoingServer type="smtp">
              <hostname>smtp.%EMAILDOMAIN%</hostname>
              <port>587</port>
              <socketType>STARTTLS</socketType>
            </outgoingServer>
          </emailProvider>
        </clientConfig>
    """.trimIndent()

    @Test
    fun prefersImapAndSubstitutesPlaceholders() {
        val settings = Autoconfig.parse(ispdb, "jane@example.net")!!
        assertEquals("imap", settings.protocol)
        assertEquals("imap.example.net", settings.incomingHost)
        assertEquals(143, settings.incomingPort)
        assertEquals("STARTTLS_REQUIRED", settings.incomingSecurity)
        assertEquals("jane", settings.username)
        assertEquals("smtp.example.net", settings.smtpHost)
        assertEquals(587, settings.smtpPort)
        assertTrue(settings.discovered)
    }

    @Test
    fun rejectsConfigWithoutSmtp() {
        val xml = ispdb.replace(Regex("(?s)<outgoingServer.*</outgoingServer>"), "")
        assertNull(Autoconfig.parse(xml, "jane@example.net"))
    }

    @Test
    fun knowsGmailAndOutlook() {
        val gmail = Autoconfig.builtIn("someone@gmail.com")!!
        assertEquals("imap.gmail.com", gmail.incomingHost)
        assertEquals(OAuthProvider.GOOGLE, gmail.oauthProvider)
        val outlook = Autoconfig.builtIn("someone@hotmail.com")!!
        assertEquals("outlook.office365.com", outlook.incomingHost)
        assertEquals(OAuthProvider.MICROSOFT, outlook.oauthProvider)
        assertNull(Autoconfig.builtIn("someone@example.org"))
    }

    @Test
    fun mailIdsRoundTrip() {
        val id = MailIds.emailId("[Gmail]/All Mail", "4711")
        assertEquals("4711", MailIds.messageServerId(id))
        assertTrue(MailIds.belongsTo(id, "[Gmail]/All Mail"))
        assertFalse(MailIds.belongsTo(id, "INBOX"))
    }

    @Test
    fun parsesMessageIds() {
        assertEquals(
            listOf("a@b", "c@d"),
            MessageMapper.parseIds(arrayOf("<a@b> <c@d>", "<a@b>")),
        )
    }
}
