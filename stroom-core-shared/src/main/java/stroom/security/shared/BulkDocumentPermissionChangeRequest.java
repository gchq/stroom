package stroom.security.shared;

import stroom.query.api.ExpressionOperator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class BulkDocumentPermissionChangeRequest implements PermissionChangeRequest {

    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final AbstractDocumentPermissionsChange change;

    @JsonCreator
    public BulkDocumentPermissionChangeRequest(@JsonProperty("expression") final ExpressionOperator expression,
                                               @JsonProperty("change") final AbstractDocumentPermissionsChange
                                                       change) {
        Objects.requireNonNull(expression, "Expression is null");
        Objects.requireNonNull(change, "Request is null");
        this.expression = expression;
        this.change = change;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public AbstractDocumentPermissionsChange getChange() {
        return change;
    }
}
