package stroom.document.client;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

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
