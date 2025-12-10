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

package stroom.planb.impl;

import stroom.planb.impl.db.histogram.HistogramFields;
import stroom.planb.impl.db.metric.MetricFields;
import stroom.planb.impl.db.rangestate.RangeStateFields;
import stroom.planb.impl.db.session.SessionFields;
import stroom.planb.impl.db.state.StateFields;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeStateFields;
import stroom.planb.impl.db.temporalstate.TemporalStateFields;
import stroom.planb.impl.db.trace.TraceFields;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.MetricSettings;
import stroom.planb.shared.MetricValueSchema;
import stroom.planb.shared.PlanBDoc;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StateFieldUtil {

    public static List<QueryField> getQueryableFields(final PlanBDoc doc) {
        return switch (doc.getStateType()) {
            case STATE -> StateFields.FIELDS;
            case TEMPORAL_STATE -> TemporalStateFields.FIELDS;
            case RANGED_STATE -> RangeStateFields.FIELDS;
            case TEMPORAL_RANGED_STATE -> TemporalRangeStateFields.FIELDS;
            case SESSION -> SessionFields.FIELDS;
            case HISTOGRAM -> HistogramFields.FIELDS;
            case METRIC -> getMetricFields(doc);
            case TRACE -> TraceFields.FIELDS;
        };
    }

    private static List<QueryField> getMetricFields(final PlanBDoc doc) {
        if (doc != null) {
            final AbstractPlanBSettings settings = doc.getSettings();
            if (settings instanceof final MetricSettings metricSettings) {
                final MetricValueSchema valueSchema = NullSafe.getOrElse(
                        metricSettings,
                        MetricSettings::getValueSchema,
                        new MetricValueSchema.Builder().build());
                final List<QueryField> fields = new ArrayList<>(MetricFields.CORE_FIELDS);
                if (getBoolean(valueSchema.getStoreLatestValue())) {
                    fields.add(MetricFields.VALUE_FIELD);
                }
                if (getBoolean(valueSchema.getStoreMin())) {
                    fields.add(MetricFields.MIN_FIELD);
                }
                if (getBoolean(valueSchema.getStoreMax())) {
                    fields.add(MetricFields.MAX_FIELD);
                }
                if (getBoolean(valueSchema.getStoreCount())) {
                    fields.add(MetricFields.COUNT_FIELD);
                }
                if (getBoolean(valueSchema.getStoreSum())) {
                    fields.add(MetricFields.SUM_FIELD);
                }
                if (getBoolean(valueSchema.getStoreCount()) && getBoolean(valueSchema.getStoreSum())) {
                    fields.add(MetricFields.AVERAGE_FIELD);
                }
                return fields;
            }
        }

        return MetricFields.CORE_FIELDS;
    }

    private static boolean getBoolean(final Boolean b) {
        return b != null && b;
    }

    public static Map<String, QueryField> getFieldMap(final PlanBDoc doc) {
        return switch (doc.getStateType()) {
            case STATE -> StateFields.FIELD_MAP;
            case TEMPORAL_STATE -> TemporalStateFields.FIELD_MAP;
            case RANGED_STATE -> RangeStateFields.FIELD_MAP;
            case TEMPORAL_RANGED_STATE -> TemporalRangeStateFields.FIELD_MAP;
            case SESSION -> SessionFields.FIELD_MAP;
            case HISTOGRAM -> HistogramFields.FIELD_MAP;
            case METRIC -> getMetricFields(doc)
                    .stream()
                    .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
            case TRACE -> TraceFields.FIELD_MAP;
        };
    }

    public static QueryField getTimeField(final PlanBDoc doc) {
        return switch (doc.getStateType()) {
            case STATE, RANGED_STATE -> null;
            case TEMPORAL_STATE -> TemporalStateFields.EFFECTIVE_TIME_FIELD;
            case TEMPORAL_RANGED_STATE -> TemporalRangeStateFields.EFFECTIVE_TIME_FIELD;
            case SESSION -> SessionFields.START_FIELD;
            case HISTOGRAM -> HistogramFields.TIME_FIELD;
            case METRIC -> MetricFields.TIME_FIELD;
            case TRACE -> TraceFields.START_TIME_FIELD;
        };
    }
}
