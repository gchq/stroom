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

public interface SessionFields {

    QueryField KEY_FIELD = QueryField.createText(CIKeys.KEY, true);
    QueryField START_FIELD = QueryField.createDate(CIKeys.START, true);
    QueryField END_FIELD = QueryField.createDate(CIKeys.END, true);
    QueryField TERMINAL_FIELD = QueryField.createText(CIKeys.TERMINAL, false);

    List<QueryField> FIELDS = List.of(
            KEY_FIELD,
            START_FIELD,
            END_FIELD,
            TERMINAL_FIELD);

    Map<CIKey, QueryField> FIELD_NAME_TO_FIELD_MAP = Map.of(
            CIKeys.KEY, KEY_FIELD,
            CIKeys.START, START_FIELD,
            CIKeys.END, END_FIELD,
            CIKeys.TERMINAL, TERMINAL_FIELD);
}
