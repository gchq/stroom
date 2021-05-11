package stroom.meta.api;

import java.util.Collection;

/**
 * Map that does not care about key case.
 */
public class AttributeMap extends CIStringHashMap {
    private final boolean overrideEmbeddedMeta;

    public AttributeMap(final boolean overrideEmbeddedMeta) {
        this.overrideEmbeddedMeta = overrideEmbeddedMeta;
    }

    public AttributeMap() {
        this.overrideEmbeddedMeta = false;
    }

    public void removeAll(final Collection<String> keySet) {
        for (final String key : keySet) {
            remove(key);
        }
    }

    public boolean isOverrideEmbeddedMeta() {
        return overrideEmbeddedMeta;
    }
}
