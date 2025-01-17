package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class SnapshotRequest {

    @JsonProperty
    private final String mapName;
    @JsonProperty
    private final long effectiveTime;
    @JsonProperty
    private final Long currentSnapshotTime;

    @JsonCreator
    public SnapshotRequest(@JsonProperty("mapName") final String mapName,
                           @JsonProperty("effectiveTime") final long effectiveTime,
                           @JsonProperty("currentSnapshotTime")final Long currentSnapshotTime) {
        this.mapName = mapName;
        this.effectiveTime = effectiveTime;
        this.currentSnapshotTime = currentSnapshotTime;
    }

    public String getMapName() {
        return mapName;
    }

    public long getEffectiveTime() {
        return effectiveTime;
    }

    public Long getCurrentSnapshotTime() {
        return currentSnapshotTime;
    }
}
