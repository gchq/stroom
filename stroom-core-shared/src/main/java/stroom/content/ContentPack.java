package stroom.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ContentPack {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String path;
    @JsonProperty("repo")
    private final GitRepo repo;

    @JsonCreator
    public ContentPack(@JsonProperty("name") final String name,
                       @JsonProperty("path") final String path,
                       @JsonProperty("repo") final GitRepo repo) {
        this.name = name;
        this.path = path;
        this.repo = repo;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public GitRepo getRepo() {
        return repo;
    }
}
