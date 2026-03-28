package stroom.visualisation.shared;

import stroom.docs.shared.Description;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Holds the data about a web asset within a visualisation.
 */
@Description(
        "Holds the data on a web asset within a visualisation."
)
@JsonPropertyOrder({
        "id",
        "path",
        "folder"
})
@JsonInclude(Include.NON_NULL)
public class VisualisationAsset {

    @JsonProperty
    private String id;

    @JsonProperty
    private String path;

    @JsonProperty
    private boolean folder;

    @JsonCreator
    public VisualisationAsset(@JsonProperty("id") final String id,
                              @JsonProperty("path") final String path,
                              @JsonProperty("folder") final boolean folder) {
        Objects.requireNonNull(id);
        this.id = id;
        this.path = path;
        this.folder = folder;
    }

    public String getId() {
        Objects.requireNonNull(id);
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public boolean isFolder() {
        return folder;
    }

    public void setFolder(final boolean folder) {
        this.folder = folder;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisualisationAsset that = (VisualisationAsset) o;
        return folder == that.folder && Objects.equals(id, that.id) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, path, folder);
    }

    @Override
    public String toString() {
        return "VisualisationAsset{" +
               "id='" + id + '\'' +
               ", path='" + path + '\'' +
               ", isFolder=" + folder +
               '}';
    }
}
