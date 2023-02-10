package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ExplorerNodeKey {

    @JsonProperty
    private final String type;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String rootNodeUuid;

    @JsonCreator
    public ExplorerNodeKey(@JsonProperty("type") final String type,
                           @JsonProperty("uuid") final String uuid,
                           @JsonProperty("rootNodeUuid") final String rootNodeUuid) {
        this.type = type;
        this.uuid = uuid;
        this.rootNodeUuid = rootNodeUuid;
    }

    public String getType() {
        return type;
    }

    public String getUuid() {
        return uuid;
    }

    public String getRootNodeUuid() {
        return rootNodeUuid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, uuid, rootNodeUuid);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExplorerNodeKey that = (ExplorerNodeKey) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(uuid, that.uuid) &&
                Objects.equals(rootNodeUuid, that.rootNodeUuid);
    }
}
