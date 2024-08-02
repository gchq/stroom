/*
 * Copyright 2024 Crown Copyright
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

import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnalyticFields {

    public static final String ANALYTICS_STORE_TYPE = "Analytics";
    public static final DocRef ANALYTICS_DOC_REF = DocRef.builder()
            .type(ANALYTICS_STORE_TYPE)
            .uuid(ANALYTICS_STORE_TYPE)
            .name(ANALYTICS_STORE_TYPE)
            .build();

    public static final String NAME_FIELD_NAME = "Name";
    public static final String UUID_FIELD_NAME = "UUID";
    public static final String TIME_FIELD_NAME = "Time";
    public static final String VALUE_FIELD_NAME = "Value";

    public static final CIKey NAME_FIELD_KEY = CIKey.of(NAME_FIELD_NAME);
    public static final CIKey UUID_FIELD_KEY = CIKey.of(UUID_FIELD_NAME);
    public static final CIKey TIME_FIELD_KEY = CIKey.of(TIME_FIELD_NAME);
    public static final CIKey VALUE_FIELD_KEY = CIKey.of(VALUE_FIELD_NAME);

    // Times
    public static final QueryField TIME_FIELD = QueryField.createDate(TIME_FIELD_NAME);
    public static final QueryField NAME_FIELD = QueryField.createText(NAME_FIELD_NAME);
    public static final QueryField UUID_FIELD = QueryField.createText(UUID_FIELD_NAME);
    public static final QueryField VALUE_FIELD = QueryField.createText(VALUE_FIELD_NAME);

    private static final List<QueryField> FIELDS = List.of(
            TIME_FIELD,
            NAME_FIELD,
            UUID_FIELD,
            VALUE_FIELD);

    private static final Map<CIKey, QueryField> FIELD_NAME_TO_FIELD_MAP = Map.of(
            NAME_FIELD_KEY, NAME_FIELD,
            UUID_FIELD_KEY, UUID_FIELD,
            TIME_FIELD_KEY, TIME_FIELD,
            VALUE_FIELD_KEY, VALUE_FIELD);

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<CIKey, QueryField> getFieldMap() {
        return FIELD_NAME_TO_FIELD_MAP;
    }
}
