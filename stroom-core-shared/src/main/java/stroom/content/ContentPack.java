package stroom.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ContentPack {

    @JsonProperty
    private final String url;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String version;

    @JsonCreator
    public ContentPack(@JsonProperty("url") final String url,
                       @JsonProperty("name") final String name,
                       @JsonProperty("version") final String version) {
        this.url = url;
        this.name = name;
        this.version = version;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String toFileName() {
        return name + "-v" + version + ".zip";
    }

    @Override
    public String toString() {
        return name + "-v" + version;
    }
}
