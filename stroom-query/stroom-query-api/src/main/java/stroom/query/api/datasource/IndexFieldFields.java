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

package stroom.query.api.datasource;

public class IndexFieldFields extends FieldFields {

    public static final String STORE = "Store";
    public static final String INDEX = "Index";
    public static final String POSITIONS = "Positions";
    public static final String ANALYSER = "Analyser";
    public static final String CASE_SENSITIVE = "CaseSensitive";

    public static final QueryField STORE_FIELD = QueryField.createBoolean(STORE);
    public static final QueryField INDEX_FIELD = QueryField.createBoolean(INDEX);
    public static final QueryField POSITIONS_FIELD = QueryField.createBoolean(POSITIONS);
    public static final QueryField ANALYSER_FIELD = QueryField.createText(ANALYSER);
    public static final QueryField CASE_SENSITIVE_FIELD = QueryField.createBoolean(CASE_SENSITIVE);
}
