package stroom.receive.common;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.DocPath;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import javax.validation.constraints.NotBlank;

@JsonPropertyOrder(alphabetic = true)
public class AutoContentCreationConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig {

    public static final String DEFAULT_DESTINATION_PATH_PART = "Feeds";
    public static final String DEFAULT_TEMPLATES_PATH_PART = "Content Templates";

    @JsonProperty
    private final boolean enabled;

    @JsonProperty
    private final String destinationPath;

    @JsonProperty
    private final String templatesPath;

    @JsonProperty
    private final String additionalGroupSuffix;

    public AutoContentCreationConfig() {
        enabled = false;
        destinationPath = DocPath.fromParts(DEFAULT_DESTINATION_PATH_PART)
                .toString();
        templatesPath = DocPath.fromParts(DEFAULT_DESTINATION_PATH_PART, DEFAULT_TEMPLATES_PATH_PART)
                .toString();
        additionalGroupSuffix = " (sandbox)";
    }

    public AutoContentCreationConfig(@JsonProperty("enabled") final boolean enabled,
                                     @JsonProperty("destinationPath") final String destinationPath,
                                     @JsonProperty("templatesPath") final String templatesPath,
                                     @JsonProperty("additionalGroupSuffix") final String additionalGroupSuffix) {
        this.enabled = enabled;
        this.destinationPath = destinationPath;
        this.templatesPath = templatesPath;
        this.additionalGroupSuffix = additionalGroupSuffix;
    }

    private AutoContentCreationConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.destinationPath = builder.destinationPath;
        this.templatesPath = builder.templatesPath;
        this.additionalGroupSuffix = builder.additionalGroupSuffix;
    }

    @JsonPropertyDescription("Whether the auto-creation of content on data receipt is enabled or not. " +
            "If enabled, Stroom will automatically create content such as Feeds/XSLTs/Pipelines on receipt of " +
            "a data stream. The property 'templatesPath' will contain content to be used as templates for " +
            "auto-creation.")
    public boolean isEnabled() {
        return enabled;
    }

    @NotBlank
    @JsonPropertyDescription("The path to a folder in the Stroom explorer tree where Stroom will auto-create " +
            "content. If it doesn't exist it will be created. Content will be created in a sub-folder of this " +
            "folder with a name derived from the system name of the received data.")
    public String getDestinationPath() {
        return destinationPath;
    }

    @JsonPropertyDescription("The path to a folder in the Stroom explorer tree where Stroom will look for content " +
            "to use as a template for auto-creating content.")
    public String getTemplatesPath() {
        return templatesPath;
    }

    @JsonPropertyDescription("If set, when Stroom auto-creates a feed, it will create an addition user group with " +
            "name '<system name><additionalGroupSuffix>'. This is in addition to creating a user group " +
            "called '<system name>'. If not set, only the latter user group will be created.")
    public String getAdditionalGroupSuffix() {
        return additionalGroupSuffix;
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "destinationPath must be an absolute path.")
    public boolean isDestinationPathValid() {
        if (destinationPath == null) {
            return true;
        } else {
            final DocPath docPath = DocPath.fromParts(destinationPath);
            return docPath.isAbsolute();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder()
                .enabled(enabled)
                .destinationPath(destinationPath)
                .templatesPath(templatesPath);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean enabled;
        private String destinationPath;
        private String templatesPath;
        public String additionalGroupSuffix;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder destinationPath(String destinationPath) {
            this.destinationPath = destinationPath;
            return this;
        }

        public Builder templatesPath(String templatesPath) {
            this.templatesPath = templatesPath;
            return this;
        }

        public Builder additionalGroupSuffix(String additionalGroupSuffix) {
            this.additionalGroupSuffix = additionalGroupSuffix;
            return this;
        }

        public Builder copy() {
            return new Builder()
                    .enabled(this.enabled)
                    .destinationPath(this.destinationPath)
                    .templatesPath(this.templatesPath)
                    .additionalGroupSuffix(this.additionalGroupSuffix);
        }

        public AutoContentCreationConfig build() {
            return new AutoContentCreationConfig(this);
        }
    }
}
