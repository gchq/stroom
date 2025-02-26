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

    public static final String PROP_NAME_SUB_PATH_TEMPLATE = "subPathTemplate";
    public static final TemplatingMode DEFAULT_TEMPLATING_MODE = TemplatingMode.REPLACE_UNKNOWN;

    private static final String DEFAULT_SUB_PATH_TEMPLATE = "${year}${month}${day}/${feed}";

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String path;
    private final String subPathTemplate;
    private final TemplatingMode templatingMode;

    public ForwardFileConfig() {
        enabled = true;
        instant = false;
        name = null;
        path = null;
        subPathTemplate = DEFAULT_SUB_PATH_TEMPLATE;
        templatingMode = DEFAULT_TEMPLATING_MODE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardFileConfig(@JsonProperty("enabled") final boolean enabled,
                             @JsonProperty("instant") final boolean instant,
                             @JsonProperty("name") final String name,
                             @JsonProperty("path") final String path,
                             @JsonProperty(PROP_NAME_SUB_PATH_TEMPLATE) final String subPathTemplate,
                             @JsonProperty("templatingMode") final TemplatingMode templatingMode) {
        this.enabled = enabled;
        this.instant = instant;
        this.name = name;
        this.path = path;
        this.subPathTemplate = subPathTemplate;
        this.templatingMode = Objects.requireNonNullElse(templatingMode, DEFAULT_TEMPLATING_MODE);
    }

    private ForwardFileConfig(final Builder builder) {
        enabled = builder.enabled;
        instant = builder.instant;
        name = builder.name;
        path = builder.path;
        subPathTemplate = builder.subPathTemplate;
        templatingMode = builder.templatingMode;
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
    @JsonPropertyDescription("The unique name of the destination (across all file/http forward destinations. " +
                             "The name is used in the directories on the file system, so do not change the name " +
                             "once proxy has processed data. Must be provided.")
    public String getName() {
        return name;
    }

    /**
     * The string to use for the destination path.
     */
    @NotNull
    @ValidDirectoryPath(ensureExistence = true)
    @JsonPropertyDescription("The string to use for the destination path.")
    @JsonProperty
    public String getPath() {
        return path;
    }

    /**
     * The template to create subdirectories within the 'path' directory.
     * The default value is <code>${year}${month}${day}/${feed}</code>
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
    @JsonPropertyDescription("The templated relative sub-path of path. " +
                             "The default value is '" + DEFAULT_SUB_PATH_TEMPLATE + "'. " +
                             "Cannot be an absolute path and must resolve to a descendant of path.")
    @JsonProperty
    public String getSubPathTemplate() {
        return subPathTemplate;
    }

    @JsonPropertyDescription("How to handle unknown parameters in the subPathTemplate. " +
                             "Default value is 'REPLACE_UNKNOWN'.")
    @JsonProperty
    public TemplatingMode getTemplatingMode() {
        return templatingMode;
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
                that.subPathTemplate) && templatingMode == that.templatingMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, instant, name, path, subPathTemplate, templatingMode);
    }

    @Override
    public String toString() {
        return "ForwardFileConfig{" +
               "enabled=" + enabled +
               ", instant=" + instant +
               ", name='" + name + '\'' +
               ", path='" + path + '\'' +
               ", subPathTemplate='" + subPathTemplate + '\'' +
               ", templatingMode=" + templatingMode +
               '}';
    }

    public static Builder builder() {
        return builder(new ForwardFileConfig());
    }

    public static Builder builder(final ForwardFileConfig copy) {
        Builder builder = new Builder();
        builder.enabled = copy.isEnabled();
        builder.instant = copy.isInstant();
        builder.name = copy.getName();
        builder.path = copy.getPath();
        builder.subPathTemplate = copy.getSubPathTemplate();
        builder.templatingMode = copy.getTemplatingMode();
        return builder;
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private boolean enabled;
        private boolean instant;
        private String name;
        private String path;
        private String subPathTemplate;
        private TemplatingMode templatingMode;

        private Builder() {
        }

        public Builder enabled() {
            this.enabled = true;
            return this;
        }

        public Builder disabled() {
            this.enabled = false;
            return this;
        }

        public Builder withEnabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder instant() {
            this.instant = true;
            return this;
        }

        public Builder withInstant(final boolean instant) {
            this.instant = instant;
            return this;
        }

        public Builder withName(final String name) {
            this.name = name;
            return this;
        }

        public Builder withPath(final String path) {
            this.path = path;
            return this;
        }

        public Builder withSubPathTemplate(final String subPathTemplate) {
            this.subPathTemplate = subPathTemplate;
            return this;
        }

        public Builder withTemplatingMode(final TemplatingMode templatingMode) {
            this.templatingMode = templatingMode;
            return this;
        }

        public ForwardFileConfig build() {
            return new ForwardFileConfig(this);
        }
    }
}
