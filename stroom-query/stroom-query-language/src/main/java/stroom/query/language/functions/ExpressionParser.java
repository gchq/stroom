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

import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.FunctionGroup;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenGroup;
import stroom.query.api.token.TokenType;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Tokeniser;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ExpressionParser {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExpressionParser.class);

    // We deliberately exclude brackets as they are treated as an unnamed function.
    private static final TokenType[] BODMAS = new TokenType[]{
            TokenType.ORDER,
            TokenType.DIVISION,
            TokenType.MULTIPLICATION,
            TokenType.MODULUS,
            TokenType.PLUS,
            TokenType.MINUS,
    };

    private static final TokenType[] EQUALITY = new TokenType[]{
            TokenType.EQUALS,
            TokenType.NOT_EQUALS,
            TokenType.GREATER_THAN,
            TokenType.GREATER_THAN_OR_EQUAL_TO,
            TokenType.LESS_THAN,
            TokenType.LESS_THAN_OR_EQUAL_TO
    };

    private static final Set<TokenType> BODMAS_SET = Set.of(
            TokenType.ORDER,
            TokenType.DIVISION,
            TokenType.MULTIPLICATION,
            TokenType.MODULUS,
            TokenType.PLUS,
            TokenType.MINUS);

    private static final Set<TokenType> LOGICAL_OPERATORS = Set.of(
            TokenType.NOT,
            TokenType.OR,
            TokenType.AND);

    private final ParamFactory paramFactory;

    public ExpressionParser(final ParamFactory paramFactory) {
        this.paramFactory = paramFactory;
    }

    public Expression parse(final ExpressionContext expressionContext,
                            final FieldIndex fieldIndex,
                            final String input) throws ParseException {
        LOGGER.trace(() -> "parse() - " + input);

        try {
            if (input == null || input.isBlank()) {
                return null;
            }

            // First tokenize the expression.
            final List<Token> tokens = Tokeniser.parse(input);
            // Then create some structure from the tokens.
            final TokenGroup tokenGroup = StructureBuilder.create(tokens);

            return parse(expressionContext, fieldIndex, tokenGroup.getChildren());

        } catch (final Throwable e) {
            LOGGER.debug(() -> "Error parsing expression: " + input);
            throw e;
        }
    }

    public Expression parse(final ExpressionContext expressionContext,
                            final FieldIndex fieldIndex,
                            final List<AbstractToken> tokens) throws ParseException {
        LOGGER.trace(() -> "parse() - " + getTokenString(tokens));

        try {
            final List<Param> objects = processObjects(tokens, expressionContext, fieldIndex);

            // We should have a single object.
            if (objects.isEmpty()) {
                return null;
            } else if (objects.size() > 1) {
                throw new ParseException("Expected only 1 object", -1);
            }

            final Expression expression = new Expression();
            expression.setParams(objects.toArray(new Param[0]));
            return expression;

        } catch (final Throwable e) {
            LOGGER.debug(() -> "Error parsing expression: " + getTokenString(tokens));
            throw e;
        }
    }

    private List<Param> processObjects(final List<AbstractToken> tokens,
                                       final ExpressionContext expressionContext,
                                       final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "processObjects() - " + getTokenString(tokens));

        final List<Object> output = new ArrayList<>(tokens.size());

        // Process functions and bracketed groups first.
        for (final AbstractToken token : tokens) {
            if (token instanceof final FunctionGroup functionGroup) {
                final Function function = getFunction(
                        functionGroup,
                        functionGroup.getChildren(),
                        functionGroup.getName(),
                        expressionContext,
                        fieldIndex);
                output.add(function);

            } else if (token instanceof final TokenGroup tokenGroup) {
                final Function function = getGroup(tokenGroup, expressionContext, fieldIndex);
                output.add(function);

            } else if (token instanceof final KeywordGroup keywordGroup) {

                // Add a special case for the NOT keyword that can exist as a function in the context of an expression.
                if (LOGICAL_OPERATORS.contains(keywordGroup.getTokenType())
                        && keywordGroup.getChildren().size() == 1
                        && keywordGroup.getChildren().get(0) instanceof final TokenGroup tokenGroup) {
                    final String functionName = keywordGroup.getTokenType().toString().toLowerCase(Locale.ROOT);
                    final Function function = getFunction(
                            keywordGroup,
                            tokenGroup.getChildren(),
                            functionName,
                            expressionContext,
                            fieldIndex);
                    output.add(function);
                } else {
                    throw new TokenException(token, "Unexpected keyword: " + token.getText());
                }
            } else {
                output.add(token);
            }
        }

        // If we got here then there are no functions left. If there are no functions left to process then we can try
        // and turn anything that remains into a single function.
        if (output.size() == 1 && output.get(0) instanceof final Function function) {
            return Collections.singletonList(function);
        }

        // We should not have any comma, whitespace or unidentified tokens here.
        for (final Object object : output) {
            if (object instanceof final Token token) {
                if (TokenType.COMMA.equals(token.getTokenType())
                        || TokenType.WHITESPACE.equals(token.getTokenType())
                        || TokenType.UNKNOWN.equals(token.getTokenType())) {
                    throw new TokenException(token, "Unexpected token found");
                }
            }
        }

        // Any content that remains must be a parameter or parameter expression.
        final Param param = getParam(new ArrayList<>(output), expressionContext, fieldIndex);
        return Collections.singletonList(param);

    }

    private Function getFunction(final AbstractToken token,
                                 final List<AbstractToken> children,
                                 final String functionName,
                                 final ExpressionContext expressionContext,
                                 final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "getFunction() - " + functionName);

        // Create the function.
        final Function function = FunctionFactory.create(expressionContext, functionName);
        if (function == null) {
            throw new TokenException(token, "Unknown function '" + functionName + "'");
        }

        // Set the parameters on the function.
        final Param[] params = getParams(children, expressionContext, fieldIndex);
        try {
            function.setParams(params);
        } catch (final ParseException e) {
            throw new TokenException(token, e.getMessage());
        }

        // Return the function.
        return function;
    }

    private Function getGroup(final TokenGroup functionGroup,
                              final ExpressionContext expressionContext,
                              final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "getGroup() - " + functionGroup.toString());

        // If this function just represents a bracketed section then get a Brackets function.
        final Function function = new Brackets();

        // Set the parameters on the function.
        final Param[] params = getParams(functionGroup.getChildren(), expressionContext, fieldIndex);
        try {
            function.setParams(params);
        } catch (final ParseException e) {
            throw new TokenException(functionGroup, e.getMessage());
        }

        // Return the function.
        return function;
    }

    private Param[] getParams(final List<AbstractToken> children,
                              final ExpressionContext expressionContext,
                              final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "getParams() - " + getTokenString(children));

        final List<Param> paramList = new ArrayList<>(children.size());

        // Turn comma separated tokens into parameters.
        final List<Object> childSet = new ArrayList<>();
        for (final AbstractToken token : children) {
            if (TokenType.COMMA.equals(token.getTokenType())) {
                // If we haven't found a parameter from the previous token or object then this comma
                // is unexpected.
                if (childSet.isEmpty()) {
                    throw new TokenException(token, "Unexpected comma");
                }

                final Param param = getParam(
                        childSet,
                        expressionContext,
                        fieldIndex);
                paramList.add(param);
                childSet.clear();
            } else {
                childSet.add(token);
            }
        }

        // Capture last param if there is one.
        if (!childSet.isEmpty()) {
            final Param param = getParam(childSet, expressionContext, fieldIndex);
            paramList.add(param);
        }

        // Turn param list into an array.
        return paramList.toArray(new Param[0]);
    }

    private Param getParam(final List<Object> objects,
                           final ExpressionContext expressionContext,
                           final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "getParam() - " + getObjectString(objects));

        // If no objects are included to create this param then return null.
        if (objects.isEmpty()) {
            return null;
        }

        // If there is only a single object then turn it into a parameter if necessary and return.
        if (objects.size() == 1) {
            return convertParam(objects.get(0), expressionContext, fieldIndex);
        }

        return applyEquality(objects, expressionContext, fieldIndex);
    }

    private Param convertParam(final Object object,
                               final ExpressionContext expressionContext,
                               final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "convertParam() - " + object);

        Object result = object;
        if (object instanceof final Token token) {
            result = paramFactory.create(fieldIndex, token);

        } else if (object instanceof final FunctionGroup functionGroup) {
            result = getFunction(
                    functionGroup,
                    functionGroup.getChildren(),
                    functionGroup.getName(),
                    expressionContext,
                    fieldIndex);
        } else if (object instanceof final TokenGroup tokenGroup) {
            result = getGroup(tokenGroup, expressionContext, fieldIndex);
        }

        if (result instanceof Param) {
            return (Param) result;
        }

        if (result instanceof final AbstractToken token) {
            throw new TokenException(token, "Unexpected token");
        }

        throw new RuntimeException("Unexpected object: " + result);
    }

    private Function negate(final Param param) throws ParseException {
        LOGGER.trace(() -> "negate() - " + param);

        final Negate negate = new Negate("-");
        negate.setParams(new Param[]{param});
        return negate;
    }

    private Param applyEquality(final List<Object> objects,
                                final ExpressionContext expressionContext,
                                final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "applyEquality() - " + getObjectString(objects));

        int index = -1;
        for (final TokenType type : EQUALITY) {
            for (int i = 0; i < objects.size() && index == -1; i++) {
                final Object object = objects.get(i);
                if (object instanceof final Token token) {
                    if (type.equals(token.getTokenType())) {
                        index = i;
                    }
                }
            }
        }

        // If we don't find an operator then we are done.
        if (index == -1) {
            return applyBODMAS(objects, expressionContext, fieldIndex);

        } else {
            // If we found an operator at the end then this is unexpected.
            final Token token = (Token) objects.get(index);
            if (index == objects.size() - 1) {
                throw new TokenException(token, "Unexpected trailing equality");
            }
            if (index == 0) {
                throw new TokenException(token, "No parameter before equality");
            }

            // Get left param.
            final Param leftParam = applyEquality(
                    copyList(objects, 0, index - 1), expressionContext, fieldIndex);
            final Param rightParam = applyEquality(
                    copyList(objects, index + 1, objects.size() - 1), expressionContext, fieldIndex);

            final Function function = FunctionFactory.create(expressionContext, token.getText());
            if (function == null) {
                throw new TokenException(token, "Unknown function '" + token + "'");
            }
            try {
                function.setParams(new Param[]{leftParam, rightParam});
            } catch (final ParseException e) {
                throw new TokenException(token, e.getMessage());
            }
            return function;
        }
    }

    private List<Object> applySigns(final List<Object> objects,
                                    final ExpressionContext expressionContext,
                                    final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "applySigns() - " + getObjectString(objects));

        boolean loop = true;
        List<Object> result = new ArrayList<>(objects);

        // Ensure all objects are negated where necessary.
        while (loop) {

            boolean doneReplace = false;
            for (int i = 0; i < result.size() && !doneReplace; i++) {
                final Object object = result.get(i);

                // See if object is a non token or non op token.
                if (!(object instanceof Token) || !BODMAS_SET.contains(((Token) object).getTokenType())) {
                    // See if the token before was a +-;
                    if (i > 0) {
                        final Object previousToken = result.get(i - 1);
                        if (previousToken instanceof final Token signToken) {
                            if (TokenType.PLUS.equals(signToken.getTokenType()) ||
                                    TokenType.MINUS.equals(signToken.getTokenType())) {

                                // If there were no tokens before that or the token before was an operator then
                                // apply sign.
                                boolean sign = false;
                                if (i == 1) {
                                    sign = true;
                                } else {
                                    final Object firstToken = result.get(i - 2);
                                    if (firstToken instanceof final Token token3) {
                                        sign = BODMAS_SET.contains(token3.getTokenType());
                                    }
                                }

                                if (sign) {
                                    Param param = convertParam(object, expressionContext, fieldIndex);
                                    if (TokenType.MINUS.equals(signToken.getTokenType())) {
                                        // If there is no left param and we have a minus sign then negate the param.
                                        if (param instanceof final Val val) {
                                            switch (val.type()) {
                                                case INTEGER ->
                                                        param = ValInteger.create(-((ValInteger) param).toInteger());
                                                case LONG -> param = ValLong.create(-((ValLong) param).toLong());
                                                case FLOAT -> param = ValFloat.create(-((ValFloat) param).toFloat());
                                                case DOUBLE ->
                                                        param = ValDouble.create(-((ValDouble) param).toDouble());
                                                case DURATION -> {
                                                    final ValDuration valDuration = (ValDuration) param;
                                                    param = ValDuration.create(-valDuration.toLong());
                                                }
                                                default -> throw new TokenException(signToken,
                                                        "Illegal negation of " + val.type().getName());
                                            }
                                        } else {
                                            try {
                                                param = negate(param);
                                            } catch (final ParseException e) {
                                                throw new TokenException(signToken, e.getMessage());
                                            }
                                        }
                                        result = replace(result, param, i - 1, i);
                                        doneReplace = true;
                                    } else if (TokenType.PLUS.equals(signToken.getTokenType())) {
                                        result = replace(result, param, i - 1, i);
                                        doneReplace = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Keep going if we did a replace.
            loop = doneReplace;
        }
        return result;
    }

    public Param applyBODMAS(final List<Object> objects,
                             final ExpressionContext expressionContext,
                             final FieldIndex fieldIndex) {
        LOGGER.trace(() -> "applyBODMAS() - " + getObjectString(objects));

        boolean complete = false;
        List<Object> result = new ArrayList<>(objects);

        // Apply signs to objects.
        result = applySigns(result, expressionContext, fieldIndex);

        while (!complete) {

            // If there is more than one object then apply BODMAS rules.
            int index = -1;
            Token operator = null;

            for (final TokenType type : BODMAS) {
                for (int i = 0; i < result.size() && index == -1; i++) {
                    final Object object = result.get(i);
                    if (object instanceof final Token token) {
                        if (type.equals(token.getTokenType())) {
                            index = i;
                            operator = token;
                        }
                    }
                }
            }

            if (index == -1) {
                complete = true;

            } else if (index == 0) {
                throw new TokenException(operator, "Unexpected leading operator");

            } else if (index == result.size() - 1) {
                throw new TokenException(operator, "Unexpected trailing operator");

            } else {
                final Param leftParam = convertParam(result.get(index - 1), expressionContext, fieldIndex);
                final Param rightParam = convertParam(result.get(index + 1), expressionContext, fieldIndex);
                final Function function = FunctionFactory.create(expressionContext, operator.getText());
                if (function == null) {
                    throw new TokenException(operator, "Unknown function '" + operator + "'");
                }
                try {
                    function.setParams(new Param[]{leftParam, rightParam});
                } catch (final ParseException e) {
                    throw new TokenException(operator, e.getMessage());
                }

                result = replace(result, function, index - 1, index + 1);
            }
        }

        if (result.isEmpty()) {
            return null;
        } else if (result.size() == 1) {
            return convertParam(result.get(0), expressionContext, fieldIndex);
        } else if (result.get(1) instanceof
                final Token token) {
            throw new TokenException(token, "Unexpected token without joining operator");
        } else {
            throw new RuntimeException("Unexpected token without joining operator");
        }
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

    private List<Object> replace(final List<Object> list,
                                 final Object object,
                                 final int replaceStart,
                                 final int replaceEnd) {
        if (replaceStart <= 0 && replaceEnd >= list.size() - 1) {
            return Collections.singletonList(object);
        }

        // Make sure the insert start position will allow the new item to be added.
        assert (replaceStart <= list.size() - 1);

        // Add all objects that exist before the insert start position.
        final List<Object> newList = new ArrayList<>(list.size());
        for (int i = 0; i < replaceStart; i++) {
            newList.add(list.get(i));
        }

        // Add the object.
        newList.add(object);

        // Add all objects that exist after the insert end position.
        for (int i = replaceEnd + 1; i < list.size(); i++) {
            newList.add(list.get(i));
        }

        return newList;
    }

    private String getObjectString(final List<Object> objects) {
        return objects
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    private String getTokenString(final List<AbstractToken> tokens) {
        return tokens
                .stream()
                .map(AbstractToken::toString)
                .collect(Collectors.joining(","));
    }
}
