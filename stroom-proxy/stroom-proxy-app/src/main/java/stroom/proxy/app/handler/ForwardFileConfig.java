package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.validation.ValidDirectoryPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public class ForwardFileConfig extends AbstractConfig implements IsProxyConfig {

    private static final String DEFAULT_SUB_PATH_TEMPLATE = "${feed}";

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String path;
    private final String subPathTemplate;

    public ForwardFileConfig() {
        enabled = true;
        instant = false;
        name = null;
        path = null;
        subPathTemplate = DEFAULT_SUB_PATH_TEMPLATE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardFileConfig(@JsonProperty("enabled") final boolean enabled,
                             @JsonProperty("instant") final boolean instant,
                             @JsonProperty("name") final String name,
                             @JsonProperty("path") final String path,
                             @JsonProperty("subPathTemplate") final String subPathTemplate) {
        this.enabled = enabled;
        this.instant = instant;
        this.name = name;
        this.path = path;
        this.subPathTemplate = subPathTemplate;
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

    /**
     * The template to create subdirectories within the 'path' directory.
     * e.g. <code>${feed}/${year}/${month}/${day}</code>
     * Must be a relative path.
     * Supported template parameters (must be lower-case) are:
     * <ul>
     *     <li><code>${feed}</code></li>
     *     <li><code>${type}</code></li>
     *     <li><code>${year}</code></li>
     *     <li><code>${month}</code></li>
     *     <li><code>${day}</code></li>
     *     <li><code>${hour}</code></li>
     *     <li><code>${minute}</code></li>
     *     <li><code>${second}</code></li>
     *     <li><code>${millis}</code></li>
     *     <li><code>${ms}</code></li>
     * </ul>
     */
    @Pattern(regexp = "^[^/].*$") // Relative paths only
    @JsonProperty
    public String getSubPathTemplate() {
        return subPathTemplate;
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
        return enabled == that.enabled && instant == that.instant && Objects.equals(name,
                that.name) && Objects.equals(path, that.path) && Objects.equals(subPathTemplate,
                that.subPathTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, instant, name, path, subPathTemplate);
    }

    @Override
    public String toString() {
        return "ForwardFileConfig{" +
               "enabled=" + enabled +
               ", instant=" + instant +
               ", name='" + name + '\'' +
               ", path='" + path + '\'' +
               ", subPathTemplate='" + subPathTemplate + '\'' +
               '}';
    }
}
