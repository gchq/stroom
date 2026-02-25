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

package stroom.planb.impl.db.rangestate;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface RangeStateFields {

    String KEY_START = "KeyStart";
    String KEY_END = "KeyEnd";
    String VALUE_TYPE = "ValueType";
    String VALUE = "Value";

    QueryField KEY_START_FIELD = QueryField.createLong(KEY_START);
    QueryField KEY_END_FIELD = QueryField.createLong(KEY_END);
    QueryField VALUE_TYPE_FIELD = QueryField.createText(VALUE_TYPE, false);
    QueryField VALUE_FIELD = QueryField.createText(VALUE, false);

    List<QueryField> FIELDS = Arrays.asList(
            KEY_START_FIELD,
            KEY_END_FIELD,
            VALUE_TYPE_FIELD,
            VALUE_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            KEY_START, KEY_START_FIELD,
            KEY_END, KEY_END_FIELD,
            VALUE_TYPE, VALUE_TYPE_FIELD,
            VALUE, VALUE_FIELD);
}
