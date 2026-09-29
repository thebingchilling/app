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

import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import androidx.annotation.NonNull;
import rs.ltt.android.mail.model.Mailbox;
import rs.ltt.android.mail.model.Role;
import rs.ltt.android.mail.util.QueryStringUtils;

public class MailboxFilterCondition implements FilterCondition<Mailbox> {
    private String parentId;
    private String name;
    private Role role;
    private Boolean hasAnyRole;
    private Boolean isSubscribed;

    @Override
    public int compareTo(@NonNull Filter<Mailbox> filter) {
        if (filter instanceof MailboxFilterCondition) {
            final MailboxFilterCondition other = (MailboxFilterCondition) filter;
            return ComparisonChain.start().compare(Strings.nullToEmpty(parentId), Strings.nullToEmpty(other.parentId)).compare(Strings.nullToEmpty(name), Strings.nullToEmpty(other.name)).compare(QueryStringUtils.nullToEmpty(role), QueryStringUtils.nullToEmpty(other.role)).compare(hasAnyRole, other.hasAnyRole, QueryStringUtils.BOOLEAN_COMPARATOR).compare(isSubscribed, other.isSubscribed, QueryStringUtils.BOOLEAN_COMPARATOR).result();
        } else {
            return 1;
        }
    }

    @Override
    public String toQueryString() {
        return QueryStringUtils.toQueryString(L3_DIVIDER, L4_DIVIDER, parentId, name, role, hasAnyRole, isSubscribed);
    }

    @java.lang.SuppressWarnings("all")
    MailboxFilterCondition(final String parentId, final String name, final Role role, final Boolean hasAnyRole, final Boolean isSubscribed) {
        this.parentId = parentId;
        this.name = name;
        this.role = role;
        this.hasAnyRole = hasAnyRole;
        this.isSubscribed = isSubscribed;
    }


    @java.lang.SuppressWarnings("all")
    public static class MailboxFilterConditionBuilder {
        @java.lang.SuppressWarnings("all")
        private String parentId;
        @java.lang.SuppressWarnings("all")
        private String name;
        @java.lang.SuppressWarnings("all")
        private Role role;
        @java.lang.SuppressWarnings("all")
        private Boolean hasAnyRole;
        @java.lang.SuppressWarnings("all")
        private Boolean isSubscribed;

        @java.lang.SuppressWarnings("all")
        MailboxFilterConditionBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public MailboxFilterCondition.MailboxFilterConditionBuilder parentId(final String parentId) {
            this.parentId = parentId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public MailboxFilterCondition.MailboxFilterConditionBuilder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public MailboxFilterCondition.MailboxFilterConditionBuilder role(final Role role) {
            this.role = role;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public MailboxFilterCondition.MailboxFilterConditionBuilder hasAnyRole(final Boolean hasAnyRole) {
            this.hasAnyRole = hasAnyRole;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public MailboxFilterCondition.MailboxFilterConditionBuilder isSubscribed(final Boolean isSubscribed) {
            this.isSubscribed = isSubscribed;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public MailboxFilterCondition build() {
            return new MailboxFilterCondition(this.parentId, this.name, this.role, this.hasAnyRole, this.isSubscribed);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "MailboxFilterCondition.MailboxFilterConditionBuilder(parentId=" + this.parentId + ", name=" + this.name + ", role=" + this.role + ", hasAnyRole=" + this.hasAnyRole + ", isSubscribed=" + this.isSubscribed + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static MailboxFilterCondition.MailboxFilterConditionBuilder builder() {
        return new MailboxFilterCondition.MailboxFilterConditionBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getParentId() {
        return this.parentId;
    }

    @java.lang.SuppressWarnings("all")
    public String getName() {
        return this.name;
    }

    @java.lang.SuppressWarnings("all")
    public Role getRole() {
        return this.role;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getHasAnyRole() {
        return this.hasAnyRole;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getIsSubscribed() {
        return this.isSubscribed;
    }
}
