package stroom.data.meta.impl.db;

import stroom.query.api.v2.ExpressionOperator;
import stroom.data.meta.shared.MetaSecurityFilter;

import java.util.Optional;

class DataSecurityFilterImpl implements MetaSecurityFilter {
    @Override
    public Optional<ExpressionOperator> getExpression(final String permission) {
        // TODO : @66 Add an implementation based on feed doc permissions.
        // Ultimately this ought to be implemented as permission expressions in the UI etc
        return Optional.empty();
    }
}
