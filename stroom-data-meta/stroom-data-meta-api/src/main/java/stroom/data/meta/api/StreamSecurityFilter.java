package stroom.data.meta.api;

import stroom.query.api.v2.ExpressionOperator;

import java.util.Optional;

public interface StreamSecurityFilter {
    Optional<ExpressionOperator> getExpression(String permission);
}
