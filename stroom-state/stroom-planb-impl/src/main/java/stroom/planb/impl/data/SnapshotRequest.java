package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class SnapshotRequest {

    @JsonProperty
    private final String mapName;
    @JsonProperty
    private final long effectiveTime;

    @JsonCreator
    public SnapshotRequest(@JsonProperty("mapName") final String mapName,
                           @JsonProperty("effectiveTime") final long effectiveTime) {
        this.mapName = mapName;
        this.effectiveTime = effectiveTime;
    }

    public String getMapName() {
        return mapName;
    }

    public long getEffectiveTime() {
        return effectiveTime;
    }
}
