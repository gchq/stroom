package stroom.core.receive;

import stroom.meta.api.StandardHeaderArguments;
import stroom.security.shared.User;
import stroom.util.collections.CollectionUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.DocPath;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;
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

import java.util.HashSet;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
public class AutoContentCreationConfig
        extends AbstractConfig
        implements IsStroomConfig {

    public static final String DEFAULT_DESTINATION_BASE_PART = "Feeds";
    public static final String DEFAULT_DESTINATION_SUB_DIR_PART = "${accountid}";
    public static final String DEFAULT_GROUP_TEMPLATE = "grp-${accountid}";
    public static final String DEFAULT_ADDITIONAL_GROUP_TEMPLATE = "grp-${accountid}-sandbox";

    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final String destinationExplorerPathTemplate;
    @JsonProperty
    private final String groupTemplate;
    @JsonProperty
    private final String additionalGroupTemplate;
    @JsonProperty
    private final String createAsSubjectId;
    @JsonProperty
    private final UserType createAsType;
    @JsonProperty
    private final Set<String> templateMatchFields;

    public AutoContentCreationConfig() {
        enabled = false;
        destinationExplorerPathTemplate = DocPath.fromParts(
                        DEFAULT_DESTINATION_BASE_PART,
                        DEFAULT_DESTINATION_SUB_DIR_PART)
                .toString();
        groupTemplate = DEFAULT_GROUP_TEMPLATE;
        additionalGroupTemplate = DEFAULT_ADDITIONAL_GROUP_TEMPLATE;
        createAsSubjectId = User.ADMINISTRATORS_GROUP_SUBJECT_ID;
        createAsType = UserType.GROUP;
        // Ensure consistent order in the serialised json
        // Make all lower case as expression matching is case-sense on field name, and we
        // can't be sure what case is used in the receipt headers.
        templateMatchFields = CollectionUtil.asUnmodifiabledConsistentOrderSet(normaliseFields(Set.of(
                StandardHeaderArguments.FEED,
                StandardHeaderArguments.ACCOUNT_ID,
                StandardHeaderArguments.ACCOUNT_NAME,
                StandardHeaderArguments.COMPONENT,
                StandardHeaderArguments.FORMAT,
                StandardHeaderArguments.SCHEMA,
                StandardHeaderArguments.SCHEMA_VERSION)));
    }

    @JsonCreator
    public AutoContentCreationConfig(
            @JsonProperty("enabled") final boolean enabled,
            @JsonProperty("destinationExplorerPathTemplate") final String destinationExplorerPathTemplate,
            @JsonProperty("groupTemplate") final String groupTemplate,
            @JsonProperty("additionalGroupTemplate") final String additionalGroupTemplate,
            @JsonProperty("createAsSubjectId") final String createAsSubjectId,
            @JsonProperty("createAsType") final UserType createAsType,
            @JsonProperty("templateMatchFields") final Set<String> templateMatchFields) {

        this.enabled = enabled;
        this.destinationExplorerPathTemplate = destinationExplorerPathTemplate;
        this.groupTemplate = NullSafe.nonBlankStringElse(groupTemplate, DEFAULT_GROUP_TEMPLATE);
        this.additionalGroupTemplate = additionalGroupTemplate;
        this.createAsSubjectId = createAsSubjectId;
        this.createAsType = createAsType;
        this.templateMatchFields = normaliseFields(templateMatchFields);
    }

    private AutoContentCreationConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.destinationExplorerPathTemplate = builder.destinationExplorerPathTemplate;
        this.groupTemplate = builder.groupTemplate;
        this.additionalGroupTemplate = builder.additionalGroupTemplate;
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
            "The templated path to a folder in the Stroom explorer tree where Stroom will auto-create " +
            "content. If it doesn't exist it will be created. Content will be created in a sub-folder of this " +
            "folder with a name derived from the system name of the received data. By default this is " +
            "'Feeds/${accountid}'.")
    public String getDestinationExplorerPathTemplate() {
        return destinationExplorerPathTemplate;
    }

    @JsonPropertyDescription(
            "When Stroom auto-creates a feed, it will create a user group with a " +
            "name derived from this template. Default value is 'grp-${accountid}'.")
    public String getGroupTemplate() {
        return groupTemplate;
    }

    @JsonPropertyDescription(
            "If set, when Stroom auto-creates a feed, it will create an additional user group with a " +
            "name derived from this template. This is in addition to the user group defined by 'groupTemplate'." +
            "If not set, only the latter user group will be created. Default value is 'grp-${accountid}-sandbox'.")
    public String getAdditionalGroupTemplate() {
        return additionalGroupTemplate;
    }

    @NotNull
    @JsonPropertyDescription(
            "The subjectId of the user/group who the auto-created content will be created by, " +
            "typically a group with administrator privileges. " +
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
        if (destinationExplorerPathTemplate == null) {
            return true;
        } else {
            final DocPath docPath = DocPath.fromParts(destinationExplorerPathTemplate);
            return docPath.isAbsolute();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder()
                .enabled(enabled)
                .destinationPathTemplate(destinationExplorerPathTemplate)
                .groupTemplate(groupTemplate)
                .additionalGroupTemplate(additionalGroupTemplate)
                .createAsSubjectId(createAsSubjectId)
                .createAsType(createAsType)
                .templateMatchFields(templateMatchFields);
    }

    private static Set<String> normaliseFields(final Set<String> fields) {
        return CollectionUtil.cleanItems(fields, s -> s.trim().toLowerCase());
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean enabled;
        private String destinationExplorerPathTemplate;
        private String groupTemplate;
        private String additionalGroupTemplate;
        private String createAsSubjectId;
        private UserType createAsType;
        private Set<String> templateMatchFields;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder destinationPathTemplate(String destinationPathTemplate) {
            this.destinationExplorerPathTemplate = destinationPathTemplate;
            return this;
        }

        public Builder groupTemplate(String groupTemplate) {
            this.groupTemplate = groupTemplate;
            return this;
        }

        public Builder additionalGroupTemplate(String additionalGroupSuffix) {
            this.additionalGroupTemplate = additionalGroupSuffix;
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
                    .destinationPathTemplate(this.destinationExplorerPathTemplate)
                    .groupTemplate(this.groupTemplate)
                    .additionalGroupTemplate(this.additionalGroupTemplate)
                    .createAsSubjectId(this.createAsSubjectId)
                    .createAsType(this.createAsType)
                    .templateMatchFields(this.templateMatchFields);
        }

        public AutoContentCreationConfig build() {
            return new AutoContentCreationConfig(this);
        }
    }
}
