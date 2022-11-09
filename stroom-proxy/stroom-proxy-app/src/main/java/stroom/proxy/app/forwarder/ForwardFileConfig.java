package stroom.proxy.app.forwarder;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.validation.ValidDirectoryPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public class ForwardFileConfig extends AbstractConfig implements ForwardConfig, IsProxyConfig {

    private final boolean enabled;
    private final String name;
    private final String path;

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardFileConfig(@JsonProperty("enabled") final boolean enabled,
                             @JsonProperty("name") final String name,
                             @JsonProperty("path") final String path) {
        this.enabled = enabled;
        this.name = name;
        this.path = path;
    }

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @JsonProperty
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The name of the destination. Must be supplied.")
    @Override
    public String getName() {
        return name;
    }

    /**
     * The string to use for the destination path.
     */
    @NotNull
    @ValidDirectoryPath
    @JsonProperty
    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ForwardFileConfig that = (ForwardFileConfig) o;
        return enabled == that.enabled && Objects.equals(name, that.name) && Objects.equals(path,
                that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, name, path);
    }

    @Override
    public String toString() {
        return "ForwardFileConfig{" +
                "enabled=" + enabled +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}
