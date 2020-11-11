package stroom.query.common.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Arrays;

@JsonInclude(Include.NON_NULL)
public class CoprocessorKey implements Serializable {
    @JsonProperty
    private final int id;
    @JsonProperty
    private final String[] componentIds;

    @JsonCreator
    public CoprocessorKey(@JsonProperty("id") final int id,
                          @JsonProperty("componentIds") final String[] componentIds) {
        this.id = id;
        this.componentIds = componentIds;
    }

    public int getId() {
        return id;
    }

    public String[] getComponentIds() {
        return componentIds;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final CoprocessorKey that = (CoprocessorKey) o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return Arrays.toString(componentIds);
    }
}
