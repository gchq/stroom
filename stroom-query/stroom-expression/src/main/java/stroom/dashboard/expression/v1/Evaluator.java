package stroom.dashboard.expression.v1;


abstract class Evaluator {

    protected abstract Val evaluate(final Val a, final Val b);
}
