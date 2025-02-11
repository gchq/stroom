package stroom.receive.content;

import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ContentTemplates extends Doc {

    public static final String TYPE = "ContentTemplates";
    public static final DocumentType DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            TYPE,
            "Content Auto-Creation Templates",
            SvgImage.SETTINGS);

    @JsonProperty
    private final List<ContentTemplate> contentTemplates;

    public ContentTemplates() {
        contentTemplates = Collections.emptyList();
    }

    public ContentTemplates(final List<ContentTemplate> contentTemplates) {
        this.contentTemplates = GwtNullSafe.list(contentTemplates);
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
        this.contentTemplates = GwtNullSafe.list(contentTemplates);
        final Set<Integer> templateNumbers = new HashSet<>(contentTemplates.size());
        for (final ContentTemplate contentTemplate : contentTemplates) {
            final boolean didAdd = templateNumbers.add(contentTemplate.getTemplateNumber());
            if (!didAdd) {
                throw new IllegalArgumentException("Duplicate templateNumber " + contentTemplate.getTemplateNumber());
            }
        }
    }

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
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public List<ContentTemplate> getContentTemplates() {
        return contentTemplates;
    }

    /**
     * @return A list of enabled {@link ContentTemplate}, ordered by their
     * templateNumber (lowest number, highest priority, first).
     */
    @JsonIgnore
    public List<ContentTemplate> getActiveTemplates() {
        return GwtNullSafe.stream(contentTemplates)
                .filter(Objects::nonNull)
                .filter(ContentTemplate::isEnabled)
                .sorted(Comparator.comparing(ContentTemplate::getTemplateNumber))
                .collect(Collectors.toList());
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
}
