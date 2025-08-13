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
    private final Map<String, Constraint> requiredAttributes;
    @JsonProperty
    private final Map<String, Constraint> optionalAttributes;

    @JsonCreator
    public Constraints(@JsonProperty("duration") final NanoTimeRange duration,
                       @JsonProperty("flags") final Constraint flags,
                       @JsonProperty("kind") final Set<SpanKind> kind,
                       @JsonProperty("requiredAttributes") final Map<String, Constraint> requiredAttributes,
                       @JsonProperty("optionalAttributes") final Map<String, Constraint> optionalAttributes) {
        this.duration = duration;
        this.flags = flags;
        this.kind = kind;
        this.requiredAttributes = requiredAttributes;
        this.optionalAttributes = optionalAttributes;
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

    public Map<String, Constraint> getRequiredAttributes() {
        return requiredAttributes;
    }

    public Map<String, Constraint> getOptionalAttributes() {
        return optionalAttributes;
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
               Objects.equals(requiredAttributes, that.requiredAttributes) &&
               Objects.equals(optionalAttributes, that.optionalAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, flags, kind, requiredAttributes, optionalAttributes);
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
        private Map<String, Constraint> requiredAttributes;
        private Map<String, Constraint> optionalAttributes;

        public Builder() {
        }

        public Builder(final Constraints constraints) {
            this.duration = constraints.duration;
            this.flags = constraints.flags;
            this.kind = constraints.kind;
            this.requiredAttributes = constraints.requiredAttributes;
            this.optionalAttributes = constraints.optionalAttributes;
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

        public Builder requiredAttributes(final Map<String, Constraint> requiredAttributes) {
            this.requiredAttributes = requiredAttributes;
            return self();
        }

        public Builder optionalAttributes(final Map<String, Constraint> optionalAttributes) {
            this.optionalAttributes = optionalAttributes;
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
                    requiredAttributes,
                    optionalAttributes);
        }
    }
}
