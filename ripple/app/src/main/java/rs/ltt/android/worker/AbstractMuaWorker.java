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
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.net.ssl.SSLException;
import rs.ltt.android.MuaPool;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.engine.Mua;

public abstract class AbstractMuaWorker extends Worker {

    public static final String TAG_EMAIL_MODIFICATION = "email_modification";
    protected static final String MAILBOX_ID_KEY = "mailboxId";
    protected static final String KEYWORD_KEY = "keyword";
    static final String ACCOUNT_KEY = "account";
    protected final Long account;

    AbstractMuaWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = getInputData();
        if (data.hasKeyWithValueOfType(ACCOUNT_KEY, Long.class)) {
            this.account = data.getLong(ACCOUNT_KEY, 0L);
        } else {
            throw new IllegalStateException("Missing required account");
        }
    }

    static boolean shouldRetry(final ExecutionException e) {
        return isNetworkIssue(e.getCause());
    }

    public static boolean isNetworkIssue(final Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof SocketException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof SSLException) {
                return true;
            }
            if (current instanceof com.fsck.k9.mail.AuthenticationFailedException) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    public static String uniqueName(Long accountId) {
        return String.format(Locale.ENGLISH, "account-%d", accountId);
    }

    protected LttrsDatabase getDatabase() {
        return LttrsDatabase.getInstance(getApplicationContext(), this.account);
    }

    protected Mua getMua() {
        final AccountWithCredentials account =
                AppDatabase.getInstance(getApplicationContext())
                        .accountDao()
                        .getAccount(this.account);
        return MuaPool.getInstance(getApplicationContext(), account);
    }
}
