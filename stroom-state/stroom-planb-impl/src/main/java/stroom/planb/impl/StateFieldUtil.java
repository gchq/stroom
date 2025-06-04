package stroom.planb.impl;

import stroom.planb.impl.db.histogram.HistogramFields;
import stroom.planb.impl.db.metric.MetricFields;
import stroom.planb.impl.db.rangestate.RangeStateFields;
import stroom.planb.impl.db.session.SessionFields;
import stroom.planb.impl.db.state.StateFields;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeStateFields;
import stroom.planb.impl.db.temporalstate.TemporalStateFields;
import stroom.planb.shared.StateType;
import stroom.query.api.datasource.QueryField;

import java.util.List;
import java.util.Map;

public class StateFieldUtil {

    public static List<QueryField> getQueryableFields(final StateType stateType) {
        return switch (stateType) {
            case STATE -> StateFields.FIELDS;
            case TEMPORAL_STATE -> TemporalStateFields.FIELDS;
            case RANGED_STATE -> RangeStateFields.FIELDS;
            case TEMPORAL_RANGED_STATE -> TemporalRangeStateFields.FIELDS;
            case SESSION -> SessionFields.FIELDS;
            case HISTOGRAM -> HistogramFields.FIELDS;
            case METRIC -> MetricFields.FIELDS;
        };
    }

    public static Map<String, QueryField> getFieldMap(final StateType stateType) {
        return switch (stateType) {
            case STATE -> StateFields.FIELD_MAP;
            case TEMPORAL_STATE -> TemporalStateFields.FIELD_MAP;
            case RANGED_STATE -> RangeStateFields.FIELD_MAP;
            case TEMPORAL_RANGED_STATE -> TemporalRangeStateFields.FIELD_MAP;
            case SESSION -> SessionFields.FIELD_MAP;
            case HISTOGRAM -> HistogramFields.FIELD_MAP;
            case METRIC -> MetricFields.FIELD_MAP;
        };
    }

    public static QueryField getTimeField(final StateType stateType) {
        return switch (stateType) {
            case STATE, RANGED_STATE -> null;
            case TEMPORAL_STATE -> TemporalStateFields.EFFECTIVE_TIME_FIELD;
            case TEMPORAL_RANGED_STATE -> TemporalRangeStateFields.EFFECTIVE_TIME_FIELD;
            case SESSION -> SessionFields.START_FIELD;
            case HISTOGRAM -> HistogramFields.TIME_FIELD;
            case METRIC -> MetricFields.TIME_FIELD;
        };
    }
}
