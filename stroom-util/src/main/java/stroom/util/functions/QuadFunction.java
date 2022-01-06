package stroom.util.functions;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface QuadFunction<T1, T2, T3, T4, R> {

    R apply(T1 t1, T2 t2, T3 t3, T4 t4);

    default <V> QuadFunction<T1, T2, T3, T4, V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);

        return (t1, t2, t3, t4) ->
                after.apply(apply(t1, t2, t3, t4));
    }
}
