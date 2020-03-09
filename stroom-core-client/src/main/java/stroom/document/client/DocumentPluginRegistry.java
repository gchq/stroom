package stroom.document.client;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class DocumentPluginRegistry {
    private final Map<String, DocumentPlugin<?>> pluginMap = new HashMap<>();

    public void register(String type, DocumentPlugin<?> plugin) {
        pluginMap.put(type, plugin);
    }

    public DocumentPlugin<?> get(final String type) {
        return pluginMap.get(type);
    }
}
