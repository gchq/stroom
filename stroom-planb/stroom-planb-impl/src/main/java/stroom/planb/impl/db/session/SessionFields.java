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

package stroom.planb.impl.db.session;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface SessionFields {

    String KEY = "Key";
    String START = "Start";
    String END = "End";

    QueryField KEY_FIELD = QueryField.createText(KEY);
    QueryField START_FIELD = QueryField.createDate(START);
    QueryField END_FIELD = QueryField.createDate(END);

    List<QueryField> FIELDS = Arrays.asList(
            KEY_FIELD,
            START_FIELD,
            END_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            KEY, KEY_FIELD,
            START, START_FIELD,
            END, END_FIELD);
}
