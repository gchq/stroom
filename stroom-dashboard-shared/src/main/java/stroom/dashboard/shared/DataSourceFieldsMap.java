/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.datasource.api.v1.DataSourceField;

import java.util.HashMap;
import java.util.List;

public class DataSourceFieldsMap extends HashMap<String, DataSourceField> {
    private static final long serialVersionUID = -7687167987530520359L;

    public DataSourceFieldsMap() {
    }

    public DataSourceFieldsMap(final List<DataSourceField> fields) {
        for (final DataSourceField indexField : fields) {
            put(indexField);
        }
    }

    public void put(final DataSourceField field) {
        put(field.getName(), field);
    }
}

