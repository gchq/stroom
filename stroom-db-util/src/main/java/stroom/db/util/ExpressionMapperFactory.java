package stroom.db.util;

import stroom.query.api.ExpressionItem;

import jakarta.inject.Inject;
import org.jooq.Condition;

import java.util.function.Function;

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
