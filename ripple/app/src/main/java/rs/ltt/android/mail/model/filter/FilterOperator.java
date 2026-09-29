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

package rs.ltt.android.mail.model.filter;

import com.google.common.base.MoreObjects;
import java.util.Arrays;
import androidx.annotation.NonNull;
import rs.ltt.android.mail.model.AbstractIdentifiableEntity;
import rs.ltt.android.mail.util.QueryStringUtils;

public class FilterOperator<T extends AbstractIdentifiableEntity> implements Filter<T> {

    private Operator operator;

    private Filter<T>[] conditions;

    private FilterOperator(Filter<T>[] conditions, Operator operator) {
        Arrays.sort(conditions);
        this.conditions = conditions;
        this.operator = operator;
    }

    @SafeVarargs
    public static <T extends AbstractIdentifiableEntity> FilterOperator<T> and(
            Filter<T>... filters) {
        return new FilterOperator<>(filters, Operator.AND);
    }

    @SafeVarargs
    public static <T extends AbstractIdentifiableEntity> FilterOperator<T> or(
            Filter<T>... filters) {
        return new FilterOperator<>(filters, Operator.OR);
    }

    @SafeVarargs
    public static <T extends AbstractIdentifiableEntity> FilterOperator<T> not(
            Filter<T>... filters) {
        return new FilterOperator<>(filters, Operator.NOT);
    }

    public Operator getOperator() {
        return operator;
    }

    public Filter<T>[] getConditions() {
        return conditions;
    }

    @Override
    public String toQueryString() {
        return QueryStringUtils.toQueryString(L1_DIVIDER, L2_DIVIDER, conditions, operator);
    }

    @Override
    public int compareTo(@NonNull Filter<T> filter) {
        if (filter instanceof FilterOperator) {
            return 0;
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("operator", operator)
                .add("conditions", conditions)
                .toString();
    }
}
