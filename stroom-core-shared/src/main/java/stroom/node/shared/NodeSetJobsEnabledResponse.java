package stroom.node.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class NodeSetJobsEnabledResponse implements Serializable {

    @JsonProperty
    private final Integer modifiedCount;

    @JsonCreator
    public NodeSetJobsEnabledResponse(@JsonProperty("modifiedCount") final Integer modifiedCount) {
        this.modifiedCount = modifiedCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeSetJobsEnabledResponse response = (NodeSetJobsEnabledResponse) o;
        return Objects.equals(modifiedCount, response.modifiedCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modifiedCount);
    }

    @Override
    public String toString() {
        return modifiedCount.toString();
    }
}
