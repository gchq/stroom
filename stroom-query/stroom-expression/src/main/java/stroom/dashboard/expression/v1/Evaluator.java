package stroom.dashboard.expression.v1;

import java.io.Serializable;

abstract class Evaluator implements Serializable {
    private static final long serialVersionUID = 7429374303172048909L;

    protected abstract Val evaluate(final Val a, final Val b);
}