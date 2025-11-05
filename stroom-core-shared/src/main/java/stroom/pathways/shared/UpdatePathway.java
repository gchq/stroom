package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.pathways.shared.pathway.Pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class UpdatePathway {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final Pathway pathway;

    @JsonCreator
    public UpdatePathway(@JsonProperty("docRef") final DocRef docRef,
                         @JsonProperty("name") final String name,
                         @JsonProperty("pathway") final Pathway pathway) {
        this.docRef = docRef;
        this.name = name;
        this.pathway = pathway;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getName() {
        return name;
    }

    public Pathway getPathway() {
        return pathway;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UpdatePathway that = (UpdatePathway) o;
        return Objects.equals(docRef, that.docRef) &&
               Objects.equals(name, that.name) &&
               Objects.equals(pathway, that.pathway);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, name, pathway);
    }

    @Override
    public String toString() {
        return "UpdatePathway{" +
               "docRef=" + docRef +
               ", name=" + name +
               ", pathway=" + pathway +
               '}';
    }
}
