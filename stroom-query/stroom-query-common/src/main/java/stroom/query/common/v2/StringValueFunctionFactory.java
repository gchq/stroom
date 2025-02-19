package stroom.query.common.v2;

import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.util.date.DateUtil;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;

public class StringValueFunctionFactory implements ValueFunctionFactory<String> {

    private final QueryField field;

    public StringValueFunctionFactory(final QueryField field) {
        this.field = field;
    }

    public static ValueFunctionFactories<String> create(final QueryField field) {
        final StringValueFunctionFactory stringValueFunctionFactory = new StringValueFunctionFactory(field);
        return fieldName -> stringValueFunctionFactory;
    }

    @Override
    public Function<String, Boolean> createNullCheck() {
        return Objects::isNull;
    }

    @Override
    public Function<String, String> createStringExtractor() {
        return string -> string;
    }

    @Override
    public Function<String, Long> createDateExtractor() {
        return DateUtil::parseNormalDateTimeString;
    }

    @Override
    public Function<String, BigDecimal> createNumberExtractor() {
        return BigDecimal::new;
    }

    @Override
    public FieldType getFieldType() {
        return field.getFldType();
    }
}
