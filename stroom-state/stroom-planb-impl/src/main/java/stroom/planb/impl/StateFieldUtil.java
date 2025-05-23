package stroom.planb.impl;

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
            case RANGE_STATE -> RangeStateFields.FIELDS;
            case TEMPORAL_RANGE_STATE -> TemporalRangeStateFields.FIELDS;
            case SESSION -> SessionFields.FIELDS;
        };
    }

    public static Map<String, QueryField> getFieldMap(final StateType stateType) {
        return switch (stateType) {
            case STATE -> StateFields.FIELD_MAP;
            case TEMPORAL_STATE -> TemporalStateFields.FIELD_MAP;
            case RANGE_STATE -> RangeStateFields.FIELD_MAP;
            case TEMPORAL_RANGE_STATE -> TemporalRangeStateFields.FIELD_MAP;
            case SESSION -> SessionFields.FIELD_MAP;
        };
    }

    public static QueryField getTimeField(final StateType stateType) {
        return switch (stateType) {
            case STATE, RANGE_STATE -> null;
            case TEMPORAL_STATE -> TemporalStateFields.EFFECTIVE_TIME_FIELD;
            case TEMPORAL_RANGE_STATE -> TemporalRangeStateFields.EFFECTIVE_TIME_FIELD;
            case SESSION -> SessionFields.START_FIELD;
        };
    }
}
