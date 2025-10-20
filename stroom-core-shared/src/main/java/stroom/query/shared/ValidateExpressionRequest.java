package stroom.query.shared;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ValidateExpressionRequest {

    @JsonProperty
    private final ExpressionItem expressionItem;
    @JsonProperty
    private final List<QueryField> fields;
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;

    @JsonCreator
    public ValidateExpressionRequest(@JsonProperty("expressionItem") final ExpressionItem expressionItem,
                                     @JsonProperty("fields") final List<QueryField> fields,
                                     @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings) {
        this.expressionItem = Objects.requireNonNull(expressionItem);
        this.fields = NullSafe.list(fields);
        this.dateTimeSettings = Objects.requireNonNull(dateTimeSettings);
    }

    @SerialisationTestConstructor
    private ValidateExpressionRequest() {
        this(ExpressionOperator.builder().build(), Collections.emptyList(), DateTimeSettings.builder().build());
    }

    public ExpressionItem getExpressionItem() {
        return expressionItem;
    }

    public List<QueryField> getFields() {
        return fields;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    @Override
    public String toString() {
        return "ValidateExpressionRequest{" +
               "expressionItem=" + expressionItem +
               ", fields=" + fields +
               ", dateTimeSettings=" + dateTimeSettings +
               '}';
    }
}
