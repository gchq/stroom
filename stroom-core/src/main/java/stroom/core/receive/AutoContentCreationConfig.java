package stroom.core.receive;

import stroom.meta.api.StandardHeaderArguments;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.DocPath;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.UserType;
import stroom.util.shared.validation.AllMatchPattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;


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
    private final String additionalGroupSuffix;
    @JsonProperty
    private final String createAsSubjectId;
    @JsonProperty
    private final UserType createAsType;
    @JsonProperty
    private final Set<String> templateMatchFields;

    public AutoContentCreationConfig() {
        enabled = false;
        destinationExplorerPath = DocPath.fromParts(DEFAULT_DESTINATION_PATH_PART)
                .toString();
        additionalGroupSuffix = " (sandbox)";
        createAsSubjectId = User.ADMINISTRATORS_GROUP_SUBJECT_ID;
        createAsType = UserType.GROUP;
        // TreeSet to ensure consistent order in the serialised json
        // Make all lower case as expression matching is case-sense on field name and we
        // can't be sure what case is used in the receipt headers.
        templateMatchFields = normaliseFields(Set.of(
                StandardHeaderArguments.FEED,
                StandardHeaderArguments.ACCOUNT_ID,
                StandardHeaderArguments.ACCOUNT_NAME,
                StandardHeaderArguments.COMPONENT,
                StandardHeaderArguments.FORMAT,
                StandardHeaderArguments.SCHEMA,
                StandardHeaderArguments.SCHEMA_VERSION));
    }

    @JsonCreator
    public AutoContentCreationConfig(
            @JsonProperty("enabled") final boolean enabled,
            @JsonProperty("destinationExplorerPath") final String destinationExplorerPath,
            @JsonProperty("additionalGroupSuffix") final String additionalGroupSuffix,
            @JsonProperty("createAsSubjectId") final String createAsSubjectId,
            @JsonProperty("createAsType") final UserType createAsType,
            @JsonProperty("templateMatchFields") final Set<String> templateMatchFields) {

        this.enabled = enabled;
        this.destinationExplorerPath = destinationExplorerPath;
        this.additionalGroupSuffix = additionalGroupSuffix;
        this.createAsSubjectId = createAsSubjectId;
        this.createAsType = createAsType;
        this.templateMatchFields = normaliseFields(templateMatchFields);
    }

    private AutoContentCreationConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.destinationExplorerPath = builder.destinationPath;
        this.additionalGroupSuffix = builder.additionalGroupSuffix;
        this.createAsSubjectId = builder.createAsSubjectId;
        this.createAsType = builder.createAsType;
        this.templateMatchFields = normaliseFields(builder.templateMatchFields);
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
            "If set, when Stroom auto-creates a feed, it will create an addition user group with " +
            "name '<system name><additionalGroupSuffix>'. This is in addition to creating a user group " +
            "called '<system name>'. If not set, only the latter user group will be created.")
    public String getAdditionalGroupSuffix() {
        return additionalGroupSuffix;
    }

    @NotNull
    @JsonPropertyDescription(
            "The subjectId of the user/group who will be the owner of any auto-created content. " +
            "This user/group must have the permission to create all content required. It will also be the " +
            "'run as' user for created pipeline processor filters.")
    public String getCreateAsSubjectId() {
        return createAsSubjectId;
    }

    @NotNull
    @JsonPropertyDescription("The type of the entity represented by createAsSubjectId, i.g. 'USER' or 'GROUP'. " +
                             "It is possible for content to be owned by a group rather than individual users.")
    public UserType getCreateAsType() {
        return createAsType;
    }

    @AllMatchPattern(pattern = "^[a-z0-9_-]+$")
    @JsonPropertyDescription("The header keys available for use when matching a request to a content template. " +
                             "Must be in lower case.")
    public Set<String> getTemplateMatchFields() {
        return templateMatchFields;
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
                .additionalGroupSuffix(additionalGroupSuffix)
                .createAsSubjectId(createAsSubjectId)
                .createAsType(createAsType)
                .templateMatchFields(templateMatchFields);
    }

    private static Set<String> normaliseFields(final Set<String> fields) {
        // TreeSet to ensure consistent order in the serialised json
        // Make all lower case as expression matching is case-sense on field name and we
        // can't be sure what case is used in the receipt headers.
        if (NullSafe.isEmptyCollection(fields)) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableNavigableSet(NullSafe.stream(fields)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toCollection(TreeSet::new)));
        }
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean enabled;
        private String destinationPath;
        private String additionalGroupSuffix;
        private String createAsSubjectId;
        private UserType createAsType;
        private Set<String> templateMatchFields;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder destinationPath(String destinationPath) {
            this.destinationPath = destinationPath;
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

        public Builder templateMatchFields(Set<String> templateMatchFields) {
            this.templateMatchFields = NullSafe.mutableSet(templateMatchFields);
            return this;
        }

        public Builder addTemplateMatchFields(String templateMatchField) {
            if (templateMatchFields == null) {
                templateMatchFields = new HashSet<>();
            }
            templateMatchFields.add(templateMatchField);
            return this;
        }

        public Builder copy() {
            return new Builder()
                    .enabled(this.enabled)
                    .destinationPath(this.destinationPath)
                    .additionalGroupSuffix(this.additionalGroupSuffix)
                    .createAsSubjectId(this.createAsSubjectId)
                    .createAsType(this.createAsType)
                    .templateMatchFields(this.templateMatchFields);
        }

        public AutoContentCreationConfig build() {
            return new AutoContentCreationConfig(this);
        }
    }
}
