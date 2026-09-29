/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import com.fsck.k9.mail.Address
import com.fsck.k9.mail.Body
import net.thunderbird.core.common.mail.Flag
import com.fsck.k9.mail.internet.AddressHeaderBuilder
import com.fsck.k9.mail.internet.Headers
import com.fsck.k9.mail.internet.MessageIdGenerator
import com.fsck.k9.mail.internet.MimeBodyPart
import com.fsck.k9.mail.internet.MimeHeader
import com.fsck.k9.mail.internet.MimeMessage
import com.fsck.k9.mail.internet.MimeMessageHelper
import com.fsck.k9.mail.internet.MimeMultipart
import com.fsck.k9.mail.internet.TextBody
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import org.apache.james.mime4j.codec.Base64OutputStream
import rs.ltt.android.mail.model.Email
import rs.ltt.android.mail.model.EmailAddress

/** Turns the email the compose screen builds into a MIME message for SMTP and IMAP APPEND. */
object MimeComposer {

    fun interface AttachmentFiles {
        fun fileFor(blobId: String): File
    }

    fun compose(email: Email, files: AttachmentFiles, messageId: String? = null): MimeMessage {
        val message = MimeMessage.create()
        email.from?.firstOrNull()?.let { message.setFrom(address(it)) }
        setAddressHeader(message, "To", addresses(email.to))
        setAddressHeader(message, "CC", addresses(email.cc))
        setAddressHeader(message, "BCC", addresses(email.bcc))
        email.replyTo?.takeIf { it.isNotEmpty() }?.let { message.setReplyTo(addresses(it)) }
        message.subject = email.subject ?: ""
        message.setSentDate(Date(), false)
        message.setMessageId(messageId ?: MessageIdGenerator.getInstance().generateMessageId(message))
        val inReplyTo = email.inReplyTo.orEmpty()
        if (inReplyTo.isNotEmpty()) {
            message.setInReplyTo(inReplyTo.joinToString(" ") { "<$it>" })
            val references = (email.references.orEmpty() + inReplyTo).distinct()
            message.setReferences(references.joinToString(" ") { "<$it>" })
        }
        email.userAgent?.let { message.setHeader("User-Agent", it) }

        val text = email.textBody?.firstOrNull()?.partId?.let { email.bodyValues?.get(it)?.value } ?: ""
        val textBody = TextBody(text)
        val attachments = email.attachments.orEmpty()
        if (attachments.isEmpty()) {
            message.setHeader(MimeHeader.HEADER_CONTENT_TYPE, "text/plain; charset=utf-8")
            MimeMessageHelper.setBody(message, textBody)
        } else {
            val multipart = MimeMultipart.newInstance()
            multipart.setSubType("mixed")
            multipart.addBodyPart(MimeBodyPart(textBody, "text/plain"))
            for (attachment in attachments) {
                val file = files.fileFor(attachment.blobId)
                val part = MimeBodyPart(FileBody(file))
                val type = attachment.type ?: "application/octet-stream"
                val name = attachment.name ?: file.name
                part.setHeader(MimeHeader.HEADER_CONTENT_TYPE, Headers.contentType(type, name))
                part.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, Headers.contentDisposition("attachment", name, file.length()))
                part.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, "base64")
                multipart.addBodyPart(part)
            }
            MimeMessageHelper.setBody(message, multipart)
        }
        if (email.keywords?.containsKey(rs.ltt.android.mail.model.Keyword.DRAFT) == true) {
            message.setFlag(Flag.DRAFT, true)
        }
        message.setFlag(Flag.SEEN, true)
        return message
    }

    private fun setAddressHeader(message: MimeMessage, name: String, addresses: Array<Address>) {
        if (addresses.isNotEmpty()) {
            message.setHeader(name, AddressHeaderBuilder.createHeaderValue(addresses))
        }
    }

    private fun address(address: EmailAddress) = Address(address.email, address.name)

    private fun addresses(addresses: Collection<EmailAddress>?): Array<Address> =
        addresses.orEmpty().filter { !it.email.isNullOrBlank() }.map { address(it) }.toTypedArray()

    /** Streams a file as base64 without loading it into memory. */
    private class FileBody(private val file: File) : Body {
        override fun getInputStream(): InputStream = FileInputStream(file)

        override fun setEncoding(encoding: String?) {
            // always base64
        }

        override fun writeTo(out: OutputStream) {
            val base64 = Base64OutputStream(object : OutputStream() {
                override fun write(b: Int) = out.write(b)

                override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            })
            FileInputStream(file).use { it.copyTo(base64) }
            base64.close()
        }
    }
}
