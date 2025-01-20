package stroom.planb.impl.data;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class SnapshotRequest {

    @JsonProperty
    private final DocRef planBDocRef;
    @JsonProperty
    private final long effectiveTime;
    @JsonProperty
    private final Long currentSnapshotTime;

    @JsonCreator
    public SnapshotRequest(@JsonProperty("planBDocRef") final DocRef planBDocRef,
                           @JsonProperty("effectiveTime") final long effectiveTime,
                           @JsonProperty("currentSnapshotTime")final Long currentSnapshotTime) {
        this.planBDocRef = planBDocRef;
        this.effectiveTime = effectiveTime;
        this.currentSnapshotTime = currentSnapshotTime;
    }

    public DocRef getPlanBDocRef() {
        return planBDocRef;
    }

    public long getEffectiveTime() {
        return effectiveTime;
    }

    public Long getCurrentSnapshotTime() {
        return currentSnapshotTime;
    }
}
