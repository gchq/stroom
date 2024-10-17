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
import stroom.state.shared.StateType;
import stroom.util.shared.string.CIKey;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataType;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class StateFieldUtil {

    public static List<QueryField> getQueryableFields(final StateType stateType) {
        return switch (stateType) {
            case STATE -> StateFields.FIELDS;
            case TEMPORAL_STATE -> TemporalStateFields.FIELDS;
            case RANGED_STATE -> RangedStateFields.FIELDS;
            case TEMPORAL_RANGED_STATE -> TemporalRangedStateFields.FIELDS;
            case SESSION -> SessionFields.FIELDS;
        };
    }

    public static Map<CIKey, QueryField> getFieldMap(final StateType stateType) {
        return switch (stateType) {
            case STATE -> StateFields.FIELD_NAME_TO_FIELD_MAP;
            case TEMPORAL_STATE -> TemporalStateFields.FIELD_NAME_TO_FIELD_MAP;
            case RANGED_STATE -> RangedStateFields.FIELD_NAME_TO_FIELD_MAP;
            case TEMPORAL_RANGED_STATE -> TemporalRangedStateFields.FIELD_NAME_TO_FIELD_MAP;
            case SESSION -> SessionFields.FIELD_NAME_TO_FIELD_MAP;
        };
    }

    public static QueryField getTimeField(final StateType stateType) {
        return switch (stateType) {
            case STATE, RANGED_STATE -> null;
            case TEMPORAL_STATE -> TemporalStateFields.EFFECTIVE_TIME_FIELD;
            case TEMPORAL_RANGED_STATE -> TemporalRangedStateFields.EFFECTIVE_TIME_FIELD;
            case SESSION -> SessionFields.START_FIELD;
        };
    }

    public static Entry<CIKey, ScyllaDbColumn> createNameToColumnEntry(
            final CIKey name,
            final DataType dataType,
            final CqlIdentifier identifier) {

        return Map.entry(
                name,
                new ScyllaDbColumn(name.get(), dataType, identifier));
    }
}
