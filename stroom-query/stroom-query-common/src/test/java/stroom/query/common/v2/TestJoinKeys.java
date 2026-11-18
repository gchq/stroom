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

package stroom.query.common.v2;

import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJoinKeys {

    @Test
    void keyIsTheValueOfEachExpression() {
        final FieldIndex fieldIndex = new FieldIndex();
        final JoinKeys keys = compile(List.of("lowerCase(${UserId})", "${Host}"), fieldIndex);

        assertThat(fieldIndex.getFields()).containsExactly("UserId", "Host");
        assertThat(keys.getKey(JoinTestHelper.row("JBloggs", "h1")))
                .isEqualTo(keys.getKey(JoinTestHelper.row("jbloggs", "h1")))
                .isNotEqualTo(keys.getKey(JoinTestHelper.row("jbloggs", "h2")));
    }

    @Test
    void blankOrNullValueGivesNoKey() {
        final JoinKeys keys = compile(List.of("${UserId}", "${Host}"), new FieldIndex());

        assertThat(keys.getKey(JoinTestHelper.row("a", null))).isNull();
        assertThat(keys.getKey(JoinTestHelper.row("a", ""))).isNull();
        assertThat(keys.getKey(JoinTestHelper.row("a", "  "))).isNull();
        assertThat(keys.getKey(new Val[]{ValString.create("a")})).isNull();
        assertThat(keys.getKey(JoinTestHelper.row("a", "b"))).isNotNull();
    }

    @Test
    void differentValuesNeverMakeTheSameKey() {
        final JoinKeys keys = compile(List.of("${A}", "${B}"), new FieldIndex());

        assertThat(keys.getKey(JoinTestHelper.row("a:1", "b")))
                .isNotEqualTo(keys.getKey(JoinTestHelper.row("a", "1:b")));
    }

    @Test
    void numberMatchesItsTextForm() {
        final JoinKeys keys = compile(List.of("${Id}"), new FieldIndex());

        assertThat(keys.getKey(new Val[]{ValLong.create(42)}))
                .isEqualTo(keys.getKey(JoinTestHelper.row("42")));
    }

    @Test
    void equalNumbersOfDifferentTypesMatch() {
        final JoinKeys keys = compile(List.of("${Id}"), new FieldIndex());

        assertThat(keys.getKey(new Val[]{ValDouble.create(5.0)}))
                .isEqualTo(keys.getKey(new Val[]{ValLong.create(5)}))
                .isEqualTo(keys.getKey(new Val[]{ValFloat.create(5.0F)}))
                .isEqualTo(keys.getKey(new Val[]{ValInteger.create(5)}));
        assertThat(keys.getKey(new Val[]{ValDouble.create(5.5)}))
                .isNotEqualTo(keys.getKey(new Val[]{ValLong.create(5)}));
    }

    @Test
    void aggregateIsRefused() {
        assertThatThrownBy(() -> compile(List.of("count()"), new FieldIndex()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregate");
    }

    private static JoinKeys compile(final List<String> expressions, final FieldIndex fieldIndex) {
        return JoinKeys.compile(new ExpressionContext(), expressions, fieldIndex, Map.of());
    }
}
