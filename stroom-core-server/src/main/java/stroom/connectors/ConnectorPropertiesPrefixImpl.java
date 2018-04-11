package stroom.connectors;

import stroom.properties.StroomPropertyService;

import java.util.Properties;

/**
 * This class provides properties to external connectors, where the properties are defined in the {@link StroomPropertyService}
 * with prefixes. When a connector requests a property, the prefix will be applied to the key before being passed off
 * to the {@link StroomPropertyService}.
 *
 * A set of override properties are provided, this can be used by one class wishing to give specific values to the connector.
 * It will mostly be used for backwards compatibility and testing.
 */
public class ConnectorPropertiesPrefixImpl implements ConnectorProperties {

    private final String prefix;
    private final StroomPropertyService propertyService;
    private final Properties overriddenValues;

    /**
     * If this constructor is called, then this will simply provide a transparent wrapper for standard {@link java.util.Properties}
     */
    public ConnectorPropertiesPrefixImpl() {
        this(null, null);
    }

    /**
     * If this constructor is called, the prefix will be added to any property keys before being passed onto the given
     * property service.
     * @param prefix The prefix to apply.
     * @param propertyService The full stroom property service.
     */
    public ConnectorPropertiesPrefixImpl(final String prefix, final StroomPropertyService propertyService) {
        this.prefix = prefix;
        this.propertyService = propertyService;
        this.overriddenValues = new Properties();
    }

    @Override
    public String getProperty(String key) {
        final Object value = get(key);
        return (value != null) ? value.toString() : null;
    }

    @Override
    public Object get(String key) {
        Object value = null;

        if (overriddenValues.containsKey(key)) {
            value = overriddenValues.getProperty(key);
        } else if (this.prefix != null) {
            final String prefixedKey = String.format("%s%s", this.prefix, key);
            value = this.propertyService.getProperty(prefixedKey);
        }

        return value;
    }

    @Override
    public Object getOrDefault(String key, Object defaultValue) {
        final Object value = get(key);
        return (value != null) ? value : defaultValue;
    }

    @Override
    public void put(String key, Object value) {
        this.overriddenValues.put(key, value);
    }
}
