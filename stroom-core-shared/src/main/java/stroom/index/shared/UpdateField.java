package stroom.index.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class UpdateField {

    @JsonProperty
    private final DocRef indexDocRef;
    @JsonProperty
    private final String fieldName;
    @JsonProperty
    private final IndexFieldImpl indexField;

    @JsonCreator
    public UpdateField(@JsonProperty("indexDocRef") final DocRef indexDocRef,
                       @JsonProperty("fieldName") final String fieldName,
                       @JsonProperty("indexField") final IndexFieldImpl indexField) {
        this.indexDocRef = indexDocRef;
        this.fieldName = fieldName;
        this.indexField = indexField;
    }

    public DocRef getIndexDocRef() {
        return indexDocRef;
    }

    public String getFieldName() {
        return fieldName;
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
        final UpdateField that = (UpdateField) o;
        return Objects.equals(indexDocRef, that.indexDocRef) &&
               Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(indexField, that.indexField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexDocRef, fieldName, indexField);
    }

    @Override
    public String toString() {
        return "UpdateField{" +
               "indexDocRef=" + indexDocRef +
               ", fieldName=" + fieldName +
               ", indexField=" + indexField +
               '}';
    }
}
