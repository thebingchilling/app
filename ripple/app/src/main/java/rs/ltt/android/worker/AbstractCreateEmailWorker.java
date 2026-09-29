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

package rs.ltt.android.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;
import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.entity.IdentityWithNameAndEmail;
import rs.ltt.android.util.AttachmentSerializer;
import rs.ltt.android.util.UserAgent;
import rs.ltt.android.mail.model.Attachment;
import rs.ltt.android.mail.model.Email;
import rs.ltt.android.mail.model.EmailAddress;
import rs.ltt.android.mail.model.EmailBodyPart;
import rs.ltt.android.mail.model.EmailBodyValue;
import rs.ltt.android.engine.Status;
import rs.ltt.android.mail.util.AttachmentUtil;
import rs.ltt.android.mail.util.EmailAddressUtil;

public abstract class AbstractCreateEmailWorker extends AbstractMuaWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCreateEmailWorker.class);

    private static final String ENCRYPTED_KEY = "encrypted";
    private static final String IDENTITY_KEY = "identity";
    private static final String IN_REPLY_TO_KEY = "in_reply_to";
    private static final String TO_KEY = "to";
    private static final String CC_KEY = "cc";
    private static final String SUBJECT_KEY = "subject";
    private static final String BODY_KEY = "body";
    public static final String ATTACHMENTS_KEY = "attachments";

    private final String identity;
    private final List<String> inReplyTo;
    private final Collection<EmailAddress> to;
    private final Collection<EmailAddress> cc;
    private final String subject;
    private final String body;
    private final List<Attachment> attachments;
    private final boolean encrypted;

    AbstractCreateEmailWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = workerParams.getInputData();
        this.identity = data.getString(IDENTITY_KEY);
        final String to = data.getString(TO_KEY);
        this.to = to == null ? Collections.emptyList() : EmailAddressUtil.parse(to);
        final String cc = data.getString(CC_KEY);
        this.cc = cc == null ? Collections.emptyList() : EmailAddressUtil.parse(cc);
        this.subject = data.getString(SUBJECT_KEY);
        this.body = Strings.nullToEmpty(data.getString(BODY_KEY));
        final String[] inReplyTo = data.getStringArray(IN_REPLY_TO_KEY);
        this.inReplyTo = inReplyTo == null ? Collections.emptyList() : Arrays.asList(inReplyTo);
        final byte[] attachments = data.getByteArray(ATTACHMENTS_KEY);
        this.attachments = attachments == null ? null : AttachmentSerializer.of(attachments);
        this.encrypted = data.getBoolean(ENCRYPTED_KEY, false);
    }

    public static Data data(
            final Long account,
            final String identity,
            final Collection<String> inReplyTo,
            final Collection<EmailAddress> to,
            final Collection<EmailAddress> cc,
            final String subject,
            final String body,
            final Collection<? extends Attachment> attachments,
            final boolean encrypted) {
        return new Data.Builder()
                .putLong(ACCOUNT_KEY, account)
                .putString(IDENTITY_KEY, identity)
                .putStringArray(IN_REPLY_TO_KEY, inReplyTo.toArray(new String[0]))
                .putString(TO_KEY, EmailAddressUtil.toHeaderValue(to))
                .putString(CC_KEY, EmailAddressUtil.toHeaderValue(cc))
                .putString(SUBJECT_KEY, subject)
                .putString(BODY_KEY, body)
                .putByteArray(ATTACHMENTS_KEY, AttachmentSerializer.toByteArray(attachments))
                .putBoolean(ENCRYPTED_KEY, encrypted)
                .build();
    }

    protected Result refreshAndFetchThreadId(String emailId) {
        refresh();
        final String threadId = getDatabase().threadAndEmailDao().getThreadId(emailId);
        LOGGER.info("Email saved as draft with id {} in thread {}", emailId, threadId);
        final Data data =
                new Data.Builder()
                        .putString("emailId", emailId)
                        .putString("threadId", threadId)
                        .build();
        return Result.success(data);
    }

    private void refresh() {
        try {
            final Status status = getMua().refresh().get();
            if (status != Status.UPDATED) {
                LOGGER.debug("Unexpected status {} after refresh", status);
            }
        } catch (Exception e) {
            LOGGER.warn("Refresh after email creation failed", e);
        }
    }

    protected Email buildCleartextEmail(final IdentityWithNameAndEmail identity) {
        final EmailBodyValue emailBodyValue = EmailBodyValue.builder().value(this.body).build();
        final String partId = "0";
        final EmailBodyPart emailBodyPart =
                EmailBodyPart.builder().partId(partId).type("text/plain").build();
        final List<EmailBodyPart> attachments =
                this.attachments == null
                        ? java.util.Collections.emptyList()
                        : this.attachments.stream()
                        .map(AttachmentUtil::toEmailBodyPart)
                        .collect(Collectors.toList());
        return getEmailBuilder(identity)
                .bodyValue(partId, emailBodyValue)
                .textBody(emailBodyPart)
                .attachments(attachments)
                .build();
    }

    private Email.EmailBuilder getEmailBuilder(final IdentityWithNameAndEmail identity) {
        return Email.builder()
                .from(identity.getEmailAddress())
                .inReplyTo(this.inReplyTo)
                .to(this.to)
                .cc(this.cc)
                .userAgent(UserAgent.get(getApplicationContext()))
                .subject(this.subject);
    }

    protected Email buildEmail(final IdentityWithNameAndEmail identity) {
        return buildCleartextEmail(identity);
    }

    IdentityWithNameAndEmail getIdentity() {
        return getDatabase().identityDao().get(this.account, this.identity);
    }
}
