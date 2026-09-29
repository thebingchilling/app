/*
 * Copyright 2019 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package rs.ltt.android.mail.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Email extends AbstractIdentifiableEntity implements IdentifiableEmailWithKeywords, IdentifiableEmailWithMailboxIds, IdentifiableEmailWithAddressesAndTime, IdentifiableEmailWithSubject {
    // Metadata
    private String blobId;
    private String threadId;
    private Map<String, Boolean> mailboxIds;
    private Map<String, Boolean> keywords;
    private Long size;
    private Instant receivedAt;
    // Header
    private List<EmailHeader> headers;
    // The following convenience properties are also specified for the Email object:
    private List<String> messageId;
    private List<String> inReplyTo;
    private List<String> references;
    private List<EmailAddress> sender;
    private List<EmailAddress> from;
    private List<EmailAddress> to;
    private List<EmailAddress> cc;
    private List<EmailAddress> bcc;
    private List<EmailAddress> replyTo;
    private String subject;
    private OffsetDateTime sentAt;
    // The following properties are not directly specified by JMAP but are provided by the library
    // for your convenience
    @SerializedName(Property.USER_AGENT)
    private String userAgent;
    @SerializedName(Property.AUTOCRYPT)
    private List<String> autocrypt;
    @SerializedName(Property.AUTOCRYPT_DRAFT_STATE)
    private String autocryptDraftState;
    @SerializedName(Property.AUTOCRYPT_SETUP_MESSAGE)
    private String autocryptSetupMessage;
    // body data
    private EmailBodyPart bodyStructure;
    private Map<String, EmailBodyValue> bodyValues;
    private List<EmailBodyPart> textBody;
    private List<EmailBodyPart> htmlBody;
    private List<EmailBodyPart> attachments;
    private Boolean hasAttachment;
    private String preview;

    public Email(String id, String blobId, String threadId, Map<String, Boolean> mailboxIds, Map<String, Boolean> keywords, Long size, Instant receivedAt, List<EmailHeader> headers, List<String> messageId, List<String> inReplyTo, List<String> references, List<EmailAddress> sender, List<EmailAddress> from, List<EmailAddress> to, List<EmailAddress> cc, List<EmailAddress> bcc, List<EmailAddress> replyTo, String subject, OffsetDateTime sentAt, String userAgent, List<String> autocrypt, String autocryptDraftState, String autocryptSetupMessage, EmailBodyPart bodyStructure, Map<String, EmailBodyValue> bodyValues, List<EmailBodyPart> textBody, List<EmailBodyPart> htmlBody, List<EmailBodyPart> attachments, Boolean hasAttachment, String preview) {
        this.id = id;
        this.blobId = blobId;
        this.threadId = threadId;
        this.mailboxIds = mailboxIds;
        this.keywords = keywords;
        this.size = size;
        this.receivedAt = receivedAt;
        this.headers = headers;
        this.messageId = messageId;
        this.inReplyTo = inReplyTo;
        this.references = references;
        this.sender = sender;
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.replyTo = replyTo;
        this.subject = subject;
        this.sentAt = sentAt;
        this.userAgent = userAgent;
        this.autocrypt = autocrypt;
        this.autocryptDraftState = autocryptDraftState;
        this.autocryptSetupMessage = autocryptSetupMessage;
        this.bodyStructure = bodyStructure;
        this.bodyValues = bodyValues;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.attachments = attachments;
        this.hasAttachment = hasAttachment;
        this.preview = preview;
    }

    public static Email of(String id) {
        return Email.builder().id(id).build();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("blobId", blobId).add("threadId", threadId).add("mailboxIds", mailboxIds).add("keywords", keywords).add("size", size).add("receivedAt", receivedAt).add("headers", headers).add("messageId", messageId).add("inReplyTo", inReplyTo).add("references", references).add("sender", sender).add("from", from).add("to", to).add("cc", cc).add("bcc", bcc).add("replyTo", replyTo).add("subject", subject).add("sentAt", sentAt).add("userAgent", userAgent).add("autocrypt", autocrypt).add("autocryptDraftState", autocryptDraftState).add("autocryptSetupMessage", autocryptSetupMessage).add("bodyStructure", bodyStructure).add("bodyValues", bodyValues).add("textBody", textBody).add("htmlBody", htmlBody).add("attachments", attachments).add("hasAttachment", hasAttachment).add("preview", preview).toString();
    }


    public static final class Property {
        public static final String ID = "id";
        public static final String BLOB_ID = "id";
        public static final String THREAD_ID = "threadId";
        public static final String MAILBOX_IDS = "mailboxIds";
        public static final String KEYWORDS = "keywords";
        public static final String SIZE = "size";
        public static final String RECEIVED_AT = "receivedAt";
        public static final String MESSAGE_ID = "messageId";
        public static final String IN_REPLY_TO = "inReplyTo";
        public static final String REFERENCES = "references";
        public static final String SENDER = "sender";
        public static final String FROM = "from";
        public static final String TO = "to";
        public static final String CC = "cc";
        public static final String BCC = "bcc";
        public static final String REPLY_TO = "replyTo";
        public static final String SUBJECT = "subject";
        public static final String SENT_AT = "sentAt";
        public static final String HAS_ATTACHMENT = "hasAttachment";
        public static final String PREVIEW = "preview";
        public static final String BODY_STRUCTURE = "bodyStructure";
        public static final String BODY_VALUES = "bodyValues";
        public static final String TEXT_BODY = "textBody";
        public static final String HTML_BODY = "htmlBody";
        public static final String ATTACHMENTS = "attachments";
        public static final String USER_AGENT = "header:User-Agent:asText";
        public static final String AUTOCRYPT = "header:Autocrypt:asText:all";
        public static final String AUTOCRYPT_DRAFT_STATE = "header:Autocrypt-Draft-State:asText";
        public static final String AUTOCRYPT_SETUP_MESSAGE = "header:Autocrypt-Setup-Message:asText";

        private Property() {
        }
    }


    public static final class Properties {
        public static final String[] THREAD_ID = new String[] {Property.THREAD_ID};
        public static final String[] MUTABLE = new String[] {Property.KEYWORDS, Property.MAILBOX_IDS};
        private static final String[] BASE = new String[] {Property.ID, Property.BLOB_ID, Property.THREAD_ID, Property.MAILBOX_IDS, Property.KEYWORDS, Property.SIZE, Property.RECEIVED_AT, Property.MESSAGE_ID, Property.IN_REPLY_TO, Property.REFERENCES, Property.SENDER, Property.FROM, Property.TO, Property.CC, Property.BCC, Property.REPLY_TO, Property.SUBJECT, Property.BODY_VALUES, Property.TEXT_BODY, Property.HTML_BODY, Property.ATTACHMENTS};
        public static final String[] RFC_8621_DEFAULT = new ImmutableList.Builder<String>().addAll(Arrays.asList(BASE)).add(Property.HAS_ATTACHMENT).add(Property.PREVIEW).build().toArray(new String[0]);
        public static final String[] LTTRS_DEFAULT = new ImmutableList.Builder<String>().addAll(Arrays.asList(BASE)).add(Property.SENT_AT).add(Property.BODY_STRUCTURE).add(Property.USER_AGENT).add(Property.AUTOCRYPT).add(Property.AUTOCRYPT_DRAFT_STATE).add(Property.AUTOCRYPT_SETUP_MESSAGE).build().toArray(new String[0]);

        private Properties() {
        }
    }


    @java.lang.SuppressWarnings("all")
    public static class EmailBuilder {
        @java.lang.SuppressWarnings("all")
        private String id;
        @java.lang.SuppressWarnings("all")
        private String blobId;
        @java.lang.SuppressWarnings("all")
        private String threadId;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> mailboxIds$key;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<Boolean> mailboxIds$value;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> keywords$key;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<Boolean> keywords$value;
        @java.lang.SuppressWarnings("all")
        private Long size;
        @java.lang.SuppressWarnings("all")
        private Instant receivedAt;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailHeader> headers;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> messageId;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> inReplyTo;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> references;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> sender;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> from;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> to;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> cc;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> bcc;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> replyTo;
        @java.lang.SuppressWarnings("all")
        private String subject;
        @java.lang.SuppressWarnings("all")
        private OffsetDateTime sentAt;
        @java.lang.SuppressWarnings("all")
        private String userAgent;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> autocrypt;
        @java.lang.SuppressWarnings("all")
        private String autocryptDraftState;
        @java.lang.SuppressWarnings("all")
        private String autocryptSetupMessage;
        @java.lang.SuppressWarnings("all")
        private EmailBodyPart bodyStructure;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> bodyValues$key;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailBodyValue> bodyValues$value;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailBodyPart> textBody;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailBodyPart> htmlBody;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailBodyPart> attachments;
        @java.lang.SuppressWarnings("all")
        private Boolean hasAttachment;
        @java.lang.SuppressWarnings("all")
        private String preview;

        @java.lang.SuppressWarnings("all")
        EmailBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder id(final String id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder blobId(final String blobId) {
            this.blobId = blobId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder threadId(final String threadId) {
            this.threadId = threadId;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder mailboxId(final String mailboxIdKey, final Boolean mailboxIdValue) {
            if (this.mailboxIds$key == null) {
                this.mailboxIds$key = new java.util.ArrayList<String>();
                this.mailboxIds$value = new java.util.ArrayList<Boolean>();
            }
            this.mailboxIds$key.add(mailboxIdKey);
            this.mailboxIds$value.add(mailboxIdValue);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder mailboxIds(final java.util.Map<? extends String, ? extends Boolean> mailboxIds) {
            if (mailboxIds == null) {
                throw new java.lang.NullPointerException("mailboxIds cannot be null");
            }
            if (this.mailboxIds$key == null) {
                this.mailboxIds$key = new java.util.ArrayList<String>();
                this.mailboxIds$value = new java.util.ArrayList<Boolean>();
            }
            for (final java.util.Map.Entry<? extends String, ? extends Boolean> $lombokEntry : mailboxIds.entrySet()) {
                this.mailboxIds$key.add($lombokEntry.getKey());
                this.mailboxIds$value.add($lombokEntry.getValue());
            }
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearMailboxIds() {
            if (this.mailboxIds$key != null) {
                this.mailboxIds$key.clear();
                this.mailboxIds$value.clear();
            }
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder keyword(final String keywordKey, final Boolean keywordValue) {
            if (this.keywords$key == null) {
                this.keywords$key = new java.util.ArrayList<String>();
                this.keywords$value = new java.util.ArrayList<Boolean>();
            }
            this.keywords$key.add(keywordKey);
            this.keywords$value.add(keywordValue);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder keywords(final java.util.Map<? extends String, ? extends Boolean> keywords) {
            if (keywords == null) {
                throw new java.lang.NullPointerException("keywords cannot be null");
            }
            if (this.keywords$key == null) {
                this.keywords$key = new java.util.ArrayList<String>();
                this.keywords$value = new java.util.ArrayList<Boolean>();
            }
            for (final java.util.Map.Entry<? extends String, ? extends Boolean> $lombokEntry : keywords.entrySet()) {
                this.keywords$key.add($lombokEntry.getKey());
                this.keywords$value.add($lombokEntry.getValue());
            }
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearKeywords() {
            if (this.keywords$key != null) {
                this.keywords$key.clear();
                this.keywords$value.clear();
            }
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder size(final Long size) {
            this.size = size;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder receivedAt(final Instant receivedAt) {
            this.receivedAt = receivedAt;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder header(final EmailHeader header) {
            if (this.headers == null) this.headers = new java.util.ArrayList<EmailHeader>();
            this.headers.add(header);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder headers(final java.util.Collection<? extends EmailHeader> headers) {
            if (headers == null) {
                throw new java.lang.NullPointerException("headers cannot be null");
            }
            if (this.headers == null) this.headers = new java.util.ArrayList<EmailHeader>();
            this.headers.addAll(headers);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearHeaders() {
            if (this.headers != null) this.headers.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder messageId(final String messageId) {
            if (this.messageId == null) this.messageId = new java.util.ArrayList<String>();
            this.messageId.add(messageId);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder messageId(final java.util.Collection<? extends String> messageId) {
            if (messageId == null) {
                throw new java.lang.NullPointerException("messageId cannot be null");
            }
            if (this.messageId == null) this.messageId = new java.util.ArrayList<String>();
            this.messageId.addAll(messageId);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearMessageId() {
            if (this.messageId != null) this.messageId.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder inReplyTo(final String inReplyTo) {
            if (this.inReplyTo == null) this.inReplyTo = new java.util.ArrayList<String>();
            this.inReplyTo.add(inReplyTo);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder inReplyTo(final java.util.Collection<? extends String> inReplyTo) {
            if (inReplyTo == null) {
                throw new java.lang.NullPointerException("inReplyTo cannot be null");
            }
            if (this.inReplyTo == null) this.inReplyTo = new java.util.ArrayList<String>();
            this.inReplyTo.addAll(inReplyTo);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearInReplyTo() {
            if (this.inReplyTo != null) this.inReplyTo.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder reference(final String reference) {
            if (this.references == null) this.references = new java.util.ArrayList<String>();
            this.references.add(reference);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder references(final java.util.Collection<? extends String> references) {
            if (references == null) {
                throw new java.lang.NullPointerException("references cannot be null");
            }
            if (this.references == null) this.references = new java.util.ArrayList<String>();
            this.references.addAll(references);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearReferences() {
            if (this.references != null) this.references.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder sender(final EmailAddress sender) {
            if (this.sender == null) this.sender = new java.util.ArrayList<EmailAddress>();
            this.sender.add(sender);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder sender(final java.util.Collection<? extends EmailAddress> sender) {
            if (sender == null) {
                throw new java.lang.NullPointerException("sender cannot be null");
            }
            if (this.sender == null) this.sender = new java.util.ArrayList<EmailAddress>();
            this.sender.addAll(sender);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearSender() {
            if (this.sender != null) this.sender.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder from(final EmailAddress from) {
            if (this.from == null) this.from = new java.util.ArrayList<EmailAddress>();
            this.from.add(from);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder from(final java.util.Collection<? extends EmailAddress> from) {
            if (from == null) {
                throw new java.lang.NullPointerException("from cannot be null");
            }
            if (this.from == null) this.from = new java.util.ArrayList<EmailAddress>();
            this.from.addAll(from);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearFrom() {
            if (this.from != null) this.from.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder to(final EmailAddress to) {
            if (this.to == null) this.to = new java.util.ArrayList<EmailAddress>();
            this.to.add(to);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder to(final java.util.Collection<? extends EmailAddress> to) {
            if (to == null) {
                throw new java.lang.NullPointerException("to cannot be null");
            }
            if (this.to == null) this.to = new java.util.ArrayList<EmailAddress>();
            this.to.addAll(to);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearTo() {
            if (this.to != null) this.to.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder cc(final EmailAddress cc) {
            if (this.cc == null) this.cc = new java.util.ArrayList<EmailAddress>();
            this.cc.add(cc);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder cc(final java.util.Collection<? extends EmailAddress> cc) {
            if (cc == null) {
                throw new java.lang.NullPointerException("cc cannot be null");
            }
            if (this.cc == null) this.cc = new java.util.ArrayList<EmailAddress>();
            this.cc.addAll(cc);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearCc() {
            if (this.cc != null) this.cc.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder bcc(final EmailAddress bcc) {
            if (this.bcc == null) this.bcc = new java.util.ArrayList<EmailAddress>();
            this.bcc.add(bcc);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder bcc(final java.util.Collection<? extends EmailAddress> bcc) {
            if (bcc == null) {
                throw new java.lang.NullPointerException("bcc cannot be null");
            }
            if (this.bcc == null) this.bcc = new java.util.ArrayList<EmailAddress>();
            this.bcc.addAll(bcc);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearBcc() {
            if (this.bcc != null) this.bcc.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder replyTo(final EmailAddress replyTo) {
            if (this.replyTo == null) this.replyTo = new java.util.ArrayList<EmailAddress>();
            this.replyTo.add(replyTo);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder replyTo(final java.util.Collection<? extends EmailAddress> replyTo) {
            if (replyTo == null) {
                throw new java.lang.NullPointerException("replyTo cannot be null");
            }
            if (this.replyTo == null) this.replyTo = new java.util.ArrayList<EmailAddress>();
            this.replyTo.addAll(replyTo);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearReplyTo() {
            if (this.replyTo != null) this.replyTo.clear();
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder subject(final String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder sentAt(final OffsetDateTime sentAt) {
            this.sentAt = sentAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder userAgent(final String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder autocrypt(final String autocrypt) {
            if (this.autocrypt == null) this.autocrypt = new java.util.ArrayList<String>();
            this.autocrypt.add(autocrypt);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder autocrypt(final java.util.Collection<? extends String> autocrypt) {
            if (autocrypt == null) {
                throw new java.lang.NullPointerException("autocrypt cannot be null");
            }
            if (this.autocrypt == null) this.autocrypt = new java.util.ArrayList<String>();
            this.autocrypt.addAll(autocrypt);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearAutocrypt() {
            if (this.autocrypt != null) this.autocrypt.clear();
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder autocryptDraftState(final String autocryptDraftState) {
            this.autocryptDraftState = autocryptDraftState;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder autocryptSetupMessage(final String autocryptSetupMessage) {
            this.autocryptSetupMessage = autocryptSetupMessage;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder bodyStructure(final EmailBodyPart bodyStructure) {
            this.bodyStructure = bodyStructure;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder bodyValue(final String bodyValueKey, final EmailBodyValue bodyValueValue) {
            if (this.bodyValues$key == null) {
                this.bodyValues$key = new java.util.ArrayList<String>();
                this.bodyValues$value = new java.util.ArrayList<EmailBodyValue>();
            }
            this.bodyValues$key.add(bodyValueKey);
            this.bodyValues$value.add(bodyValueValue);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder bodyValues(final java.util.Map<? extends String, ? extends EmailBodyValue> bodyValues) {
            if (bodyValues == null) {
                throw new java.lang.NullPointerException("bodyValues cannot be null");
            }
            if (this.bodyValues$key == null) {
                this.bodyValues$key = new java.util.ArrayList<String>();
                this.bodyValues$value = new java.util.ArrayList<EmailBodyValue>();
            }
            for (final java.util.Map.Entry<? extends String, ? extends EmailBodyValue> $lombokEntry : bodyValues.entrySet()) {
                this.bodyValues$key.add($lombokEntry.getKey());
                this.bodyValues$value.add($lombokEntry.getValue());
            }
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearBodyValues() {
            if (this.bodyValues$key != null) {
                this.bodyValues$key.clear();
                this.bodyValues$value.clear();
            }
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder textBody(final EmailBodyPart textBody) {
            if (this.textBody == null) this.textBody = new java.util.ArrayList<EmailBodyPart>();
            this.textBody.add(textBody);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder textBody(final java.util.Collection<? extends EmailBodyPart> textBody) {
            if (textBody == null) {
                throw new java.lang.NullPointerException("textBody cannot be null");
            }
            if (this.textBody == null) this.textBody = new java.util.ArrayList<EmailBodyPart>();
            this.textBody.addAll(textBody);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearTextBody() {
            if (this.textBody != null) this.textBody.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder htmlBody(final EmailBodyPart htmlBody) {
            if (this.htmlBody == null) this.htmlBody = new java.util.ArrayList<EmailBodyPart>();
            this.htmlBody.add(htmlBody);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder htmlBody(final java.util.Collection<? extends EmailBodyPart> htmlBody) {
            if (htmlBody == null) {
                throw new java.lang.NullPointerException("htmlBody cannot be null");
            }
            if (this.htmlBody == null) this.htmlBody = new java.util.ArrayList<EmailBodyPart>();
            this.htmlBody.addAll(htmlBody);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearHtmlBody() {
            if (this.htmlBody != null) this.htmlBody.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder attachment(final EmailBodyPart attachment) {
            if (this.attachments == null) this.attachments = new java.util.ArrayList<EmailBodyPart>();
            this.attachments.add(attachment);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder attachments(final java.util.Collection<? extends EmailBodyPart> attachments) {
            if (attachments == null) {
                throw new java.lang.NullPointerException("attachments cannot be null");
            }
            if (this.attachments == null) this.attachments = new java.util.ArrayList<EmailBodyPart>();
            this.attachments.addAll(attachments);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder clearAttachments() {
            if (this.attachments != null) this.attachments.clear();
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder hasAttachment(final Boolean hasAttachment) {
            this.hasAttachment = hasAttachment;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Email.EmailBuilder preview(final String preview) {
            this.preview = preview;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Email build() {
            java.util.Map<String, Boolean> mailboxIds;
            switch (this.mailboxIds$key == null ? 0 : this.mailboxIds$key.size()) {
            case 0: 
                mailboxIds = java.util.Collections.emptyMap();
                break;
            case 1: 
                mailboxIds = java.util.Collections.singletonMap(this.mailboxIds$key.get(0), this.mailboxIds$value.get(0));
                break;
            default: 
                mailboxIds = new java.util.LinkedHashMap<String, Boolean>(this.mailboxIds$key.size() < 1073741824 ? 1 + this.mailboxIds$key.size() + (this.mailboxIds$key.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
                for (int $i = 0; $i < this.mailboxIds$key.size(); $i++) mailboxIds.put(this.mailboxIds$key.get($i), (Boolean) this.mailboxIds$value.get($i));
                mailboxIds = java.util.Collections.unmodifiableMap(mailboxIds);
            }
            java.util.Map<String, Boolean> keywords;
            switch (this.keywords$key == null ? 0 : this.keywords$key.size()) {
            case 0: 
                keywords = java.util.Collections.emptyMap();
                break;
            case 1: 
                keywords = java.util.Collections.singletonMap(this.keywords$key.get(0), this.keywords$value.get(0));
                break;
            default: 
                keywords = new java.util.LinkedHashMap<String, Boolean>(this.keywords$key.size() < 1073741824 ? 1 + this.keywords$key.size() + (this.keywords$key.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
                for (int $i = 0; $i < this.keywords$key.size(); $i++) keywords.put(this.keywords$key.get($i), (Boolean) this.keywords$value.get($i));
                keywords = java.util.Collections.unmodifiableMap(keywords);
            }
            java.util.List<EmailHeader> headers;
            switch (this.headers == null ? 0 : this.headers.size()) {
            case 0: 
                headers = java.util.Collections.emptyList();
                break;
            case 1: 
                headers = java.util.Collections.singletonList(this.headers.get(0));
                break;
            default: 
                headers = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailHeader>(this.headers));
            }
            java.util.List<String> messageId;
            switch (this.messageId == null ? 0 : this.messageId.size()) {
            case 0: 
                messageId = java.util.Collections.emptyList();
                break;
            case 1: 
                messageId = java.util.Collections.singletonList(this.messageId.get(0));
                break;
            default: 
                messageId = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.messageId));
            }
            java.util.List<String> inReplyTo;
            switch (this.inReplyTo == null ? 0 : this.inReplyTo.size()) {
            case 0: 
                inReplyTo = java.util.Collections.emptyList();
                break;
            case 1: 
                inReplyTo = java.util.Collections.singletonList(this.inReplyTo.get(0));
                break;
            default: 
                inReplyTo = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.inReplyTo));
            }
            java.util.List<String> references;
            switch (this.references == null ? 0 : this.references.size()) {
            case 0: 
                references = java.util.Collections.emptyList();
                break;
            case 1: 
                references = java.util.Collections.singletonList(this.references.get(0));
                break;
            default: 
                references = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.references));
            }
            java.util.List<EmailAddress> sender;
            switch (this.sender == null ? 0 : this.sender.size()) {
            case 0: 
                sender = java.util.Collections.emptyList();
                break;
            case 1: 
                sender = java.util.Collections.singletonList(this.sender.get(0));
                break;
            default: 
                sender = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailAddress>(this.sender));
            }
            java.util.List<EmailAddress> from;
            switch (this.from == null ? 0 : this.from.size()) {
            case 0: 
                from = java.util.Collections.emptyList();
                break;
            case 1: 
                from = java.util.Collections.singletonList(this.from.get(0));
                break;
            default: 
                from = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailAddress>(this.from));
            }
            java.util.List<EmailAddress> to;
            switch (this.to == null ? 0 : this.to.size()) {
            case 0: 
                to = java.util.Collections.emptyList();
                break;
            case 1: 
                to = java.util.Collections.singletonList(this.to.get(0));
                break;
            default: 
                to = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailAddress>(this.to));
            }
            java.util.List<EmailAddress> cc;
            switch (this.cc == null ? 0 : this.cc.size()) {
            case 0: 
                cc = java.util.Collections.emptyList();
                break;
            case 1: 
                cc = java.util.Collections.singletonList(this.cc.get(0));
                break;
            default: 
                cc = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailAddress>(this.cc));
            }
            java.util.List<EmailAddress> bcc;
            switch (this.bcc == null ? 0 : this.bcc.size()) {
            case 0: 
                bcc = java.util.Collections.emptyList();
                break;
            case 1: 
                bcc = java.util.Collections.singletonList(this.bcc.get(0));
                break;
            default: 
                bcc = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailAddress>(this.bcc));
            }
            java.util.List<EmailAddress> replyTo;
            switch (this.replyTo == null ? 0 : this.replyTo.size()) {
            case 0: 
                replyTo = java.util.Collections.emptyList();
                break;
            case 1: 
                replyTo = java.util.Collections.singletonList(this.replyTo.get(0));
                break;
            default: 
                replyTo = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailAddress>(this.replyTo));
            }
            java.util.List<String> autocrypt;
            switch (this.autocrypt == null ? 0 : this.autocrypt.size()) {
            case 0: 
                autocrypt = java.util.Collections.emptyList();
                break;
            case 1: 
                autocrypt = java.util.Collections.singletonList(this.autocrypt.get(0));
                break;
            default: 
                autocrypt = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.autocrypt));
            }
            java.util.Map<String, EmailBodyValue> bodyValues;
            switch (this.bodyValues$key == null ? 0 : this.bodyValues$key.size()) {
            case 0: 
                bodyValues = java.util.Collections.emptyMap();
                break;
            case 1: 
                bodyValues = java.util.Collections.singletonMap(this.bodyValues$key.get(0), this.bodyValues$value.get(0));
                break;
            default: 
                bodyValues = new java.util.LinkedHashMap<String, EmailBodyValue>(this.bodyValues$key.size() < 1073741824 ? 1 + this.bodyValues$key.size() + (this.bodyValues$key.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
                for (int $i = 0; $i < this.bodyValues$key.size(); $i++) bodyValues.put(this.bodyValues$key.get($i), (EmailBodyValue) this.bodyValues$value.get($i));
                bodyValues = java.util.Collections.unmodifiableMap(bodyValues);
            }
            java.util.List<EmailBodyPart> textBody;
            switch (this.textBody == null ? 0 : this.textBody.size()) {
            case 0: 
                textBody = java.util.Collections.emptyList();
                break;
            case 1: 
                textBody = java.util.Collections.singletonList(this.textBody.get(0));
                break;
            default: 
                textBody = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailBodyPart>(this.textBody));
            }
            java.util.List<EmailBodyPart> htmlBody;
            switch (this.htmlBody == null ? 0 : this.htmlBody.size()) {
            case 0: 
                htmlBody = java.util.Collections.emptyList();
                break;
            case 1: 
                htmlBody = java.util.Collections.singletonList(this.htmlBody.get(0));
                break;
            default: 
                htmlBody = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailBodyPart>(this.htmlBody));
            }
            java.util.List<EmailBodyPart> attachments;
            switch (this.attachments == null ? 0 : this.attachments.size()) {
            case 0: 
                attachments = java.util.Collections.emptyList();
                break;
            case 1: 
                attachments = java.util.Collections.singletonList(this.attachments.get(0));
                break;
            default: 
                attachments = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailBodyPart>(this.attachments));
            }
            return new Email(this.id, this.blobId, this.threadId, mailboxIds, keywords, this.size, this.receivedAt, headers, messageId, inReplyTo, references, sender, from, to, cc, bcc, replyTo, this.subject, this.sentAt, this.userAgent, autocrypt, this.autocryptDraftState, this.autocryptSetupMessage, this.bodyStructure, bodyValues, textBody, htmlBody, attachments, this.hasAttachment, this.preview);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "Email.EmailBuilder(id=" + this.id + ", blobId=" + this.blobId + ", threadId=" + this.threadId + ", mailboxIds$key=" + this.mailboxIds$key + ", mailboxIds$value=" + this.mailboxIds$value + ", keywords$key=" + this.keywords$key + ", keywords$value=" + this.keywords$value + ", size=" + this.size + ", receivedAt=" + this.receivedAt + ", headers=" + this.headers + ", messageId=" + this.messageId + ", inReplyTo=" + this.inReplyTo + ", references=" + this.references + ", sender=" + this.sender + ", from=" + this.from + ", to=" + this.to + ", cc=" + this.cc + ", bcc=" + this.bcc + ", replyTo=" + this.replyTo + ", subject=" + this.subject + ", sentAt=" + this.sentAt + ", userAgent=" + this.userAgent + ", autocrypt=" + this.autocrypt + ", autocryptDraftState=" + this.autocryptDraftState + ", autocryptSetupMessage=" + this.autocryptSetupMessage + ", bodyStructure=" + this.bodyStructure + ", bodyValues$key=" + this.bodyValues$key + ", bodyValues$value=" + this.bodyValues$value + ", textBody=" + this.textBody + ", htmlBody=" + this.htmlBody + ", attachments=" + this.attachments + ", hasAttachment=" + this.hasAttachment + ", preview=" + this.preview + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static Email.EmailBuilder builder() {
        return new Email.EmailBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public Email.EmailBuilder toBuilder() {
        final Email.EmailBuilder builder = new Email.EmailBuilder().id(this.id).blobId(this.blobId).threadId(this.threadId).size(this.size).receivedAt(this.receivedAt).subject(this.subject).sentAt(this.sentAt).userAgent(this.userAgent).autocryptDraftState(this.autocryptDraftState).autocryptSetupMessage(this.autocryptSetupMessage).bodyStructure(this.bodyStructure).hasAttachment(this.hasAttachment).preview(this.preview);
        if (this.mailboxIds != null) builder.mailboxIds(this.mailboxIds);
        if (this.keywords != null) builder.keywords(this.keywords);
        if (this.headers != null) builder.headers(this.headers);
        if (this.messageId != null) builder.messageId(this.messageId);
        if (this.inReplyTo != null) builder.inReplyTo(this.inReplyTo);
        if (this.references != null) builder.references(this.references);
        if (this.sender != null) builder.sender(this.sender);
        if (this.from != null) builder.from(this.from);
        if (this.to != null) builder.to(this.to);
        if (this.cc != null) builder.cc(this.cc);
        if (this.bcc != null) builder.bcc(this.bcc);
        if (this.replyTo != null) builder.replyTo(this.replyTo);
        if (this.autocrypt != null) builder.autocrypt(this.autocrypt);
        if (this.bodyValues != null) builder.bodyValues(this.bodyValues);
        if (this.textBody != null) builder.textBody(this.textBody);
        if (this.htmlBody != null) builder.htmlBody(this.htmlBody);
        if (this.attachments != null) builder.attachments(this.attachments);
        return builder;
    }

    @java.lang.SuppressWarnings("all")
    public String getBlobId() {
        return this.blobId;
    }

    @java.lang.SuppressWarnings("all")
    public String getThreadId() {
        return this.threadId;
    }

    @java.lang.SuppressWarnings("all")
    public Map<String, Boolean> getMailboxIds() {
        return this.mailboxIds;
    }

    @java.lang.SuppressWarnings("all")
    public Map<String, Boolean> getKeywords() {
        return this.keywords;
    }

    @java.lang.SuppressWarnings("all")
    public Long getSize() {
        return this.size;
    }

    @java.lang.SuppressWarnings("all")
    public Instant getReceivedAt() {
        return this.receivedAt;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailHeader> getHeaders() {
        return this.headers;
    }

    @java.lang.SuppressWarnings("all")
    public List<String> getMessageId() {
        return this.messageId;
    }

    @java.lang.SuppressWarnings("all")
    public List<String> getInReplyTo() {
        return this.inReplyTo;
    }

    @java.lang.SuppressWarnings("all")
    public List<String> getReferences() {
        return this.references;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailAddress> getSender() {
        return this.sender;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailAddress> getFrom() {
        return this.from;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailAddress> getTo() {
        return this.to;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailAddress> getCc() {
        return this.cc;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailAddress> getBcc() {
        return this.bcc;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailAddress> getReplyTo() {
        return this.replyTo;
    }

    @java.lang.SuppressWarnings("all")
    public String getSubject() {
        return this.subject;
    }

    @java.lang.SuppressWarnings("all")
    public OffsetDateTime getSentAt() {
        return this.sentAt;
    }

    @java.lang.SuppressWarnings("all")
    public String getUserAgent() {
        return this.userAgent;
    }

    @java.lang.SuppressWarnings("all")
    public List<String> getAutocrypt() {
        return this.autocrypt;
    }

    @java.lang.SuppressWarnings("all")
    public String getAutocryptDraftState() {
        return this.autocryptDraftState;
    }

    @java.lang.SuppressWarnings("all")
    public String getAutocryptSetupMessage() {
        return this.autocryptSetupMessage;
    }

    @java.lang.SuppressWarnings("all")
    public EmailBodyPart getBodyStructure() {
        return this.bodyStructure;
    }

    @java.lang.SuppressWarnings("all")
    public Map<String, EmailBodyValue> getBodyValues() {
        return this.bodyValues;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailBodyPart> getTextBody() {
        return this.textBody;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailBodyPart> getHtmlBody() {
        return this.htmlBody;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailBodyPart> getAttachments() {
        return this.attachments;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getHasAttachment() {
        return this.hasAttachment;
    }

    @java.lang.SuppressWarnings("all")
    public String getPreview() {
        return this.preview;
    }
}
