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
import androidx.room.PrimaryKey;

@Entity(
        tableName = "query_item",
        foreignKeys = {
            @ForeignKey(
                    entity = QueryEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"queryId"},
                    onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index(value = "queryId")})
public class QueryItemEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long queryId;

    public Long position;
    public String emailId;
    public String threadId;

    public QueryItemEntity(@NonNull Long queryId, Long position, String emailId, String threadId) {
        this.queryId = queryId;
        this.position = position;
        this.emailId = emailId;
        this.threadId = threadId;
    }
}
