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

import rs.ltt.android.mail.model.EmailAddress;
import rs.ltt.android.mail.model.IdentifiableIdentity;

public class IdentityWithNameAndEmail implements IdentifiableIdentity, IdentifiableWithOwner {

    public Long accountId;
    public String id;
    public String name;
    public String email;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Long getAccountId() {
        return accountId;
    }

    public EmailAddress getEmailAddress() {
        return EmailAddress.builder().email(email).name(name).build();
    }
}
