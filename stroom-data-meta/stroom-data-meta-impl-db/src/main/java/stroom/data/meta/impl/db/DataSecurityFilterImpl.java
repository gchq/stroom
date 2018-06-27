package stroom.data.meta.impl.db;

import stroom.query.api.v2.ExpressionOperator;
import stroom.data.meta.api.DataSecurityFilter;

import java.util.Optional;

class DataSecurityFilterImpl implements DataSecurityFilter {
    @Override
    public Optional<ExpressionOperator> getExpression(final String permission) {
        // TODO : @66 Add an implementation based on feed doc permissions.
        // Ultimately this ought to be implemented as permission expressions in the UI etc
        return Optional.empty();
    }
}
