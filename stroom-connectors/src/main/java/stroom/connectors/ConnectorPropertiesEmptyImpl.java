package stroom.connectors;

import java.util.Properties;

public class ConnectorPropertiesEmptyImpl implements ConnectorProperties {
    private final Properties properties = new Properties();

    @Override
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    @Override
    public Object get(String key) {
        return properties.get(key);
    }

    @Override
    public Object getOrDefault(String key, Object defaultValue) {
        if (null != defaultValue) {
            return properties.getProperty(key, defaultValue.toString());
        } else {
            return get(key);
        }
    }

    @Override
    public void put(String key, Object value) {
        properties.put(key, value);
    }
}
