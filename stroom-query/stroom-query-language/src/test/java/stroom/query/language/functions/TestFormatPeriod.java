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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.text.ParseException;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;

class TestFormatPeriod {

    @ParameterizedTest
    @CsvSource({
            "P1Y2M3D,P1Y2M3D",
            "P14D,P14D",
            "P0D,P0D"})
    void periods(final String period, final String expected) throws ParseException {
        assertThat(eval(ValPeriod.create(Period.parse(period)))).isEqualTo(ValString.create(expected));
    }

    @Test
    void otherTypes() throws ParseException {
        assertThat(eval(ValString.create("1y2w"))).isEqualTo(ValString.create("P1Y14D"));
        assertThat(eval(ValString.create(""))).isEqualTo(ValNull.INSTANCE);
        assertThat(eval(ValNull.INSTANCE)).isEqualTo(ValNull.INSTANCE);
        assertThat(eval(ValString.create("PT1H")).type()).isEqualTo(Type.ERR);
        assertThat(eval(ValDuration.create(1)).type()).isEqualTo(Type.ERR);
    }

    @Test
    void literal() throws ParseException {
        assertThat(DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, "formatPeriod(18M)", ValNull.INSTANCE))
                .isEqualTo(ValString.create("P18M"));
    }

    private static Val eval(final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, "formatPeriod(${field})", field);
    }
}
