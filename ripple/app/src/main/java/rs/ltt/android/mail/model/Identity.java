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
import java.util.List;

public class Identity extends AbstractIdentifiableEntity implements IdentifiableIdentity {
    private String name;
    private String email;
    private List<EmailAddress> replyTo;
    private List<EmailAddress> bcc;
    private String textSignature;
    private String htmlSignature;
    private Boolean mayDelete;

    public Identity(final String id, String name, String email, List<EmailAddress> replyTo, List<EmailAddress> bcc, String textSignature, String htmlSignature, Boolean mayDelete) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.replyTo = replyTo;
        this.bcc = bcc;
        this.textSignature = textSignature;
        this.htmlSignature = htmlSignature;
        this.mayDelete = mayDelete;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", name).add("email", email).add("replyTo", replyTo).add("bcc", bcc).add("textSignature", textSignature).add("htmlSignature", htmlSignature).add("mayDelete", mayDelete).add("id", id).toString();
    }


    @java.lang.SuppressWarnings("all")
    public static class IdentityBuilder {
        @java.lang.SuppressWarnings("all")
        private String id;
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private String email;
        @java.lang.SuppressWarnings("all")
        private List<EmailAddress> replyTo;
        @java.lang.SuppressWarnings("all")
        private List<EmailAddress> bcc;
        @java.lang.SuppressWarnings("all")
        private String textSignature;
        @java.lang.SuppressWarnings("all")
        private String htmlSignature;
        @java.lang.SuppressWarnings("all")
        private Boolean mayDelete;

        @java.lang.SuppressWarnings("all")
        IdentityBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Identity.IdentityBuilder id(final String id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Identity.IdentityBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Identity.IdentityBuilder email(final String email) {
            this.email = email;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Identity.IdentityBuilder replyTo(final List<EmailAddress> replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Identity.IdentityBuilder bcc(final List<EmailAddress> bcc) {
            this.bcc = bcc;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Identity.IdentityBuilder textSignature(final String textSignature) {
            this.textSignature = textSignature;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Identity.IdentityBuilder htmlSignature(final String htmlSignature) {
            this.htmlSignature = htmlSignature;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Identity.IdentityBuilder mayDelete(final Boolean mayDelete) {
            this.mayDelete = mayDelete;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Identity build() {
            return new Identity(this.id, this.name, this.email, this.replyTo, this.bcc, this.textSignature, this.htmlSignature, this.mayDelete);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "Identity.IdentityBuilder(id=" + this.id + ", name=" + this.name + ", email=" + this.email + ", replyTo=" + this.replyTo + ", bcc=" + this.bcc + ", textSignature=" + this.textSignature + ", htmlSignature=" + this.htmlSignature + ", mayDelete=" + this.mayDelete + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static Identity.IdentityBuilder builder() {
        return new Identity.IdentityBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getName() {
        return this.name;
    }

    @java.lang.SuppressWarnings("all")
    public String getEmail() {
        return this.email;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailAddress> getReplyTo() {
        return this.replyTo;
    }

    @java.lang.SuppressWarnings("all")
    public List<EmailAddress> getBcc() {
        return this.bcc;
    }

    @java.lang.SuppressWarnings("all")
    public String getTextSignature() {
        return this.textSignature;
    }

    @java.lang.SuppressWarnings("all")
    public String getHtmlSignature() {
        return this.htmlSignature;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getMayDelete() {
        return this.mayDelete;
    }
}
