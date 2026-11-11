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

package stroom.search.elastic.suggest;

import stroom.query.api.datasource.FieldType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticTermValueQuoter {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            // Plain values are left as they are
            "alice | alice",
            "10.0.0.1 | 10.0.0.1",
            // Spaces and wildcard characters must not be read as separate words or a pattern
            "apple pie | \"apple pie\"",
            "a*b | \"a*b\"",
            "a?b | \"a?b\"",
            // A value already in quotes would otherwise lose them
            "\"x\" | \"\"x\"\"",
            // Quotes within a value are kept, as only the outer pair is removed
            "say \"hi\" now | \"say \"hi\" now\"",
            // A comma is left for the term editor to deal with
            "Smith,John | Smith,John"
    })
    void testQuote_keyword(final String value, final String expected) {
        assertThat(ElasticTermValueQuoter.quote(FieldType.KEYWORD, value))
                .isEqualTo(expected);
        assertThat(ElasticTermValueQuoter.quote(FieldType.IPV4_ADDRESS, value))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            // Text values are always phrases, so query string syntax in them is matched literally
            "alice | \"alice\"",
            "ERROR:disk | \"ERROR:disk\"",
            "/var/log/app | \"/var/log/app\"",
            "fn(x) AND y | \"fn(x) AND y\"",
            // Quotes and backslashes are escaped
            "say \"hi\" now | \"say \\\"hi\\\" now\"",
            "C:\\Temp | \"C:\\\\Temp\""
    })
    void testQuote_text(final String value, final String expected) {
        assertThat(ElasticTermValueQuoter.quote(FieldType.TEXT, value))
                .isEqualTo(expected);
    }
}
