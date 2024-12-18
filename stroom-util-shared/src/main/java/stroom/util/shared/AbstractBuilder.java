package stroom.util.shared;

public abstract class AbstractBuilder<T, B extends AbstractBuilder<T, ?>> {

    protected abstract B self();

    public abstract T build();
}
