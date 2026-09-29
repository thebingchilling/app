/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import android.os.Build
import android.text.Html
import com.fsck.k9.mail.Address
import net.thunderbird.core.common.mail.Flag
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.Multipart
import com.fsck.k9.mail.Part
import com.fsck.k9.mail.internet.MessageExtractor
import com.fsck.k9.mail.internet.MimeUtility
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import rs.ltt.android.entity.EmailBodyPartEntity
import rs.ltt.android.entity.EmailBodyPartType
import rs.ltt.android.entity.EmailBodyValueEntity
import rs.ltt.android.mail.model.Email
import rs.ltt.android.mail.model.EmailAddress
import rs.ltt.android.mail.model.Keyword
import rs.ltt.android.util.TextBodies

/** The pieces of a downloaded message in the shape of Ltt.rs' database. */
class MappedEmail(
    val email: Email,
    val bodyParts: List<EmailBodyPartEntity>,
    val bodyValues: List<EmailBodyValueEntity>,
    val attachmentBodies: Map<String, Part>,
)

object MessageMapper {

    const val TEXT_PART_ID = "text"
    private const val PREVIEW_TEXT_LIMIT = 256 * 1024L

    @JvmStatic
    fun keywordFor(flag: Flag): String? =
        when (flag) {
            Flag.SEEN -> Keyword.SEEN
            Flag.FLAGGED -> Keyword.FLAGGED
            Flag.ANSWERED -> Keyword.ANSWERED
            Flag.DRAFT -> Keyword.DRAFT
            Flag.FORWARDED -> Keyword.FORWARDED
            else -> null
        }

    @JvmStatic
    fun flagFor(keyword: String): Flag? =
        when (keyword) {
            Keyword.SEEN -> Flag.SEEN
            Keyword.FLAGGED -> Flag.FLAGGED
            Keyword.ANSWERED -> Flag.ANSWERED
            Keyword.DRAFT -> Flag.DRAFT
            Keyword.FORWARDED -> Flag.FORWARDED
            else -> null
        }

    fun map(mailboxId: String, message: Message): MappedEmail {
        val emailId = MailIds.emailId(mailboxId, message.uid)
        val messageIds = parseIds(message.getHeader("Message-ID"))
        val inReplyTo = parseIds(message.getHeader("In-Reply-To"))
        val references = parseIds(message.getHeader("References"))

        val walker = PartWalker(emailId)
        walker.walk(message, null)
        val text = walker.text()

        val bodyValues = listOf(
            EmailBodyValueEntity().apply {
                this.emailId = emailId
                partId = TEXT_PART_ID
                value = text.value
                isEncodingProblem = false
                isTruncated = text.truncated
            },
        )
        val bodyParts = ArrayList<EmailBodyPartEntity>()
        bodyParts.add(
            EmailBodyPartEntity().apply {
                this.emailId = emailId
                bodyPartType = EmailBodyPartType.TEXT_BODY
                position = 0
                partId = TEXT_PART_ID
                type = "text/plain"
                charset = "UTF-8"
            },
        )
        walker.attachments.forEachIndexed { index, attachment ->
            attachment.entity.position = index.toLong()
            bodyParts.add(attachment.entity)
        }

        val sent = message.sentDate?.toInstant()
        val received = message.internalDate?.toInstant() ?: sent ?: Instant.now()
        val keywords = HashMap<String, Boolean>()
        for (flag in message.flags) {
            keywordFor(flag)?.let { keywords[it] = true }
        }
        val seed = references.firstOrNull() ?: inReplyTo.firstOrNull() ?: messageIds.firstOrNull() ?: emailId
        val email = Email.builder()
            .id(emailId)
            .threadId(MailIds.threadId(seed))
            .mailboxIds(mapOf(mailboxId to true))
            .keywords(keywords)
            .size(message.size)
            .receivedAt(received)
            .sentAt(sent?.let { OffsetDateTime.ofInstant(it, ZoneId.systemDefault()) })
            .subject(message.subject)
            .messageId(messageIds)
            .inReplyTo(inReplyTo)
            .references(references)
            .from(addresses(message.from))
            .to(addresses(message.getRecipients(Message.RecipientType.TO)))
            .cc(addresses(message.getRecipients(Message.RecipientType.CC)))
            .bcc(addresses(message.getRecipients(Message.RecipientType.BCC)))
            .replyTo(addresses(message.replyTo))
            .sender(addresses(message.sender))
            .hasAttachment(walker.attachments.any { !it.inline })
            .preview(TextBodies.getPreview(bodyParts, bodyValues))
            .build()
        return MappedEmail(
            email,
            bodyParts,
            bodyValues,
            walker.attachments.filter { it.part.body != null }.associate { it.entity.blobId to it.part },
        )
    }

    /** Writes the decoded content of a MIME leaf part to a file. */
    @JvmStatic
    fun writePart(part: Part, target: File) {
        val body = part.body ?: throw IllegalStateException("Part has no body")
        MimeUtility.decodeBody(body).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun addresses(addresses: Array<Address>?): List<EmailAddress> =
        addresses?.map { EmailAddress.builder().name(it.personal).email(it.address).build() } ?: emptyList()

    /** Message-IDs without angle brackets, as JMAP (and Ltt.rs) store them. */
    @JvmStatic
    fun parseIds(headers: Array<String>?): List<String> {
        if (headers.isNullOrEmpty()) return emptyList()
        val result = ArrayList<String>()
        for (header in headers) {
            Regex("<([^<>\\s]+)>").findAll(header).forEach { result.add(it.groupValues[1]) }
            if (result.isEmpty() && header.isNotBlank() && !header.contains(' ')) {
                result.add(header.trim().removePrefix("<").removeSuffix(">"))
            }
        }
        return result.distinct()
    }

    private class Attachment(val entity: EmailBodyPartEntity, val part: Part, val inline: Boolean)

    private class Text(val value: String, val truncated: Boolean)

    private class PartWalker(private val emailId: String) {
        val plain = ArrayList<Part>()
        val html = ArrayList<Part>()
        val attachments = ArrayList<Attachment>()
        var missingText = false

        fun walk(part: Part, section: String?) {
            val body = part.body
            if (body is Multipart) {
                val isAlternative = part.isMimeType("multipart/alternative")
                if (isAlternative) {
                    // Prefer text/plain, fall back to text/html, like most mail clients.
                    val plainChild = body.bodyParts.indexOfFirst { it.isMimeType("text/plain") }
                    val htmlChild = body.bodyParts.indexOfFirst { it.isMimeType("text/html") }
                    val chosen = if (plainChild >= 0) plainChild else htmlChild
                    body.bodyParts.forEachIndexed { index, child ->
                        val childSection = if (section == null) "${index + 1}" else "$section.${index + 1}"
                        if (index == chosen || chosen < 0 || child.body is Multipart) {
                            if (index == chosen || chosen < 0) walk(child, childSection)
                        }
                    }
                    if (chosen < 0) {
                        // nothing we can display directly; descend into the first multipart
                        body.bodyParts.firstOrNull { it.body is Multipart }?.let { walk(it, section?.let { s -> "$s.1" } ?: "1") }
                    }
                    return
                }
                body.bodyParts.forEachIndexed { index, child ->
                    val childSection = if (section == null) "${index + 1}" else "$section.${index + 1}"
                    walk(child, childSection)
                }
                return
            }
            val partId = part.serverExtra ?: section ?: "1"
            val disposition = MimeUtility.getHeaderParameter(part.disposition, null)?.lowercase()
            val fileName = MimeUtility.getHeaderParameter(part.disposition, "filename")
                ?: MimeUtility.getHeaderParameter(part.contentType, "name")
            val isText = part.isMimeType("text/plain") || part.isMimeType("text/html")
            if (isText && disposition != "attachment" && fileName == null) {
                if (part.body == null) missingText = true
                if (part.isMimeType("text/plain")) plain.add(part) else html.add(part)
                return
            }
            val mimeType = part.mimeType ?: "application/octet-stream"
            val contentId = part.contentId
            val entity = EmailBodyPartEntity().apply {
                this.emailId = this@PartWalker.emailId
                bodyPartType = EmailBodyPartType.ATTACHMENT
                this.partId = partId
                blobId = MailIds.blobId(this@PartWalker.emailId, partId)
                size = partSize(part)
                name = fileName ?: defaultName(mimeType)
                type = mimeType
                charset = MimeUtility.getHeaderParameter(part.contentType, "charset")
                this.disposition = disposition
                cid = contentId
                encoding = MimeUtility.getHeaderParameter(
                    part.getHeader("Content-Transfer-Encoding").firstOrNull(),
                    null,
                )?.lowercase()
                downloadCount = 0
            }
            attachments.add(Attachment(entity, part, disposition == "inline" && contentId != null && fileName == null))
        }

        fun text(): Text {
            val builder = StringBuilder()
            val sources = if (plain.isNotEmpty()) plain else html
            for (part in sources) {
                val raw = MessageExtractor.getTextFromPart(part, PREVIEW_TEXT_LIMIT) ?: continue
                val value = if (part.isMimeType("text/html")) htmlToText(raw) else raw
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(value.trimEnd())
            }
            return Text(builder.toString(), missingText)
        }

        private fun partSize(part: Part): Long {
            val size = MimeUtility.getHeaderParameter(part.disposition, "size")?.toLongOrNull()
            if (size != null) return size
            val body = part.body
            if (body is com.fsck.k9.mail.internet.SizeAware) return body.size
            return 0
        }

        private fun defaultName(mimeType: String): String {
            val extension = mimeType.substringAfter('/', "bin").substringBefore(';').take(8)
            return "attachment.$extension"
        }
    }

    @JvmStatic
    fun htmlToText(html: String): String {
        val cleaned = html
            .replace(Regex("(?is)<(style|script|head|title)[^>]*>.*?</\\1\\s*>"), "")
            .replace(Regex("(?is)<!--.*?-->"), "")
        val spanned =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(cleaned, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(cleaned)
            }
        return spanned.toString()
            .replace('￼', ' ')
            .replace(Regex("[ \\t\\u00A0]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
