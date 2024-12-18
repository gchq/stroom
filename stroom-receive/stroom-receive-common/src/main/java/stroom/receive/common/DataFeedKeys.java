package stroom.receive.common;

import stroom.util.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class DataFeedKeys {

    private final List<DataFeedKey> dataFeedKeys;

    @JsonCreator
    public DataFeedKeys(@JsonProperty("dataFeedKeys") final List<DataFeedKey> dataFeedKeys) {
        this.dataFeedKeys = dataFeedKeys;
    }

    public List<DataFeedKey> getDataFeedKeys() {
        return NullSafe.list(dataFeedKeys);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DataFeedKeys that = (DataFeedKeys) object;
        return Objects.equals(dataFeedKeys, that.dataFeedKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataFeedKeys);
    }

    @Override
    public String toString() {
        return "DataFeedKeys{" +
               "dataFeedKeys=" + dataFeedKeys +
               '}';
    }
}
