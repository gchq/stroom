package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class UrlResponse {
    @JsonProperty
    private final String url;

    @JsonCreator
    public UrlResponse(@JsonProperty("url") final String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
