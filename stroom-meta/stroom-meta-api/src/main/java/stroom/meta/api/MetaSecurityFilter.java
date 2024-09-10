package stroom.meta.api;

import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.DocumentPermission;

import java.util.List;
import java.util.Optional;

public interface MetaSecurityFilter {

    Optional<ExpressionOperator> getExpression(DocumentPermission permission, List<String> fields);
}
