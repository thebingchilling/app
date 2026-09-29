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

package rs.ltt.android.repository;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkManager;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.LttrsApplication;
import rs.ltt.android.MuaPool;
import rs.ltt.android.push.PushController;
import rs.ltt.android.entity.CredentialsEntity;
import rs.ltt.android.mail.model.Identity;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.base.Strings;
import androidx.annotation.NonNull;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountName;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.SearchSuggestion;
import rs.ltt.android.entity.SearchSuggestionEntity;
import rs.ltt.android.ui.notification.EmailNotification;
import rs.ltt.android.worker.AbstractMuaWorker;
import rs.ltt.android.worker.MainMailboxQueryRefreshWorker;
import rs.ltt.android.worker.QueryRefreshWorker;

public class MainRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainRepository.class);

    private static final ListeningExecutorService IO_EXECUTOR =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

    private final AppDatabase appDatabase;
    private final Application application;

    private ListenableFuture<?> networkFuture = null;

    public MainRepository(final Application application) {
        this.application = application;
        this.appDatabase = AppDatabase.getInstance(application);
    }

    public void insertSearchSuggestion(final SearchSuggestion suggestion) {
        IO_EXECUTOR.execute(
                () ->
                        appDatabase
                                .searchSuggestionDao()
                                .insert(SearchSuggestionEntity.of(suggestion)));
    }

    public LiveData<List<SearchSuggestion>> getPreviousSearches(final String term) {
        return Transformations.map(
                appDatabase.searchSuggestionDao().getSearchSuggestions(String.format("%s%%", term)),
                previousSearches -> {
                    final var userInput = SearchSuggestion.userInput(term.trim());
                    if (previousSearches.contains(userInput) || term.trim().isEmpty()) {
                        return previousSearches;
                    }
                    final ImmutableList.Builder<SearchSuggestion> builder =
                            new ImmutableList.Builder<>();
                    return builder.add(userInput).addAll(previousSearches).build();
                });
    }

    /**
     * Stores a verified account, creates its identity and starts instant push.
     *
     * @return the internal id of the new account
     */
    public ListenableFuture<Long> insertAccount(
            final CredentialsEntity credentials,
            final String emailAddress,
            final String displayName) {
        final ListenableFuture<Long> future =
                IO_EXECUTOR.submit(
                        () -> {
                            final AccountWithCredentials account =
                                    appDatabase.accountDao().insert(credentials, emailAddress);
                            final Identity identity =
                                    Identity.builder()
                                            .id("primary")
                                            .name(Strings.nullToEmpty(displayName))
                                            .email(emailAddress)
                                            .build();
                            LttrsDatabase.getInstance(application, account.getId())
                                    .engineDao()
                                    .setIdentities(ImmutableList.of(identity));
                            return account.getId();
                        });
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Long accountId) {
                        PushController.onAccountsChanged(application);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        LOGGER.error("Unable to store account", throwable);
                    }
                },
                MoreExecutors.directExecutor());
        this.networkFuture = future;
        return future;
    }

    public LiveData<AccountName> getAccountName(final Long id) {
        return this.appDatabase.accountDao().getAccountNameLiveData(id);
    }

    public LiveData<List<AccountName>> getAccountNames() {
        return this.appDatabase.accountDao().getAccountNames();
    }

    public void setSelectedAccount(final Long id) {
        LOGGER.debug("setSelectedAccount({})", id);
        IO_EXECUTOR.execute(() -> this.appDatabase.accountDao().selectAccount(id));
    }

    public ListenableFuture<Void> removeAccountAsync(final long accountId) {
        return IO_EXECUTOR.submit(() -> removeAccount(accountId));
    }

    private Void removeAccount(final long accountId) {
        final var account = this.appDatabase.accountDao().getAccount(accountId);
        this.appDatabase.accountDao().delete(account);
        LttrsApplication.get(application).invalidateMostRecentlySelectedAccountId();
        cancelAllWork(accountId);
        MuaPool.evict(accountId);
        PushController.onAccountsChanged(application);
        final File file = LttrsDatabase.close(accountId);
        if (file != null && SQLiteDatabase.deleteDatabase(file)) {
            LOGGER.debug("Successfully deleted {}", file.getAbsolutePath());
        }
        EmailNotification.cancel(application, accountId);
        EmailNotification.deleteChannel(application, accountId);
        return null;
    }

    public boolean cancelNetworkFuture() {
        final ListenableFuture<?> currentNetworkFuture = this.networkFuture;
        if (currentNetworkFuture == null || currentNetworkFuture.isDone()) {
            return false;
        }
        return currentNetworkFuture.cancel(true);
    }

    private void cancelAllWork(final Long accountId) {
        final WorkManager workManager = WorkManager.getInstance(application);
        workManager.cancelUniqueWork(AbstractMuaWorker.uniqueName(accountId));
        workManager.cancelUniqueWork(QueryRefreshWorker.uniqueName(accountId));
        workManager.cancelUniqueWork(MainMailboxQueryRefreshWorker.uniquePeriodicName(accountId));
    }
}
