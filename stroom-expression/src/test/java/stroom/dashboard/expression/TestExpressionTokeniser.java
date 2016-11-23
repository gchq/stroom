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

import org.junit.Assert;
import org.junit.Test;
import stroom.util.test.StroomUnitTest;

import java.text.ParseException;
import java.util.List;

public class TestExpressionTokeniser extends StroomUnitTest {
    @Test
    public void testBasic() throws ParseException {
        // Test string tokenisation.
        test("");

        test("A", true);
        test("'B'");

        test("A'B'A'B'A'B'", true);
        test("'B'A'B'A'B'A", true);
        test("A'B'A'B'A'B'A", true);
        test("'B'A'B'A'B'A'B'", true);

        test("FOO'BAR'FOO'BAR'FOO'BAR'", true);
        test("'BAR'FOO'BAR'FOO'BAR'FOO", true);
        test("FOO'BAR'FOO'BAR'FOO'BAR'FOO", true);
        test("'BAR'FOO'BAR'FOO'BAR'FOO'BAR'", true);

        test("ABC", true);
        test("ABC'simple string'DEF", true);
        test("'simple string'DEF'simple string'", true);
        test("'simple string'");
        test("'simple string with ''quoted section'''");

        test("''");
        test("'''", true);
        test("''''");

        // Test field tokenisation.
        test("${val}");
        test("min(${val})");
        test("max(${val})");
        test("sum(${val})");
        test("min(round(${val}, 4))");
        test("min(roundDay(${val}))");
        test("min(roundMinute(${val}))");
        test("ceiling(${val})");
        test("floor(${val})");
        test("ceiling(floor(min(roundMinute(${val}))))");
        test("ceiling(floor(min(round(${val}))))");
        test("max(${val})-min(${val})");
        test("max(${val})/count()");
        test("round(${val})/(min(${val})+max(${val}))");
        test("concat('this is', 'it')");
        test("concat('it''s a string', 'with a quote')");
        test("'it''s a string'");
        test("stringLength('it''s a string')");
        test("upperCase('it''s a string')");
        test("lowerCase('it''s a string')");
        test("substring('Hello', 0, 1)");
        test("equals(${val}, ${val})");
        test("greaterThan(1, 0)");
        test("lessThan(1, 0)");
        test("greaterThanOrEqualTo(1, 0)");
        test("lessThanOrEqualTo(1, 0)");
        test("1=0");
        test("decode('fred', 'fr.+', 'freda', 'freddy')");

        // Test fields with non letters.
        test("sum(${user-id})");
        test("sum(${user id})");
    }

    private void test(final String expression) throws ParseException {
        test(expression, false);
    }

    private void test(final String expression, final boolean expectValidationFailure) throws ParseException {
        final ExpressionTokeniser expressionTokeniser = new ExpressionTokeniser();
        final List<ExpressionTokeniser.Token> tokens = expressionTokeniser.tokenise(expression);

        final StringBuilder sb = new StringBuilder();
        for (final ExpressionTokeniser.Token token : tokens) {
            sb.append(token.toString());
        }

        // Make sure all the tokens have captured the expression fully.
        Assert.assertEquals(expression, sb.toString());

        try {
            // Do some basic validation of the tokens.
            final ExpressionValidator expressionValidator = new ExpressionValidator();
            expressionValidator.validate(tokens);

            if (expectValidationFailure) {
                Assert.fail("Expected failure");
            }
        } catch (final ParseException e) {
            if (!expectValidationFailure) {
                throw e;
            }
        }
    }
}
