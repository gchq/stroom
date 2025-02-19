package stroom.index.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DeleteField {

    @JsonProperty
    private final DocRef indexDocRef;
    @JsonProperty
    private final String fieldName;

    @JsonCreator
    public DeleteField(@JsonProperty("indexDocRef") final DocRef indexDocRef,
                       @JsonProperty("fieldName") final String fieldName) {
        this.indexDocRef = indexDocRef;
        this.fieldName = fieldName;
    }

    public DocRef getIndexDocRef() {
        return indexDocRef;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteField that = (DeleteField) o;
        return Objects.equals(indexDocRef, that.indexDocRef) &&
               Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexDocRef, fieldName);
    }

    @Override
    public String toString() {
        return "DeleteField{" +
               "indexDocRef=" + indexDocRef +
               ", fieldName=" + fieldName +
               '}';
    }
}
