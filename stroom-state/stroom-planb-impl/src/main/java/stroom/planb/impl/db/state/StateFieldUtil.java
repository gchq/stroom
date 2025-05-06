package stroom.planb.impl.db.state;

import stroom.planb.impl.db.RangedStateFields;
import stroom.planb.impl.db.SessionFields;
import stroom.planb.impl.db.TemporalRangedStateFields;
import stroom.planb.impl.db.TemporalStateFields;
import stroom.planb.shared.StateType;
import stroom.query.api.datasource.QueryField;

import java.util.List;
import java.util.Map;

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

    public static Map<String, QueryField> getFieldMap(final StateType stateType) {
        return switch (stateType) {
            case STATE -> StateFields.FIELD_MAP;
            case TEMPORAL_STATE -> TemporalStateFields.FIELD_MAP;
            case RANGED_STATE -> RangedStateFields.FIELD_MAP;
            case TEMPORAL_RANGED_STATE -> TemporalRangedStateFields.FIELD_MAP;
            case SESSION -> SessionFields.FIELD_MAP;
        };
    }

    public static QueryField getTimeField(final StateType stateType) {
        return switch (stateType) {
            case STATE -> null;
            case TEMPORAL_STATE -> TemporalStateFields.EFFECTIVE_TIME_FIELD;
            case RANGED_STATE -> null;
            case TEMPORAL_RANGED_STATE -> TemporalRangedStateFields.EFFECTIVE_TIME_FIELD;
            case SESSION -> SessionFields.START_FIELD;
        };
    }
}
