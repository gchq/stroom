package stroom.planb.impl.data;

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
        @JsonSubTypes.Type(value = TemporalValue.class, name = "histogram"),
        @JsonSubTypes.Type(value = SpanKV.class, name = "trace")
})
public sealed interface PlanBValue permits
        State,
        TemporalState,
        RangeState,
        TemporalRangeState,
        Session,
        TemporalValue,
        SpanKV {

}
