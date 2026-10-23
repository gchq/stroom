/*
 * Copyright 2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.NanoTime;
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
    @JsonProperty
    private final long timesUsed;
    @JsonProperty
    private final NanoTime lastUsedTime;

    @JsonCreator
    public Constraint(@JsonProperty("name") final String name,
                      @JsonProperty("value") final ConstraintValue value,
                      @JsonProperty("optional") final Boolean optional,
                      @JsonProperty("timesUsed") final Long timesUsed,
                      @JsonProperty("lastUsedTime") final NanoTime lastUsedTime) {
        this.name = name;
        this.value = value;
        this.optional = Objects.requireNonNullElse(optional, false);
        this.timesUsed = Objects.requireNonNullElse(timesUsed, 0L);
        this.lastUsedTime = lastUsedTime;
    }

    /**
     * A constraint that has never been checked against anything, for a caller making one outside the
     * learner.
     */
    public Constraint(final String name, final ConstraintValue value, final boolean optional) {
        this(name, value, optional, 0L, null);
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

    /**
     * How many values this constraint has been given, counted per span rather than per trace. A
     * constraint on a node reached forty times by one trace is checked forty times.
     *
     * <p>Lower than the node's own count where the attribute is only sometimes carried, which is what
     * tells a constraint that is always there from one that is occasional.
     */
    public long getTimesUsed() {
        return timesUsed;
    }

    /**
     * The last time a span carried a value for this constraint. When it last <em>changed</em> is not
     * held here — that is what the stored changes say, and holding it twice would let the two differ.
     */
    public NanoTime getLastUsedTime() {
        return lastUsedTime;
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
        private long timesUsed;
        private NanoTime lastUsedTime;

        public Builder() {
        }

        public Builder(final Constraint constraint) {
            this.name = constraint.name;
            this.value = constraint.value;
            this.optional = constraint.optional;
            this.timesUsed = constraint.timesUsed;
            this.lastUsedTime = constraint.lastUsedTime;
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

        public Builder timesUsed(final long timesUsed) {
            this.timesUsed = timesUsed;
            return self();
        }

        public Builder lastUsedTime(final NanoTime lastUsedTime) {
            this.lastUsedTime = lastUsedTime;
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
                    optional,
                    timesUsed,
                    lastUsedTime);
        }
    }
}
