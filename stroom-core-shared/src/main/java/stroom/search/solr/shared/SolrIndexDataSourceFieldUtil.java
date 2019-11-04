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

package stroom.search.solr.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.query.api.v2.ExpressionTerm;

import java.util.List;
import java.util.stream.Collectors;

public final class SolrIndexDataSourceFieldUtil {
    public static List<DataSourceField> getDataSourceFields(final SolrIndex index) {
        if (index == null || index.getFields() == null) {
            return null;
        }

        return index.getFields()
                .stream()
                .map(field -> {
                    return new DataSourceField.Builder()
                            .type(getDataSourceFieldType(field.getFieldUse()))
                            .name(field.getFieldName())
                            .queryable(field.isIndexed())
                            .addConditions(field.getSupportedConditions().toArray(new ExpressionTerm.Condition[0]))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static DataSourceFieldType getDataSourceFieldType(final SolrIndexFieldType indexFieldType) {
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
        }
        return null;
    }
}
