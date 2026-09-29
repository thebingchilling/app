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

package rs.ltt.android;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.engine.Mua;
import rs.ltt.android.entity.AccountWithCredentials;

public final class MuaPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(MuaPool.class);

    private static final Map<Long, Mua> INSTANCES = new HashMap<>();

    private MuaPool() {}

    public static ListenableFuture<Mua> getInstance(final Context context, final long accountId) {
        return Futures.transform(
                AppDatabase.getInstance(context).accountDao().getAccountFuture(accountId),
                account -> getInstance(context, account),
                MoreExecutors.directExecutor());
    }

    public static Mua getInstance(final Context context, final AccountWithCredentials account) {
        synchronized (MuaPool.class) {
            final Mua existing = INSTANCES.get(account.getId());
            if (existing != null && existing.getAccount().equals(account)) {
                return existing;
            }
            if (existing != null) {
                existing.close();
            }
            LOGGER.info("Building Mua for account id {}", account.getId());
            final Mua mua = new Mua(context, account);
            INSTANCES.put(account.getId(), mua);
            return mua;
        }
    }

    public static void evict(final long id) {
        synchronized (MuaPool.class) {
            final Mua mua = INSTANCES.remove(id);
            if (mua != null) {
                LOGGER.debug("Evicting {} from MuaPool", id);
                mua.close();
            }
        }
    }
}
