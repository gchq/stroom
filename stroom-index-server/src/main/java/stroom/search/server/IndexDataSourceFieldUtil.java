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

package stroom.search.server;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldType;
import stroom.index.shared.IndexFields;
import stroom.query.api.v2.ExpressionTerm;

import java.util.ArrayList;
import java.util.List;

public final class IndexDataSourceFieldUtil {
    public static List<DataSourceField> getDataSourceFields(final Index index) {
        if (index == null || index.getIndexFieldsObject() == null || index.getIndexFieldsObject().getIndexFields() == null) {
            return null;
        }

        final IndexFields indexFields = index.getIndexFieldsObject();
        final List<DataSourceField> dataSourceFields = new ArrayList<>(indexFields.getIndexFields().size());
        for (int i = 0; i < indexFields.getIndexFields().size(); i++) {
            final IndexField indexField = indexFields.getIndexFields().get(i);
            // TODO should index fields include doc refs?
            dataSourceFields.add(new DataSourceField.Builder()
                    .type(getDataSourceFieldType(indexField.getFieldType()))
                    .name(indexField.getFieldName())
                    .queryable(indexField.isIndexed())
                    .addConditions(indexField.getSupportedConditions().toArray(new ExpressionTerm.Condition[0]))
                    .build());
        }

        return dataSourceFields;
    }

    private static DataSourceFieldType getDataSourceFieldType(final IndexFieldType indexFieldType) {
        switch (indexFieldType) {
            case ID_FIELD:
                return DataSourceFieldType.ID_FIELD;
            case BOOLEAN_FIELD:
                return DataSourceFieldType.BOOLEAN_FIELD;
            case INTEGER_FIELD:
                return DataSourceFieldType.INTEGER_FIELD;
            case LONG_FIELD:
                return DataSourceFieldType.LONG_FIELD;
            case FLOAT_FIELD:
                return DataSourceFieldType.FLOAT_FIELD;
            case DOUBLE_FIELD:
                return DataSourceFieldType.DOUBLE_FIELD;
            case DATE_FIELD:
                return DataSourceFieldType.DATE_FIELD;
            case TEXT_FIELD:
                return DataSourceFieldType.TEXT_FIELD;
            case NUMERIC_FIELD:
                return DataSourceFieldType.LONG_FIELD;
        }
        return null;
    }
}
