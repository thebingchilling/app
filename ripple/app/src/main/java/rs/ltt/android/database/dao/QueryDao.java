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

import static androidx.room.OnConflictStrategy.REPLACE;

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.entity.QueryEntity;
import rs.ltt.android.entity.QueryItem;
import rs.ltt.android.entity.QueryItemEntity;
import rs.ltt.android.entity.ThreadOverviewItem;

@Dao
public abstract class QueryDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryDao.class);

    @Insert(onConflict = REPLACE)
    abstract long insert(QueryEntity entity);

    @Insert
    abstract void insert(List<QueryItemEntity> entities);

    @Insert
    abstract void insert(QueryItemEntity entity);

    @Query("delete from query_item_overwrite where executed=1 and queryId=:queryId")
    abstract int deleteAllExecuted(Long queryId);

    @Query("select * from `query` where queryString=:queryString and valid=1 limit 1")
    public abstract QueryEntity get(String queryString);

    @Query(
            "select position,emailId from query_item where queryId=:queryId order by position desc"
                    + " limit 1")
    abstract QueryItem getLastQueryItem(Long queryId);

    @Query("select count(id) from query_item where queryId=:queryId")
    abstract int getItemCount(Long queryId);

    @Query(
            "select case when exists(select query_item.id from `query` join query_item on"
                + " `query`.id = query_item.queryId where queryString=:queryString) then 0 else 1"
                + " end")
    public abstract boolean empty(String queryString);

    @Query("delete from `query` where queryString=:queryString")
    abstract void deleteQuery(String queryString);

    // we inner join on threads here to make sure that we only return items that we actually have
    // due to the delay of fetchMissing we might have query_items that we do not have a
    // corresponding thread for
    @Transaction
    @Query(
            "select query_item.threadId,query_item.emailId from `query` join query_item on"
                    + " `query`.id = query_item.queryId inner join thread on"
                    + " query_item.threadId=thread.threadId where queryString=:queryString  and "
                    + " query_item.threadId not in (select threadId from query_item_overwrite where"
                    + " queryId=`query`.id) order by position asc")
    public abstract DataSource.Factory<Integer, ThreadOverviewItem> getThreadOverviewItems(
            String queryString);

    @Query(
            "select query_item.emailId from `query` join query_item on `query`.id ="
                    + " query_item.queryId where queryString=:queryString order by position asc")
    public abstract List<String> getEmailIds(final String queryString);
}
