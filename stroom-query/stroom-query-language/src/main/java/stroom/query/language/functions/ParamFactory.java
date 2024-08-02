/*
 * Copyright 2024 Crown Copyright
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

import stroom.query.language.token.Param;
import stroom.query.language.token.Token;
import stroom.query.language.token.TokenException;
import stroom.util.shared.string.CIKey;

import java.util.Map;

public class ParamFactory {

    private final Map<CIKey, Expression> expressionReference;

    public ParamFactory(final Map<CIKey, Expression> expressionReference) {
        this.expressionReference = expressionReference;
    }

    public Param create(final FieldIndex fieldIndex,
                        final Token token) {
        try {
            // Token should be string or number or field.
            return switch (token.getTokenType()) {
                case DOUBLE_QUOTED_STRING, SINGLE_QUOTED_STRING -> ValString.create(token.getUnescapedText());
                case STRING, PARAM -> createRef(token.getUnescapedText(), fieldIndex);
                case DATE_TIME -> ValDate.create(DateUtil.parseNormalDateTimeString(token.getText()));
                case DURATION -> ValDuration.create(ValDurationUtil.parseToMilliseconds(token.getText()));
                case NUMBER -> ValDouble.create(Double.parseDouble(token.getText()));
                default -> throw new TokenException(token, "Unexpected token type '" + token.getTokenType() + "'");
            };
        } catch (final RuntimeException e) {
            throw new TokenException(token, e.getMessage());
        }
    }

    private Param createRef(final String name, final FieldIndex fieldIndex) {
        final CIKey caseInsensitiveFieldName = CIKey.of(name);
        final Expression expression = expressionReference.get(caseInsensitiveFieldName);
        if (expression != null) {
            return expression;
        }
        final int pos = fieldIndex.create(caseInsensitiveFieldName);
        return new Ref(name, pos);
    }
}
