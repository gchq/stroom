package stroom.proxy.repo;

import stroom.meta.api.StandardHeaderArguments;
import stroom.util.collections.CollectionUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;


@JsonPropertyOrder(alphabetic = true)
public class LogStreamConfig extends AbstractConfig implements IsProxyConfig {

    // Is a list, so they get logged in the desired order
    private final List<String> metaKeys;

    public LogStreamConfig() {
        this(List.of(
                StandardHeaderArguments.GUID,
                StandardHeaderArguments.RECEIPT_ID,
                StandardHeaderArguments.FEED,
                StandardHeaderArguments.SYSTEM,
                StandardHeaderArguments.ENVIRONMENT,
                StandardHeaderArguments.REMOTE_HOST,
                StandardHeaderArguments.REMOTE_ADDRESS,
                StandardHeaderArguments.REMOTE_DN,
                StandardHeaderArguments.REMOTE_CERT_EXPIRY,
                StandardHeaderArguments.DATA_RECEIPT_RULE));
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public LogStreamConfig(@JsonProperty("metaKeys") final List<String> metaKeys) {
        this.metaKeys = CollectionUtil.cleanItems(metaKeys, String::trim, true);
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
