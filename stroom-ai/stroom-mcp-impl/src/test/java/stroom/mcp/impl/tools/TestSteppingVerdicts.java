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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ErrorTriage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What the stepping tools make of a step's output.
class TestSteppingVerdicts {

    @Test
    void forwardedOutputIsSentOnPurpose() {
        // A forwarding pipeline writes CEF lines or Kafka records, not Events.
        assertThat(SteppingVerdicts.sendsOn("  CEF:0|Acme|Door|1|100|Opened|3|")).isTrue();
        assertThat(SteppingVerdicts.sendsOn("<kafkaRecords><kafkaRecord/></kafkaRecords>")).isTrue();
        assertThat(SteppingVerdicts.sendsOn("some text copied through")).isFalse();
        assertThat(SteppingVerdicts.sendsOn(null)).isFalse();
    }

    @Test
    void outputWithNoElementsIsAnErrorButAnXmlDeclarationAloneIsNotAnElement() {
        assertThat(SteppingVerdicts.emptyOutput(SteppingWorld.step(7, 0, 1, "<?xml version=\"1.1\"?>text"),
                "translationFilter", "7:1"))
                .singleElement()
                .satisfies(marker -> {
                    assertThat(marker.message()).startsWith("Output contains no XML elements");
                    assertThat(marker.record()).isEqualTo("7:1");
                    assertThat(marker.severity()).isEqualTo("ERROR");
                });
        assertThat(SteppingVerdicts.emptyOutput(SteppingWorld.step(7, 0, 1, "<?xml version=\"1.1\"?><Events/>"),
                "translationFilter", "7:1")).isEmpty();
    }

    @Test
    void whatUnknownEventsHoldIsSaidAFewOfEach() {
        final String out = "<Events><Event><EventDetail><TypeId>TRAFFIC</TypeId><Unknown>"
                           + "<Data Name=\"action\" Value=\"allow\"/><Data Name=\"action\" Value=\"deny\"/>"
                           + "<Data Name=\"empty\" Value=\"\"/></Unknown></EventDetail></Event></Events>";

        assertThat(SteppingVerdicts.writesUnknown(out)).isTrue();
        assertThat(SteppingVerdicts.whatUnknownHolds(List.of(out, out)))
                .isEqualTo("TypeId: TRAFFIC; action: allow, deny");
    }

    @Test
    void aBlockingGroupGoesFirstAndMakesTheVerdictBlocking() {
        final Map<String, Object> summary = new LinkedHashMap<>();
        final Map<String, Integer> byClass = new LinkedHashMap<>();
        byClass.put(ErrorTriage.BLOCKING, 0);
        byClass.put(ErrorTriage.REVIEW, 1);
        summary.put("verdict", "review");
        summary.put("groups_by_class", byClass);
        summary.put("groups", new ArrayList<>(List.of(Map.of("class", "review"))));

        SteppingVerdicts.block(summary, SteppingVerdicts.noEvents("translationFilter", List.of("7:0", "7:1")));
        SteppingVerdicts.block(summary, null);

        assertThat(summary.get("verdict")).isEqualTo("blocking");
        assertThat(byClass.get(ErrorTriage.BLOCKING)).isEqualTo(1);
        assertThat(SteppingVerdicts.groups(summary).getFirst().get("records")).isEqualTo(List.of("7:0", "7:1"));
        assertThat(SteppingVerdicts.groups(summary).getFirst().get("count")).isEqualTo(2);
    }
}
