package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;

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

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<ResourceSpans, Builder> {

        private Resource resource;
        private List<ScopeSpans> scopeSpans;
        private String schemaUrl;

        private Builder() {
        }

        private Builder(final ResourceSpans resourceSpans) {
            this.resource = resourceSpans.resource;
            this.scopeSpans = resourceSpans.scopeSpans;
            this.schemaUrl = resourceSpans.schemaUrl;
        }

        public Builder resource(final Resource resource) {
            this.resource = resource;
            return self();
        }

        public Builder scopeSpans(final List<ScopeSpans> scopeSpans) {
            this.scopeSpans = scopeSpans;
            return self();
        }

        public Builder schemaUrl(final String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ResourceSpans build() {
            return new ResourceSpans(
                    resource,
                    scopeSpans,
                    schemaUrl
            );
        }
    }
}
