package stroom.meta.api;

import java.util.Collection;

/**
 * Map that does not care about key case.
 */
public class AttributeMap extends CIStringHashMap {
    private static final long serialVersionUID = 4877407570072403322L;

    public void removeAll(final Collection<String> keySet) {
        for (final String key : keySet) {
            remove(key);
        }
    }
}
