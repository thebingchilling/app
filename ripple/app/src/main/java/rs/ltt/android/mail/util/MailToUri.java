/*
 * Copyright 2020 Daniel Gultsch
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
package rs.ltt.android.mail.util;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import rs.ltt.android.mail.model.EmailAddress;

public class MailToUri {
    private static final String MAIL_TO = "mailto";
    private static final String TO = "to";
    private static final String CC = "cc";
    private static final String BCC = "bcc";
    private static final String IN_REPLY_TO = "in-reply-to";
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";
    private final Collection<EmailAddress> to;
    private final Collection<EmailAddress> cc;
    private final Collection<EmailAddress> bcc;
    private final String inReplyTo;
    private final String subject;
    private final String body;

    @Nullable
    public static MailToUri parse(final String input) {
        try {
            return get(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @NonNull
    public static MailToUri get(final String input) throws IllegalArgumentException {
        return get(input, true);
    }

    @NonNull
    public static MailToUri get(final String input, final boolean stripNames) throws IllegalArgumentException {
        final int schemeDelimiter = input.indexOf(":");
        if (schemeDelimiter < 0) {
            throw new IllegalArgumentException("No scheme detected");
        }
        if (input.substring(0, schemeDelimiter).equals(MAIL_TO)) {
            final int queryDelimiter = input.length() > schemeDelimiter ? input.indexOf("?", schemeDelimiter + 1) : -1;
            final String to;
            final String query;
            if (queryDelimiter > 0) {
                to = input.substring(schemeDelimiter + 1, queryDelimiter);
                query = input.substring(queryDelimiter + 1);
            } else {
                to = input.substring(schemeDelimiter + 1);
                query = null;
            }
            final Map<String, String> parameters = parseQuery(query);
            final String cc = parameters.get(CC);
            final String bcc = parameters.get(BCC);
            final String inReplyTo = parameters.get(IN_REPLY_TO);
            final String subject = parameters.get(SUBJECT);
            final String body = parameters.get(BODY);
            final MailToUriBuilder mailToUriBuilder = MailToUri.builder();
            if (Strings.isNullOrEmpty(to)) {
                final String toParameter = parameters.get(TO);
                if (toParameter != null) {
                    mailToUriBuilder.to(parseEmailAddress(toParameter, stripNames));
                }
            } else {
                final Collection<EmailAddress> addresses = EmailAddressUtil.parse(decode(to));
                throwOnName(addresses);
                mailToUriBuilder.to(addresses);
            }
            if (cc != null) {
                mailToUriBuilder.cc(parseEmailAddress(cc, stripNames));
            }
            if (bcc != null) {
                mailToUriBuilder.bcc(parseEmailAddress(bcc, stripNames));
            }
            mailToUriBuilder.inReplyTo(inReplyTo);
            mailToUriBuilder.subject(subject);
            mailToUriBuilder.body(body);
            return mailToUriBuilder.build();
        }
        throw new IllegalArgumentException("Unknown scheme");
    }

    private static void throwOnName(final Collection<EmailAddress> addresses) throws IllegalArgumentException {
        for (final EmailAddress address : addresses) {
            if (Strings.isNullOrEmpty(address.getName())) {
                continue;
            }
            throw new IllegalArgumentException("Mailto address must not have a name");
        }
    }

    private static Map<String, String> parseQuery(final String query) {
        if (query == null) {
            return Collections.emptyMap();
        }
        final ImmutableMap.Builder<String, String> mapBuilder = new ImmutableMap.Builder<>();
        for (final String parameter : query.split("&")) {
            final String[] parts = parameter.split("=", 2);
            if (parts.length == 2) {
                mapBuilder.put(parts[0].toLowerCase(Locale.ENGLISH), decode(parts[1]));
            }
        }
        return mapBuilder.build();
    }

    private static String decode(String input) {
        try {
            return URLDecoder.decode(input, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Collection<EmailAddress> parseEmailAddress(final String address, final boolean stripNames) {
        return stripNames ? stripNames(EmailAddressUtil.parse(address)) : EmailAddressUtil.parse(address);
    }

    private static Collection<EmailAddress> stripNames(Collection<EmailAddress> emailAddresses) {
        return Collections2.transform(emailAddresses, new Function<EmailAddress, EmailAddress>() {
            @Nullable
            @Override
            public EmailAddress apply(@Nullable EmailAddress emailAddress) {
                if (emailAddress == null || Strings.isNullOrEmpty(emailAddress.getName())) {
                    return emailAddress;
                } else {
                    return EmailAddress.builder().email(emailAddress.getEmail()).build();
                }
            }
        });
    }

    @java.lang.SuppressWarnings("all")
    MailToUri(final Collection<EmailAddress> to, final Collection<EmailAddress> cc, final Collection<EmailAddress> bcc, final String inReplyTo, final String subject, final String body) {
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.inReplyTo = inReplyTo;
        this.subject = subject;
        this.body = body;
    }


    @java.lang.SuppressWarnings("all")
    public static class MailToUriBuilder {
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> to;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> cc;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailAddress> bcc;
        @java.lang.SuppressWarnings("all")
        private String inReplyTo;
        @java.lang.SuppressWarnings("all")
        private String subject;
        @java.lang.SuppressWarnings("all")
        private String body;

        @java.lang.SuppressWarnings("all")
        MailToUriBuilder() {
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder to(final EmailAddress to) {
            if (this.to == null) this.to = new java.util.ArrayList<EmailAddress>();
            this.to.add(to);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder to(final java.util.Collection<? extends EmailAddress> to) {
            if (to == null) {
                throw new java.lang.NullPointerException("to cannot be null");
            }
            if (this.to == null) this.to = new java.util.ArrayList<EmailAddress>();
            this.to.addAll(to);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder clearTo() {
            if (this.to != null) this.to.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder cc(final EmailAddress cc) {
            if (this.cc == null) this.cc = new java.util.ArrayList<EmailAddress>();
            this.cc.add(cc);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder cc(final java.util.Collection<? extends EmailAddress> cc) {
            if (cc == null) {
                throw new java.lang.NullPointerException("cc cannot be null");
            }
            if (this.cc == null) this.cc = new java.util.ArrayList<EmailAddress>();
            this.cc.addAll(cc);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder clearCc() {
            if (this.cc != null) this.cc.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder bcc(final EmailAddress bcc) {
            if (this.bcc == null) this.bcc = new java.util.ArrayList<EmailAddress>();
            this.bcc.add(bcc);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder bcc(final java.util.Collection<? extends EmailAddress> bcc) {
            if (bcc == null) {
                throw new java.lang.NullPointerException("bcc cannot be null");
            }
            if (this.bcc == null) this.bcc = new java.util.ArrayList<EmailAddress>();
            this.bcc.addAll(bcc);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder clearBcc() {
            if (this.bcc != null) this.bcc.clear();
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder inReplyTo(final String inReplyTo) {
            this.inReplyTo = inReplyTo;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder subject(final String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public MailToUri.MailToUriBuilder body(final String body) {
            this.body = body;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailToUri build() {
            java.util.Collection<EmailAddress> to;
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
            java.util.Collection<EmailAddress> cc;
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
            java.util.Collection<EmailAddress> bcc;
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
            return new MailToUri(to, cc, bcc, this.inReplyTo, this.subject, this.body);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "MailToUri.MailToUriBuilder(to=" + this.to + ", cc=" + this.cc + ", bcc=" + this.bcc + ", inReplyTo=" + this.inReplyTo + ", subject=" + this.subject + ", body=" + this.body + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static MailToUri.MailToUriBuilder builder() {
        return new MailToUri.MailToUriBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public Collection<EmailAddress> getTo() {
        return this.to;
    }

    @java.lang.SuppressWarnings("all")
    public Collection<EmailAddress> getCc() {
        return this.cc;
    }

    @java.lang.SuppressWarnings("all")
    public Collection<EmailAddress> getBcc() {
        return this.bcc;
    }

    @java.lang.SuppressWarnings("all")
    public String getInReplyTo() {
        return this.inReplyTo;
    }

    @java.lang.SuppressWarnings("all")
    public String getSubject() {
        return this.subject;
    }

    @java.lang.SuppressWarnings("all")
    public String getBody() {
        return this.body;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "MailToUri(to=" + this.getTo() + ", cc=" + this.getCc() + ", bcc=" + this.getBcc() + ", inReplyTo=" + this.getInReplyTo() + ", subject=" + this.getSubject() + ", body=" + this.getBody() + ")";
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof MailToUri)) return false;
        final MailToUri other = (MailToUri) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$to = this.getTo();
        final java.lang.Object other$to = other.getTo();
        if (this$to == null ? other$to != null : !this$to.equals(other$to)) return false;
        final java.lang.Object this$cc = this.getCc();
        final java.lang.Object other$cc = other.getCc();
        if (this$cc == null ? other$cc != null : !this$cc.equals(other$cc)) return false;
        final java.lang.Object this$bcc = this.getBcc();
        final java.lang.Object other$bcc = other.getBcc();
        if (this$bcc == null ? other$bcc != null : !this$bcc.equals(other$bcc)) return false;
        final java.lang.Object this$inReplyTo = this.getInReplyTo();
        final java.lang.Object other$inReplyTo = other.getInReplyTo();
        if (this$inReplyTo == null ? other$inReplyTo != null : !this$inReplyTo.equals(other$inReplyTo)) return false;
        final java.lang.Object this$subject = this.getSubject();
        final java.lang.Object other$subject = other.getSubject();
        if (this$subject == null ? other$subject != null : !this$subject.equals(other$subject)) return false;
        final java.lang.Object this$body = this.getBody();
        final java.lang.Object other$body = other.getBody();
        if (this$body == null ? other$body != null : !this$body.equals(other$body)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof MailToUri;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $to = this.getTo();
        result = result * PRIME + ($to == null ? 43 : $to.hashCode());
        final java.lang.Object $cc = this.getCc();
        result = result * PRIME + ($cc == null ? 43 : $cc.hashCode());
        final java.lang.Object $bcc = this.getBcc();
        result = result * PRIME + ($bcc == null ? 43 : $bcc.hashCode());
        final java.lang.Object $inReplyTo = this.getInReplyTo();
        result = result * PRIME + ($inReplyTo == null ? 43 : $inReplyTo.hashCode());
        final java.lang.Object $subject = this.getSubject();
        result = result * PRIME + ($subject == null ? 43 : $subject.hashCode());
        final java.lang.Object $body = this.getBody();
        result = result * PRIME + ($body == null ? 43 : $body.hashCode());
        return result;
    }
}
