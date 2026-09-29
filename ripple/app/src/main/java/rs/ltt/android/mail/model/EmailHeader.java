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

public class EmailHeader {
    private String name;
    private String value;

    @java.lang.SuppressWarnings("all")
    EmailHeader(final String name, final String value) {
        this.name = name;
        this.value = value;
    }


    @java.lang.SuppressWarnings("all")
    public static class EmailHeaderBuilder {
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private String value;

        @java.lang.SuppressWarnings("all")
        EmailHeaderBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailHeader.EmailHeaderBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailHeader.EmailHeaderBuilder value(final String value) {
            this.value = value;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailHeader build() {
            return new EmailHeader(this.name, this.value);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "EmailHeader.EmailHeaderBuilder(name=" + this.name + ", value=" + this.value + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static EmailHeader.EmailHeaderBuilder builder() {
        return new EmailHeader.EmailHeaderBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getName() {
        return this.name;
    }

    @java.lang.SuppressWarnings("all")
    public String getValue() {
        return this.value;
    }
}
