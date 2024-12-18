package stroom.util.shared;

public abstract class BaseBuilder<T, B extends BaseBuilder<T, ?>> {

    protected abstract B self();

    public abstract T build();
}
