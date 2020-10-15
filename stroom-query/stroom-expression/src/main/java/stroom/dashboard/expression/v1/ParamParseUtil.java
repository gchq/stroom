package stroom.dashboard.expression.v1;

import java.text.ParseException;

public class ParamParseUtil {
    private static final String[] POSITIONS = {"first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth"};

    public static String parseStringParam(final Param[] params, final int pos, final String functionName) throws ParseException {
        if (params.length > pos) {
            final Param param = params[pos];
            if (!(param instanceof ValString)) {
                throw new ParseException("String expected as " + getPos(pos) + " argument of '" + functionName + "' function", 0);
            }
            return param.toString();
        }
        return null;
    }

    public static Function parseStringFunctionParam(final Param[] params, final int pos, final String functionName) throws ParseException {
        Function function = null;

        if (params.length > pos) {
            final Param param = params[pos];
            if (param instanceof Function) {
                function = (Function) param;
            } else if (!(param instanceof ValString)) {
                throw new ParseException("String or function expected as " + getPos(pos) + " argument of '" + functionName + "' function", 0);
            } else {
                function = new StaticValueFunction((Val) param);
            }
        }

        return function;
    }

    public static int parseIntParam(final Param[] params, final int pos, final String functionName, final boolean positive) throws ParseException {
        if (params.length > pos) {
            if (params[pos] instanceof Val) {
                final Param param = params[pos];
                final Integer num = ((Val) param).toInteger();
                if (num != null) {
                    if (positive && num <= 0) {
                        throw new ParseException("Positive number expected as " + getPos(pos) + " argument of '" + functionName + "' function", 0);
                    }
                    return num;
                }
            }
        }
        throw new ParseException("Number expected as " + getPos(pos) + " argument of '" + functionName + "' function", 0);
    }

    private static String getPos(int pos) {
        if (pos < POSITIONS.length) {
            return POSITIONS[pos];
        }
        return String.valueOf(pos);
    }
}
