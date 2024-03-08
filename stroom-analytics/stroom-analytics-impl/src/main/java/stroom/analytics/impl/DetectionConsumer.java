package stroom.analytics.impl;

import stroom.search.extraction.ProcessLifecycleAware;

public interface DetectionConsumer extends ProcessLifecycleAware {

    void accept(Detection detection);
}
