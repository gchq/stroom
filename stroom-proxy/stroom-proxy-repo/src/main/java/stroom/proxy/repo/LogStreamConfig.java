package stroom.proxy.repo;

import stroom.meta.api.StandardHeaderArguments;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@JsonPropertyOrder(alphabetic = true)
public class LogStreamConfig extends AbstractConfig implements IsProxyConfig {

    private final List<String> metaKeys;

    public LogStreamConfig() {
        // Linked
        this(List.of(
                StandardHeaderArguments.GUID,
                StandardHeaderArguments.RECEIPT_ID,
                StandardHeaderArguments.FEED,
                StandardHeaderArguments.SYSTEM,
                StandardHeaderArguments.ENVIRONMENT,
                StandardHeaderArguments.REMOTE_HOST,
                StandardHeaderArguments.REMOTE_ADDRESS,
                StandardHeaderArguments.REMOTE_DN,
                StandardHeaderArguments.REMOTE_CERT_EXPIRY));
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public LogStreamConfig(@JsonProperty("metaKeys") final List<String> metaKeys) {
        this.metaKeys = NullSafe.stream(metaKeys)
                .distinct()
                .collect(Collectors.toList());
    }

    @JsonProperty
    @JsonPropertyDescription("Optional log line with header attributes output as defined by this property." +
                             "The headers attributes that will be output in log lines." +
                             "They will be output in the order that they appear in this list." +
                             "Duplicates will be ignored, case does not matter.")
    public List<String> getMetaKeys() {
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
