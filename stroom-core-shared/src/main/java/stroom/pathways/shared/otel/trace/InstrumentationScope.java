package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class InstrumentationScope {

    @JsonProperty("name")
    private final String name;

    @JsonProperty("version")
    private final String version;

    @JsonProperty("attributes")
    private final List<KeyValue> attributes;

    @JsonProperty("droppedAttributesCount")
    private final int droppedAttributesCount;

    @JsonCreator
    public InstrumentationScope(@JsonProperty("name") final String name,
                                @JsonProperty("version") final String version,
                                @JsonProperty("attributes") final List<KeyValue> attributes,
                                @JsonProperty("droppedAttributesCount") final int droppedAttributesCount) {
        this.name = name;
        this.version = version;
        this.attributes = attributes;
        this.droppedAttributesCount = droppedAttributesCount;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public List<KeyValue> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final InstrumentationScope that = (InstrumentationScope) o;
        return droppedAttributesCount == that.droppedAttributesCount &&
               Objects.equals(name, that.name) &&
               Objects.equals(version, that.version) &&
               Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, attributes, droppedAttributesCount);
    }

    @Override
    public String toString() {
        return "InstrumentationScope{" +
               "name='" + name + '\'' +
               ", version='" + version + '\'' +
               ", attributes=" + attributes +
               ", droppedAttributesCount=" + droppedAttributesCount +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<InstrumentationScope, Builder> {

        private String name;
        private String version;
        private List<KeyValue> attributes;
        private int droppedAttributesCount;

        private Builder() {
        }

        private Builder(final InstrumentationScope instrumentationScope) {
            this.name = instrumentationScope.name;
            this.version = instrumentationScope.version;
            this.attributes = instrumentationScope.attributes;
            this.droppedAttributesCount = instrumentationScope.droppedAttributesCount;
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder version(final String version) {
            this.version = version;
            return self();
        }

        public Builder attributes(final List<KeyValue> attributes) {
            this.attributes = attributes;
            return self();
        }

        public Builder droppedAttributesCount(final int droppedAttributesCount) {
            this.droppedAttributesCount = droppedAttributesCount;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public InstrumentationScope build() {
            return new InstrumentationScope(
                    name,
                    version,
                    attributes,
                    droppedAttributesCount
            );
        }
    }
}
