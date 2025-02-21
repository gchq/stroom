package stroom.index.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AddField {

    @JsonProperty
    private final DocRef indexDocRef;
    @JsonProperty
    private final IndexFieldImpl indexField;

    @JsonCreator
    public AddField(@JsonProperty("indexDocRef") final DocRef indexDocRef,
                    @JsonProperty("indexField") final IndexFieldImpl indexField) {
        this.indexDocRef = indexDocRef;
        this.indexField = indexField;
    }

    public DocRef getIndexDocRef() {
        return indexDocRef;
    }

    public IndexFieldImpl getIndexField() {
        return indexField;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AddField that = (AddField) o;
        return Objects.equals(indexDocRef, that.indexDocRef) &&
               Objects.equals(indexField, that.indexField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexDocRef, indexField);
    }

    @Override
    public String toString() {
        return "AddField{" +
               "indexDocRef=" + indexDocRef +
               ", indexField=" + indexField +
               '}';
    }
}
