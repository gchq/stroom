package stroom.meta.impl;

import stroom.meta.shared.MetaSecurityFilter;
import stroom.query.api.v2.ExpressionOperator;

import java.util.Optional;

class MetaSecurityFilterImpl implements MetaSecurityFilter {
    @Override
    public Optional<ExpressionOperator> getExpression(final String permission) {
        // TODO : @66 Add an implementation based on feed doc permissions.
        // Ultimately this ought to be implemented as permission expressions in the UI etc
        return Optional.empty();
    }
}
