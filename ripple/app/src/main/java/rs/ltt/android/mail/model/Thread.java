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

import java.util.List;

public class Thread extends AbstractIdentifiableEntity {
    private List<String> emailIds;

    public Thread(final String id, final List<String> emailIds) {
        this.id = id;
        this.emailIds = emailIds;
    }


    @java.lang.SuppressWarnings("all")
    public static class ThreadBuilder {
        @java.lang.SuppressWarnings("all")
        private String id;
        @java.lang.SuppressWarnings("all")
        private java.util.ArrayList<String> emailIds;

        @java.lang.SuppressWarnings("all")
        ThreadBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Thread.ThreadBuilder id(final String id) {
            this.id = id;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Thread.ThreadBuilder emailId(final String emailId) {
            if (this.emailIds == null) this.emailIds = new java.util.ArrayList<String>();
            this.emailIds.add(emailId);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Thread.ThreadBuilder emailIds(final java.util.Collection<? extends String> emailIds) {
            if (emailIds == null) {
                throw new java.lang.NullPointerException("emailIds cannot be null");
            }
            if (this.emailIds == null) this.emailIds = new java.util.ArrayList<String>();
            this.emailIds.addAll(emailIds);
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Thread.ThreadBuilder clearEmailIds() {
            if (this.emailIds != null) this.emailIds.clear();
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Thread build() {
            java.util.List<String> emailIds;
            switch (this.emailIds == null ? 0 : this.emailIds.size()) {
            case 0: 
                emailIds = java.util.Collections.emptyList();
                break;
            case 1: 
                emailIds = java.util.Collections.singletonList(this.emailIds.get(0));
                break;
            default: 
                emailIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.emailIds));
            }
            return new Thread(this.id, emailIds);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "Thread.ThreadBuilder(id=" + this.id + ", emailIds=" + this.emailIds + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static Thread.ThreadBuilder builder() {
        return new Thread.ThreadBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public List<String> getEmailIds() {
        return this.emailIds;
    }
}
