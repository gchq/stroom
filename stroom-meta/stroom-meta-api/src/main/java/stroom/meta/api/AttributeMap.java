package stroom.meta.api;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Map that does not care about key case.
 */
public class AttributeMap extends CIStringHashMap {

    private final boolean overrideEmbeddedMeta;

    public AttributeMap(final boolean overrideEmbeddedMeta) {
        this.overrideEmbeddedMeta = overrideEmbeddedMeta;
    }

    public AttributeMap(final boolean overrideEmbeddedMeta, final Map<String, String> values) {
        this.overrideEmbeddedMeta = overrideEmbeddedMeta;
        putAll(values);
    }

    public AttributeMap() {
        this.overrideEmbeddedMeta = false;
    }

    public AttributeMap(final Map<String, String> values) {
        this.overrideEmbeddedMeta = false;
        putAll(values);
    }

    private AttributeMap(final Builder builder) {
        overrideEmbeddedMeta = builder.overrideEmbeddedMeta;
        putAll(builder.attributes);
    }

    public static Builder copy(final AttributeMap copy) {
        final Builder builder = new Builder();
        builder.overrideEmbeddedMeta = copy.overrideEmbeddedMeta;
        builder.attributes = new AttributeMap();
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void removeAll(final Collection<String> keySet) {
        for (final String key : keySet) {
            remove(key);
        }
    }

    public boolean isOverrideEmbeddedMeta() {
        return overrideEmbeddedMeta;
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private boolean overrideEmbeddedMeta = false;
        private AttributeMap attributes = new AttributeMap();

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withOverrideEmbeddedMeta(final boolean val) {
            overrideEmbeddedMeta = val;
            return this;
        }

        public Builder overrideEmbeddedMeta() {
            overrideEmbeddedMeta = true;
            return this;
        }

        public Builder put(final String key, final String value) {
            Objects.requireNonNull(key);
            overrideEmbeddedMeta = true;
            attributes.put(key, value);
            return this;
        }

        public AttributeMap build() {
            return new AttributeMap(this);
        }
    }
}
