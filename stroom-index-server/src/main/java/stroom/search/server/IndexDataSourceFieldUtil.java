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

import stroom.annotation.api.AnnotationDataSource;
import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.index.shared.Index;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldType;
import stroom.index.shared.IndexFields;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.SecurityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class IndexDataSourceFieldUtil {
    public static List<DataSourceField> getDataSourceFields(final Index index, final SecurityContext securityContext) {
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

        // Add annotation fields if this index has stream and event ids.
        if (securityContext == null || securityContext.hasAppPermission(AnnotationDataSource.ANNOTATIONS_PERMISSION)) {
            final Set<String> names = indexFields
                    .getIndexFields()
                    .stream()
                    .map(IndexField::getFieldName)
                    .collect(Collectors.toSet());
            if (names.contains(IndexConstants.STREAM_ID) && names.contains(IndexConstants.EVENT_ID)) {
                dataSourceFields.addAll(AnnotationDataSource.FIELDS);
            }
        }

        return dataSourceFields;
    }

    private static DataSourceFieldType getDataSourceFieldType(final IndexFieldType indexFieldType) {
        switch (indexFieldType) {
            case DATE_FIELD:
                return DataSourceFieldType.DATE_FIELD;
            case FIELD:
                return DataSourceFieldType.FIELD;
            case ID:
                return DataSourceFieldType.ID;
            case NUMERIC_FIELD:
                return DataSourceFieldType.NUMERIC_FIELD;
        }

        return null;
    }
}
