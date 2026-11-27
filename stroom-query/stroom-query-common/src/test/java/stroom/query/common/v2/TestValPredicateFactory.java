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

import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class TestValPredicateFactory {

    @Test
    void durations() {
        final Predicate<Val> predicate = new ValPredicateFactory(new ExpressionPredicateFactory()).createValPredicate(
                Column.builder().id("c").name("c").format(Format.DURATION).build(), ">1h", null);
        assertThat(predicate.test(ValDuration.create(3_600_001L))).isTrue();
        assertThat(predicate.test(ValString.create("1.5h 30m"))).isTrue();
        assertThat(predicate.test(ValDuration.create(3_600_000L))).isFalse();
        assertThat(predicate.test(ValErr.create("bad"))).isFalse();
    }
}
