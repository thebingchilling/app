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

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Server settings of a mail account: IMAP or POP3 for receiving, SMTP for sending. Password
 * accounts store the password; OAuth accounts (Google, Microsoft) store the AppAuth state that
 * holds the refresh token.
 */
@Entity(tableName = "credentials")
public class CredentialsEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    /** "imap" or "pop3" */
    public String incomingProtocol;

    public String incomingHost;
    public int incomingPort;

    /** name of {@link com.fsck.k9.mail.ConnectionSecurity} */
    public String incomingSecurity;

    public String smtpHost;
    public int smtpPort;
    public String smtpSecurity;

    /** name of {@link com.fsck.k9.mail.AuthType} */
    public String authType;

    public String username;
    public String password;

    /** "google", "microsoft" or null for password accounts */
    public String oauthProvider;

    /** Serialized net.openid.appauth.AuthState */
    public String oauthState;
}
