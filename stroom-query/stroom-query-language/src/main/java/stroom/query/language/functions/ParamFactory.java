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

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;

import java.util.Map;

public class ParamFactory {

    private final Map<String, Expression> expressionReference;

    public ParamFactory(final Map<String, Expression> expressionReference) {
        this.expressionReference = expressionReference;
    }

    public Param create(final FieldIndex fieldIndex,
                        final Token token) {
        try {
            // Token should be string or number or field.
            switch (token.getTokenType()) {
                case DOUBLE_QUOTED_STRING, SINGLE_QUOTED_STRING:
                    return ValString.create(token.getUnescapedText());

                case STRING, PARAM:
                    return createRef(token.getUnescapedText(), fieldIndex);

                case DATE_TIME:
                    return ValDate.create(DateUtil.parseNormalDateTimeString(token.getText()));

                case DURATION:
                    return ValDuration.create(ValDurationUtil.parseToMilliseconds(token.getText()));

                case NUMBER:
                    return ValDouble.create(Double.parseDouble(token.getText()));

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
