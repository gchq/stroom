package stroom.planb.impl.db;

import stroom.planb.impl.db.state.State;
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
        @JsonSubTypes.Type(value = RangedState.class, name = "rangedState"),
        @JsonSubTypes.Type(value = TemporalRangedState.class, name = "temporalRangedState"),
        @JsonSubTypes.Type(value = Session.class, name = "session")
})
public interface PlanBValue {

}
