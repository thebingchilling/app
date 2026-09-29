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

public class EmailBodyValue {
    private String value;
    private Boolean isEncodingProblem;
    private Boolean isTruncated;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("value", value).add("isEncodingProblem", isEncodingProblem).add("isTruncated", isTruncated).toString();
    }

    @java.lang.SuppressWarnings("all")
    EmailBodyValue(final String value, final Boolean isEncodingProblem, final Boolean isTruncated) {
        this.value = value;
        this.isEncodingProblem = isEncodingProblem;
        this.isTruncated = isTruncated;
    }


    @java.lang.SuppressWarnings("all")
    public static class EmailBodyValueBuilder {
        @java.lang.SuppressWarnings("all")
        private String value;
        @java.lang.SuppressWarnings("all")
        private Boolean isEncodingProblem;
        @java.lang.SuppressWarnings("all")
        private Boolean isTruncated;

        @java.lang.SuppressWarnings("all")
        EmailBodyValueBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyValue.EmailBodyValueBuilder value(final String value) {
            this.value = value;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyValue.EmailBodyValueBuilder isEncodingProblem(final Boolean isEncodingProblem) {
            this.isEncodingProblem = isEncodingProblem;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public EmailBodyValue.EmailBodyValueBuilder isTruncated(final Boolean isTruncated) {
            this.isTruncated = isTruncated;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public EmailBodyValue build() {
            return new EmailBodyValue(this.value, this.isEncodingProblem, this.isTruncated);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "EmailBodyValue.EmailBodyValueBuilder(value=" + this.value + ", isEncodingProblem=" + this.isEncodingProblem + ", isTruncated=" + this.isTruncated + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static EmailBodyValue.EmailBodyValueBuilder builder() {
        return new EmailBodyValue.EmailBodyValueBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getValue() {
        return this.value;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getIsEncodingProblem() {
        return this.isEncodingProblem;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getIsTruncated() {
        return this.isTruncated;
    }
}
