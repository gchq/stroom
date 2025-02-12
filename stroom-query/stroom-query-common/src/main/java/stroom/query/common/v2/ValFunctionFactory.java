package stroom.query.common.v2;

import stroom.datasource.api.v2.FieldType;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Format;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.util.date.DateUtil;

import java.math.BigDecimal;
import java.util.function.Function;

public class ValFunctionFactory implements ValueFunctionFactory<Val> {

    private final Column column;

    public ValFunctionFactory(final Column column) {
        this.column = column;
    }

    @Override
    public Function<Val, Boolean> createNullCheck() {
        return values -> Type.NULL.equals(values.type());
    }

    @Override
    public Function<Val, String> createStringExtractor() {
        return Val::toString;
    }

    @Override
    public Function<Val, Long> createDateExtractor() {
        return values -> {
            if (Type.LONG.equals(values.type()) || Type.DATE.equals(values.type())) {
                return values.toLong();
            } else {
                String string = values.toString();
                if (string != null) {
                    try {
                        return DateUtil.parseNormalDateTimeString(string);
                    } catch (final NumberFormatException e) {
//                        throw new MatchException(
//                                "Unable to parse a date/time from value \"" + string + "\"");
                    }
                }
            }
            return null;
        };
    }

    @Override
    public Function<Val, BigDecimal> createNumberExtractor() {
        return values -> {
            try {
                if (Type.LONG.equals(values.type())) {
                    return BigDecimal.valueOf(values.toLong());
                } else if (Type.INTEGER.equals(values.type())) {
                    return BigDecimal.valueOf(values.toInteger());
                } else if (Type.DOUBLE.equals(values.type())) {
                    return BigDecimal.valueOf(values.toDouble());
                } else if (Type.FLOAT.equals(values.type())) {
                    return BigDecimal.valueOf(values.toFloat());
                }
                return new BigDecimal(values.toString());
            } catch (final NumberFormatException e) {
                return null;

//            throw new MatchException(
//                    "Expected a numeric value but was given string \"" + val.toString() + "\"");
            }
        };
    }

    @Override
    public FieldType getFieldType() {
        FieldType fieldType = FieldType.TEXT;
        if (column.getFormat() != null) {
            if (Format.Type.NUMBER.equals(column.getFormat().getType())) {
                fieldType = FieldType.LONG;
            } else if (Format.Type.DATE_TIME.equals(column.getFormat().getType())) {
                fieldType = FieldType.DATE;
            }
        }
        return fieldType;
    }
}
