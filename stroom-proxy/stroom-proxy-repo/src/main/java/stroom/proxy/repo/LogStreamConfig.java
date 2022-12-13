package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;


@JsonPropertyOrder(alphabetic = true)
public class LogStreamConfig extends AbstractConfig implements IsProxyConfig {

    private final SortedSet<String> metaKeys;

    public LogStreamConfig() {
        // SortedSet so the order the keys appear in the log entries can be controlled
        this(Collections.unmodifiableSortedSet(new TreeSet<>(Set.of(
                "guid",
                "feed",
                "system",
                "environment",
                "remotehost",
                "remoteaddress",
                "remotedn",
                "remotecertexpiry"))));
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public LogStreamConfig(@JsonProperty("metaKeys") final SortedSet<String> metaKeys) {
        this.metaKeys = Collections.unmodifiableSortedSet(metaKeys);
    }

    /**
     * Optional log line with header attributes output as defined by this property
     * @return
     */
    @JsonProperty
    public SortedSet<String> getMetaKeys() {
        return metaKeys;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LogStreamConfig that = (LogStreamConfig) o;
        return Objects.equals(metaKeys, that.metaKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaKeys);
    }

    @Override
    public String toString() {
        return "LogStreamConfig{" +
                "metaKeys='" + metaKeys + '\'' +
                '}';
    }
}
