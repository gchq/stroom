package stroom.analytics.shared;

import stroom.docref.DocRef;

public abstract class AbstractAnalyticProcessConfigBuilder<T extends AnalyticProcessConfig, B
        extends AbstractAnalyticProcessConfigBuilder<T, ?>> {

    boolean enabled;
    String node;
    DocRef errorFeed;

    AbstractAnalyticProcessConfigBuilder() {
    }

    AbstractAnalyticProcessConfigBuilder(final T t) {
        this.enabled = t.enabled;
        this.node = t.node;
        this.errorFeed = t.errorFeed;
    }

    public B enabled(final boolean enabled) {
        this.enabled = enabled;
        return self();
    }

    public B node(final String node) {
        this.node = node;
        return self();
    }

    public B errorFeed(final DocRef errorFeed) {
        this.errorFeed = errorFeed;
        return self();
    }

    abstract B self();

    public abstract T build();
}
