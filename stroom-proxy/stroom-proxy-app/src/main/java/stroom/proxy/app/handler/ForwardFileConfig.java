package stroom.proxy.app.handler;

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
public class ForwardFileConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String path;

    public ForwardFileConfig() {
        enabled = true;
        instant = false;
        name = null;
        path = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardFileConfig(@JsonProperty("enabled") final boolean enabled,
                             @JsonProperty("instant") final boolean instant,
                             @JsonProperty("name") final String name,
                             @JsonProperty("path") final String path) {
        this.enabled = enabled;
        this.instant = instant;
        this.name = name;
        this.path = path;
    }

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @JsonProperty
    public boolean isEnabled() {
        return enabled;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("Should data be forwarded instantly during the receipt process, i.e. must we" +
            " successfully forward before returning a success response to the sender.")
    public boolean isInstant() {
        return instant;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The name of the destination. Must be supplied.")
    public String getName() {
        return name;
    }

    /**
     * The string to use for the destination path.
     */
    @NotNull
    @ValidDirectoryPath(ensureExistence = true)
    @JsonProperty
    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final ForwardFileConfig that)) {
            return false;
        }
        return enabled == that.enabled &&
                instant == that.instant &&
                Objects.equals(name, that.name) &&
                Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, instant, name, path);
    }

    @Override
    public String toString() {
        return "ForwardFileConfig{" +
                "enabled=" + enabled +
                ", instant=" + instant +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}
