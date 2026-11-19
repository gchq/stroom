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

package stroom.visualisation.shared;

import stroom.query.api.Format;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisInput {

    @Test
    void testInputTypes() {
        // Names are read regardless of case, and ones that aren't types are left out
        assertThat(input("number", "DATE_TIME", "DATE", "NUMBER").getInputTypes())
                .containsExactly(VisInputType.NUMBER, VisInputType.DATE_TIME);
        assertThat(new VisInput("x", null, null, null, null, null).getInputTypes()).isEmpty();
    }

    @Test
    void testAccepts() {
        final VisInput numbers = input("NUMBER");
        assertThat(numbers.accepts(Format.Type.NUMBER)).isTrue();
        assertThat(numbers.accepts(Format.Type.TEXT)).isFalse();
        assertThat(numbers.accepts(Format.Type.DATE_TIME)).isFalse();
        // A general column's type isn't known until its values are seen
        assertThat(numbers.accepts(Format.Type.GENERAL)).isTrue();
        assertThat(numbers.accepts(null)).isTrue();

        // Anything can be read as text
        final VisInput text = input("TEXT");
        assertThat(text.accepts(Format.Type.NUMBER)).isTrue();
        assertThat(text.accepts(Format.Type.DATE_TIME)).isTrue();

        final VisInput timeOrNumber = input("DATE_TIME", "NUMBER");
        assertThat(timeOrNumber.accepts(Format.Type.DATE_TIME)).isTrue();
        assertThat(timeOrNumber.accepts(Format.Type.TEXT)).isFalse();

        // An input that names no types takes any column, as before inputs existed
        assertThat(new VisInput("x", null, null, null, null, null).accepts(Format.Type.TEXT)).isTrue();
    }

    @Test
    void testConversion() {
        // A column of a type the input takes is left as it is
        assertThat(input("NUMBER").conversionFor(Format.Type.NUMBER)).isNull();
        assertThat(input("TEXT", "NUMBER").conversionFor(Format.Type.TEXT)).isNull();
        // An input of one type converts any other column to it
        assertThat(input("NUMBER").conversionFor(Format.Type.TEXT)).isEqualTo(Format.Type.NUMBER);
        assertThat(input("NUMBER").conversionFor(Format.Type.GENERAL)).isEqualTo(Format.Type.NUMBER);
        assertThat(input("NUMBER").conversionFor(null)).isEqualTo(Format.Type.NUMBER);
        assertThat(input("TEXT").conversionFor(Format.Type.DATE_TIME)).isEqualTo(Format.Type.TEXT);
        // An input of several types leaves a general column to be judged by its values
        assertThat(input("TEXT", "NUMBER", "DATE_TIME").conversionFor(Format.Type.GENERAL)).isNull();
        assertThat(new VisInput("x", null, null, null, null, null).conversionFor(Format.Type.TEXT)).isNull();
    }

    @Test
    void testFlags() {
        assertThat(new VisInput("x", null, null, null, true, true).isRequired()).isTrue();
        assertThat(new VisInput("x", null, null, null, true, true).isMultiple()).isTrue();
        assertThat(new VisInput("x", null, null, null, null, null).isRequired()).isFalse();
        assertThat(new VisInput("x", null, null, null, false, null).isMultiple()).isFalse();
    }

    @Test
    void testParseType() {
        assertThat(VisInputType.parse(" date_time ")).isEqualTo(VisInputType.DATE_TIME);
        assertThat(VisInputType.parse("GENERAL")).isNull();
        assertThat(VisInputType.parse(null)).isNull();
    }

    private static VisInput input(final String... types) {
        final List<String> list = Arrays.asList(types);
        return new VisInput("x", "X axis", null, list, true, false);
    }
}
