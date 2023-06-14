package stroom.search.impl;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

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
