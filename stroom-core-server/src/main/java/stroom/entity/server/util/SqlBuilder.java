/*
 * Copyright 2017 Crown Copyright
 *
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

package stroom.entity.server.util;

import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.HasPrimitiveValue;

public class SqlBuilder extends AbstractSqlBuilder {
    public SqlBuilder() {
        super();
    }

    public SqlBuilder(final String prepared, final Object... args) {
        super(prepared, args);
    }

    @Override
    void appendCriteriaSet(final String fieldOrEntity, final CriteriaSet<Long> set) {
        append(fieldOrEntity);
        append(" IN (");

        boolean added = false;
        for (final Long item : set) {
            if (item != null) {
                arg(item);
                append(",");
                added = true;
            }
        }

        if (added) {
            // Remove the last comma.
            setLength(length() - 1);
        }

        append(")");
    }

    @Override
    <T extends HasPrimitiveValue> void appendPrimitiveValueSet(final String fieldOrEntity, final CriteriaSet<T> set) {
        append(fieldOrEntity);
        append(" IN (");

        boolean added = false;
        for (final HasPrimitiveValue item : set) {
            if (item != null) {
                arg(item.getPrimitiveValue());
                append(",");
                added = true;
            }
        }

        if (added) {
            // Remove the last comma.
            setLength(length() - 1);
        }

        append(")");
    }


    @Override
    void appendEntityIdSet(final String fieldOrEntity, final EntityIdSet<?> set) {
        append(fieldOrEntity);
        append(" IN (");

        boolean added = false;
        for (final Long item : set) {
            if (item != null) {
                arg(item);
                append(",");
                added = true;
            }
        }

        if (added) {
            // Remove the last comma.
            setLength(length() - 1);
        }

        append(")");
    }
}