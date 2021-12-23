package stroom.dashboard.expression.v1;

import io.vavr.Tuple;
import io.vavr.Tuple4;

import java.util.ArrayList;
import java.util.Collection;

public class QuadList<T1, T2, T3, T4> extends ArrayList<Tuple4<T1, T2, T3, T4>> {

    public QuadList(final int initialCapacity) {
        super(initialCapacity);
    }

    public QuadList() {
        super();
    }

    public QuadList(final Collection<? extends Tuple4<T1, T2, T3, T4>> c) {
        super(c);
    }

    public void add(final T1 value1, final T2 value2, final T3 value3, final T4 value4) {
        super.add(Tuple.of(value1, value2, value3, value4));
    }
}
