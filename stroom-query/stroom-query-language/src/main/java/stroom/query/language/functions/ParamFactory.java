/*
 * Copyright 2023 Crown Copyright
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

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.language.token.TimeLiteral;
import stroom.util.shared.time.SimpleDuration;

import java.util.Map;

public class ParamFactory {

    private final Map<String, Expression> expressionReference;

    public ParamFactory(final Map<String, Expression> expressionReference) {
        this.expressionReference = expressionReference;
    }

    /// Creates the parameter that a token of an expression stands for.
    ///
    /// @param expressionContext The context of the expression, whose time zone a date with no offset is in.
    /// @param fieldIndex        The fields of the expression, which any field the token names is added to.
    /// @param token             The token.
    /// @return The parameter.
    public Param create(final ExpressionContext expressionContext,
                        final FieldIndex fieldIndex,
                        final Token token) {
        try {
            // Token should be string or number or field.
            switch (token.getTokenType()) {
                case DOUBLE_QUOTED_STRING, SINGLE_QUOTED_STRING:
                    return ValString.create(token.getUnescapedText());

                case STRING, PARAM:
                    return createRef(token.getUnescapedText(), fieldIndex);

                case DATE_TIME:
                    return ValDate.create(DateReader.parseDateTime(
                            token.getText(), new QueryZone(expressionContext)));

                case DURATION:
                    final SimpleDuration literal = TimeLiteral.parse(token.getText());
                    return TimeLiteral.isPeriod(literal)
                            ? ValPeriod.create(TimeLiteral.toPeriod(literal))
                            : ValDuration.create(TimeLiteral.toExactMillis(literal));

                case NUMBER:
                    final double number = Double.parseDouble(token.getText());
                    if (Double.isInfinite(number)) {
                        throw new TokenException(token, "Number is out of range");
                    }
                    return ValDouble.create(number);

                default:
                    throw new TokenException(token, "Unexpected token type '" + token.getTokenType() + "'");
            }
        } catch (final RuntimeException e) {
            throw new TokenException(token, e.getMessage());
        }
    }

    private Param createRef(final String name, final FieldIndex fieldIndex) {
        final Expression expression = expressionReference.get(name);
        if (expression != null) {
            return expression;
        }
        final int pos = fieldIndex.create(name);
        return new Ref(name, pos);
    }
}
