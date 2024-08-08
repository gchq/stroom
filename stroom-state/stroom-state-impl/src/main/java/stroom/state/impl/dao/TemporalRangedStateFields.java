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
import stroom.util.shared.string.CIKeys;

import java.util.List;
import java.util.Map;

public interface TemporalRangedStateFields {

    QueryField KEY_START_FIELD = QueryField.createLong(CIKeys.KEY_START, true);
    QueryField KEY_END_FIELD = QueryField.createText(CIKeys.KEY_END, true);
    QueryField EFFECTIVE_TIME_FIELD = QueryField.createDate(CIKeys.EFFECTIVE_TIME, true);
    QueryField VALUE_TYPE_FIELD = QueryField.createText(CIKeys.VALUE_TYPE, false);
    QueryField VALUE_FIELD = QueryField.createText(CIKeys.VALUE, false);

    List<QueryField> FIELDS = List.of(
            KEY_START_FIELD,
            KEY_END_FIELD,
            EFFECTIVE_TIME_FIELD,
            VALUE_TYPE_FIELD,
            VALUE_FIELD);

    Map<CIKey, QueryField> FIELD_NAME_TO_FIELD_MAP = Map.of(
            CIKeys.KEY_START, KEY_START_FIELD,
            CIKeys.KEY_END, KEY_END_FIELD,
            CIKeys.EFFECTIVE_TIME, EFFECTIVE_TIME_FIELD,
            CIKeys.VALUE_TYPE, VALUE_TYPE_FIELD,
            CIKeys.VALUE, VALUE_FIELD);
}
