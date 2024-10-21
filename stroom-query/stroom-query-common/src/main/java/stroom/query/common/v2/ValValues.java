package stroom.query.common.v2;

import stroom.query.common.v2.ExpressionPredicateBuilder.QueryFieldPosition;
import stroom.query.common.v2.ExpressionPredicateBuilder.Values;
import stroom.query.language.functions.DateUtil;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;

import java.math.BigDecimal;

public class ValValues implements Values {

    private final Val[] values;

    public ValValues(final Val[] values) {
        this.values = values;
    }

    @Override
    public String getString(final QueryFieldPosition queryFieldPosition) {
        final Val val = values[queryFieldPosition.index()];
        return val.toString();
    }

    @Override
    public BigDecimal getNumber(final QueryFieldPosition queryFieldPosition) {
        final Val val = values[queryFieldPosition.index()];
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
    }

    @Override
    public Long getDate(final QueryFieldPosition queryFieldPosition) {
        final Val val = values[queryFieldPosition.index()];
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
    }

    @Override
    public boolean isNull(final QueryFieldPosition queryFieldPosition) {
        final Val val = values[queryFieldPosition.index()];
        return Type.NULL.equals(val.type());
    }

//
//    private BigDecimal getNumber(final String columnName, final Object value) {
//        if (value == null) {
//            return null;
//        } else {
//            try {
//                if (value instanceof Long) {
//                    return BigDecimal.valueOf((long) value);
//                } else if (value instanceof Double) {
//                    return BigDecimal.valueOf((Double) value);
//                }
//                return new BigDecimal(value.toString());
//            } catch (final NumberFormatException e) {
//                throw new RowExpressionMatcher.MatchException(
//                        "Expected a numeric value for field \"" + columnName +
//                                "\" but was given string \"" + value + "\"");
//            }
//        }
//    }
//
//    private Long getDate(final String columnName, final Object value) {
//        if (value == null) {
//            return null;
//        } else {
//            if (value instanceof final String valueStr) {
//                try {
//                    return DateUtil.parseNormalDateTimeString(valueStr);
//                } catch (final NumberFormatException e) {
//                    throw new MatchException(
//                            "Unable to parse a date/time from value \"" + valueStr + "\"");
//                }
//            } else {
//                throw new MatchException(
//                        "Expected a string value for field \"" + columnName + "\" but was given \"" + value
//                                + "\" of type " + value.getClass().getName());
//            }
//        }
//    }
//
//    private Long getDate(final Object value) {
//        if (value == null) {
//            return null;
//        } else {
//            if (value instanceof final String valueStr) {
//                try {
//                    final Optional<ZonedDateTime> optionalZonedDateTime =
//                            DateExpressionParser.parse(valueStr, dateTimeSettings);
//                    final ZonedDateTime zonedDateTime = optionalZonedDateTime.orElseThrow(() ->
//                            new NumberFormatException("Unexpected: " + valueStr));
//                    return zonedDateTime.toInstant().toEpochMilli();
//                } catch (final NumberFormatException e) {
//                    throw new RowExpressionMatcher.MatchException(
//                            "Unable to parse a date/time from value \"" + valueStr + "\"");
//                }
//            } else {
//                throw new RowExpressionMatcher.MatchException(
//                        "Expected a string value but was given \"" + value
//                                + "\" of type " + value.getClass().getName());
//            }
//        }
//    }

}
