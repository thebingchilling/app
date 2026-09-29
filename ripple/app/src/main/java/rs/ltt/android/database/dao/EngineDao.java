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

package rs.ltt.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import rs.ltt.android.entity.EmailBodyPartEntity;
import rs.ltt.android.entity.EmailBodyValueEntity;
import rs.ltt.android.entity.EmailEmailAddressEntity;
import rs.ltt.android.entity.EmailEntity;
import rs.ltt.android.entity.EmailInReplyToEntity;
import rs.ltt.android.entity.EmailKeywordEntity;
import rs.ltt.android.entity.EmailMailboxEntity;
import rs.ltt.android.entity.EmailMessageIdEntity;
import rs.ltt.android.entity.EmailReferenceEntity;
import rs.ltt.android.entity.EngineExtraEntity;
import rs.ltt.android.entity.FolderStateEntity;
import rs.ltt.android.entity.IdentityEmailAddressEntity;
import rs.ltt.android.entity.IdentityEntity;
import rs.ltt.android.entity.MailboxEntity;
import rs.ltt.android.entity.QueryEntity;
import rs.ltt.android.entity.QueryItemEntity;
import rs.ltt.android.entity.ThreadEntity;
import rs.ltt.android.entity.ThreadItemEntity;
import rs.ltt.android.mail.model.Email;
import rs.ltt.android.mail.model.Identity;
import rs.ltt.android.mail.model.Role;

/**
 * Writes done by Ripple's mail engine (IMAP/POP3 sync, local actions). The UI only reads these
 * tables through the other DAOs.
 */
@Dao
public abstract class EngineDao {

    // ---------------------------------------------------------------- extras

    @Query("select text from engine_extra where mailboxId=:mailboxId and name=:name")
    public abstract String getExtraString(String mailboxId, String name);

    @Query("select number from engine_extra where mailboxId=:mailboxId and name=:name")
    public abstract Long getExtraNumber(String mailboxId, String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(EngineExtraEntity entity);

    @Query("delete from engine_extra where mailboxId=:mailboxId and name=:name")
    abstract void deleteExtra(String mailboxId, String name);

    public void setExtraString(final String mailboxId, final String name, final String value) {
        if (value == null) {
            deleteExtra(mailboxId, name);
        } else {
            insert(new EngineExtraEntity(mailboxId, name, value, null));
        }
    }

    public void setExtraNumber(final String mailboxId, final String name, final long value) {
        insert(new EngineExtraEntity(mailboxId, name, null, value));
    }

    // ---------------------------------------------------------------- folders

    @Query("select id from mailbox")
    public abstract List<String> getMailboxIds();

    @Query("select * from mailbox where id=:id")
    public abstract MailboxEntity getMailbox(String id);

    @Query("select id from mailbox where role=:role limit 1")
    public abstract String getMailboxId(Role role);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(MailboxEntity mailbox);

    @Query("update mailbox set name=:name, role=:role where id=:id")
    public abstract void updateMailbox(String id, String name, Role role);

    @Query("update mailbox set role=null where role=:role and id!=:except")
    public abstract void clearRole(Role role, String except);

    @Query("update mailbox set role=:role where id=:id")
    public abstract void setRole(String id, Role role);

    @Query("delete from mailbox where id=:id")
    abstract void deleteMailboxRow(String id);

    @Query("delete from folder_state where mailboxId=:id")
    abstract void deleteFolderState(String id);

    @Query("delete from engine_extra where mailboxId=:id")
    abstract void deleteFolderExtras(String id);

    @Transaction
    public void deleteMailbox(final String id) {
        final List<String> emailIds = getEmailIds(id);
        deleteEmails(emailIds);
        deleteFolderState(id);
        deleteFolderExtras(id);
        deleteMailboxRow(id);
    }

    @Query("select * from folder_state where mailboxId=:mailboxId")
    public abstract FolderStateEntity getFolderState(String mailboxId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(FolderStateEntity folderState);

    // ---------------------------------------------------------------- emails in a folder

    @Query("select emailId from email_mailbox where mailboxId=:mailboxId")
    public abstract List<String> getEmailIds(String mailboxId);

    @Query(
            "select email.id as id,email.receivedAt as receivedAt from email join email_mailbox on"
                    + " email_mailbox.emailId=email.id where email_mailbox.mailboxId=:mailboxId")
    public abstract List<EmailIdAndTime> getEmailIdsAndTimes(String mailboxId);

    @Query(
            "select min(email.receivedAt) from email join email_mailbox on"
                    + " email_mailbox.emailId=email.id where email_mailbox.mailboxId=:mailboxId")
    public abstract Instant getOldestReceivedAt(String mailboxId);

    @Query("select exists(select 1 from email where id=:emailId)")
    public abstract boolean emailExists(String emailId);

    @Query("select keyword from email_keyword where emailId=:emailId")
    public abstract List<String> getKeywords(String emailId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insert(EmailKeywordEntity keyword);

    @Query("delete from email_keyword where emailId=:emailId and keyword=:keyword")
    abstract void deleteKeyword(String emailId, String keyword);

    public void setKeyword(final String emailId, final String keyword, final boolean value) {
        if (value) {
            insert(new EmailKeywordEntity(emailId, keyword));
        } else {
            deleteKeyword(emailId, keyword);
        }
    }

    @Query("select distinct threadId from email where id in (:emailIds)")
    abstract List<String> getThreadIds(Collection<String> emailIds);

    @Query("delete from email where id in (:emailIds)")
    abstract void deleteEmailRows(Collection<String> emailIds);

    @Query("delete from query_item where emailId in (:emailIds)")
    abstract void deleteQueryItems(Collection<String> emailIds);

    @Transaction
    public void deleteEmails(final Collection<String> emailIds) {
        if (emailIds.isEmpty()) {
            return;
        }
        for (final List<String> chunk : chunks(emailIds)) {
            final List<String> threadIds = getThreadIds(chunk);
            deleteEmailRows(chunk);
            deleteQueryItems(chunk);
            for (final String threadId : threadIds) {
                rebuildThread(threadId);
            }
        }
    }

    @Query(
            "delete from email where id in (select emailId from email_mailbox where"
                    + " mailboxId=:mailboxId)")
    abstract void deleteEmailsInMailboxRows(String mailboxId);

    @Transaction
    public void clearMailbox(final String mailboxId) {
        deleteEmails(getEmailIds(mailboxId));
    }

    @Query("select mailboxId from email_mailbox where emailId=:emailId limit 1")
    public abstract String getMailboxOf(String emailId);

    @Query("select * from email_body_part where blobId=:blobId limit 1")
    public abstract EmailBodyPartEntity getBodyPart(String blobId);

    @Query("select emailId from email_message_id where id in (:messageIds)")
    public abstract List<String> getEmailIdsByMessageIds(Collection<String> messageIds);

    // ---------------------------------------------------------------- saving emails

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(EmailEntity entity);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertEmailAddresses(List<EmailEmailAddressEntity> entities);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertInReplyTo(List<EmailInReplyToEntity> entities);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertMessageId(List<EmailMessageIdEntity> entities);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertReferences(List<EmailReferenceEntity> entities);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertMailboxes(List<EmailMailboxEntity> entities);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertKeywords(List<EmailKeywordEntity> entities);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insertEmailBodyValues(List<EmailBodyValueEntity> entities);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insertEmailBodyParts(List<EmailBodyPartEntity> entities);

    @Query("delete from email where id=:emailId")
    abstract void deleteEmailRow(String emailId);

    @Query("select threadId from email where id=:emailId")
    public abstract String getThreadId(String emailId);

    @Query(
            "select distinct email.threadId from email join email_message_id on"
                    + " email_message_id.emailId=email.id where email_message_id.id in (:messageIds)")
    abstract List<String> getThreadIdsByMessageIds(Collection<String> messageIds);

    @Query(
            "select distinct email.threadId from email where id in (select emailId from"
                    + " email_reference where reference in (:messageIds)) or id in (select emailId"
                    + " from email_in_reply_to where email_in_reply_to.id in (:messageIds))")
    abstract List<String> getThreadIdsReferencing(Collection<String> messageIds);

    @Query("update email set threadId=:to where threadId=:from")
    abstract void moveThread(String from, String to);

    @Query("update keyword_overwrite set threadId=:to where threadId=:from")
    abstract void moveKeywordOverwrites(String from, String to);

    @Query("update mailbox_overwrite set threadId=:to where threadId=:from")
    abstract void moveMailboxOverwrites(String from, String to);

    /**
     * Stores (or replaces) an email together with its addresses, keywords, mailbox membership and
     * bodies. The thread is found by Message-ID/In-Reply-To/References; threads that turn out to
     * belong together are merged.
     *
     * @param email an email with id and mailboxIds set. threadId is used as a fallback when no
     *     related email is known yet.
     * @return the thread id the email ended up in
     */
    @Transaction
    public String saveEmail(final Email email, final List<EmailBodyPartEntity> bodyParts,
            final List<EmailBodyValueEntity> bodyValues) {
        final String previousThreadId = getThreadId(email.getId());
        final Set<String> related = new HashSet<>();
        addAll(related, email.getMessageId());
        addAll(related, email.getInReplyTo());
        addAll(related, email.getReferences());
        final List<String> candidates = new ArrayList<>();
        if (!related.isEmpty()) {
            candidates.addAll(getThreadIdsByMessageIds(related));
            final Set<String> ownIds = new HashSet<>();
            addAll(ownIds, email.getMessageId());
            if (!ownIds.isEmpty()) {
                candidates.addAll(getThreadIdsReferencing(ownIds));
            }
        }
        final String threadId;
        if (previousThreadId != null) {
            threadId = previousThreadId;
        } else if (!candidates.isEmpty()) {
            threadId = candidates.get(0);
        } else {
            threadId = email.getThreadId();
        }
        deleteEmailRow(email.getId());
        final Email withThread = email.toBuilder().threadId(threadId).build();
        insert(EmailEntity.of(withThread));
        insertInReplyTo(EmailInReplyToEntity.of(withThread));
        insertMessageId(EmailMessageIdEntity.of(withThread));
        insertReferences(EmailReferenceEntity.of(withThread));
        insertEmailAddresses(EmailEmailAddressEntity.of(withThread));
        insertMailboxes(EmailMailboxEntity.of(withThread));
        insertKeywords(EmailKeywordEntity.of(withThread));
        insertEmailBodyParts(bodyParts);
        insertEmailBodyValues(bodyValues);
        for (final String other : new HashSet<>(candidates)) {
            if (other != null && !other.equals(threadId)) {
                moveThread(other, threadId);
                moveKeywordOverwrites(other, threadId);
                moveMailboxOverwrites(other, threadId);
                rebuildThread(other);
            }
        }
        rebuildThread(threadId);
        return threadId;
    }

    @Query("update email set preview=:preview where id=:emailId")
    public abstract void setPreview(String emailId, String preview);

    @Query("delete from email_body_value where emailId=:emailId")
    abstract void deleteBodyValues(String emailId);

    @Query("delete from email_body_part where emailId=:emailId")
    abstract void deleteBodyParts(String emailId);

    @Query("update email set hasAttachment=:hasAttachment where id=:emailId")
    abstract void setHasAttachment(String emailId, boolean hasAttachment);

    @Transaction
    public void replaceBodies(
            final String emailId,
            final List<EmailBodyPartEntity> bodyParts,
            final List<EmailBodyValueEntity> bodyValues,
            final String preview,
            final boolean hasAttachment) {
        deleteBodyParts(emailId);
        deleteBodyValues(emailId);
        insertEmailBodyParts(bodyParts);
        insertEmailBodyValues(bodyValues);
        setPreview(emailId, preview);
        setHasAttachment(emailId, hasAttachment);
    }

    // ---------------------------------------------------------------- moves

    @Query("update email set id=:newId where id=:oldId")
    abstract void rekeyEmailRow(String oldId, String newId);

    @Query("update email_mailbox set mailboxId=:to where emailId=:emailId and mailboxId=:from")
    abstract void updateEmailMailbox(String emailId, String from, String to);

    @Query("update thread_item set emailId=:newId where emailId=:oldId")
    abstract void rekeyThreadItem(String oldId, String newId);

    @Query("update query_item set emailId=:newId where emailId=:oldId")
    abstract void rekeyQueryItem(String oldId, String newId);

    /**
     * Re-labels an email that the server moved (MOVE/COPYUID) so we do not have to download it
     * again.
     */
    @Transaction
    public void moveEmail(
            final String oldId, final String newId, final String fromMailbox, final String toMailbox) {
        if (emailExists(newId)) {
            deleteEmails(List.of(oldId));
            return;
        }
        rekeyEmailRow(oldId, newId);
        updateEmailMailbox(newId, fromMailbox, toMailbox);
        rekeyThreadItem(oldId, newId);
        rekeyQueryItem(oldId, newId);
    }

    // ---------------------------------------------------------------- threads

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(ThreadEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insertThreadItems(List<ThreadItemEntity> entities);

    @Query("delete from thread_item where threadId=:threadId")
    abstract void deleteThreadItems(String threadId);

    @Query("delete from thread where threadId=:threadId")
    abstract void deleteThread(String threadId);

    @Query("select id from email where threadId=:threadId order by receivedAt asc, id asc")
    abstract List<String> getThreadEmailIds(String threadId);

    @Query("select exists(select 1 from thread where threadId=:threadId)")
    abstract boolean threadExists(String threadId);

    @Transaction
    public void rebuildThread(final String threadId) {
        if (threadId == null) {
            return;
        }
        final List<String> emailIds = getThreadEmailIds(threadId);
        if (emailIds.isEmpty()) {
            deleteThread(threadId);
            return;
        }
        if (threadExists(threadId)) {
            deleteThreadItems(threadId);
        } else {
            insert(new ThreadEntity(threadId));
        }
        final List<ThreadItemEntity> items = new ArrayList<>(emailIds.size());
        for (int i = 0; i < emailIds.size(); ++i) {
            items.add(new ThreadItemEntity(threadId, emailIds.get(i), i));
        }
        insertThreadItems(items);
    }

    // ---------------------------------------------------------------- counts

    @Query(
            "update mailbox set totalEmails=(select count(*) from email_mailbox where"
                + " email_mailbox.mailboxId=mailbox.id),unreadEmails=(select count(*) from"
                + " email_mailbox where email_mailbox.mailboxId=mailbox.id and"
                + " email_mailbox.emailId not in (select emailId from email_keyword where"
                + " keyword='$seen')),totalThreads=(select count(distinct email.threadId) from"
                + " email join email_mailbox on email_mailbox.emailId=email.id where"
                + " email_mailbox.mailboxId=mailbox.id),unreadThreads=(select count(distinct"
                + " email.threadId) from email join email_mailbox on email_mailbox.emailId=email.id"
                + " where email_mailbox.mailboxId=mailbox.id and email.id not in (select emailId"
                + " from email_keyword where keyword='$seen'))")
    public abstract void updateMailboxCounts();

    // ---------------------------------------------------------------- query results

    @Query("select id from `query` where queryString=:queryString")
    abstract Long getQueryId(String queryString);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract long insert(QueryEntity entity);

    @Query("delete from query_item where queryId=:queryId")
    abstract void deleteQueryItems(Long queryId);

    @Query("delete from query_item_overwrite where executed=1 and queryId=:queryId")
    abstract void deleteExecutedQueryOverwrites(Long queryId);

    @Insert
    abstract void insertQueryItems(List<QueryItemEntity> items);

    /** Replaces the cached result of a query without touching pending (unexecuted) overwrites. */
    @Transaction
    public void setQueryResult(final String queryString, final List<QueryItem> items) {
        Long queryId = getQueryId(queryString);
        if (queryId == null) {
            queryId = insert(new QueryEntity(queryString, "local", false, true));
        } else {
            deleteQueryItems(queryId);
            deleteExecutedQueryOverwrites(queryId);
        }
        final List<QueryItemEntity> entities = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); ++i) {
            final QueryItem item = items.get(i);
            entities.add(new QueryItemEntity(queryId, (long) i, item.emailId, item.threadId));
        }
        for (final List<QueryItemEntity> chunk : chunks(entities)) {
            insertQueryItems(chunk);
        }
    }

    @Query("select id as emailId, threadId, receivedAt from email")
    public abstract List<QueryCandidate> getAllCandidates();

    @Query(
            "select email.id as emailId, email.threadId as threadId, email.receivedAt as"
                    + " receivedAt from email join email_mailbox on email_mailbox.emailId=email.id"
                    + " where email_mailbox.mailboxId=:mailboxId")
    public abstract List<QueryCandidate> getCandidates(String mailboxId);

    @Query("select emailId from email_keyword where keyword=:keyword")
    public abstract List<String> getEmailIdsWithKeyword(String keyword);

    @Query("select emailId from email_mailbox where mailboxId in (:mailboxIds)")
    public abstract List<String> getEmailIdsInMailboxes(Collection<String> mailboxIds);

    @Query(
            "select emailId from email_email_address where type=:type and (email like :pattern or"
                    + " name like :pattern)")
    public abstract List<String> getEmailIdsByAddress(String type, String pattern);

    @Query(
            "select id from email where subject like :pattern or preview like :pattern or id in"
                    + " (select emailId from email_email_address where email like :pattern or name"
                    + " like :pattern) or id in (select emailId from email_body_value where value"
                    + " like :pattern)")
    public abstract List<String> getEmailIdsByText(String pattern);

    @Query("select id from email where subject like :pattern")
    public abstract List<String> getEmailIdsBySubject(String pattern);

    @Query("select emailId from email_body_value where value like :pattern")
    public abstract List<String> getEmailIdsByBody(String pattern);

    @Query("select id from email where hasAttachment=1")
    public abstract List<String> getEmailIdsWithAttachment();

    // ---------------------------------------------------------------- overwrites

    @Query("delete from keyword_overwrite where threadId in (:threadIds)")
    abstract void deleteKeywordOverwrites(Collection<String> threadIds);

    @Query("delete from mailbox_overwrite where threadId in (:threadIds)")
    abstract void deleteMailboxOverwrites(Collection<String> threadIds);

    @Query("update query_item_overwrite set executed=1 where threadId in (:threadIds)")
    abstract void markQueryOverwritesExecuted(Collection<String> threadIds);

    /** Called once a local action reached the server and the local tables reflect it. */
    @Transaction
    public void confirmOverwrites(final Collection<String> threadIds) {
        if (threadIds.isEmpty()) {
            return;
        }
        deleteKeywordOverwrites(threadIds);
        deleteMailboxOverwrites(threadIds);
        markQueryOverwritesExecuted(threadIds);
    }

    // ---------------------------------------------------------------- identities

    @Query("delete from identity")
    abstract void deleteIdentities();

    @Insert
    abstract void insert(IdentityEntity entity);

    @Insert
    abstract void insertIdentityAddresses(Collection<IdentityEmailAddressEntity> entities);

    @Transaction
    public void setIdentities(final Collection<Identity> identities) {
        deleteIdentities();
        for (final Identity identity : identities) {
            insert(IdentityEntity.of(identity));
            insertIdentityAddresses(IdentityEmailAddressEntity.of(identity));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void addAll(final Set<String> set, final List<String> values) {
        if (values != null) {
            for (final String value : values) {
                if (value != null && !value.isEmpty()) {
                    set.add(value);
                }
            }
        }
    }

    private static <T> List<List<T>> chunks(final Collection<T> items) {
        final List<List<T>> chunks = new ArrayList<>();
        List<T> current = new ArrayList<>();
        for (final T item : items) {
            current.add(item);
            if (current.size() == 500) {
                chunks.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    public static class EmailIdAndTime {
        public String id;
        public Instant receivedAt;
    }

    public static class QueryCandidate {
        public String emailId;
        public String threadId;
        public Instant receivedAt;
    }

    public static class QueryItem {
        public final String emailId;
        public final String threadId;

        public QueryItem(final String emailId, final String threadId) {
            this.emailId = emailId;
            this.threadId = threadId;
        }
    }
}
