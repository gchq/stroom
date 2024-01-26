package stroom.query.language.functions;

abstract class Evaluator {

    protected abstract Val evaluate(final Val a, final Val b);

}
