package stroom.planb.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "useSnapshotsForLookup",
        "useSnapshotsForGet",
        "useSnapshotsForQuery"
})
@JsonInclude(Include.NON_NULL)
public class SnapshotSettings {
    @JsonProperty
    private final boolean useSnapshotsForLookup;
    @JsonProperty
    private final boolean useSnapshotsForGet;
    @JsonProperty
    private final boolean useSnapshotsForQuery;

    public SnapshotSettings() {
        this.useSnapshotsForLookup = false;
        this.useSnapshotsForGet = false;
        this.useSnapshotsForQuery = false;
    }

    @JsonCreator
    public SnapshotSettings(@JsonProperty("useSnapshotsForLookup") final boolean useSnapshotsForLookup,
                            @JsonProperty("useSnapshotsForGet") final boolean useSnapshotsForGet,
                            @JsonProperty("useSnapshotsForQuery") final boolean useSnapshotsForQuery) {
        this.useSnapshotsForLookup = useSnapshotsForLookup;
        this.useSnapshotsForGet = useSnapshotsForGet;
        this.useSnapshotsForQuery = useSnapshotsForQuery;
    }

    public boolean isUseSnapshotsForLookup() {
        return useSnapshotsForLookup;
    }

    public boolean isUseSnapshotsForGet() {
        return useSnapshotsForGet;
    }

    public boolean isUseSnapshotsForQuery() {
        return useSnapshotsForQuery;
    }
}
