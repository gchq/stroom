package stroom.analytics.impl;

public interface DetectionConsumer extends ProcessLifecycleAware {

    void accept(Detection detection);
}
