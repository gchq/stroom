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

package stroom.index.shared;

import stroom.query.api.datasource.FieldType;

import java.util.ArrayList;
import java.util.List;

public class LuceneFieldTypes {

    public static final List<FieldType> FIELD_TYPES = new ArrayList<>();

    static {
        FIELD_TYPES.add(FieldType.ID);
        FIELD_TYPES.add(FieldType.BOOLEAN);
        FIELD_TYPES.add(FieldType.INTEGER);
        FIELD_TYPES.add(FieldType.LONG);
        FIELD_TYPES.add(FieldType.FLOAT);
        FIELD_TYPES.add(FieldType.DOUBLE);
        FIELD_TYPES.add(FieldType.DATE);
        FIELD_TYPES.add(FieldType.TEXT);
        FIELD_TYPES.add(FieldType.DENSE_VECTOR);
    }
}
