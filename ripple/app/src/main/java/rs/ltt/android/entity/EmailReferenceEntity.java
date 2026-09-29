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

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import rs.ltt.android.mail.model.Email;

/** Message-IDs from the References header; used to group emails into threads. */
@Entity(
        tableName = "email_reference",
        primaryKeys = {"emailId", "reference"},
        indices = {@Index(value = "reference")},
        foreignKeys =
                @ForeignKey(
                        entity = EmailEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"emailId"},
                        onDelete = ForeignKey.CASCADE,
                        onUpdate = ForeignKey.CASCADE))
public class EmailReferenceEntity {

    @NonNull public String emailId;
    @NonNull public String reference;

    public EmailReferenceEntity(@NonNull String emailId, @NonNull String reference) {
        this.emailId = emailId;
        this.reference = reference;
    }

    public static List<EmailReferenceEntity> of(Email email) {
        final List<String> references = email.getReferences();
        if (references == null) {
            return Collections.emptyList();
        }
        final ImmutableList.Builder<EmailReferenceEntity> builder = new ImmutableList.Builder<>();
        for (final String reference : references) {
            builder.add(new EmailReferenceEntity(email.getId(), reference));
        }
        return builder.build();
    }
}
