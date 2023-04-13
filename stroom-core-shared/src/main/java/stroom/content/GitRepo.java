package stroom.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class GitRepo {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String url;
    @JsonProperty
    private final String branch;

    @JsonCreator
    public GitRepo(@JsonProperty("name") final String name,
                   @JsonProperty("url") final String url,
                   @JsonProperty("branch") final String branch) {
        this.name = name;
        this.url = url;
        this.branch = branch;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getBranch() {
        return branch;
    }

    @Override
    public String toString() {
        return "GitRepo{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", branch='" + branch + '\'' +
                '}';
    }
}
