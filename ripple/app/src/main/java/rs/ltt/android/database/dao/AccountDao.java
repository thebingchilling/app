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
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import rs.ltt.android.entity.AccountEntity;
import rs.ltt.android.entity.AccountName;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.CredentialsEntity;

@Dao
public abstract class AccountDao {

    private static final String ACCOUNT_WITH_CREDENTIALS =
            "select account.id as id,credentialsId,accountId,name,incomingProtocol,incomingHost,"
                    + "incomingPort,incomingSecurity,smtpHost,smtpPort,smtpSecurity,authType,"
                    + "username,password,oauthProvider from credentials join account on"
                    + " credentialsId = credentials.id";

    @Query(ACCOUNT_WITH_CREDENTIALS + " where account.id=:id limit 1")
    public abstract ListenableFuture<AccountWithCredentials> getAccountFuture(Long id);

    @Query(ACCOUNT_WITH_CREDENTIALS)
    public abstract ListenableFuture<List<AccountWithCredentials>> getAccounts();

    @Query(ACCOUNT_WITH_CREDENTIALS)
    public abstract List<AccountWithCredentials> getAccountsSync();

    @Query(ACCOUNT_WITH_CREDENTIALS + " where account.id=:id limit 1")
    public abstract AccountWithCredentials getAccount(Long id);

    @Query("select exists (select 1 from account where lower(accountId)=lower(:emailAddress))")
    public abstract boolean hasAccount(String emailAddress);

    @Query("select id,name from account where id=:id limit 1")
    public abstract LiveData<AccountName> getAccountNameLiveData(Long id);

    @Query("select id,name from account where id=:id limit 1")
    public abstract AccountName getAccountName(Long id);

    @Query("select id,name from account order by name")
    public abstract LiveData<List<AccountName>> getAccountNames();

    @Query("select id from account")
    public abstract LiveData<List<Long>> getAccountIds();

    @Query("select id from account order by selected desc limit 1")
    public abstract Long getMostRecentlySelectedAccountId();

    @Query("select exists (select 1 from account)")
    public abstract boolean hasAccounts();

    @Query("delete from account where id=:id")
    abstract void deleteAccount(final Long id);

    @Query("delete from credentials where id=:id")
    abstract void deleteCredentials(final Long id);

    @Query("select exists (select 1 from account where credentialsId=:credentialsId)")
    abstract boolean hasAccountsWithCredentialsId(final Long credentialsId);

    @Insert
    abstract Long insert(CredentialsEntity entity);

    @Insert
    abstract Long insert(AccountEntity entity);

    @Transaction
    public AccountWithCredentials insert(
            final CredentialsEntity credentials, final String emailAddress) {
        final Long credentialsId = insert(credentials);
        final Long id = insert(new AccountEntity(credentialsId, emailAddress, emailAddress));
        return getAccount(id);
    }

    @Transaction
    public boolean delete(final AccountWithCredentials account) {
        final var credentials = account.getCredentials();
        deleteAccount(account.getId());
        if (hasAccountsWithCredentialsId(credentials.getId())) {
            return false;
        }
        deleteCredentials(credentials.getId());
        return true;
    }

    @Query("update account set selected=1 where id=:id")
    abstract void setSelected(final Long id);

    @Query("update account set selected=0 where id is not :id")
    abstract void setNotSelected(final Long id);

    @Transaction
    public void selectAccount(final Long id) {
        setSelected(id);
        setNotSelected(id);
    }

    @Query("select oauthState from credentials where id=:credentialsId")
    public abstract String getOAuthState(Long credentialsId);

    @Query("update credentials set oauthState=:state where id=:credentialsId")
    public abstract void setOAuthState(Long credentialsId, String state);

    @Query("update credentials set password=:password where id=:credentialsId")
    public abstract void setPassword(Long credentialsId, String password);

    /** Replaces the login of an existing account after the user signed in again. */
    @Query(
            "update credentials set authType=:authType, username=:username, password=:password,"
                    + " oauthProvider=:oauthProvider, oauthState=:oauthState where id=(select"
                    + " credentialsId from account where id=:accountId)")
    public abstract void updateLogin(
            Long accountId,
            String authType,
            String username,
            String password,
            String oauthProvider,
            String oauthState);
}
