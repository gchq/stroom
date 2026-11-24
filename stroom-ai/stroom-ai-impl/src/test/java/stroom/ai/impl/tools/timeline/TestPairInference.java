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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.timeline.PairInference.Candidate;
import stroom.ai.impl.tools.timeline.PairInference.Inferred;
import stroom.ai.impl.tools.timeline.PairInference.Typed;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/// Pairs inferred from a user's own events by alternation: no words are known (Open, Close...), so a vendor's pair
/// in any naming is found and unrelated types sharing a key aren't.
class TestPairInference {

    private static final long MINUTE = 60_000L;

    private final List<Typed> events = new ArrayList<>();
    private int ids;

    @Test
    void twoTypesAlternatingForEveryKeyAreAPairUsed() {
        // A vendor's own names, nothing like open and close: a tunnel's up and its down, per tunnel id.
        for (int tunnel = 1; tunnel <= 6; tunnel++) {
            final long t = tunnel * 100 * MINUTE;
            add("Tun-Ascend", t, "Tunnel/Id", "t" + tunnel);
            add("Tun-Recede", t + 20 * MINUTE, "Tunnel/Id", "t" + tunnel);
            add("Tun-Ascend", t + 40 * MINUTE, "Tunnel/Id", "t" + tunnel);
            add("Tun-Recede", t + 50 * MINUTE, "Tunnel/Id", "t" + tunnel);
        }

        final Inferred inferred = PairInference.infer(events, List.of());

        assertThat(inferred.clear()).singleElement().satisfies(pair -> {
            assertThat(pair.start()).isEqualTo("Tun-Ascend");
            assertThat(pair.end()).isEqualTo("Tun-Recede");
            assertThat(pair.by()).isEqualTo("Tunnel/Id");
            assertThat(pair.share()).isEqualTo(1.0);
            assertThat(pair.keys()).isEqualTo(6);
            assertThat(pair.medianGapMs()).isEqualTo(20 * MINUTE);
        });
        assertThat(inferred.borderline()).isEmpty();
    }

    @Test
    void aPairAlternatingForAboutHalfTheKeysIsListedNotUsed() {
        for (int key = 1; key <= 4; key++) {
            final long t = key * 100 * MINUTE;
            add("Sess-A", t, "Session", "k" + key);
            add("Sess-B", t + 5 * MINUTE, "Session", "k" + key);
        }
        for (int key = 5; key <= 8; key++) {
            final long t = key * 100 * MINUTE;
            add("Sess-A", t, "Session", "k" + key);
            add("Sess-A", t + 5 * MINUTE, "Session", "k" + key);
            add("Sess-B", t + 9 * MINUTE, "Session", "k" + key);
        }

        final Inferred inferred = PairInference.infer(events, List.of());

        assertThat(inferred.clear()).isEmpty();
        final Candidate borderline = inferred.borderline().getFirst();
        assertThat(borderline.start()).isEqualTo("Sess-A");
        assertThat(borderline.percent()).isEqualTo(50);
        assertThat(borderline.clear()).isFalse();
    }

    @Test
    void aCleanAlternationWithLopsidedCountsIsListedNotUsed() {
        for (int key = 1; key <= 5; key++) {
            final long t = key * 100 * MINUTE;
            add("Up", t, "Link", "k" + key);
            add("Down", t + 5 * MINUTE, "Link", "k" + key);
        }
        // Many more starts, on keys that never end.
        for (int key = 10; key < 30; key++) {
            add("Up", key * 100 * MINUTE, "Link", "k" + key);
        }

        final Inferred inferred = PairInference.infer(events, List.of());

        assertThat(inferred.clear()).isEmpty();
        assertThat(inferred.borderline()).singleElement().satisfies(pair -> {
            assertThat(pair.share()).isEqualTo(1.0);
            assertThat(pair.startCount()).isEqualTo(25);
            assertThat(pair.endCount()).isEqualTo(5);
        });
    }

    @Test
    void unrelatedTypesSharingAKeyAreNotPaired() {
        // Two kinds of web request from the same hosts, in no order: they share the host, but don't alternate.
        final Random random = new Random(7);
        long t = 0;
        for (int i = 0; i < 200; i++) {
            t += (1 + random.nextInt(5)) * MINUTE;
            add(random.nextBoolean()
                    ? "GET"
                    : "POST", t, "Client/Host", "h" + random.nextInt(3));
        }

        final Inferred inferred = PairInference.infer(events, List.of());

        assertThat(inferred.clear()).isEmpty();
        assertThat(inferred.borderline()).isEmpty();
    }

    @Test
    void typesAlreadyInAPairArentPairedAgain() {
        for (int key = 1; key <= 5; key++) {
            final long t = key * 100 * MINUTE;
            add("Tun-Ascend", t, "Tunnel/Id", "t" + key);
            add("Tun-Recede", t + 5 * MINUTE, "Tunnel/Id", "t" + key);
        }
        final PairRule known = new PairRule(0, "Tun-Ascend", "Tun-Recede", "Tunnel/Id", null,
                PairRule.Origin.SKILL, 1);

        assertThat(PairInference.infer(events, List.of(known)).clear()).isEmpty();
    }

    @Test
    void typesWhoseElementAndActionAreTheSchemasPairArentPairedAgainByTheirTypeIds() {
        for (int key = 1; key <= 5; key++) {
            final long t = key * 100 * MINUTE;
            events.add(new Typed("e" + ++ids, 0, t, "host-1", "proc-start", "Process/Execute", Map.of("ProcessId",
                    "p" + key)));
            events.add(new Typed("e" + ++ids, 0, t + MINUTE, "host-1", "proc-end", "Process/Terminate",
                    Map.of("ProcessId", "p" + key)));
        }

        assertThat(PairInference.infer(events, PairRule.SCHEMA).clear()).isEmpty();
        assertThat(PairInference.infer(events, List.of()).clear()).singleElement()
                .satisfies(pair -> assertThat(pair.start()).isEqualTo("proc-start"));
    }

    @Test
    void aBFarAfterItsAIsntItsEnd() {
        for (int key = 1; key <= 5; key++) {
            final long t = key * 10_000 * MINUTE;
            add("Begin", t, "Job", "j" + key);
            // Two days later.
            add("Finish", t + 2 * 24 * 60 * MINUTE, "Job", "j" + key);
        }

        assertThat(PairInference.infer(events, List.of()).clear()).isEmpty();
    }

    @Test
    void howAPairStillAlternatesIsEvaluated() {
        for (int key = 1; key <= 4; key++) {
            add("Tun-Ascend", key * 100 * MINUTE, "Tunnel/Id", "t" + key);
            add("Tun-Recede", key * 100 * MINUTE + MINUTE, "Tunnel/Id", "t" + key);
        }

        assertThat(PairInference.evaluate(events, "Tun-Ascend", "Tun-Recede", "Tunnel/Id").share()).isEqualTo(1.0);
        assertThat(PairInference.evaluate(events, "Nothing", "Else", null)).isNull();
    }

    private void add(final String type, final long time, final String path, final String value) {
        events.add(new Typed("e" + ++ids, 0, time, "host-1", type, null, Map.of(path, value)));
    }
}
