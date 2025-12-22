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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTypeConverter {
    @Test
    void testDoubleToString() {
        test("123456789", 123456789D);
        test("123456789000000000000", 123456789000000000000D);
        test("123456789", 123456789.0D);
        test("123456789.1", 123456789.1D);
        test("123456789.000231", 123456789.000231D);
    }

    private void test(final String expected, final double dbl) {
        final String actual = ValDouble.create(dbl).toString();
        assertThat(actual).isEqualTo(expected);
    }
}
