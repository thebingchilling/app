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
package rs.ltt.android.mail.model.filter;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import java.time.Instant;
import androidx.annotation.NonNull;
import rs.ltt.android.mail.model.Email;
import rs.ltt.android.mail.util.QueryStringUtils;

public class EmailFilterCondition implements FilterCondition<Email> {
    private String inMailbox;
    private String[] inMailboxOtherThan;
    private Instant before;
    private Instant after;
    private Long minSize;
    private Long maxSize;
    private String allInThreadHaveKeyword;
    private String someInThreadHaveKeyword;
    private String noneInThreadHaveKeyword;
    private String hasKeyword;
    private String notKeyword;
    private Boolean hasAttachment;
    private String text;
    private String from;
    private String to;
    private String cc;
    private String bcc;
    private String subject;
    private String body;
    private String[] header;

    @Override
    public String toQueryString() {
        return QueryStringUtils.toQueryString(L3_DIVIDER, L4_DIVIDER, inMailbox, inMailboxOtherThan, before, after, minSize, maxSize, allInThreadHaveKeyword, someInThreadHaveKeyword, noneInThreadHaveKeyword, hasKeyword, notKeyword, hasAttachment, text, from, to, cc, bcc, subject, body, header);
    }

    @Override
    public int compareTo(@NonNull final Filter<Email> filter) {
        if (filter instanceof EmailFilterCondition) {
            final EmailFilterCondition other = (EmailFilterCondition) filter;
            return ComparisonChain.start().compare(Strings.nullToEmpty(inMailbox), Strings.nullToEmpty(other.inMailbox)).compare(inMailboxOtherThan, other.inMailboxOtherThan, QueryStringUtils.STRING_ARRAY_COMPARATOR).compare(before, other.before, QueryStringUtils.INSTANT_COMPARATOR).compare(after, other.after, QueryStringUtils.INSTANT_COMPARATOR).compare(minSize == null ? 0L : minSize, other.minSize == null ? 0L : other.minSize).compare(maxSize == null ? 0L : maxSize, other.maxSize == null ? 0L : other.maxSize).compare(Strings.nullToEmpty(allInThreadHaveKeyword), Strings.nullToEmpty(other.allInThreadHaveKeyword)).compare(Strings.nullToEmpty(someInThreadHaveKeyword), Strings.nullToEmpty(other.someInThreadHaveKeyword)).compare(Strings.nullToEmpty(noneInThreadHaveKeyword), Strings.nullToEmpty(other.noneInThreadHaveKeyword)).compare(Strings.nullToEmpty(hasKeyword), Strings.nullToEmpty(other.hasKeyword)).compare(Strings.nullToEmpty(notKeyword), Strings.nullToEmpty(other.notKeyword)).compareFalseFirst(nullToFalse(hasAttachment), nullToFalse(other.hasAttachment)).compare(Strings.nullToEmpty(text), Strings.nullToEmpty(other.text)).compare(Strings.nullToEmpty(from), Strings.nullToEmpty(other.from)).compare(Strings.nullToEmpty(cc), Strings.nullToEmpty(other.cc)).compare(Strings.nullToEmpty(bcc), Strings.nullToEmpty(other.bcc)).compare(Strings.nullToEmpty(subject), Strings.nullToEmpty(other.subject)).compare(Strings.nullToEmpty(body), Strings.nullToEmpty(other.body)).compare(header, other.header, QueryStringUtils.STRING_ARRAY_COMPARATOR).result();
        } else {
            return 1;
        }
    }

    private static boolean nullToFalse(Boolean b) {
        return b != null && b;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("inMailbox", inMailbox).add("inMailboxOtherThan", inMailboxOtherThan).add("minSize", minSize).add("maxSize", maxSize).add("allInThreadHaveKeyword", allInThreadHaveKeyword).add("someInThreadHaveKeyword", someInThreadHaveKeyword).add("noneInThreadHaveKeyword", noneInThreadHaveKeyword).add("hasKeyword", hasKeyword).add("notKeyword", notKeyword).add("hasAttachment", hasAttachment).add("text", text).add("from", from).add("to", to).add("cc", cc).add("bcc", bcc).add("subject", subject).add("body", body).add("header", header).omitNullValues().toString();
    }


    public static class EmailFilterConditionBuilder {
        @java.lang.SuppressWarnings("all")
        private String inMailbox;
        @java.lang.SuppressWarnings("all")
        private String[] inMailboxOtherThan;
        @java.lang.SuppressWarnings("all")
        private Instant before;
        @java.lang.SuppressWarnings("all")
        private Instant after;
        @java.lang.SuppressWarnings("all")
        private Long minSize;
        @java.lang.SuppressWarnings("all")
        private Long maxSize;
        @java.lang.SuppressWarnings("all")
        private String allInThreadHaveKeyword;
        @java.lang.SuppressWarnings("all")
        private String someInThreadHaveKeyword;
        @java.lang.SuppressWarnings("all")
        private String noneInThreadHaveKeyword;
        @java.lang.SuppressWarnings("all")
        private String hasKeyword;
        @java.lang.SuppressWarnings("all")
        private String notKeyword;
        @java.lang.SuppressWarnings("all")
        private Boolean hasAttachment;
        @java.lang.SuppressWarnings("all")
        private String text;
        @java.lang.SuppressWarnings("all")
        private String from;
        @java.lang.SuppressWarnings("all")
        private String to;
        @java.lang.SuppressWarnings("all")
        private String cc;
        @java.lang.SuppressWarnings("all")
        private String bcc;
        @java.lang.SuppressWarnings("all")
        private String subject;
        @java.lang.SuppressWarnings("all")
        private String body;
        @java.lang.SuppressWarnings("all")
        private String[] header;

        public EmailFilterConditionBuilder header(final String[] header) {
            Preconditions.checkArgument(header != null && (header.length == 1 || header.length == 2), "The header array MUST contain either one or two elements.");
            this.header = header;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        EmailFilterConditionBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder inMailbox(final String inMailbox) {
            this.inMailbox = inMailbox;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder inMailboxOtherThan(final String[] inMailboxOtherThan) {
            this.inMailboxOtherThan = inMailboxOtherThan;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder before(final Instant before) {
            this.before = before;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder after(final Instant after) {
            this.after = after;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder minSize(final Long minSize) {
            this.minSize = minSize;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder maxSize(final Long maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder allInThreadHaveKeyword(final String allInThreadHaveKeyword) {
            this.allInThreadHaveKeyword = allInThreadHaveKeyword;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder someInThreadHaveKeyword(final String someInThreadHaveKeyword) {
            this.someInThreadHaveKeyword = someInThreadHaveKeyword;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder noneInThreadHaveKeyword(final String noneInThreadHaveKeyword) {
            this.noneInThreadHaveKeyword = noneInThreadHaveKeyword;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder hasKeyword(final String hasKeyword) {
            this.hasKeyword = hasKeyword;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder notKeyword(final String notKeyword) {
            this.notKeyword = notKeyword;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder hasAttachment(final Boolean hasAttachment) {
            this.hasAttachment = hasAttachment;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder text(final String text) {
            this.text = text;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder from(final String from) {
            this.from = from;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder to(final String to) {
            this.to = to;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder cc(final String cc) {
            this.cc = cc;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder bcc(final String bcc) {
            this.bcc = bcc;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder subject(final String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition.EmailFilterConditionBuilder body(final String body) {
            this.body = body;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailFilterCondition build() {
            return new EmailFilterCondition(this.inMailbox, this.inMailboxOtherThan, this.before, this.after, this.minSize, this.maxSize, this.allInThreadHaveKeyword, this.someInThreadHaveKeyword, this.noneInThreadHaveKeyword, this.hasKeyword, this.notKeyword, this.hasAttachment, this.text, this.from, this.to, this.cc, this.bcc, this.subject, this.body, this.header);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "EmailFilterCondition.EmailFilterConditionBuilder(inMailbox=" + this.inMailbox + ", inMailboxOtherThan=" + java.util.Arrays.deepToString(this.inMailboxOtherThan) + ", before=" + this.before + ", after=" + this.after + ", minSize=" + this.minSize + ", maxSize=" + this.maxSize + ", allInThreadHaveKeyword=" + this.allInThreadHaveKeyword + ", someInThreadHaveKeyword=" + this.someInThreadHaveKeyword + ", noneInThreadHaveKeyword=" + this.noneInThreadHaveKeyword + ", hasKeyword=" + this.hasKeyword + ", notKeyword=" + this.notKeyword + ", hasAttachment=" + this.hasAttachment + ", text=" + this.text + ", from=" + this.from + ", to=" + this.to + ", cc=" + this.cc + ", bcc=" + this.bcc + ", subject=" + this.subject + ", body=" + this.body + ", header=" + java.util.Arrays.deepToString(this.header) + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    EmailFilterCondition(final String inMailbox, final String[] inMailboxOtherThan, final Instant before, final Instant after, final Long minSize, final Long maxSize, final String allInThreadHaveKeyword, final String someInThreadHaveKeyword, final String noneInThreadHaveKeyword, final String hasKeyword, final String notKeyword, final Boolean hasAttachment, final String text, final String from, final String to, final String cc, final String bcc, final String subject, final String body, final String[] header) {
        this.inMailbox = inMailbox;
        this.inMailboxOtherThan = inMailboxOtherThan;
        this.before = before;
        this.after = after;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.allInThreadHaveKeyword = allInThreadHaveKeyword;
        this.someInThreadHaveKeyword = someInThreadHaveKeyword;
        this.noneInThreadHaveKeyword = noneInThreadHaveKeyword;
        this.hasKeyword = hasKeyword;
        this.notKeyword = notKeyword;
        this.hasAttachment = hasAttachment;
        this.text = text;
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.subject = subject;
        this.body = body;
        this.header = header;
    }

    @java.lang.SuppressWarnings("all")
    public static EmailFilterCondition.EmailFilterConditionBuilder builder() {
        return new EmailFilterCondition.EmailFilterConditionBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getInMailbox() {
        return this.inMailbox;
    }

    @java.lang.SuppressWarnings("all")
    public String[] getInMailboxOtherThan() {
        return this.inMailboxOtherThan;
    }

    @java.lang.SuppressWarnings("all")
    public Instant getBefore() {
        return this.before;
    }

    @java.lang.SuppressWarnings("all")
    public Instant getAfter() {
        return this.after;
    }

    @java.lang.SuppressWarnings("all")
    public Long getMinSize() {
        return this.minSize;
    }

    @java.lang.SuppressWarnings("all")
    public Long getMaxSize() {
        return this.maxSize;
    }

    @java.lang.SuppressWarnings("all")
    public String getAllInThreadHaveKeyword() {
        return this.allInThreadHaveKeyword;
    }

    @java.lang.SuppressWarnings("all")
    public String getSomeInThreadHaveKeyword() {
        return this.someInThreadHaveKeyword;
    }

    @java.lang.SuppressWarnings("all")
    public String getNoneInThreadHaveKeyword() {
        return this.noneInThreadHaveKeyword;
    }

    @java.lang.SuppressWarnings("all")
    public String getHasKeyword() {
        return this.hasKeyword;
    }

    @java.lang.SuppressWarnings("all")
    public String getNotKeyword() {
        return this.notKeyword;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getHasAttachment() {
        return this.hasAttachment;
    }

    @java.lang.SuppressWarnings("all")
    public String getText() {
        return this.text;
    }

    @java.lang.SuppressWarnings("all")
    public String getFrom() {
        return this.from;
    }

    @java.lang.SuppressWarnings("all")
    public String getTo() {
        return this.to;
    }

    @java.lang.SuppressWarnings("all")
    public String getCc() {
        return this.cc;
    }

    @java.lang.SuppressWarnings("all")
    public String getBcc() {
        return this.bcc;
    }

    @java.lang.SuppressWarnings("all")
    public String getSubject() {
        return this.subject;
    }

    @java.lang.SuppressWarnings("all")
    public String getBody() {
        return this.body;
    }

    @java.lang.SuppressWarnings("all")
    public String[] getHeader() {
        return this.header;
    }
}
