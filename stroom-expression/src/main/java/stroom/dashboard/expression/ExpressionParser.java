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
    // We deliberately exclude brackets as they are treated as an unnamed function.
    private static final Type[] BODMAS = new Type[]{
            Type.ORDER,
            Type.DIVISION,
            Type.MULTIPLICATION,
            Type.ADDITION,
            Type.SUBTRACTION,
    };

    private static final Type[] EQUALITY = new Type[]{
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

        // Repeatedly scan so that innermost nested functions are created first.
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

                    // Create a new list of objects to sandwich this function.
                    return sandwich(objects, function, start, end);
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
        final Object param = getParam(copyList(objects, 0, objects.size() - 1), fieldIndexMap);
        return Collections.singletonList(param);

    }

    private Function getFunction(final List<Object> objects, final int start, final int end, final FieldIndexMap fieldIndexMap) throws ParseException {
        // Get the function.
        final Token functionToken = (Token) objects.get(start);

        int functionStart = start + 1;
        int functionEnd = end - 1;
        Object[] params;

        // Don't bother to try and get parameters if there can't be any.
        if (functionStart <= functionEnd) {
            // Process each parameter.
            int paramStart = -1;
            final List<Object> paramList = new ArrayList<>((functionEnd - functionStart) + 1);

            // Turn comma separated tokens into parameters.
            for (int i = functionStart; i <= functionEnd; i++) {
                final Object object = objects.get(i);
                if (object instanceof Token) {
                    final Token token = (Token) object;
                    if (Type.COMMA.equals(token.getType())) {
                        // Ifg we haven't found a parameter from the previous token or object then this comma is unexpected.
                        if (paramStart == -1) {
                            throw new ParseException("Unexpected comma", token.getStart());
                        }

                        final int paramEnd = i - 1;
                        final Object param = getParam(copyList(objects, paramStart, paramEnd), fieldIndexMap);
                        paramList.add(param);

                        paramStart = -1;
                    } else if (paramStart == -1) {
                        paramStart = i;
                    }
                } else if (paramStart == -1) {
                    paramStart = i;
                }
            }

            // Capture last param if there is one.
            if (paramStart != -1) {
                final Object param = getParam(copyList(objects, paramStart, functionEnd), fieldIndexMap);
                paramList.add(param);
            }

            // Turn param list into an array.
            params = paramList.toArray();

        } else {
            // There are no parameters.
            params = new Object[0];
        }

        String functionName = functionToken.toString();
        Function function;

        if (functionName.equals("(")) {
            // If this function just represents a bracketed section then get a Brackets function.
            function = new Brackets();
        } else {
            // This is a named function so see if we can create it.

            // Trim off the bracket to get the function name.
            functionName = functionName.substring(0, functionName.length() - 1);

            // Create the function.
            function = functionFactory.create(functionName);
        }

        if (function == null) {
            throw new ParseException("Unknown function '" + functionName + "'", functionToken.getStart());
        }

        // Set the parameters on the function.
        function.setParams(params);

        // Return the function.
        return function;
    }

    private Object getParam(final List<Object> objects, final FieldIndexMap fieldIndexMap) throws ParseException {
        List<Object> newObjects = objects;

        // If no objects are included to create this param then return null.
        if (newObjects.size() == 0) {
            return null;
        }

        // If there is only a single object then turn it into a parameter if necessary and return.
        if (newObjects.size() == 1) {
            final Object object = newObjects.get(0);
            if (object instanceof Token) {
                final Token token = (Token) object;
                return paramFactory.create(fieldIndexMap, token);
            }
            return object;
        }

        // Repeatedly try and apply BODMAS operators.
        int lastSize = 0;
        while (newObjects.size() > 1 && newObjects.size() != lastSize) {
            lastSize = newObjects.size();
            newObjects = applyBODMAS(newObjects, fieldIndexMap);
        }

        // Repeatedly try and apply equality operators.
        lastSize = 0;
        while (newObjects.size() > 1 && newObjects.size() != lastSize) {
            lastSize = newObjects.size();
            newObjects = applyEquality(newObjects, fieldIndexMap);
        }

        // If there is only a single object then turn it into a parameter if necessary and return.
        if (newObjects.size() == 1) {
            final Object object = newObjects.get(0);
            if (object instanceof Token) {
                final Token token = (Token) object;
                return paramFactory.create(fieldIndexMap, token);
            }
            return object;
        }

        // So we've got more than one object and no BODMAS or equality operators to apply - this is not allowed.
        final Object object = newObjects.get(1);
        if (object instanceof Token) {
            final Token token = (Token) object;
            throw new ParseException("Unexpected token", token.getStart());
        }

        throw new ParseException("Unexpected '" + object.toString() + "'", -1);
    }

    private List<Object> applyBODMAS(final List<Object> objects, final FieldIndexMap fieldIndexMap) throws ParseException {
        // If there is more than one object then apply BODMAS rules.
        for (final Type type : BODMAS) {
            for (int i = 0; i < objects.size(); i++) {
                final Object object = objects.get(i);
                if (object instanceof Token) {
                    final Token token = (Token) object;
                    if (type.equals(token.getType())) {
                        int leftParamIndex = i - 1;
                        int rightParamIndex = i + 1;

                        // Get left param.
                        final Object leftParam = getParam(copyList(objects, leftParamIndex, leftParamIndex), fieldIndexMap);
                        // Get right param.
                        final Object rightParam = getParam(copyList(objects, rightParamIndex, rightParamIndex), fieldIndexMap);

                        // Addition and subtraction without a preceding param are allowed. In this form plus can be
                        // ignored and minus will negate right param.
                        if (leftParam == null && !Type.ADDITION.equals(type) && !Type.SUBTRACTION.equals(type)) {
                            throw new ParseException("No parameter before operator", token.getStart());
                        }
                        if (rightParam == null) {
                            throw new ParseException("No parameter after operator", token.getStart());
                        }

                        Object param = null;
                        if (leftParam == null) {
                            // Ignore positive sign as it is superfluous.
                            if (Type.ADDITION.equals(type)) {
                                param = rightParam;
                            }

                            // If there is a negative sign then negate the param.
                            if (Type.SUBTRACTION.equals(type)) {
                                final Negate negate = new Negate(token.toString());
                                negate.setParams(new Object[]{rightParam});
                                param = negate;
                            }
                        } else {
                            final Function function = functionFactory.create(token.toString());
                            function.setParams(new Object[]{leftParam, rightParam});
                            param = function;
                        }

                        // Return a new object list that sandwiches the new object.
                        return sandwich(objects, param, leftParamIndex, rightParamIndex);
                    }
                }
            }
        }

        return objects;
    }

    private List<Object> applyEquality(final List<Object> objects, final FieldIndexMap fieldIndexMap) throws ParseException {
        // If there is more than one object then apply equality rules.
        for (final Type type : EQUALITY) {
            for (int i = 0; i < objects.size(); i++) {
                final Object object = objects.get(i);
                if (object instanceof Token) {
                    final Token token = (Token) object;
                    if (type.equals(token.getType())) {
                        // Get before param.
                        final Object leftParam = getParam(copyList(objects, 0, i - 1), fieldIndexMap);
                        // Get after param.
                        final Object rightParam = getParam(copyList(objects, i + 1, objects.size() - 1), fieldIndexMap);

                        if (leftParam == null) {
                            throw new ParseException("No parameter before operator", token.getStart());
                        }
                        if (rightParam == null) {
                            throw new ParseException("No parameter after operator", token.getStart());
                        }

                        final Function function = functionFactory.create(token.toString());
                        function.setParams(new Object[]{leftParam, rightParam});
                        return Collections.singletonList(function);
                    }
                }
            }
        }

        return objects;
    }

    private List<Object> copyList(final List<Object> list, final int startIndex, final int endIndex) {
        if (endIndex < startIndex) {
            return Collections.emptyList();
        }

        if (startIndex == endIndex) {
            return Collections.singletonList(list.get(startIndex));
        }

        final List<Object> newList = new ArrayList<>((endIndex - startIndex) + 1);
        for (int i = startIndex; i <= endIndex; i++) {
            newList.add(list.get(i));
        }
        return newList;
    }

    private List<Object> sandwich(final List<Object> list, final Object object, final int insertStart, final int insertEnd) {
        if (insertStart <= 0 && insertEnd >= list.size() - 1) {
            return Collections.singletonList(object);
        }

        // Make sure the insert start position will allow the new item to be added.
        assert (insertStart < list.size() - 1);

        // Add all objects that exist before the insert start position.
        final List<Object> newList = new ArrayList<>(list.size() + 1);
        for (int i = 0; i < insertStart; i++) {
            newList.add(list.get(i));
        }

        // Add the object.
        newList.add(object);

        // Add all objects that exist after the insert end position.
        for (int i = insertEnd + 1; i < list.size(); i++) {
            newList.add(list.get(i));
        }

        return newList;
    }
}
