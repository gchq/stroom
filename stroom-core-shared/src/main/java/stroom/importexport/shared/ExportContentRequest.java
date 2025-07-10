package stroom.importexport.shared;

import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ExportContentRequest {

    @JsonProperty
    private final Set<DocRef> docRefs;
    @JsonProperty
    private final boolean includeProcessorFilters;

    @JsonCreator
    public ExportContentRequest(@JsonProperty("docRefs") final Set<DocRef> docRefs,
                                @JsonProperty("includeProcessorFilters") final Boolean includeProcessorFilters) {
        this.docRefs = NullSafe.unmodifialbeCopyOf(docRefs);
        this.includeProcessorFilters = NullSafe.requireNonNullElse(includeProcessorFilters, false);
    }

    public ExportContentRequest(final DocRef docRef, final boolean includeProcessorFilters) {
        this(NullSafe.singletonSet(docRef), includeProcessorFilters);
    }

    public Set<DocRef> getDocRefs() {
        return docRefs;
    }

    public boolean isIncludeProcessorFilters() {
        return includeProcessorFilters;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final ExportContentRequest that = (ExportContentRequest) object;
        return includeProcessorFilters == that.includeProcessorFilters && Objects.equals(docRefs, that.docRefs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRefs, includeProcessorFilters);
    }

    @Override
    public String toString() {
        return "ExportContentRequest{" +
               "docRefs=" + docRefs +
               ", includeProcessorFilters=" + includeProcessorFilters +
               '}';
    }
}
