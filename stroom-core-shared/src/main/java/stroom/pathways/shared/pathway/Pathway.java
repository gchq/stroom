package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Pathway {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final PathKey pathKey;
    @JsonProperty
    private final PathNode root;

    @JsonCreator
    public Pathway(@JsonProperty("name") final String name,
                   @JsonProperty("pathKey") final PathKey pathKey,
                   @JsonProperty("root") final PathNode root) {
        this.name = name;
        this.pathKey = pathKey;
        this.root = root;
    }

    public String getName() {
        return name;
    }

    public PathKey getPathKey() {
        return pathKey;
    }

    public PathNode getRoot() {
        return root;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Pathway pathway = (Pathway) o;
        return Objects.equals(name, pathway.name) &&
               Objects.equals(pathKey, pathway.pathKey) &&
               Objects.equals(root, pathway.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, pathKey, root);
    }
}
