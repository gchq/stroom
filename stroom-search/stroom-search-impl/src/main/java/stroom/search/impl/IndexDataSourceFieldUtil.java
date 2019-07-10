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

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.NumberField;
import stroom.datasource.api.v2.TextField;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.List;

public final class IndexDataSourceFieldUtil {
    public static List<AbstractField> getDataSourceFields(final IndexDoc index) {
        if (index == null || index.getIndexFields() == null) {
            return null;
        }

        final List<IndexField> indexFields = index.getIndexFields();
        final List<AbstractField> dataSourceFields = new ArrayList<>(indexFields.size());
        for (final IndexField indexField : indexFields) {
            // TODO should index fields include doc refs?
            dataSourceFields.add(convert(indexField));
        }

        return dataSourceFields;
    }

    private static AbstractField convert(final IndexField indexField) {
        final List<Condition> conditions = indexField.getSupportedConditions();
        switch (indexField.getFieldType()) {
            case DATE_FIELD:
                return new DateField(indexField.getFieldName(), indexField.isIndexed(), conditions);
            case FIELD:
                return new TextField(indexField.getFieldName(), indexField.isIndexed(), conditions);
            case ID:
                return new IdField(indexField.getFieldName(), indexField.isIndexed(), conditions);
            case NUMERIC_FIELD:
                return new NumberField(indexField.getFieldName(), indexField.isIndexed(), conditions);
        }

        return null;
    }
}
