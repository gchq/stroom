/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.expression;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class ExpressionParser {
	private static final String[] NUMERIC_FUNCTIONS = new String[] { "/", "*", "+", "-", "=", ">", "<", ">=", "<=" };

    private final FunctionFactory functionFactory;
    private final ParamFactory paramFactory;

    public ExpressionParser(final FunctionFactory functionFactory, final ParamFactory paramFactory) {
        this.functionFactory = functionFactory;
        this.paramFactory = paramFactory;
    }

    public Expression parse(final FieldIndexMap fieldIndexMap, final String expression) throws ParseException {
        if (expression == null || expression.trim().length() == 0) {
            return null;
        }

        final CharSlice slice = new CharSlice(expression);
        final Object[] params = getParams(fieldIndexMap, slice);
        if (params.length == 0) {
            return null;
        }

        if (params.length > 1) {
            throw new ParseException("Unexpected number of parameters", 0);
        }

        final Expression exp = new Expression();
        exp.setParams(params);
        return exp;
    }

    private Object[] getParams(final FieldIndexMap fieldIndexMap, final CharSlice slice) throws ParseException {
        final List<Object> params = new ArrayList<Object>();
        final List<CharSlice> parts = split(slice, ",");

        // Parse each param.
        for (final CharSlice part : parts) {
            final Object param = getParam(fieldIndexMap, part);
            params.add(param);
        }

        Object[] arr = new Object[params.size()];
        arr = params.toArray(arr);

        return arr;
    }

    private Object getParam(final FieldIndexMap fieldIndexMap, final CharSlice slice) throws ParseException {
        Function function = null;

        for (final String func : NUMERIC_FUNCTIONS) {
            final List<CharSlice> parts = split(slice, func);
            if (parts.size() > 1) {
                function = functionFactory.create(func);
                final Object[] params = new Object[parts.size()];
                for (int i = 0; i < parts.size(); i++) {
                    final CharSlice part = parts.get(i);
                    final Object param = getParam(fieldIndexMap, part);
                    params[i] = param;
                }
                function.setParams(params);

                break;
            }
        }

        // If we got a numeric function then this is our work done.
        if (function != null) {
            return function;
        }

        // We didn't get a numeric function so try and get a named function.
        final Object p = getFunction(fieldIndexMap, slice.trim());
        return p;
    }

    private List<CharSlice> split(final CharSlice slice, final String delimiter) throws ParseException {
        final List<CharSlice> parts = new ArrayList<CharSlice>();

        boolean inQuote = false;
        int bracketDepth = 0;
        int quotePos = -1;
        int off = 0;

        for (int i = 0; i < slice.length(); i++) {
            final char c = slice.charAt(i);

            if (c == '\'') {
                // If we are in a quote and get two quotes together then this is
                // an escaped quote.
                boolean escapedQuote = false;
                if (inQuote && i + 1 < slice.length()) {
                    if (slice.charAt(i + 1) == '\'') {
                        escapedQuote = true;
                        i++;
                    }
                }

                if (!escapedQuote) {
                    inQuote = !inQuote;
                    quotePos = slice.getOffset() + i;
                }

            } else if (!inQuote) {
                if (c == '(') {
                    bracketDepth++;
                } else if (c == ')') {
                    bracketDepth--;
                }

                if (c == delimiter.charAt(0) && bracketDepth == 0) {
                    parts.add(slice.subSlice(off, i));
                    off = i + 1;
                }
            }
        }

        if (off < slice.length()) {
            parts.add(slice.subSlice(off, slice.length()));
        }

        if (inQuote) {
            throw new ParseException("Unmatched quote", quotePos);
        }
        if (bracketDepth > 0) {
            throw new ParseException("Unmatched brackets", slice.getOffset() + off);
        }

        return parts;
    }

    private Object getFunction(final FieldIndexMap fieldIndexMap, final CharSlice slice) throws ParseException {
        Function function = null;

        boolean inQuote = false;
        boolean inCurlyBraces = false;
        for (int i = 0; i < slice.length(); i++) {
            final char c = slice.charAt(i);

            if (c == '\'') {
                inQuote = !inQuote;

            } else if (c == '{') {
                inCurlyBraces = true;

            } else if (c == '}') {
                inCurlyBraces = false;

            } else if (!inQuote && !inCurlyBraces) {
                if (c == '(') {
                    final CharSlice name = slice.subSlice(0, i);
                    final CharSlice trimmedName = name.trim();
                    if (trimmedName.length() == 0) {
                        function = new Brackets();
                    } else {
                        function = functionFactory.create(trimmedName.toString());
                    }

                    if (function == null) {
                        throw new ParseException("Unknown function '" + trimmedName.toString() + "'",
                                slice.getOffset());
                    }

                    // Get params for this function.
                    if (slice.length() < 2 || !slice.endsWith(")")) {
                        throw new ParseException("Unmatched brackets", slice.getOffset());
                    }

                    final CharSlice sub = slice.subSlice(i + 1, slice.length() - 1);
                    final Object[] params = getParams(fieldIndexMap, sub);
                    function.setParams(params);

                    break;
                }
            }
        }

        if (function != null) {
            return function;
        }

        final Object param = paramFactory.create(fieldIndexMap, slice.trim());
        return param;
    }
}
