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

package stroom.search.impl;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.QueryField;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;

import java.util.ArrayList;
import java.util.List;

public final class IndexDataSourceFieldUtil {

    public static List<QueryField> getDataSourceFields(final LuceneIndexDoc index) {
        if (index == null || index.getFields() == null) {
            return null;
        }

        final List<LuceneIndexField> indexFields = index.getFields();
        final List<QueryField> dataSourceFields = new ArrayList<>(indexFields.size());
        for (final LuceneIndexField indexField : indexFields) {
            dataSourceFields.add(convert(indexField));
        }

        return dataSourceFields;
    }

    private static QueryField convert(final LuceneIndexField field) {
        return QueryField
                .builder()
                .fldName(field.getFldName())
                .fldType(field.getFldType())
                .conditionSet(ConditionSet.getDefault(field.getFldType()))
                .queryable(true)
                .build();
    }
}
