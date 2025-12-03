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

package stroom.analytics.impl;

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnalyticFields {

    public static final String ANALYTICS_STORE_TYPE = "Analytics";
    public static final DocRef ANALYTICS_DOC_REF = DocRef.builder()
            .type(ANALYTICS_STORE_TYPE)
            .uuid(ANALYTICS_STORE_TYPE)
            .name(ANALYTICS_STORE_TYPE)
            .build();

    public static final String NAME = "Name";
    public static final String UUID = "UUID";
    public static final String TIME = "Time";
    public static final String VALUE = "Value";
    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<String, QueryField> FIELD_MAP;

    // Times
    public static final QueryField TIME_FIELD = QueryField.createDate(TIME);

    public static final QueryField NAME_FIELD = QueryField.createText(NAME);
    public static final QueryField UUID_FIELD = QueryField.createText(UUID);
    public static final QueryField VALUE_FIELD = QueryField.createText(VALUE);

    static {
        FIELDS.add(TIME_FIELD);
        FIELDS.add(NAME_FIELD);
        FIELDS.add(UUID_FIELD);
        FIELDS.add(VALUE_FIELD);

        FIELD_MAP = FIELDS.stream()
                .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
