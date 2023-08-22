package stroom.analytics.shared;

public abstract class AbstractAnalyticProcessConfigBuilder<T extends AnalyticProcessConfig<B>, B
        extends AbstractAnalyticProcessConfigBuilder<T, ?>> {

    boolean enabled;
    String node;

    AbstractAnalyticProcessConfigBuilder() {
    }

    AbstractAnalyticProcessConfigBuilder(final T t) {
        this.enabled = t.enabled;
        this.node = t.node;
    }

    public B enabled(final boolean enabled) {
        this.enabled = enabled;
        return self();
    }

    public B node(final String node) {
        this.node = node;
        return self();
    }

    abstract B self();

    public abstract T build();
}
