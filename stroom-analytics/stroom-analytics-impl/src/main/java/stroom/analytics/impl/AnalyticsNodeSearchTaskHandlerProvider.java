package stroom.analytics.impl;

import stroom.search.impl.NodeSearchTaskHandler;
import stroom.search.impl.NodeSearchTaskHandlerProvider;
import stroom.search.impl.NodeSearchTaskType;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
class AnalyticsNodeSearchTaskHandlerProvider implements NodeSearchTaskHandlerProvider {

    private final Provider<AnalyticsNodeSearchTaskHandler> nodeSearchTaskHandlerProvider;

    @Inject
    AnalyticsNodeSearchTaskHandlerProvider(
            final Provider<AnalyticsNodeSearchTaskHandler> nodeSearchTaskHandlerProvider) {
        this.nodeSearchTaskHandlerProvider = nodeSearchTaskHandlerProvider;
    }

    @Override
    public NodeSearchTaskHandler get() {
        return nodeSearchTaskHandlerProvider.get();
    }

    @Override
    public NodeSearchTaskType getType() {
        return NodeSearchTaskType.ANALYTICS;
    }
}
