package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ExportTraceServiceRequest {

    @JsonProperty("resourceSpans")
    private final List<ResourceSpans> resourceSpans;

    @JsonCreator
    public ExportTraceServiceRequest(@JsonProperty("resourceSpans") final List<ResourceSpans> resourceSpans) {
        this.resourceSpans = resourceSpans;
    }

    public List<ResourceSpans> getResourceSpans() {
        return resourceSpans;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExportTraceServiceRequest that = (ExportTraceServiceRequest) o;
        return Objects.equals(resourceSpans, that.resourceSpans);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(resourceSpans);
    }

    @Override
    public String toString() {
        return "ExportTraceServiceRequest{" +
               "resourceSpans=" + resourceSpans +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<ExportTraceServiceRequest, Builder> {

        private List<ResourceSpans> resourceSpans;

        private Builder() {
        }

        private Builder(final ExportTraceServiceRequest exportTraceServiceRequest) {
            this.resourceSpans = exportTraceServiceRequest.resourceSpans;
        }

        public Builder resourceSpans(final List<ResourceSpans> resourceSpans) {
            this.resourceSpans = resourceSpans;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ExportTraceServiceRequest build() {
            return new ExportTraceServiceRequest(
                    resourceSpans
            );
        }
    }
}
