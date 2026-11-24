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

import stroom.query.api.token.TokenException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.text.ParseException;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestParsePeriod {

    @ParameterizedTest
    @CsvSource({
            "P1Y2M3D,P1Y2M3D",
            "p1y,P1Y",
            "P2W,P14D",
            "-P1M,P-1M",
            "P12M,P12M",
            "1y2M3d,P1Y2M3D",
            "2w,P14D",
            "1d1d,P2D",
            "' 3M ',P3M",
            "1 d,P1D",
            "1y 2M 3 d,P1Y2M3D"})
    void texts(final String text, final String expected) throws ParseException {
        final Val period = ValPeriod.create(Period.parse(expected));

        assertThat(eval(ValString.create(text))).isEqualTo(period);
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "parsePeriod('" + text + "')", ValNull.INSTANCE)).isEqualTo(period);
    }

    @ParameterizedTest
    @CsvSource({
            "PT1H,Use parseDuration",
            "P1DT1H,Use parseDuration",
            "1d2h,Use parseDuration",
            "1m,Use parseDuration",
            "P1X,is not a period",
            "P,is not a period",
            "1Y,Unknown unit 'Y'",
            "soon,is not a number and a unit",
            "99999999999d,too long"})
    void badTexts(final String text, final String message) throws ParseException {
        final Val val = eval(ValString.create(text));

        assertThat(val.type()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
        assertThatThrownBy(() -> DateFunctionTestUtil.eval(
                DateFunctionTestUtil.LONDON, "parsePeriod('" + text + "')", ValNull.INSTANCE))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    @Test
    void otherTypes() throws ParseException {
        final Val period = ValPeriod.create(1, 2, 3);
        assertThat(eval(period)).isEqualTo(period);
        assertThat(eval(ValNull.INSTANCE)).isEqualTo(ValNull.INSTANCE);
        assertThat(eval(ValString.create(" "))).isEqualTo(ValNull.INSTANCE);
        assertThat(eval(ValDuration.create(1)).toString()).contains("A duration can't be read as a period");
        assertThat(eval(ValLong.create(1)).toString()).contains("A long can't be read as a period");
        assertThat(eval(ValErr.create("bad"))).isEqualTo(ValErr.create("bad"));
    }

    private static Val eval(final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, "parsePeriod(${field})", field);
    }
}
