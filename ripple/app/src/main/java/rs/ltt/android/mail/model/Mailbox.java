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

public class Mailbox extends AbstractIdentifiableEntity implements IdentifiableMailboxWithRoleAndName {
    private String name;
    private String parentId;
    private Role role;
    private Long sortOrder;
    private Long totalEmails;
    private Long unreadEmails;
    private Long totalThreads;
    private Long unreadThreads;
    private MailboxRights myRights;
    private Boolean isSubscribed;

    public Mailbox(String id, String name, String parentId, Role role, Long sortOrder, Long totalEmails, Long unreadEmails, Long totalThreads, Long unreadThreads, MailboxRights myRights, Boolean isSubscribed) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.role = role;
        this.sortOrder = sortOrder;
        this.totalEmails = totalEmails;
        this.unreadEmails = unreadEmails;
        this.totalThreads = totalThreads;
        this.unreadThreads = unreadThreads;
        this.myRights = myRights;
        this.isSubscribed = isSubscribed;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", name).add("parentId", parentId).add("role", role).add("sortOrder", sortOrder).add("totalEmails", totalEmails).add("unreadEmails", unreadEmails).add("totalThreads", totalThreads).add("unreadThreads", unreadThreads).add("myRights", myRights).add("isSubscribed", isSubscribed).add("id", id).toString();
    }


    @java.lang.SuppressWarnings("all")
    public static class MailboxBuilder {
        @java.lang.SuppressWarnings("all")
        private String id;
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private String parentId;
        @java.lang.SuppressWarnings("all")
        private Role role;
        @java.lang.SuppressWarnings("all")
        private Long sortOrder;
        @java.lang.SuppressWarnings("all")
        private Long totalEmails;
        @java.lang.SuppressWarnings("all")
        private Long unreadEmails;
        @java.lang.SuppressWarnings("all")
        private Long totalThreads;
        @java.lang.SuppressWarnings("all")
        private Long unreadThreads;
        @java.lang.SuppressWarnings("all")
        private MailboxRights myRights;
        @java.lang.SuppressWarnings("all")
        private Boolean isSubscribed;

        @java.lang.SuppressWarnings("all")
        MailboxBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder id(final String id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder parentId(final String parentId) {
            this.parentId = parentId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder role(final Role role) {
            this.role = role;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder sortOrder(final Long sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder totalEmails(final Long totalEmails) {
            this.totalEmails = totalEmails;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder unreadEmails(final Long unreadEmails) {
            this.unreadEmails = unreadEmails;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder totalThreads(final Long totalThreads) {
            this.totalThreads = totalThreads;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder unreadThreads(final Long unreadThreads) {
            this.unreadThreads = unreadThreads;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder myRights(final MailboxRights myRights) {
            this.myRights = myRights;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public Mailbox.MailboxBuilder isSubscribed(final Boolean isSubscribed) {
            this.isSubscribed = isSubscribed;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public Mailbox build() {
            return new Mailbox(this.id, this.name, this.parentId, this.role, this.sortOrder, this.totalEmails, this.unreadEmails, this.totalThreads, this.unreadThreads, this.myRights, this.isSubscribed);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "Mailbox.MailboxBuilder(id=" + this.id + ", name=" + this.name + ", parentId=" + this.parentId + ", role=" + this.role + ", sortOrder=" + this.sortOrder + ", totalEmails=" + this.totalEmails + ", unreadEmails=" + this.unreadEmails + ", totalThreads=" + this.totalThreads + ", unreadThreads=" + this.unreadThreads + ", myRights=" + this.myRights + ", isSubscribed=" + this.isSubscribed + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static Mailbox.MailboxBuilder builder() {
        return new Mailbox.MailboxBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getName() {
        return this.name;
    }

    @java.lang.SuppressWarnings("all")
    public String getParentId() {
        return this.parentId;
    }

    @java.lang.SuppressWarnings("all")
    public Role getRole() {
        return this.role;
    }

    @java.lang.SuppressWarnings("all")
    public Long getSortOrder() {
        return this.sortOrder;
    }

    @java.lang.SuppressWarnings("all")
    public Long getTotalEmails() {
        return this.totalEmails;
    }

    @java.lang.SuppressWarnings("all")
    public Long getUnreadEmails() {
        return this.unreadEmails;
    }

    @java.lang.SuppressWarnings("all")
    public Long getTotalThreads() {
        return this.totalThreads;
    }

    @java.lang.SuppressWarnings("all")
    public Long getUnreadThreads() {
        return this.unreadThreads;
    }

    @java.lang.SuppressWarnings("all")
    public MailboxRights getMyRights() {
        return this.myRights;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getIsSubscribed() {
        return this.isSubscribed;
    }
}
