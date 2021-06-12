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

package stroom.search.elastic.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.query.api.v2.ExpressionTerm;

import java.util.List;
import java.util.stream.Collectors;

public final class ElasticIndexDataSourceFieldUtil {
    public static List<DataSourceField> getDataSourceFields(final ElasticIndex index) {
        if (index == null || index.getFields() == null) {
            return null;
        }

        return index.getFields()
                .stream()
                .map(field -> {
                    return new DataSourceField.Builder()
                            .type(getDataSourceFieldType(field.getFieldUse()))
                            .name(field.getFieldName())
                            .queryable(true)
                            .addConditions(field.getFieldUse().getSupportedConditions().toArray(new ExpressionTerm.Condition[0]))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static DataSourceFieldType getDataSourceFieldType(final ElasticIndexFieldType indexFieldType) {
        switch (indexFieldType) {
            case ID:
                return DataSourceFieldType.ID_FIELD;
            case BOOLEAN:
                return DataSourceFieldType.BOOLEAN_FIELD;
            case INTEGER:
                return DataSourceFieldType.INTEGER_FIELD;
            case LONG:
                return DataSourceFieldType.LONG_FIELD;
            case FLOAT:
                return DataSourceFieldType.FLOAT_FIELD;
            case DOUBLE:
                return DataSourceFieldType.DOUBLE_FIELD;
            case DATE:
                return DataSourceFieldType.DATE_FIELD;
            case TEXT:
                return DataSourceFieldType.TEXT_FIELD;
        }
        return null;
    }
}
