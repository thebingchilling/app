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
 *
 */

package rs.ltt.android.mail.model.query;

import rs.ltt.android.mail.model.AbstractIdentifiableEntity;
import rs.ltt.android.mail.model.Comparator;
import rs.ltt.android.mail.model.filter.Filter;
import rs.ltt.android.mail.model.filter.QueryString;

public abstract class Query<T extends AbstractIdentifiableEntity> implements QueryString {

    public final Filter<T> filter;

    public final Comparator[] sort;

    protected Query(final Filter<T> filter, final Comparator[] sort) {
        this.filter = filter;
        this.sort = sort;
    }
}
