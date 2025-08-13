package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class Constraints {

    @JsonProperty
    private final NanoTimeRange duration;
    @JsonProperty
    private final Constraint flags;
    @JsonProperty
    private final Set<SpanKind> kind;
    @JsonProperty
    private final Map<String, Constraint> attributes;

    @JsonCreator
    public Constraints(@JsonProperty("duration") final NanoTimeRange duration,
                       @JsonProperty("flags") final Constraint flags,
                       @JsonProperty("kind") final Set<SpanKind> kind,
                       @JsonProperty("attributes") final Map<String, Constraint> attributes) {
        this.duration = duration;
        this.flags = flags;
        this.kind = kind;
        this.attributes = attributes;
    }

    public NanoTimeRange getDuration() {
        return duration;
    }

    public Constraint getFlags() {
        return flags;
    }

    public Set<SpanKind> getKind() {
        return kind;
    }

    public Map<String, Constraint> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Constraints that = (Constraints) o;
        return Objects.equals(duration, that.duration) &&
               Objects.equals(flags, that.flags) &&
               Objects.equals(kind, that.kind) &&
               Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, flags, kind, attributes);
    }

    @Override
    public String toString() {
        return "Constraints{" +
               "duration=" + duration +
               ", flags=" + flags +
               ", kind=" + kind +
               ", attributes=" + attributes +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Constraints, Builder> {

        private NanoTimeRange duration;
        private Constraint flags;
        private Set<SpanKind> kind;
        private Map<String, Constraint> attributes;

        public Builder() {
        }

        public Builder(final Constraints constraints) {
            this.duration = constraints.duration;
            this.flags = constraints.flags;
            this.kind = constraints.kind;
            this.attributes = constraints.attributes;
        }

        public Builder duration(final NanoTimeRange duration) {
            this.duration = duration;
            return self();
        }

        public Builder flags(final Constraint flags) {
            this.flags = flags;
            return self();
        }

        public Builder kind(final Set<SpanKind> kind) {
            this.kind = kind;
            return self();
        }

        public Builder attributes(final Map<String, Constraint> attributes) {
            this.attributes = attributes;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Constraints build() {
            return new Constraints(
                    duration,
                    flags,
                    kind,
                    attributes);
        }
    }
}
