package stroom.pathways.shared.pathway;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Constraint {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final ConstraintValue value;
    @JsonProperty
    private final boolean optional;

    @JsonCreator
    public Constraint(@JsonProperty("name") final String name,
                      @JsonProperty("value") final ConstraintValue value,
                      @JsonProperty("optional") final boolean optional) {
        this.name = name;
        this.value = value;
        this.optional = optional;
    }

    public String getName() {
        return name;
    }

    public ConstraintValue getValue() {
        return value;
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Constraint that = (Constraint) o;
        return optional == that.optional &&
               Objects.equals(name, that.name) &&
               Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, optional);
    }

    @Override
    public String toString() {
        return "Constraint{" +
               "name='" + name + '\'' +
               ", value=" + value +
               ", optional=" + optional +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Constraint, Builder> {

        private String name;
        private ConstraintValue value;
        private boolean optional;

        public Builder() {
        }

        public Builder(final Constraint constraint) {
            this.name = constraint.name;
            this.value = constraint.value;
            this.optional = constraint.optional;
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder value(final ConstraintValue value) {
            this.value = value;
            return self();
        }

        public Builder optional(final boolean optional) {
            this.optional = optional;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Constraint build() {
            return new Constraint(
                    name,
                    value,
                    optional);
        }
    }
}
