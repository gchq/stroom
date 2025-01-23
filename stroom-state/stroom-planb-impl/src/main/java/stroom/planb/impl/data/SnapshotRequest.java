package stroom.planb.impl.data;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

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
                           @JsonProperty("currentSnapshotTime") final Long currentSnapshotTime) {
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SnapshotRequest request = (SnapshotRequest) o;
        return effectiveTime == request.effectiveTime &&
               Objects.equals(planBDocRef, request.planBDocRef) &&
               Objects.equals(currentSnapshotTime, request.currentSnapshotTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planBDocRef, effectiveTime, currentSnapshotTime);
    }

    @Override
    public String toString() {
        return "SnapshotRequest{" +
               "planBDocRef=" + planBDocRef +
               ", effectiveTime=" + effectiveTime +
               ", currentSnapshotTime=" + currentSnapshotTime +
               '}';
    }
}
