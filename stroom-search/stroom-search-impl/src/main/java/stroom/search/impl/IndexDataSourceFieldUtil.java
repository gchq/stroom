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

import stroom.annotation.api.AnnotationDataSource;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DoubleField;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class IndexDataSourceFieldUtil {
    public static List<AbstractField> getDataSourceFields(final IndexDoc index, final SecurityContext securityContext) {
        if (index == null || index.getFields() == null) {
            return null;
        }

        final List<IndexField> indexFields = index.getFields();
        final List<AbstractField> dataSourceFields = new ArrayList<>(indexFields.size());
        for (final IndexField indexField : indexFields) {
            // TODO should index fields include doc refs?
            dataSourceFields.add(convert(indexField));
        }

        // Add annotation fields if this index has stream and event ids.
        if (securityContext == null || securityContext.hasAppPermission(PermissionNames.ANNOTATIONS)) {
            final Set<String> names = indexFields
                    .stream()
                    .map(IndexField::getFieldName)
                    .collect(Collectors.toSet());
            if (names.contains(IndexConstants.STREAM_ID) && names.contains(IndexConstants.EVENT_ID)) {
                dataSourceFields.addAll(AnnotationDataSource.FIELDS);
            }
        }

        return dataSourceFields;
    }

    private static AbstractField convert(final IndexField field) {
        switch (field.getFieldType()) {
            case ID:
                return new IdField(field.getFieldName(), field.isIndexed(), field.getSupportedConditions());
            case BOOLEAN_FIELD:
                return new BooleanField(field.getFieldName(), field.isIndexed(), field.getSupportedConditions());
            case INTEGER_FIELD:
                return new IntegerField(field.getFieldName(), field.isIndexed(), field.getSupportedConditions());
            case LONG_FIELD:
                return new LongField(field.getFieldName(), field.isIndexed(), field.getSupportedConditions());
            case FLOAT_FIELD:
                return new FloatField(field.getFieldName(), field.isIndexed(), field.getSupportedConditions());
            case DOUBLE_FIELD:
                return new DoubleField(field.getFieldName(), field.isIndexed(), field.getSupportedConditions());
            case DATE_FIELD:
                return new DateField(field.getFieldName(), field.isIndexed(), field.getSupportedConditions());
            case FIELD:
                return new TextField(field.getFieldName(), field.isIndexed(), field.getSupportedConditions());
        }

        return null;
    }
}
