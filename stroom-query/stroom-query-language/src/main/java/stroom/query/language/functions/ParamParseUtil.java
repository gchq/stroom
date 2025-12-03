/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.language.functions;

import java.text.ParseException;

public class ParamParseUtil {

    private static final String[] POSITIONS = {
            "first",
            "second",
            "third",
            "fourth",
            "fifth",
            "sixth",
            "seventh",
            "eighth",
            "ninth",
            "tenth"};

    public static String parseStringParam(final Param[] params, final int pos, final String functionName)
            throws ParseException {
        if (params.length > pos) {
            final Param param = params[pos];
            if (!(param instanceof ValString)) {
                throw new ParseException(
                        "String expected as " + getPos(pos) + " argument of '" + functionName + "' function", 0);
            }
            return param.toString();
        }
        return null;
    }

    public static Function parseStringFunctionParam(final Param[] params, final int pos, final String functionName)
            throws ParseException {
        Function function = null;

        if (params.length > pos) {
            final Param param = params[pos];
            if (param instanceof Function) {
                function = (Function) param;
            } else if (!(param instanceof ValString)) {
                throw new ParseException("String or function expected as " + getPos(pos) + " argument of '" +
                                         functionName + "' function", 0);
            } else {
                function = new StaticValueFunction((Val) param);
            }
        }

        return function;
    }

    public static int parseIntParam(final Param[] params,
                                    final int pos,
                                    final String functionName,
                                    final boolean positive) throws ParseException {
        if (params.length > pos) {
            if (params[pos] instanceof Val) {
                final Param param = params[pos];
                final Integer num = ((Val) param).toInteger();
                if (num != null) {
                    if (positive && num <= 0) {
                        throw new ParseException("Positive number expected as " + getPos(pos) + " argument of '" +
                                                 functionName + "' function", 0);
                    }
                    return num;
                }
            }
        }
        throw new ParseException(
                "Number expected as " + getPos(pos) + " argument of '" + functionName + "' function", 0);
    }

    public static boolean parseBooleanParam(final Param[] params,
                                            final int pos,
                                            final String functionName) throws ParseException {
        if (params.length > pos) {
            final Param param = params[pos];
            if (!(param instanceof ValBoolean)) {
                throw new ParseException(
                        "Boolean expected as " + getPos(pos) + " argument of '" + functionName + "' function", 0);
            }
            return ((ValBoolean) param).toBoolean();
        }
        return false;
    }

    private static String getPos(final int pos) {
        if (pos < POSITIONS.length) {
            return POSITIONS[pos];
        }
        return String.valueOf(pos);
    }

    public static boolean supportsStaticComputation(final Param... params) {
        // See if this is a static computation.
        boolean simple = true;
        if (params != null) {
            for (final Param param : params) {
                if (!(param instanceof Val)) {
                    simple = false;
                    break;
                }
            }
        }
        return simple;
    }
}
