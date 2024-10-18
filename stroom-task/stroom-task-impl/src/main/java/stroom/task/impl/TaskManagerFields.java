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

package stroom.task.impl;

import stroom.datasource.api.v2.QueryField;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TaskManagerFields {

    public static final String FIELD_NODE = "Node";
    public static final String FIELD_NAME = "Name";
    public static final String FIELD_USER = "User";
    public static final String FIELD_SUBMIT_TIME = "Submit Time";
    public static final String FIELD_AGE = "Age";
    public static final String FIELD_INFO = "Info";

    public static final QueryField NODE = QueryField.createText(CIKey.ofStaticKey(FIELD_NODE), true);
    public static final QueryField NAME = QueryField.createText(CIKey.ofStaticKey(FIELD_NAME), true);
    public static final QueryField USER = QueryField.createText(CIKey.ofStaticKey(FIELD_USER), true);
    public static final QueryField SUBMIT_TIME = QueryField.createDate(
            CIKey.ofStaticKey(FIELD_SUBMIT_TIME), true);
    public static final QueryField AGE = QueryField.createLong(CIKey.ofStaticKey(FIELD_AGE), true);
    public static final QueryField INFO = QueryField.createText(CIKey.ofStaticKey(FIELD_INFO), true);

    private static final List<QueryField> FIELDS = List.of(
            NODE,
            NAME,
            USER,
            SUBMIT_TIME,
            AGE,
            INFO);

    private static final Map<CIKey, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(
                    QueryField::getFldNameAsCIKey,
                    Function.identity()));

    private static final Map<String, CIKey> NAME_TO_KEY_MAP = FIELD_MAP.keySet()
            .stream()
            .collect(Collectors.toMap(CIKey::get, Function.identity()));

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<CIKey, QueryField> getFieldMap() {
        return FIELD_MAP;
    }

    public static CIKey createCIKey(final String fieldName) {
        return CIKey.of(fieldName, NAME_TO_KEY_MAP);
    }
}
