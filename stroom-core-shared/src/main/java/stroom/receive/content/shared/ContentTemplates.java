package stroom.receive.content.shared;

import stroom.docref.DocRef;
import stroom.docref.DocRef.TypedBuilder;
import stroom.docs.shared.Description;
import stroom.docs.shared.NotDocumented;
import stroom.docstore.shared.AbstractSingletonDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@NotDocumented
@Description("")
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ContentTemplates extends AbstractSingletonDoc {

    public static final String TYPE = "ContentTemplates";

    // =============================================================================
    // DO NOT CHANGE THIS UUID VALUE as it should be the same on all stroom clusters
    // to allow import/export to work
    // =============================================================================
    public static final String SINGLETON_UUID = "4f05f416-c5e6-48ff-aee7-bd905a1cd7a7";

    public static final DocumentType DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            TYPE,
            "Content Auto-Creation Templates",
            SvgImage.STAMP);

    @JsonProperty
    private final List<ContentTemplate> contentTemplates;

    public ContentTemplates() {
        contentTemplates = Collections.emptyList();
    }

    @Override
    protected String getSingletonUuid() {
        return SINGLETON_UUID;
    }

    public ContentTemplates(final List<ContentTemplate> contentTemplates) {
        this.contentTemplates = resetTemplateNumbers(contentTemplates);
    }

    @JsonCreator
    public ContentTemplates(
            @JsonProperty("type") final String type,
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("contentTemplates") final List<ContentTemplate> contentTemplates) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);

        this.contentTemplates = resetTemplateNumbers(contentTemplates);
    }

    public static List<ContentTemplate> resetTemplateNumbers(final List<ContentTemplate> contentTemplates) {
        List<ContentTemplate> workingList;
        if (NullSafe.isEmptyCollection(contentTemplates)) {
            workingList = List.of();
        } else {
            // Ensure the template numbers are correct
            workingList = new ArrayList<>(contentTemplates.size());
            int templateNo = 1;
            for (final ContentTemplate contentTemplate : contentTemplates) {
                if (contentTemplate != null) {
                    if (contentTemplate.getTemplateNumber() == templateNo) {
                        workingList.add(contentTemplate);
                    } else {
                        workingList.add(contentTemplate.copy()
                                .withTemplateNumber(templateNo)
                                .build());
                    }
                    templateNo++;
                }
            }
            workingList = Collections.unmodifiableList(workingList);
        }
        return workingList;
    }

//    private ContentTemplates(final Builder builder) {
//        setType(builder.type);
//        setUuid(builder.uuid);
//        setName(builder.name);
//        setVersion(builder.version);
//        setCreateTimeMs(builder.createTimeMs);
//        setUpdateTimeMs(builder.updateTimeMs);
//        setCreateUser(builder.createUser);
//        setUpdateUser(builder.updateUser);
//        contentTemplates = builder.contentTemplates;
//    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public Builder copy() {
        return copy(this);
    }

    public static Builder copy(final ContentTemplates copy) {
        return new Builder(copy);
    }

    public List<ContentTemplate> getContentTemplates() {
        return contentTemplates;
    }

//    /**
//     * @return All templates, sorted by templateNumber
//     * (lowest number, highest priority, first)
//     */
//    @JsonIgnore
//    public List<ContentTemplate> getSortedContentTemplates() {
//        //noinspection SimplifyStreamApiCallChains // Cos GWT
//        return NullSafe.stream(contentTemplates)
//                .filter(Objects::nonNull)
//                .sorted(Comparator.comparing(ContentTemplate::getTemplateNumber))
//                .collect(Collectors.toUnmodifiableList());
//    }

    /**
     * @return A list of enabled {@link ContentTemplate}, ordered by their
     * templateNumber (lowest number, highest priority, first).
     */
    @JsonIgnore
    public List<ContentTemplate> getActiveTemplates() {
        //noinspection SimplifyStreamApiCallChains // Cos GWT
        return NullSafe.stream(contentTemplates)
                .filter(Objects::nonNull)
                .filter(ContentTemplate::isEnabled)
                .sorted(Comparator.comparing(ContentTemplate::getTemplateNumber))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentTemplates that = (ContentTemplates) o;
        return Objects.equals(contentTemplates, that.contentTemplates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentTemplates);
    }

    @Override
    public String toString() {
        return "ContentTemplates{" +
               "contentTemplates=" + contentTemplates +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder
            extends AbstractSingletonDoc.AbstractBuilder<ContentTemplates, ContentTemplates.Builder> {

        private List<ContentTemplate> contentTemplates;

        private Builder() {
        }

        private Builder(final ContentTemplates doc) {
            super(doc);
            this.contentTemplates = doc.contentTemplates;
        }

        @Override
        protected String getUuid() {
            return SINGLETON_UUID;
        }

        @Override
        protected String getType() {
            return TYPE;
        }

        public Builder withContentTemplates(final List<ContentTemplate> contentTemplates) {
            this.contentTemplates = contentTemplates;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ContentTemplates build() {
            return new ContentTemplates(
                    type,
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    contentTemplates);
        }
    }
}
