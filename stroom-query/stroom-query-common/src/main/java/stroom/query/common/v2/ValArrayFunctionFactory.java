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

public class ValArrayFunctionFactory implements ValueFunctionFactory<Val[]> {

    private final Column column;
    private final int index;

    public ValArrayFunctionFactory(final Column column, final int index) {
        this.column = column;
        this.index = index;
    }

    @Override
    public Function<Val[], Boolean> createNullCheck() {
        return values -> stroom.query.language.functions.Type.NULL.equals(values[index].type());
    }

    @Override
    public Function<Val[], String> createStringExtractor() {
        return values -> values[index].toString();
    }

    @Override
    public Function<Val[], Long> createDateExtractor() {
        return values -> {
            final Val val = values[index];
            if (Type.LONG.equals(val.type()) || Type.DATE.equals(val.type())) {
                return val.toLong();
            } else {
                String string = val.toString();
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
    public Function<Val[], BigDecimal> createNumberExtractor() {
        return values -> {
            final Val val = values[index];
            try {
                if (Type.LONG.equals(val.type())) {
                    return BigDecimal.valueOf(val.toLong());
                } else if (Type.INTEGER.equals(val.type())) {
                    return BigDecimal.valueOf(val.toInteger());
                } else if (Type.DOUBLE.equals(val.type())) {
                    return BigDecimal.valueOf(val.toDouble());
                } else if (Type.FLOAT.equals(val.type())) {
                    return BigDecimal.valueOf(val.toFloat());
                }
                return new BigDecimal(val.toString());
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
