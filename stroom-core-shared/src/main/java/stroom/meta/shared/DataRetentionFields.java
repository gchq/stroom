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

package stroom.meta.shared;

import stroom.query.api.datasource.QueryField;

public final class DataRetentionFields {

    public static final String RETENTION_AGE = "Age";
    public static final String RETENTION_UNTIL = "Until";
    public static final String RETENTION_RULE = "Rule";

    public static final QueryField RETENTION_AGE_FIELD = QueryField.createText(RETENTION_AGE, false);
    public static final QueryField RETENTION_UNTIL_FIELD = QueryField.createText(RETENTION_UNTIL, false);
    public static final QueryField RETENTION_RULE_FIELD = QueryField.createText(RETENTION_RULE, false);

    private DataRetentionFields() {
    }
}
