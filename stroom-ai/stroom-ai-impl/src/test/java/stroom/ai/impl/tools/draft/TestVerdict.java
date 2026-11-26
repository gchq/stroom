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

package stroom.ai.impl.tools.draft;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// The three-state verdict every count of records by conditions reads them by.
class TestVerdict {

    @Test
    void anEvaluatorsVerdictIsReadAsOneOfThree() {
        assertThat(Verdict.of(true)).isEqualTo(Verdict.HOLDS);
        assertThat(Verdict.of(false)).isEqualTo(Verdict.DOESNT);
        assertThat(Verdict.of(null)).isEqualTo(Verdict.UNKNOWN);
        assertThat(Verdict.HOLDS.toBoolean()).isTrue();
        assertThat(Verdict.DOESNT.toBoolean()).isFalse();
        assertThat(Verdict.UNKNOWN.toBoolean()).isNull();
    }

    @Test
    void allHoldingFailsAtOneThatDoesntElseIsUnknownWhereOneCantBeTold() {
        assertThat(Verdict.HOLDS.and(Verdict.HOLDS)).isEqualTo(Verdict.HOLDS);
        assertThat(Verdict.HOLDS.and(Verdict.UNKNOWN)).isEqualTo(Verdict.UNKNOWN);
        assertThat(Verdict.UNKNOWN.and(Verdict.DOESNT)).isEqualTo(Verdict.DOESNT);
        assertThat(Verdict.all(List.<Boolean>of(), Verdict::of)).isEqualTo(Verdict.HOLDS);
        assertThat(Verdict.all(Arrays.asList(true, null, true), Verdict::of)).isEqualTo(Verdict.UNKNOWN);

        // The first that doesn't hold decides it: none after it is read.
        final AtomicInteger read = new AtomicInteger();
        final List<Boolean> conditions = List.of(true, false, true);
        assertThat(Verdict.all(conditions, holds -> {
            read.incrementAndGet();
            return Verdict.of(holds);
        })).isEqualTo(Verdict.DOESNT);
        assertThat(read.get()).isEqualTo(2);
    }

    @Test
    void eitherHoldingHoldsAtOneThatDoesElseIsUnknownWhereOneCantBeTold() {
        assertThat(Verdict.DOESNT.or(Verdict.DOESNT)).isEqualTo(Verdict.DOESNT);
        assertThat(Verdict.DOESNT.or(Verdict.UNKNOWN)).isEqualTo(Verdict.UNKNOWN);
        assertThat(Verdict.UNKNOWN.or(Verdict.HOLDS)).isEqualTo(Verdict.HOLDS);
    }
}
