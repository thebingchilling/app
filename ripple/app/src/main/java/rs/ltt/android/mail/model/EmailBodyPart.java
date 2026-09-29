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
import com.google.common.base.Optional;
import com.google.common.net.MediaType;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

public class EmailBodyPart implements Attachment {
    private String partId;
    private String blobId;
    private Long size;
    private List<EmailHeader> headers;
    private String name;
    private String type;
    private String charset;
    private String disposition;
    private String cid;
    private List<String> language;
    private String location;
    private List<EmailBodyPart> subParts;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("partId", partId).add("blobId", blobId).add("size", size).add("headers", headers).add("name", name).add("type", type).add("charset", charset).add("disposition", disposition).add("cid", cid).add("language", language).add("location", location).add("subParts", subParts).toString();
    }


    public static class EmailBodyPartBuilder {
        @java.lang.SuppressWarnings("all")
        private String partId;
        @java.lang.SuppressWarnings("all")
        private String blobId;
        @java.lang.SuppressWarnings("all")
        private Long size;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailHeader> headers;
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private String type;
        @java.lang.SuppressWarnings("all")
        private String charset;
        @java.lang.SuppressWarnings("all")
        private String disposition;
        @java.lang.SuppressWarnings("all")
        private String cid;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> language;
        @java.lang.SuppressWarnings("all")
        private String location;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<EmailBodyPart> subParts;

        public EmailBodyPartBuilder mediaType(MediaType mediaType) {
            this.type(mediaType.withoutParameters().toString());
            final Optional<Charset> optionalCharset = mediaType.charset();
            if (optionalCharset.isPresent()) {
                final Charset charset = optionalCharset.get();
                this.charset(charset.name().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        @java.lang.SuppressWarnings("all")
        EmailBodyPartBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder partId(final String partId) {
            this.partId = partId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder blobId(final String blobId) {
            this.blobId = blobId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder size(final Long size) {
            this.size = size;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder header(final EmailHeader header) {
            if (this.headers == null) this.headers = new java.util.ArrayList<EmailHeader>();
            this.headers.add(header);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder headers(final java.util.Collection<? extends EmailHeader> headers) {
            if (headers == null) {
                throw new java.lang.NullPointerException("headers cannot be null");
            }
            if (this.headers == null) this.headers = new java.util.ArrayList<EmailHeader>();
            this.headers.addAll(headers);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder clearHeaders() {
            if (this.headers != null) this.headers.clear();
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder type(final String type) {
            this.type = type;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder charset(final String charset) {
            this.charset = charset;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder disposition(final String disposition) {
            this.disposition = disposition;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder cid(final String cid) {
            this.cid = cid;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder language(final String language) {
            if (this.language == null) this.language = new java.util.ArrayList<String>();
            this.language.add(language);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder language(final java.util.Collection<? extends String> language) {
            if (language == null) {
                throw new java.lang.NullPointerException("language cannot be null");
            }
            if (this.language == null) this.language = new java.util.ArrayList<String>();
            this.language.addAll(language);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder clearLanguage() {
            if (this.language != null) this.language.clear();
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder location(final String location) {
            this.location = location;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder subPart(final EmailBodyPart subPart) {
            if (this.subParts == null) this.subParts = new java.util.ArrayList<EmailBodyPart>();
            this.subParts.add(subPart);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder subParts(final java.util.Collection<? extends EmailBodyPart> subParts) {
            if (subParts == null) {
                throw new java.lang.NullPointerException("subParts cannot be null");
            }
            if (this.subParts == null) this.subParts = new java.util.ArrayList<EmailBodyPart>();
            this.subParts.addAll(subParts);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart.EmailBodyPartBuilder clearSubParts() {
            if (this.subParts != null) this.subParts.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyPart build() {
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
            java.util.List<String> language;
            switch (this.language == null ? 0 : this.language.size()) {
            case 0: 
                language = java.util.Collections.emptyList();
                break;
            case 1: 
                language = java.util.Collections.singletonList(this.language.get(0));
                break;
            default: 
                language = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.language));
            }
            java.util.List<EmailBodyPart> subParts;
            switch (this.subParts == null ? 0 : this.subParts.size()) {
            case 0: 
                subParts = java.util.Collections.emptyList();
                break;
            case 1: 
                subParts = java.util.Collections.singletonList(this.subParts.get(0));
                break;
            default: 
                subParts = java.util.Collections.unmodifiableList(new java.util.ArrayList<EmailBodyPart>(this.subParts));
            }
            return new EmailBodyPart(this.partId, this.blobId, this.size, headers, this.name, this.type, this.charset, this.disposition, this.cid, language, this.location, subParts);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "EmailBodyPart.EmailBodyPartBuilder(partId=" + this.partId + ", blobId=" + this.blobId + ", size=" + this.size + ", headers=" + this.headers + ", name=" + this.name + ", type=" + this.type + ", charset=" + this.charset + ", disposition=" + this.disposition + ", cid=" + this.cid + ", language=" + this.language + ", location=" + this.location + ", subParts=" + this.subParts + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    EmailBodyPart(final String partId, final String blobId, final Long size, final List<EmailHeader> headers, final String name, final String type, final String charset, final String disposition, final String cid, final List<String> language, final String location, final List<EmailBodyPart> subParts) {
        this.partId = partId;
        this.blobId = blobId;
        this.size = size;
        this.headers = headers;
        this.name = name;
        this.type = type;
        this.charset = charset;
        this.disposition = disposition;
        this.cid = cid;
        this.language = language;
        this.location = location;
        this.subParts = subParts;
    }

    @java.lang.SuppressWarnings("all")
    public static EmailBodyPart.EmailBodyPartBuilder builder() {
        return new EmailBodyPart.EmailBodyPartBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getPartId() {
        return this.partId;
    }

    @java.lang.SuppressWarnings("all")
    public String getBlobId() {
        return this.blobId;
    }

    @java.lang.SuppressWarnings("all")
    public Long getSize() {
        return this.size;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailHeader> getHeaders() {
        return this.headers;
    }

    @java.lang.SuppressWarnings("all")
    public String getName() {
        return this.name;
    }

    @java.lang.SuppressWarnings("all")
    public String getType() {
        return this.type;
    }

    @java.lang.SuppressWarnings("all")
    public String getCharset() {
        return this.charset;
    }

    @java.lang.SuppressWarnings("all")
    public String getDisposition() {
        return this.disposition;
    }

    @java.lang.SuppressWarnings("all")
    public String getCid() {
        return this.cid;
    }

    @java.lang.SuppressWarnings("all")
    public List<String> getLanguage() {
        return this.language;
    }

    @java.lang.SuppressWarnings("all")
    public String getLocation() {
        return this.location;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailBodyPart> getSubParts() {
        return this.subParts;
    }
}
