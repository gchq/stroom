package stroom.util.sysinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

// Wraps a list to avoid json ser/deser issues with generic types
@JsonInclude(Include.NON_DEFAULT)
@JsonPropertyOrder(alphabetic = true)
public class SystemInfoResultList {

    @JsonProperty("results")
    private final List<SystemInfoResult> results;

    @JsonCreator
    public SystemInfoResultList(@JsonProperty("results") final List<SystemInfoResult> results) {
        this.results = results;
    }

    public static SystemInfoResultList of(final List<SystemInfoResult> results) {
        return new SystemInfoResultList(results);
    }

    public static SystemInfoResultList of(final SystemInfoResult... results) {
        return new SystemInfoResultList(List.of(results));
    }

    public List<SystemInfoResult> getResults() {
        return results;
    }

    @Override
    public String toString() {
        return "SystemInfoResultList{" +
                "results=" + results +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SystemInfoResultList that = (SystemInfoResultList) o;
        return Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(results);
    }
}
