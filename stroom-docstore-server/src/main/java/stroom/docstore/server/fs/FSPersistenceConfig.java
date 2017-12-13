package stroom.docstore.server.fs;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FSPersistenceConfig {
    private String path;

    @JsonProperty
    public String getPath() {
        return path;
    }

    @JsonProperty
    public void setPath(final String path) {
        this.path = path;
    }
}
