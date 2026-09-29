/*
 * Copyright 2021 Daniel Gultsch
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

public class Upload implements BinaryData {
    private String accountId;
    private String blobId;
    private String type;
    private Long size;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("accountId", accountId).add("blobId", blobId).add("type", type).add("size", size).toString();
    }

    @java.lang.SuppressWarnings("all")
    Upload(final String accountId, final String blobId, final String type, final Long size) {
        this.accountId = accountId;
        this.blobId = blobId;
        this.type = type;
        this.size = size;
    }


    @java.lang.SuppressWarnings("all")
    public static class UploadBuilder {
        @java.lang.SuppressWarnings("all")
        private String accountId;
        @java.lang.SuppressWarnings("all")
        private String blobId;
        @java.lang.SuppressWarnings("all")
        private String type;
        @java.lang.SuppressWarnings("all")
        private Long size;

        @java.lang.SuppressWarnings("all")
        UploadBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Upload.UploadBuilder accountId(final String accountId) {
            this.accountId = accountId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Upload.UploadBuilder blobId(final String blobId) {
            this.blobId = blobId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Upload.UploadBuilder type(final String type) {
            this.type = type;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Upload.UploadBuilder size(final Long size) {
            this.size = size;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Upload build() {
            return new Upload(this.accountId, this.blobId, this.type, this.size);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "Upload.UploadBuilder(accountId=" + this.accountId + ", blobId=" + this.blobId + ", type=" + this.type + ", size=" + this.size + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static Upload.UploadBuilder builder() {
        return new Upload.UploadBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getAccountId() {
        return this.accountId;
    }

    @java.lang.SuppressWarnings("all")
    public String getBlobId() {
        return this.blobId;
    }

    @java.lang.SuppressWarnings("all")
    public String getType() {
        return this.type;
    }

    @java.lang.SuppressWarnings("all")
    public Long getSize() {
        return this.size;
    }
}
