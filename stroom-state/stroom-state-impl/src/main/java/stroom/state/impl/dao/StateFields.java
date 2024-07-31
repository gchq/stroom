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

package stroom.state.impl.dao;

import stroom.datasource.api.v2.QueryField;
import stroom.util.shared.string.CIKey;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface StateFields {

    QueryField KEY_FIELD = QueryField.createText(FieldNames.KEY_FIELD_NAME);
    QueryField VALUE_TYPE_FIELD = QueryField.createText(FieldNames.VALUE_TYPE_FIELD_NAME, false);
    QueryField VALUE_FIELD = QueryField.createText(FieldNames.VALUE_FIELD_NAME, false);
    QueryField INSERT_TIME_FIELD = QueryField.createText(FieldNames.INSERT_TIME_FIELD_NAME, false);

    List<QueryField> FIELDS = Arrays.asList(
            KEY_FIELD,
            VALUE_TYPE_FIELD,
            VALUE_FIELD,
            INSERT_TIME_FIELD);

    Map<CIKey, QueryField> FIELD_NAME_TO_FIELD_MAP = Map.of(
            FieldNames.KEY_FIELD_KEY, KEY_FIELD,
            FieldNames.VALUE_TYPE_FIELD_KEY, VALUE_TYPE_FIELD,
            FieldNames.VALUE_FIELD_KEY, VALUE_FIELD,
            FieldNames.INSERT_TIME_FIELD_KEY, INSERT_TIME_FIELD);
}
