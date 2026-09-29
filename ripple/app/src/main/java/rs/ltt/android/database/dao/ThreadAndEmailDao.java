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

import androidx.lifecycle.LiveData;
import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.entity.DownloadableBlob;
import rs.ltt.android.entity.EmailWithBodies;
import rs.ltt.android.entity.EmailWithBodiesAndSubject;
import rs.ltt.android.entity.EmailWithEncryptionStatus;
import rs.ltt.android.entity.EmailWithKeywords;
import rs.ltt.android.entity.EmailWithMailboxes;
import rs.ltt.android.entity.EmailWithReferences;
import rs.ltt.android.entity.EncryptionStatus;
import rs.ltt.android.entity.ExpandedPosition;
import rs.ltt.android.entity.ThreadHeader;

@Dao
public abstract class ThreadAndEmailDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadAndEmailDao.class);

    @Query(
            "update email_body_part set downloadCount = downloadCount + 1 where emailId=:emailId"
                    + " and blobId=:blobId")
    public abstract void incrementEmailBodyPartDownloadCount(
            final String emailId, final String blobId);

    @Query("select threadId from email where id=:emailId")
    public abstract String getThreadId(String emailId);

    @Query("select threadId from email where id=:emailId")
    public abstract LiveData<String> getThreadIdLiveData(String emailId);

    @Transaction
    @Query("select id from email where threadId=:threadId")
    public abstract List<EmailWithKeywords> getEmailsWithKeywords(String threadId);

    @Transaction
    @Query("select id from email where threadId=:threadId")
    public abstract List<EmailWithMailboxes> getEmailsWithMailboxes(String threadId);

    @Transaction
    @Query("select id from email where threadId in (:threadIds)")
    public abstract List<EmailWithMailboxes> getEmailsWithMailboxes(Collection<String> threadIds);

    @Transaction
    @Query("select id from email where id=:id")
    public abstract EmailWithKeywords getEmailWithKeyword(final String id);

    @Query("select id,encryptionStatus,encryptedBlobId from email where id=:id")
    public abstract EmailWithEncryptionStatus getEmailWithEncryptionStatus(final String id);

    @Query(
            "select id,encryptionStatus,encryptedBlobId from email where threadId=:threadId and"
                    + " encryptionStatus=:encryptionStatus")
    public abstract LiveData<List<EmailWithEncryptionStatus>> getEmailsWithEncryptionStatus(
            final String threadId, final EncryptionStatus encryptionStatus);

    @Query(
            "select blobId,type,name,size from email_body_part where emailId=:emailId and"
                    + " blobId=:blobId")
    public abstract ListenableFuture<DownloadableBlob> getDownloadable(
            String emailId, String blobId);

    @Transaction
    @Query(
            "select id,receivedAt,sentAt,email.threadId,encryptionStatus from thread_item"
                    + " join email on thread_item.emailId=email.id where"
                    + " thread_item.threadId=:threadId order by position")
    public abstract DataSource.Factory<Integer, EmailWithBodies> getEmails(String threadId);

    @Query("select emailId from thread_item where threadId in (:threadIds)")
    public abstract ListenableFuture<List<String>> getEmailIds(final Collection<String> threadIds);

    @Transaction
    @Query(
            "select id,receivedAt,sentAt,threadId,subject,encryptionStatus from email where id in"
                    + " (:emailIds)")
    public abstract List<EmailWithBodiesAndSubject> getEmails(Collection<String> emailIds);

    // TODO remove 'preview'. 'receivedAt' is strictly speaking not necessary currently but might be
    // needed in the future for quoting the original email
    @Transaction
    @Query(
            "select :accountId as accountId,id,threadId,subject,receivedAt,sentAt,encryptionStatus"
                    + " from email where id=:id")
    public abstract ListenableFuture<EmailWithReferences> getEmailWithReferences(
            Long accountId, String id);

    @Transaction
    @Query(
            "select subject,email.threadId from thread_item join email on"
                    + " thread_item.emailId=email.id where thread_item.threadId=:threadId order by"
                    + " position limit 1")
    public abstract LiveData<ThreadHeader> getThreadHeader(String threadId);

    @Query(
            "select position,emailId from thread_item where threadId=:threadId and"
                + " thread_item.emailId not in (select thread_item.emailId from thread_item join"
                + " email_keyword on thread_item.emailId=email_keyword.emailId where"
                + " threadId=:threadId and email_keyword.keyword='$seen') order by position")
    public abstract ListenableFuture<List<ExpandedPosition>> getUnseenPositions(String threadId);

    @Query("select position,emailId from thread_item where threadId=:threadId order by position")
    public abstract ListenableFuture<List<ExpandedPosition>> getAllPositions(String threadId);

    @Query(
            "select position,emailId from thread_item where threadId=:threadId order by position"
                    + " desc limit 1")
    public abstract ListenableFuture<List<ExpandedPosition>> getMaxPosition(String threadId);

    @Query("delete from email")
    abstract void deleteAllEmail();
}
