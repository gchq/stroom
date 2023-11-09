package stroom.search.impl;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NodeSearchTaskHandlers {

    private final Map<NodeSearchTaskType, NodeSearchTaskHandlerProvider> handlerMap = new ConcurrentHashMap<>();

    @Inject
    public NodeSearchTaskHandlers(final Set<NodeSearchTaskHandlerProvider> providers) {
        for (final NodeSearchTaskHandlerProvider provider : providers) {
            handlerMap.put(provider.getType(), provider);
        }
    }

    public NodeSearchTaskHandler get(final NodeSearchTaskType nodeSearchTaskType) {
        return Optional.ofNullable(handlerMap.get(nodeSearchTaskType)).orElseThrow().get();
    }
}
