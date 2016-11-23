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

import stroom.dashboard.expression.ExpressionTokeniser.Token;
import stroom.dashboard.expression.ExpressionTokeniser.Token.Type;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExpressionParser {
    private static final Type[] BODMAS_AND_EQUALITY = new Type[]{
            Type.ORDER,
            Type.DIVISION,
            Type.MULTIPLICATION,
            Type.ADDITION,
            Type.SUBTRACTION,
            Type.EQUALS,
            Type.GREATER_THAN,
            Type.GREATER_THAN_OR_EQUAL_TO,
            Type.LESS_THAN,
            Type.LESS_THAN_OR_EQUAL_TO
    };

    private final FunctionFactory functionFactory;
    private final ParamFactory paramFactory;

    public ExpressionParser(final FunctionFactory functionFactory, final ParamFactory paramFactory) {
        this.functionFactory = functionFactory;
        this.paramFactory = paramFactory;
    }

    public Expression parse(final FieldIndexMap fieldIndexMap, final String input) throws ParseException {
        if (input == null || input.trim().length() == 0) {
            return null;
        }

        // First tokenise the expression.
        final List<Token> tokens = new ExpressionTokeniser().tokenise(input);

        // Do some preliminary validation of the tokens.
        new ExpressionValidator().validate(tokens);

        // Put tokens into an object array ready to replace with functions and remove whitespace.
        List<Object> objects = new ArrayList<>(tokens.size());
        for (final Token token : tokens) {
            if (!Type.WHITESPACE.equals(token.getType())) {
                objects.add(token);
            }
        }

        // Repeatedly scan for nested functions.
        int lastSize = 0;
        while (objects.size() != lastSize) {
            lastSize = objects.size();
            objects = processObjects(objects, fieldIndexMap);
        }

        // We should have a single object.
        if (objects.size() == 0) {
            return null;
        } else if (objects.size() > 1) {
            throw new ParseException("Expected only 1 object", -1);
        }

        final Expression expression = new Expression();
        expression.setParams(objects.toArray());
        return expression;
    }

    private List<Object> processObjects(final List<Object> objects, final FieldIndexMap fieldIndexMap) throws ParseException {
        Token functionStart = null;
        int start = -1;
        int end = -1;
        for (int i = 0; i < objects.size(); i++) {
            final Object object = objects.get(i);

            if (object instanceof Token) {
                final Token token = (Token) object;
                if (Type.FUNCTION_START.equals(token.getType())) {
                    functionStart = token;
                    start = i;
                } else if (Type.FUNCTION_END.equals(token.getType())) {
                    if (functionStart == null) {
                        throw new ParseException("Unexpected close bracket found", token.getStart());
                    }

                    end = i;
                    final Function function = getFunction(objects, start, end, fieldIndexMap);

                    // Add all objects that exist before this function.
                    final List<Object> newObjects = new ArrayList<>();
                    for (int j = 0; j < start; j++) {
                        newObjects.add(objects.get(j));
                    }

                    // Add the function.
                    newObjects.add(function);

                    // Add all objects that exist after this function.
                    for (int j = end + 1; j < objects.size(); j++) {
                        newObjects.add(objects.get(j));
                    }

                    return newObjects;
                }
            }
        }

        // If we got here then there are no functions left. If there are no functions left to process then we can try
        // and turn anything that remains into a single function.

        if (objects.size() == 1 && objects.get(0) instanceof Function) {
            return objects;
        }

        // We should not have any comma, whitespace or unidentified tokens.
        for (final Object object : objects) {
            if (object instanceof Token) {
                final Token token = (Token) object;
                if (Type.COMMA.equals(token.getType()) || Type.WHITESPACE.equals(token.getType()) || Type.UNIDENTIFIED.equals(token.getType())) {
                    throw new ParseException("Unexpected token found", token.getStart());
                }
            }
        }

        // Any content that remains must be a parameter or parameter expression.
        final Object param = getParam(objects, 0, objects.size(), fieldIndexMap);
        return Collections.singletonList(param);

    }

    private Function getFunction(final List<Object> objects, final int start, final int end, final FieldIndexMap fieldIndexMap) throws ParseException {
        // Get the function.
        final Token functionToken = (Token) objects.get(start);

        // Process each parameter.
        int functionStart = start + 1;
        int functionEnd = end - 1;
        int paramStart = -1;
        List<Object> params = new ArrayList<>();

        // Turn comma separated tokens into parameters.
        for (int i = functionStart; i <= functionEnd; i++) {
            final Object object = objects.get(i);
            if (object instanceof Token) {
                final Token token = (Token) object;
                if (Type.COMMA.equals(token.getType())) {
                    if (paramStart == -1) {
                        throw new ParseException("Unexpected comma", token.getStart());
                    }

                    final int paramEnd = i - 1;
                    final Object param = getParam(objects, paramStart, paramEnd, fieldIndexMap);
                    params.add(param);

                    paramStart = -1;
                } else if (paramStart == -1) {
                    paramStart = i;
                }
            }
        }

        // Capture last param.
        if (paramStart != -1) {
            final Object param = getParam(objects, paramStart, functionEnd, fieldIndexMap);
            params.add(param);
        }

        String functionName = functionToken.toString();
        Function function;
        if (functionName.equals("(")) {
            function = new Brackets();
        } else {
            // Trim off the bracket.
            functionName = functionName.substring(0, functionName.length() - 1);
            // Create the function.
            function = functionFactory.create(functionName);
        }

        if (function == null) {
            throw new ParseException("Unknown function '" + functionName + "'", functionToken.getStart());
        }

        function.setParams(params.toArray());

        return function;
    }

    private Object getParam(final List<Object> objects, final int start, final int end, final FieldIndexMap fieldIndexMap) throws ParseException {
        // Apply BODMAS and equality rules.
        for (final Type type : BODMAS_AND_EQUALITY) {
            for (int i = start; i <= end; i++) {
                final Object object = objects.get(i);
                final Token token = (Token) object;
                if (type.equals(token.getType())) {
                    // Get before param.
                    final Object param1 = getParam(objects, start, i - 1, fieldIndexMap);
                    // Get after param.
                    final Object param2 = getParam(objects, i + 1, end, fieldIndexMap);

                    // Addition and subtraction without a preceding param are allowed. In this form plus can be
                    // ignored and minus will negate second param.
                    if (param1 == null && !Type.ADDITION.equals(type) && !Type.SUBTRACTION.equals(type)) {
                        throw new ParseException("No parameter before operator", token.getStart());
                    }
                    if (param2 == null) {
                        throw new ParseException("No parameter after operator", token.getStart());
                    }

                    if (param1 == null) {
                        if (Type.ADDITION.equals(type)) {
                            return param2;
                        }
                        if (Type.SUBTRACTION.equals(type)) {
                            final Negate negate = new Negate(token.toString());
                            negate.setParams(new Object[]{param2});
                            return negate;
                        }
                    }

                    final Function function = functionFactory.create(token.toString());
                    function.setParams(new Object[]{param1, param2});
                    return function;
                }
            }
        }

        // So we've got no BODMAS operators to apply. We should have a single token.
        if (start != end) {
            final Token token = (Token) objects.get(start + 1);
            throw new ParseException("Unexpected token", token.getStart());
        }

        final Token token = (Token) objects.get(start);
        return paramFactory.create(fieldIndexMap, token);
    }


//
//
//
//
//    private Expression temp(final FieldIndexMap fieldIndexMap, final String expression) throws ParseException {
//
//
//
//
//
//
//
//        List<Object> segments = new ArrayList<>();
//        segments.add(expression);
//
//        // Parse out string constants.
//        segments = parseStringConstants(segments);
//
//        // Parse out field references.
//        segments = parseFieldReferences(segments, fieldIndexMap);
//
//        // Parse out functions and brackets.
//        segments = parseFunctions(segments);
//
//        while (segments.size() > 1) {
//            int functionStart = -1;
//            for (int i = 0; i < segments.size(); i++) {
//                final Object o = segments.get(i);
//                if (o instanceof FunctionStart) {
//                    functionStart = i;
//                } else if (o instanceof FunctionEnd) {
//                    if (functionStart == -1) {
//                        // TODO : Sort out the position value so that it matches original string.
//                        throw new ParseException("Unexpected closing bracket", i);
//                    }
//
//                    int functionEnd = i;
//
//                    for (int j = functionStart + 1; j < functionEnd; j++) {
//
//
//
//
//
//
//
//
//                    }
//
//
//
//                    final FunctionStart func = (FunctionStart) segments.get(functionStart);
//
//
//
//
//
//                    break;
//                }
//            }
//        }
//
//
//
//
//        // Parse out order.
//
//
//
//
//
//
//        // Parse out multiplication.
//
//
//        // Parse out addition.
//
//
//        // Parse out subtraction.
//
//
//
//
//
//
//
//
//
//
//
//        final CharSlice slice = new CharSlice(expression);
//        final Object[] params = getParams(fieldIndexMap, slice);
//        if (params.length == 0) {
//            return null;
//        }
//
//        if (params.length > 1) {
//            throw new ParseException("Unexpected number of parameters", 0);
//        }
//
//        final Expression exp = new Expression();
//        exp.setParams(params);
//        return exp;
//    }
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//    private List<Object> parseStringConstants(final List<Object> input) {
//        final List<Object> output = new ArrayList<>();
//        for (final Object object : input) {
//            if (object instanceof String) {
//                output.addAll(parseStringConstant((String) object));
//            } else {
//                output.add(object);
//            }
//        }
//        return output;
//    }
//
//    private List<Object> parseStringConstant(final String input) {
//        final List<Object> output = new ArrayList<>();
//
//        final StringBuilder sb = new StringBuilder();
//        final char[] chars = input.toCharArray();
//        boolean inQuote = false;
//        for (int i = 0; i < chars.length; i++) {
//            if (chars[i] == '\'') {
//                // If we are in a quote and get two quotes together then this is
//                // an escaped quote.
//                if (inQuote && i + 1 < chars.length && chars[i + 1] == '\'') {
//                    sb.append(chars[i]);
//                    i++;
//                } else {
//                    inQuote = !inQuote;
//
//                    final String value = sb.toString();
//                    sb.setLength(0);
//
//                    if (!inQuote) {
//                        output.add(new StaticValueFunction(value));
//                    } else if (value.length() > 0) {
//                        output.add(value);
//                    }
//                }
//            } else {
//                sb.append(chars[i]);
//            }
//        }
//
//        final String value = sb.toString();
//        if (value.length() > 0) {
//            output.add(value);
//        }
//
//        return output;
//    }
//
//    private List<Object> parseFieldReferences(final List<Object> input, final FieldIndexMap fieldIndexMap) {
//        final List<Object> output = new ArrayList<>();
//        for (final Object object : input) {
//            if (object instanceof String) {
//                output.addAll(parseFieldReference((String) object, fieldIndexMap));
//            } else {
//                output.add(object);
//            }
//        }
//        return output;
//    }
//
//    private List<Object> parseFieldReference(final String input, final FieldIndexMap fieldIndexMap) {
//        final List<Object> output = new ArrayList<>();
//
//        final StringBuilder sb = new StringBuilder();
//        final char[] chars = input.toCharArray();
//        boolean inRef = false;
//        for (int i = 0; i < chars.length; i++) {
//            if (!inRef && chars[i] == '$' && i + 1 < chars.length && chars[i + 1] == '{') {
//                inRef = true;
//                i++;
//
//                final String value = sb.toString();
//                sb.setLength(0);
//                if (value.length() > 0) {
//                    output.add(value);
//                }
//
//            } else if (inRef && chars[i] == '}') {
//                final String value = sb.toString();
//                sb.setLength(0);
//
//                if (value.length() > 0) {
//                    final int fieldIndex = fieldIndexMap.create(value);
//                    output.add(new Ref(value, fieldIndex));
//                }
//
//            } else {
//                sb.append(chars[i]);
//            }
//        }
//
//        final String value = sb.toString();
//        if (value.length() > 0) {
//            output.add(value);
//        }
//
//        return output;
//    }
//
//    private List<Object> parseFunctions(final List<Object> input) {
//        final List<Object> output = new ArrayList<>();
//        for (final Object object : input) {
//            if (object instanceof String) {
//                output.addAll(parseFunction((String) object));
//            } else {
//                output.add(object);
//            }
//        }
//        return output;
//    }
//
//    private List<Object> parseFunction(final String input) {
//        final List<Object> output = new ArrayList<>();
//
//        final StringBuilder sb = new StringBuilder();
//        final char[] chars = input.toCharArray();
//        for (int i = 0; i < chars.length; i++) {
//            if (chars[i] == '(') {
//                final String value = sb.toString();
//                sb.setLength(0);
//                output.add(new FunctionStart(value));
//
//            } else if (chars[i] == ')') {
//                final String value = sb.toString();
//                sb.setLength(0);
//
//                if (value.length() > 0) {
//                    output.addAll(atomise(value));
//                }
//
//                output.add(new FunctionEnd());
//
//            } else if (!Character.isLetter(chars[i])) {
//                sb.append(chars[i]);
//                final String value = sb.toString();
//                sb.setLength(0);
//
//                if (value.length() > 0) {
//                    output.addAll(atomise(value));
//                }
//            }
//        }
//
//        final String value = sb.toString();
//        if (value.length() > 0) {
//            output.addAll(atomise(value));
//        }
//
//        return output;
//    }
//
//    private List<Object> atomise(final String input) {
//        final List<Object> output = new ArrayList<>();
//        final char[] chars = input.toCharArray();
//
//        // Extract numerals.
//        final Matcher matcher = NUMBER.matcher(input);
//
//        int lastIndex = 0;
//        while (matcher.find()) {
//            int start = matcher.start();
//            int end = matcher.end();
//
//            if (lastIndex < start) {
//                for (int i = lastIndex; i < start; i++) {
//                    if (!Character.isWhitespace(chars[i])) {
//                        output.add(Character.valueOf(chars[i]));
//                    }
//                }
//            }
//
//            output.add(new StaticValueFunction(Long.parseLong(input.substring(start, end))));
//
//            lastIndex = end + 1;
//        }
//
//        for (int i = lastIndex; i < chars.length; i++) {
//            if (!Character.isWhitespace(chars[i])) {
//                output.add(Character.valueOf(chars[i]));
//            }
//        }
//
//        return output;
//    }
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//    private static class FunctionStart {
//        private final String name;
//        FunctionStart(final String name) {
//            this.name = name;
//        }
//
//        public String getName() {
//            return name;
//        }
//    }
//
//    private static class FunctionEnd {
//    }
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//    private Object[] getParams(final FieldIndexMap fieldIndexMap, final CharSlice slice) throws ParseException {
//        final List<Object> params = new ArrayList<>();
//        final List<CharSlice> parts = split(slice, ",");
//
//        // Parse each param.
//        for (final CharSlice part : parts) {
//            final Object param = getParam(fieldIndexMap, part);
//            params.add(param);
//        }
//
//        Object[] arr = new Object[params.size()];
//        arr = params.toArray(arr);
//
//        return arr;
//    }
//
//    private Object getParam(final FieldIndexMap fieldIndexMap, final CharSlice slice) throws ParseException {
//        Function function = null;
//
//        for (final String func : NUMERIC_FUNCTIONS) {
//            final List<CharSlice> parts = split(slice, func);
//            if (parts.size() > 1) {
//                function = functionFactory.create(func);
//                final Object[] params = new Object[parts.size()];
//                for (int i = 0; i < parts.size(); i++) {
//                    final CharSlice part = parts.get(i);
//                    final Object param = getParam(fieldIndexMap, part);
//                    params[i] = param;
//                }
//                function.setParams(params);
//
//                break;
//            }
//        }
//
//        // If we got a numeric function then this is our work done.
//        if (function != null) {
//            return function;
//        }
//
//        // We didn't get a numeric function so try and get a named function.
//        final Object p = getFunction(fieldIndexMap, slice.trim());
//        return p;
//    }
//
//    private List<CharSlice> split(final CharSlice slice, final String delimiter) throws ParseException {
//        final List<CharSlice> parts = new ArrayList<>();
//
//        boolean inQuote = false;
//        int bracketDepth = 0;
//        int quotePos = -1;
//        int off = 0;
//
//        for (int i = 0; i < slice.length(); i++) {
//            final char c = slice.charAt(i);
//
//            if (c == '\'') {
//                // If we are in a quote and get two quotes together then this is
//                // an escaped quote.
//                boolean escapedQuote = false;
//                if (inQuote && i + 1 < slice.length()) {
//                    if (slice.charAt(i + 1) == '\'') {
//                        escapedQuote = true;
//                        i++;
//                    }
//                }
//
//                if (!escapedQuote) {
//                    inQuote = !inQuote;
//                    quotePos = slice.getOffset() + i;
//                }
//
//            } else if (!inQuote) {
//                if (c == '(') {
//                    bracketDepth++;
//                } else if (c == ')') {
//                    bracketDepth--;
//                }
//
//                if (c == delimiter.charAt(0) && bracketDepth == 0) {
//                    parts.add(slice.subSlice(off, i));
//                    off = i + 1;
//                }
//            }
//        }
//
//        if (off < slice.length()) {
//            parts.add(slice.subSlice(off, slice.length()));
//        }
//
//        if (inQuote) {
//            throw new ParseException("Unmatched quote", quotePos);
//        }
//        if (bracketDepth > 0) {
//            throw new ParseException("Unmatched brackets", slice.getOffset() + off);
//        }
//
//        return parts;
//    }
//
//    private Object getFunction(final FieldIndexMap fieldIndexMap, final CharSlice slice) throws ParseException {
//        Function function = null;
//
//        boolean inQuote = false;
//        boolean inCurlyBraces = false;
//        for (int i = 0; i < slice.length(); i++) {
//            final char c = slice.charAt(i);
//
//            if (c == '\'') {
//                inQuote = !inQuote;
//
//            } else if (c == '{') {
//                inCurlyBraces = true;
//
//            } else if (c == '}') {
//                inCurlyBraces = false;
//
//            } else if (!inQuote && !inCurlyBraces) {
//                if (c == '(') {
//                    final CharSlice name = slice.subSlice(0, i);
//                    final CharSlice trimmedName = name.trim();
//                    if (trimmedName.length() == 0) {
//                        function = new Brackets();
//                    } else {
//                        function = functionFactory.create(trimmedName.toString());
//                    }
//
//                    if (function == null) {
//                        throw new ParseException("Unknown function '" + trimmedName.toString() + "'",
//                                slice.getOffset());
//                    }
//
//                    // Get params for this function.
//                    if (slice.length() < 2 || !slice.endsWith(")")) {
//                        throw new ParseException("Unmatched brackets", slice.getOffset());
//                    }
//
//                    final CharSlice sub = slice.subSlice(i + 1, slice.length() - 1);
//                    final Object[] params = getParams(fieldIndexMap, sub);
//                    function.setParams(params);
//
//                    break;
//                }
//            }
//        }
//
//        if (function != null) {
//            return function;
//        }
//
//        final Object param = paramFactory.create(fieldIndexMap, slice.trim());
//        return param;
//    }
}
