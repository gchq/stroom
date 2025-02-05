package stroom.core.receive;

import stroom.security.shared.User;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.DocPath;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.UserType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


@JsonPropertyOrder(alphabetic = true)
public class AutoContentCreationConfig
        extends AbstractConfig
        implements IsStroomConfig {

    public static final String DEFAULT_DESTINATION_PATH_PART = "Feeds";
    public static final String DEFAULT_TEMPLATES_PATH_PART = "Content Templates";

    @JsonProperty
    private final boolean enabled;

    @JsonProperty
    private final String destinationExplorerPath;

    @JsonProperty
    private final String templatesExplorerPath;

    @JsonProperty
    private final String templateConfigDir;

    @JsonProperty
    private final String additionalGroupSuffix;

    @JsonProperty
    private final String createAsSubjectId;

    @JsonProperty
    private final UserType createAsType;

    public AutoContentCreationConfig() {
        enabled = false;
        destinationExplorerPath = DocPath.fromParts(DEFAULT_DESTINATION_PATH_PART)
                .toString();
        templatesExplorerPath = DocPath.fromParts(DEFAULT_DESTINATION_PATH_PART, DEFAULT_TEMPLATES_PATH_PART)
                .toString();
        templateConfigDir = "content_template_config";
        additionalGroupSuffix = " (sandbox)";
        createAsSubjectId = User.ADMINISTRATORS_GROUP_SUBJECT_ID;
        createAsType = UserType.GROUP;
    }

    @JsonCreator
    public AutoContentCreationConfig(@JsonProperty("enabled") final boolean enabled,
                                     @JsonProperty("destinationExplorerPath") final String destinationExplorerPath,
                                     @JsonProperty("templatesExplorerPath") final String templatesExplorerPath,
                                     @JsonProperty("templateConfigDir") final String templateConfigDir,
                                     @JsonProperty("additionalGroupSuffix") final String additionalGroupSuffix,
                                     @JsonProperty("createAsSubjectId") final String createAsSubjectId,
                                     @JsonProperty("createAsType") final UserType createAsType) {
        this.enabled = enabled;
        this.destinationExplorerPath = destinationExplorerPath;
        this.templatesExplorerPath = templatesExplorerPath;
        this.templateConfigDir = templateConfigDir;
        this.additionalGroupSuffix = additionalGroupSuffix;
        this.createAsSubjectId = createAsSubjectId;
        this.createAsType = createAsType;
    }

    private AutoContentCreationConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.destinationExplorerPath = builder.destinationPath;
        this.templatesExplorerPath = builder.templatesPath;
        this.templateConfigDir = builder.templateConfigDir;
        this.additionalGroupSuffix = builder.additionalGroupSuffix;
        this.createAsSubjectId = builder.createAsSubjectId;
        this.createAsType = builder.createAsType;
    }

    @JsonPropertyDescription(
            "Whether the auto-creation of content on data receipt is enabled or not. " +
            "If enabled, Stroom will automatically create content such as Feeds/XSLTs/Pipelines on receipt of " +
            "a data stream. The property 'templatesPath' will contain content to be used as templates for " +
            "auto-creation.")
    public boolean isEnabled() {
        return enabled;
    }

    @NotBlank
    @JsonPropertyDescription(
            "The path to a folder in the Stroom explorer tree where Stroom will auto-create " +
            "content. If it doesn't exist it will be created. Content will be created in a sub-folder of this " +
            "folder with a name derived from the system name of the received data.")
    public String getDestinationExplorerPath() {
        return destinationExplorerPath;
    }

    @JsonPropertyDescription(
            "The path to a folder in the Stroom explorer tree where Stroom will look for content " +
            "to use as a template for auto-creating content.")
    public String getTemplatesExplorerPath() {
        return templatesExplorerPath;
    }

    @JsonPropertyDescription(
            "The ")
    public String getTemplateConfigDir() {
        return templateConfigDir;
    }

    @JsonPropertyDescription(
            "If set, when Stroom auto-creates a feed, it will create an addition user group with " +
            "name '<system name><additionalGroupSuffix>'. This is in addition to creating a user group " +
            "called '<system name>'. If not set, only the latter user group will be created.")
    public String getAdditionalGroupSuffix() {
        return additionalGroupSuffix;
    }

    @NotNull
    @JsonPropertyDescription(
            "The subjectId of the user/group who will be the owner of any auto-created content. " +
            "This user/group must have the permission to create all content required.")
    public String getCreateAsSubjectId() {
        return createAsSubjectId;
    }

    @NotNull
    @JsonPropertyDescription("The type of the entity represented by createAsSubjectId, i.g. 'USER' or 'GROUP'")
    public UserType getCreateAsType() {
        return createAsType;
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "destinationPath must be an absolute path.")
    public boolean isDestinationPathValid() {
        if (destinationExplorerPath == null) {
            return true;
        } else {
            final DocPath docPath = DocPath.fromParts(destinationExplorerPath);
            return docPath.isAbsolute();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder()
                .enabled(enabled)
                .destinationPath(destinationExplorerPath)
                .templatesPath(templatesExplorerPath);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean enabled;
        private String destinationPath;
        private String templatesPath;
        public String templateConfigDir;
        public String additionalGroupSuffix;
        public String createAsSubjectId;
        public UserType createAsType;

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

        public Builder templateConfigDir(String templateConfigDir) {
            this.templateConfigDir = templateConfigDir;
            return this;
        }

        public Builder additionalGroupSuffix(String additionalGroupSuffix) {
            this.additionalGroupSuffix = additionalGroupSuffix;
            return this;
        }

        public Builder createAsSubjectId(String createAsSubjectId) {
            this.createAsSubjectId = createAsSubjectId;
            return this;
        }

        public Builder createAsType(UserType createAsType) {
            this.createAsType = createAsType;
            return this;
        }

        public Builder copy() {
            return new Builder()
                    .enabled(this.enabled)
                    .destinationPath(this.destinationPath)
                    .templatesPath(this.templatesPath)
                    .templateConfigDir(this.templateConfigDir)
                    .additionalGroupSuffix(this.additionalGroupSuffix)
                    .createAsSubjectId(this.createAsSubjectId)
                    .createAsType(this.createAsType);
        }

        public AutoContentCreationConfig build() {
            return new AutoContentCreationConfig(this);
        }
    }
}
