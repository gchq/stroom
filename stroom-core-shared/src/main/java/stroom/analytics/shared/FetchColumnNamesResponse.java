package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FetchColumnNamesResponse {

    @JsonProperty
    private final List<String> columnNames;
    @JsonProperty
    private final boolean storeInitialised;

    @JsonCreator
    public FetchColumnNamesResponse(@JsonProperty("columnNames") final List<String> columnNames,
                                    @JsonProperty("storeInitialised") final boolean storeInitialised) {
        this.columnNames = columnNames;
        this.storeInitialised = storeInitialised;
    }

    public static FetchColumnNamesResponse unInitialised() {
        return new FetchColumnNamesResponse(Collections.emptyList(), false);
    }

    public static FetchColumnNamesResponse initialised(final List<String> columnNames) {
        return new FetchColumnNamesResponse(Objects.requireNonNull(columnNames), true);
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public boolean isStoreInitialised() {
        return storeInitialised;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final FetchColumnNamesResponse that = (FetchColumnNamesResponse) object;
        return storeInitialised == that.storeInitialised && Objects.equals(columnNames, that.columnNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnNames, storeInitialised);
    }

    @Override
    public String toString() {
        return "FetchColumnNamesResponse{" +
               "columnNames=" + columnNames +
               ", storeInitialised=" + storeInitialised +
               '}';
    }
}
