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

import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DoubleField;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.QueryField;
import stroom.datasource.api.v2.TextField;
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
        switch (field.getType()) {
            case ID:
                return new IdField(field.getName(), field.isIndexed());
            case BOOLEAN:
                return new BooleanField(field.getName(), field.isIndexed());
            case INTEGER:
                return new IntegerField(field.getName(), field.isIndexed());
            case LONG:
                return new LongField(field.getName(), field.isIndexed());
            case FLOAT:
                return new FloatField(field.getName(), field.isIndexed());
            case DOUBLE:
                return new DoubleField(field.getName(), field.isIndexed());
            case DATE:
                return new DateField(field.getName(), field.isIndexed());
            case TEXT:
                return new TextField(field.getName(), field.isIndexed());
        }

        return null;
    }
}
