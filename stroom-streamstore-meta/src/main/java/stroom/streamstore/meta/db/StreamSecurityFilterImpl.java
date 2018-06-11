package stroom.streamstore.meta.db;

import stroom.query.api.v2.ExpressionOperator;
import stroom.streamstore.meta.api.StreamSecurityFilter;

import java.util.Optional;

class StreamSecurityFilterImpl implements StreamSecurityFilter {
    @Override
    public Optional<ExpressionOperator> getExpression(final String permission) {
        return Optional.empty();
    }
}
