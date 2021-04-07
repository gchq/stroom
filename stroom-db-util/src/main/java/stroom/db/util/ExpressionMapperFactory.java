package stroom.db.util;

import stroom.query.api.v2.ExpressionItem;

import org.jooq.Condition;

import java.util.function.Function;
import javax.inject.Inject;

public class ExpressionMapperFactory {

    private final TermHandlerFactory termHandlerFactory;

    @Inject
    public ExpressionMapperFactory(final TermHandlerFactory termHandlerFactory) {
        this.termHandlerFactory = termHandlerFactory;
    }

    public ExpressionMapper create() {
        return new ExpressionMapper(termHandlerFactory, null);
    }

    public ExpressionMapper create(final Function<ExpressionItem, Condition> delegateItemHandler) {
        return new ExpressionMapper(termHandlerFactory, delegateItemHandler);
    }
}
