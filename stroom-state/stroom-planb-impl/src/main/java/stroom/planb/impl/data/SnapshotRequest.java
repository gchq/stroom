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
