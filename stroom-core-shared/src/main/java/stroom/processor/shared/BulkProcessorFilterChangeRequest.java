package stroom.processor.shared;

import stroom.query.api.ExpressionOperator;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class BulkProcessorFilterChangeRequest {

    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final ProcessorFilterChange change;
    @JsonProperty
    private final UserRef userRef;

    @JsonCreator
    public BulkProcessorFilterChangeRequest(@JsonProperty("expression") final ExpressionOperator expression,
                                            @JsonProperty("change") final ProcessorFilterChange change,
                                            @JsonProperty("userRef") final UserRef userRef) {
        this.expression = expression;
        this.change = change;
        this.userRef = userRef;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public ProcessorFilterChange getChange() {
        return change;
    }

    public UserRef getUserRef() {
        return userRef;
    }
}
