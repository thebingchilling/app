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

package rs.ltt.android.mail.util;

import java.util.Collection;
import rs.ltt.android.mail.model.Attachment;
import rs.ltt.android.mail.model.EmailBodyPart;

public final class AttachmentUtil {

    private AttachmentUtil() {}

    public static EmailBodyPart toEmailBodyPart(final Attachment attachment) {
        if (attachment instanceof EmailBodyPart) {
            return (EmailBodyPart) attachment;
        }
        return EmailBodyPart.builder()
                .blobId(attachment.getBlobId())
                .charset(attachment.getCharset())
                .type(attachment.getType())
                .name(attachment.getName())
                .size(attachment.getSize())
                .build();
    }

    public static EmailBodyPart toAnonymousEmailBodyPart(final Attachment attachment) {
        return EmailBodyPart.builder()
                .charset(attachment.getCharset())
                .type(attachment.getType())
                .name(attachment.getName())
                .size(attachment.getSize())
                .build();
    }

    /** Most providers (Gmail, Outlook) reject messages over 25 MB; attachments grow ~33% when encoded. */
    public static final long MAX_SIZE_ATTACHMENTS_PER_EMAIL = 18L * 1024 * 1024;

    public static void verifyAttachmentsDoNotExceedLimit(
            final Collection<? extends Attachment> attachments) {
        final long combinedAttachmentSize =
                attachments.stream().map(a -> Math.max(0, a.getSize())).reduce(0L, Long::sum);
        if (combinedAttachmentSize > MAX_SIZE_ATTACHMENTS_PER_EMAIL) {
            throw new CombinedAttachmentSizeExceedsLimitException(MAX_SIZE_ATTACHMENTS_PER_EMAIL);
        }
    }

    public static class CombinedAttachmentSizeExceedsLimitException extends RuntimeException {
        private final long limit;

        private CombinedAttachmentSizeExceedsLimitException(final long limit) {
            super(String.format("The combined size of all attachments exceeds limit of %d", limit));
            this.limit = limit;
        }

        public long getLimit() {
            return limit;
        }
    }
}
