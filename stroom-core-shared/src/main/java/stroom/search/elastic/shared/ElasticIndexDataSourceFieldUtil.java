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

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DoubleField;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.List;
import java.util.stream.Collectors;

public final class ElasticIndexDataSourceFieldUtil {
    public static List<AbstractField> getDataSourceFields(final ElasticIndexDoc index) {
        if (index == null || index.getFields() == null) {
            return null;
        }

        return index.getFields()
                .stream()
                .map(ElasticIndexDataSourceFieldUtil::convert)
                .collect(Collectors.toList());
    }

    private static AbstractField convert(final ElasticIndexField field) {
        final ElasticIndexFieldType fieldType = field.getFieldUse();
        final String fieldName = field.getFieldName();
        final List<Condition> supportedConditions = fieldType.getSupportedConditions();
        switch (fieldType) {
            case ID:
                return new IdField(fieldName, true, supportedConditions);
            case BOOLEAN:
                return new BooleanField(fieldName, true, supportedConditions);
            case INTEGER:
                return new IntegerField(fieldName, true, supportedConditions);
            case LONG:
                return new LongField(fieldName, true, supportedConditions);
            case FLOAT:
                return new FloatField(fieldName, true, supportedConditions);
            case DOUBLE:
                return new DoubleField(fieldName, true, supportedConditions);
            case DATE:
                return new DateField(fieldName, true, supportedConditions);
            case TEXT:
                return new TextField(fieldName, true, supportedConditions);
        }
        return null;
    }
}
