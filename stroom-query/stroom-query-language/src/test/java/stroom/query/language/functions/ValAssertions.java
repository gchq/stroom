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

import org.assertj.core.api.Assertions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Various methods for asserting a {@link Val} value.
 */
public class ValAssertions {

    public static final ValAssertion VAL_NULL_ASSERTION = (Val actual) -> {
        Assertions.assertThat(actual)
                .isInstanceOf(ValNull.class);
        Assertions.assertThat((ValNull) actual)
                .isSameAs(ValNull.INSTANCE);
    };

    public static final ValAssertion VAL_ERR_ASSERTION = (Val actual) ->
            Assertions.assertThat(actual)
                    .isInstanceOf(ValErr.class);

    public static final ValAssertion VAL_TRUE_ASSERTION = (Val actual) ->
            Assertions.assertThat(actual)
                    .isInstanceOf(ValBoolean.class)
                    .isSameAs(ValBoolean.TRUE)
                    .extracting(Val::toBoolean)
                    .isEqualTo(true);

    public static final ValAssertion VAL_FALSE_ASSERTION = (Val actual) ->
            Assertions.assertThat(actual)
                    .isInstanceOf(ValBoolean.class)
                    .isSameAs(ValBoolean.FALSE)
                    .extracting(Val::toBoolean)
                    .isEqualTo(false);

    public static final ValAssertion VAL_EMPTY_STRING_ASSERTION = (Val actual) ->
            assertThat(actual)
                    .isInstanceOf(ValString.class)
                    .isSameAs(ValString.EMPTY)
                    .extracting(Val::toString)
                    .isEqualTo("");

    private ValAssertions() {
    }

    public static ValAssertion valTrue() {
        return VAL_TRUE_ASSERTION;
    }

    public static ValAssertion valFalse() {
        return VAL_FALSE_ASSERTION;
    }

    public static ValAssertion valNull() {
        return VAL_NULL_ASSERTION;
    }

    public static ValAssertion valErr() {
        return VAL_ERR_ASSERTION;
    }

    /**
     * @param expectedSubStrings sub-strings that are expected to appear in the error message in any order.
     */
    public static ValAssertion valErrContainsIgnoreCase(final String... expectedSubStrings) {
        return (final Val actual) -> {
            Assertions.assertThat(actual)
                    .isInstanceOf(ValErr.class);

            for (final String expectedSubString : expectedSubStrings) {
                Assertions.assertThat(actual.toString())
                        .containsIgnoringCase(expectedSubString);
            }
        };
    }

    public static ValAssertion valString(final String expected) {
        return (Val actual) ->
                Assertions.assertThat(actual)
                        .isEqualTo(ValString.create(expected));
    }

    public static ValAssertion valStringEmpty() {
        return VAL_EMPTY_STRING_ASSERTION;
    }

    public static ValAssertion valDouble(final double expected) {
        return (Val out) ->
                Assertions.assertThat(out)
                        .isEqualTo(ValDouble.create(expected))
                        .extracting(Val::toDouble)
                        .isEqualTo(expected);
    }

    public static ValAssertion valFloat(final float expected) {
        return (Val out) ->
                Assertions.assertThat(out)
                        .isEqualTo(ValFloat.create(expected))
                        .extracting(Val::toFloat)
                        .isEqualTo(expected);
    }

    public static ValAssertion valInteger(final int expected) {
        return (Val out) ->
                Assertions.assertThat(out)
                        .isEqualTo(ValInteger.create(expected))
                        .extracting(Val::toInteger)
                        .isEqualTo(expected);
    }

    public static ValAssertion valLong(final long expected) {
        return (Val out) ->
                Assertions.assertThat(out)
                        .isEqualTo(ValLong.create(expected))
                        .extracting(Val::toLong)
                        .isEqualTo(expected);
    }

    public static ValAssertion valDate(final long expectedEpochMs) {
        return (Val out) ->
                Assertions.assertThat(out)
                        .isInstanceOf(ValDate.class)
                        .extracting(Val::toLong)
                        .isEqualTo(expectedEpochMs);
    }

    public static ValAssertion valDate(final String expectedNormalDateTimeString) {
        return valDate(DateUtil.parseNormalDateTimeString(expectedNormalDateTimeString));
    }

    public static ValAssertion valDate(final Instant expectedInstant) {
        return valDate(expectedInstant.toEpochMilli());
    }

    /**
     * No type checking, just that {@link Val#toString()} is equal to expected.
     */
    public static ValAssertion valAsString(final String expected) {
        return (Val out) ->
                Assertions.assertThat(out.toString())
                        .isEqualTo(expected);
    }
}
