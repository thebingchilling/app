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
 */

package rs.ltt.android.entity;

import com.google.common.base.Objects;

public class AccountWithCredentials {

    public Long id;
    public Long credentialsId;
    public String accountId;
    public String name;
    public String incomingProtocol;
    public String incomingHost;
    public int incomingPort;
    public String incomingSecurity;
    public String smtpHost;
    public int smtpPort;
    public String smtpSecurity;
    public String authType;
    public String username;
    public String password;
    public String oauthProvider;

    /**
     * @return The internal database ID
     */
    public Long getId() {
        return id;
    }

    /**
     * @return The email address of the account
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * @return The display name of the account (the email address)
     */
    public String getName() {
        return name;
    }

    public Credentials getCredentials() {
        return new Credentials(
                credentialsId,
                incomingProtocol,
                incomingHost,
                incomingPort,
                incomingSecurity,
                smtpHost,
                smtpPort,
                smtpSecurity,
                authType,
                username,
                password,
                oauthProvider);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountWithCredentials that = (AccountWithCredentials) o;
        return Objects.equal(id, that.id)
                && Objects.equal(accountId, that.accountId)
                && Objects.equal(name, that.name)
                && Objects.equal(getCredentials(), that.getCredentials());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, accountId, name, getCredentials());
    }

    public static class Credentials {
        private final Long id;
        public final String incomingProtocol;
        public final String incomingHost;
        public final int incomingPort;
        public final String incomingSecurity;
        public final String smtpHost;
        public final int smtpPort;
        public final String smtpSecurity;
        public final String authType;
        public final String username;
        public final String password;
        public final String oauthProvider;

        public Credentials(
                final Long id,
                final String incomingProtocol,
                final String incomingHost,
                final int incomingPort,
                final String incomingSecurity,
                final String smtpHost,
                final int smtpPort,
                final String smtpSecurity,
                final String authType,
                final String username,
                final String password,
                final String oauthProvider) {
            this.id = id;
            this.incomingProtocol = incomingProtocol;
            this.incomingHost = incomingHost;
            this.incomingPort = incomingPort;
            this.incomingSecurity = incomingSecurity;
            this.smtpHost = smtpHost;
            this.smtpPort = smtpPort;
            this.smtpSecurity = smtpSecurity;
            this.authType = authType;
            this.username = username;
            this.password = password;
            this.oauthProvider = oauthProvider;
        }

        public Long getId() {
            return this.id;
        }

        public boolean isPop3() {
            return "pop3".equals(incomingProtocol);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Credentials that = (Credentials) o;
            return incomingPort == that.incomingPort
                    && smtpPort == that.smtpPort
                    && Objects.equal(id, that.id)
                    && Objects.equal(incomingProtocol, that.incomingProtocol)
                    && Objects.equal(incomingHost, that.incomingHost)
                    && Objects.equal(incomingSecurity, that.incomingSecurity)
                    && Objects.equal(smtpHost, that.smtpHost)
                    && Objects.equal(smtpSecurity, that.smtpSecurity)
                    && Objects.equal(authType, that.authType)
                    && Objects.equal(username, that.username)
                    && Objects.equal(password, that.password)
                    && Objects.equal(oauthProvider, that.oauthProvider);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(
                    id,
                    incomingProtocol,
                    incomingHost,
                    incomingPort,
                    incomingSecurity,
                    smtpHost,
                    smtpPort,
                    smtpSecurity,
                    authType,
                    username,
                    password,
                    oauthProvider);
        }
    }
}
