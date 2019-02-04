package stroom.meta.shared;

import stroom.query.api.v2.ExpressionOperator;

import java.util.Optional;

public interface MetaSecurityFilter {
    Optional<ExpressionOperator> getExpression(String permission);
}
