package stroom.connectors;

import java.util.Properties;

/**
 * We need to expose properties to the connector implementations, but without them having to wire in our relatively heavy
 * property service. This interface will be used to hide those details, and we will provide implementations
 * that expect prefixed properties to be available in StroomPropertyService.
 */
public interface ConnectorProperties {
    /**
     * Extract the String value of a property for a given key.
     *
     * @param key The property key
     * @return The value of the property as a string.
     */
    String getProperty(String key);

    /**
     * Extract the String value of a property for a given key.
     *
     * @param key The property key
     * @return The value of the property.
     */
    Object get(String key);

    /**
     * Extract the value of a property, but use a default if not present.
     *
     * @param key The property key
     * @param defaultValue The default value to use if one cannot be found.
     * @return The value of the property.
     */
    Object getOrDefault(String key, Object defaultValue);

    /**
     * In some circumstances the property may be set by one class before this handler is passed off to
     * another class for use. In this case it may provide some default values rather than use any hidden service.
     * Any values provided in this way will have top precedence for any future get calls.
     *
     * @param key The property key
     * @param value The value to use as an override.
     */
    void put(String key, Object value);

    /**
     *
     * @param dest The properties object to put the key/value into
     * @param key The property key to lookup and insert
     * @param defaultValue The default value to use.
     */
    default void copyProp(Properties dest, String key, Object defaultValue) {
        dest.put(key, this.getOrDefault(key, defaultValue));
    }
}
