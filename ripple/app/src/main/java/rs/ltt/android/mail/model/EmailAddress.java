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
import com.google.common.base.Objects;

public class EmailAddress {
    private String name;
    private String email;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", name).add("email", email).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailAddress that = (EmailAddress) o;
        return Objects.equal(name, that.name) && Objects.equal(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, email);
    }

    @java.lang.SuppressWarnings("all")
    EmailAddress(final String name, final String email) {
        this.name = name;
        this.email = email;
    }


    @java.lang.SuppressWarnings("all")
    public static class EmailAddressBuilder {
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private String email;

        @java.lang.SuppressWarnings("all")
        EmailAddressBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailAddress.EmailAddressBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailAddress.EmailAddressBuilder email(final String email) {
            this.email = email;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailAddress build() {
            return new EmailAddress(this.name, this.email);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "EmailAddress.EmailAddressBuilder(name=" + this.name + ", email=" + this.email + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static EmailAddress.EmailAddressBuilder builder() {
        return new EmailAddress.EmailAddressBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getName() {
        return this.name;
    }

    @java.lang.SuppressWarnings("all")
    public String getEmail() {
        return this.email;
    }
}
