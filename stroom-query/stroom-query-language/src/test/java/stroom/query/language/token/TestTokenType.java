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

package stroom.query.language.token;

import stroom.query.api.token.TokenType;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

class TestTokenType {

    @Test
    @Disabled
        // Here just to see debug in the static initialiser
    void test() {
        final TokenType tokenType = TokenType.FROM;
    }

    @TestFactory
    Stream<DynamicTest> testHaveSeen_contiguous() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<List<TokenType>, TokenType[]>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        TokenType.haveSeen(
                                testCase.getInput()._1,
                                true,
                                testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[0]),
                        false)
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[]{TokenType.WHITESPACE}),
                        true)
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[]{TokenType.EQUALS, TokenType.WHITESPACE}),
                        true)
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[]{TokenType.EVAL, TokenType.WHITESPACE}),
                        true)
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[]{TokenType.EVAL, TokenType.EQUALS}),
                        false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHaveSeen_nonContiguous() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<List<TokenType>, TokenType[]>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        TokenType.haveSeen(
                                testCase.getInput()._1,
                                false,
                                testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[0]),
                        false)
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[]{TokenType.WHITESPACE}),
                        true)
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[]{TokenType.EQUALS, TokenType.WHITESPACE}),
                        true)
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[]{TokenType.EVAL, TokenType.WHITESPACE}),
                        true)
                .addCase(Tuple.of(
                                List.of(TokenType.EVAL, TokenType.WHITESPACE, TokenType.EQUALS, TokenType.WHITESPACE),
                                new TokenType[]{TokenType.EVAL, TokenType.EQUALS}),
                        true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHaveSeenLast() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<List<TokenType>, TokenType[]>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        TokenType.haveSeenLast(testCase.getInput()._1, testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(List.of(), new TokenType[]{TokenType.EQUALS}), false)
                .addCase(Tuple.of(List.of(TokenType.EQUALS), new TokenType[0]), true)
                .addCase(Tuple.of(
                                List.of(TokenType.ORDER, TokenType.BY), new TokenType[]{TokenType.ORDER}),
                        false)
                .addCase(Tuple.of(
                                List.of(TokenType.ORDER, TokenType.BY), new TokenType[]{TokenType.ORDER, TokenType.BY}),
                        true)
                .build();
    }
}
