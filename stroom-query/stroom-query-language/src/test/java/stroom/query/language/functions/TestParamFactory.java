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

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenType;
import stroom.query.language.token.Tokeniser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestParamFactory {

    private final ParamFactory paramFactory = new ParamFactory(Map.of());

    @ParameterizedTest
    @CsvSource({
            "5ms,5",
            "1s,1000",
            "1m,60000",
            "1h,3600000",
            "1d,86400000",
            "1w,604800000",
            "1000000ns,1"})
    void testDuration(final String literal, final long expected) {
        assertThat(create(literal)).isEqualTo(ValDuration.create(expected));
    }

    @ParameterizedTest
    @CsvSource({
            "1M,months and years vary in length",
            "1y,months and years vary in length",
            "1ns,held to the millisecond",
            "1H,Unknown unit 'H'",
            "1D,Unknown unit 'D'"})
    void testBadDuration(final String literal, final String message) {
        assertThatThrownBy(() -> create(literal))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    private Param create(final String literal) {
        final List<Token> tokens = Tokeniser.parse(literal);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getTokenType()).isEqualTo(TokenType.DURATION);
        return paramFactory.create(new FieldIndex(), tokens.getFirst());
    }
}
