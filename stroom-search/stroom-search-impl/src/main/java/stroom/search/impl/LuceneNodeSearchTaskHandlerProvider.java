package stroom.search.impl;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
class LuceneNodeSearchTaskHandlerProvider implements NodeSearchTaskHandlerProvider {

    private final Provider<LuceneNodeSearchTaskHandler> nodeSearchTaskHandlerProvider;

    @Inject
    LuceneNodeSearchTaskHandlerProvider(final Provider<LuceneNodeSearchTaskHandler> nodeSearchTaskHandlerProvider) {
        this.nodeSearchTaskHandlerProvider = nodeSearchTaskHandlerProvider;
    }

    @Override
    public NodeSearchTaskHandler get() {
        return nodeSearchTaskHandlerProvider.get();
    }

    @Override
    public NodeSearchTaskType getType() {
        return NodeSearchTaskType.LUCENE;
    }
}
