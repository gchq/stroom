package stroom.pathways.model.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ResourceSpans {

    @JsonProperty("resource")
    private final Resource resource;

    @JsonProperty("scopeSpans")
    private final List<ScopeSpans> scopeSpans;

    @JsonProperty("schemaUrl")
    private final String schemaUrl;

    @JsonCreator
    public ResourceSpans(@JsonProperty("resource") final Resource resource,
                         @JsonProperty("scopeSpans") final List<ScopeSpans> scopeSpans,
                         @JsonProperty("schemaUrl") final String schemaUrl) {
        this.resource = resource;
        this.scopeSpans = scopeSpans;
        this.schemaUrl = schemaUrl;
    }

    public Resource getResource() {
        return resource;
    }

    public List<ScopeSpans> getScopeSpans() {
        return scopeSpans;
    }

    public String getSchemaUrl() {
        return schemaUrl;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ResourceSpans that = (ResourceSpans) o;
        return Objects.equals(resource, that.resource) &&
               Objects.equals(scopeSpans, that.scopeSpans) &&
               Objects.equals(schemaUrl, that.schemaUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, scopeSpans, schemaUrl);
    }

    @Override
    public String toString() {
        return "ResourceSpans{" +
               "resource=" + resource +
               ", scopeSpans=" + scopeSpans +
               ", schemaUrl='" + schemaUrl + '\'' +
               '}';
    }
}
