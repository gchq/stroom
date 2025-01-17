package stroom.receive.common;

import stroom.util.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class HashedDataFeedKeys {

    private final List<HashedDataFeedKey> hashedDataFeedKeys;

    @JsonCreator
    public HashedDataFeedKeys(@JsonProperty("dataFeedKeys") final List<HashedDataFeedKey> hashedDataFeedKeys) {
        this.hashedDataFeedKeys = hashedDataFeedKeys;
    }

    public List<HashedDataFeedKey> getDataFeedKeys() {
        return NullSafe.list(hashedDataFeedKeys);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final HashedDataFeedKeys that = (HashedDataFeedKeys) object;
        return Objects.equals(hashedDataFeedKeys, that.hashedDataFeedKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashedDataFeedKeys);
    }

    @Override
    public String toString() {
        return "DataFeedKeys{" +
               "dataFeedKeys=" + hashedDataFeedKeys +
               '}';
    }
}
