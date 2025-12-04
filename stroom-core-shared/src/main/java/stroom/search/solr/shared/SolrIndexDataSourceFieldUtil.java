/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.QueryField;

import java.util.List;
import java.util.stream.Collectors;

public final class SolrIndexDataSourceFieldUtil {

    public static List<QueryField> getDataSourceFields(final SolrIndexDoc index) {
        if (index == null || index.getFields() == null) {
            return null;
        }

        return index.getFields()
                .stream()
                .map(SolrIndexDataSourceFieldUtil::convert)
                .collect(Collectors.toList());
    }

    private static QueryField convert(final SolrIndexField field) {
        return QueryField
                .builder()
                .fldName(field.getFldName())
                .fldType(field.getFldType())
                .conditionSet(ConditionSet.getSolr(field.getFldType()))
                .queryable(field.isIndexed())
                .build();
    }
}
