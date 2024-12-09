package stroom.docstore.impl.fs;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public class Manifest {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String version;
    @JsonProperty
    private final String uniqueName;
    @JsonProperty
    private final Set<String> entries;

    @JsonCreator
    public Manifest(@JsonProperty("docRef") final DocRef docRef,
                    @JsonProperty("version") final String version,
                    @JsonProperty("uniqueName") final String uniqueName,
                    @JsonProperty("entries") final Set<String> entries) {
        this.docRef = docRef;
        this.version = version;
        this.uniqueName = uniqueName;
        this.entries = entries;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getVersion() {
        return version;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public Set<String> getEntries() {
        return entries;
    }
}
