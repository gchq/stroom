package stroom.util.shared;

@FunctionalInterface
public interface ToBooleanFunction<T> {

    boolean applyAsBoolean(T value);
}
