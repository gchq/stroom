/*
 * Copyright 2026 Crown Copyright
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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.Map;

/// Evaluates expressions in queries with and without a time zone.
final class DateFunctionTestUtil {

    static final ExpressionContext LONDON = ExpressionContext.builder()
            .dateTimeSettings(DateTimeSettings.builder()
                    .timeZone(UserTimeZone.builder().use(Use.ID).id("Europe/London").build())
                    .build())
            .build();
    static final ExpressionContext UNKNOWN_ZONE = ExpressionContext.builder()
            .dateTimeSettings(DateTimeSettings.builder()
                    .timeZone(UserTimeZone.builder().use(Use.ID).id("Mars/Base").build())
                    .build())
            .build();
    // With no date time settings at all.
    static final ExpressionContext NO_SETTINGS = ExpressionContext.builder().build();

    private DateFunctionTestUtil() {
        // Static methods only.
    }

    // Evaluates an expression on a row with one field, named 'field'.
    static Val eval(final ExpressionContext expressionContext,
                    final String input,
                    final Val field) throws ParseException {
        return eval(expressionContext, input, Map.of(), field);
    }

    // Evaluates an expression with query parameters on a row with one field, named 'field'.
    static Val eval(final ExpressionContext expressionContext,
                    final String input,
                    final Map<String, String> params,
                    final Val field) throws ParseException {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("field");
        final Expression expression = new ExpressionParser(new ParamFactory(Map.of()))
                .parse(expressionContext, fieldIndex, input);
        expression.setStaticMappedValues(params);
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        expression.addValueReferences(valueReferenceIndex);
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        final Generator generator = expression.createGenerator();
        generator.set(new Val[]{field}, storedValues);
        return generator.eval(storedValues, null);
    }
}
