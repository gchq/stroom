package stroom.planb.impl.db;

import stroom.planb.impl.db.histogram.TemporalValue;
import stroom.planb.impl.db.rangestate.RangeState;
import stroom.planb.impl.db.session.Session;
import stroom.planb.impl.db.state.State;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeState;
import stroom.planb.impl.db.temporalstate.TemporalState;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = State.class, name = "state"),
        @JsonSubTypes.Type(value = TemporalState.class, name = "temporalState"),
        @JsonSubTypes.Type(value = RangeState.class, name = "rangeState"),
        @JsonSubTypes.Type(value = TemporalRangeState.class, name = "temporalRangeState"),
        @JsonSubTypes.Type(value = Session.class, name = "session"),
        @JsonSubTypes.Type(value = TemporalValue.class, name = "histogram")
})
public interface PlanBValue {

}
