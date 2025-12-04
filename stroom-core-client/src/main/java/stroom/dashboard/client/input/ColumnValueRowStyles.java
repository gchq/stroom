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

package stroom.dashboard.client.input;

import stroom.dashboard.client.table.AbstractRowStyles;
import stroom.dashboard.shared.ColumnValue;
import stroom.preferences.client.UserPreferencesManager;

public class ColumnValueRowStyles extends AbstractRowStyles<ColumnValue> {

    public ColumnValueRowStyles(final UserPreferencesManager userPreferencesManager) {
        super(userPreferencesManager,
                row -> row == null
                        ? null
                        : row.getMatchingRule());
    }
}
