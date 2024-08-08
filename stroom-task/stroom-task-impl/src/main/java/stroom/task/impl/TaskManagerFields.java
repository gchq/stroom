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

    public static final CIKey FIELD_NODE_KEY = CIKey.ofStaticKey(FIELD_NODE);
    public static final CIKey FIELD_NAME_KEY = CIKey.ofStaticKey(FIELD_NAME);
    public static final CIKey FIELD_USER_KEY = CIKey.ofStaticKey(FIELD_USER);
    public static final CIKey FIELD_SUBMIT_TIME_KEY = CIKey.ofStaticKey(FIELD_SUBMIT_TIME);
    public static final CIKey FIELD_AGE_KEY = CIKey.ofStaticKey(FIELD_AGE);
    public static final CIKey FIELD_INFO_KEY = CIKey.ofStaticKey(FIELD_INFO);

    public static final QueryField NODE = QueryField.createText(FIELD_NODE);
    public static final QueryField NAME = QueryField.createText(FIELD_NAME);
    public static final QueryField USER = QueryField.createText(FIELD_USER);
    public static final QueryField SUBMIT_TIME = QueryField.createDate(FIELD_SUBMIT_TIME);
    public static final QueryField AGE = QueryField.createLong(FIELD_AGE);
    public static final QueryField INFO = QueryField.createText(FIELD_INFO);

    private static final List<QueryField> FIELDS = List.of(
            NODE,
            NAME,
            USER,
            SUBMIT_TIME,
            AGE,
            INFO);

    private static final Map<CIKey, QueryField> FIELD_MAP = Map.of(
            FIELD_NODE_KEY, NODE,
            FIELD_NAME_KEY, NAME,
            FIELD_USER_KEY, USER,
            FIELD_SUBMIT_TIME_KEY, SUBMIT_TIME,
            FIELD_AGE_KEY, AGE,
            FIELD_INFO_KEY, INFO);

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
