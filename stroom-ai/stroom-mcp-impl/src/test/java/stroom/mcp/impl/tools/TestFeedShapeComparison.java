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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// How a build's feeds differ, from their surveys ([FeedShapeComparison]): decided by the user (2026-11-25), their
/// shapes a union with the differences shown; nothing merges silently.
class TestFeedShapeComparison {

    @Test
    void feedsOfOneShapeDifferInNothing() {
        final Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        states.put("ACME-A", state("json lines", Map.of("fields:action,time,user | action=login",
                List.of("{\"time\":\"2026-01-01T10:00:00Z\",\"user\":\"a\",\"action\":\"login\"}"))));
        states.put("ACME-B", state("json lines", Map.of("fields:action,time,user | action=login",
                List.of("{\"time\":\"2026-02-01T11:00:00Z\",\"user\":\"b\",\"action\":\"login\"}"))));

        final FeedShapeComparison.Comparison comparison = FeedShapeComparison.compare(states);

        assertThat(comparison.differs()).isFalse();
        assertThat(comparison.said()).isEmpty();
        assertThat(comparison.zonesToAsk()).isEmpty();
        assertThat(comparison.zones()).isEqualTo(Map.of("ACME-A", Set.of("UTC"), "ACME-B", Set.of("UTC")));
    }

    @Test
    void aFieldInOneFeedOnlyAnotherKindAndAnotherZoneAreEachShown() {
        final Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        states.put("ACME-A", state("json lines", Map.of(
                "fields:action,time,user | action=login",
                List.of("{\"time\":\"2026-01-01T10:00:00+10:00\",\"user\":\"a\",\"action\":\"login\"}"))));
        states.put("ACME-B", state("json lines", Map.of(
                "fields:action,site,time,user | action=login",
                List.of("{\"time\":\"2026-01-01 10:00:00\",\"site\":\"s\",\"user\":\"b\",\"action\":\"login\"}"),
                "fields:action,site,time,user | action=logout",
                List.of("{\"time\":\"2026-01-01 11:00:00\",\"site\":\"s\",\"user\":\"b\",\"action\":\"logout\"}"))));

        final FeedShapeComparison.Comparison comparison = FeedShapeComparison.compare(states);

        assertThat(comparison.differs()).isTrue();
        assertThat(comparison.fieldsOnlyIn()).isEqualTo(Map.of("site", List.of("ACME-B")));
        assertThat(comparison.kindsOnlyIn()).isEqualTo(Map.of("action=logout", List.of("ACME-B")));
        assertThat(comparison.zones()).isEqualTo(Map.of("ACME-A", Set.of("+10:00"), "ACME-B",
                Set.of(FeedShapeComparison.NO_ZONE)));
        // Only the feed whose times say no zone is asked its zone.
        assertThat(comparison.zonesToAsk()).containsExactly("ACME-B");
        assertThat(comparison.said()).containsExactly(
                "Fields in some feeds only: site (ACME-B).",
                "Kinds of event in some feeds only: action=logout (ACME-B).",
                "Times written in different zones: ACME-A: +10:00; ACME-B: no zone or offset.");
    }

    @Test
    void formatsThatDifferAreSaid() {
        final Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        states.put("A", state("json lines", Map.of()));
        states.put("B", state("", Map.of()));

        final FeedShapeComparison.Comparison comparison = FeedShapeComparison.compare(states);

        assertThat(comparison.formatsDiffer()).isTrue();
        assertThat(comparison.said()).containsExactly("Formats differ: A: json lines; B: not known.");
    }

    @Test
    void theDigestChangesOnlyWithWhatDiffers() {
        final Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        states.put("A", state("json lines", Map.of("fields:a,b", List.of("{}"))));
        states.put("B", state("json lines", Map.of("fields:a", List.of("{}"))));
        final String digest = FeedShapeComparison.compare(states).digest();

        assertThat(FeedShapeComparison.compare(states).digest()).isEqualTo(digest);
        states.put("B", state("json lines", Map.of("fields:a,c", List.of("{}"))));
        assertThat(FeedShapeComparison.compare(states).digest()).isNotEqualTo(digest);
    }

    @Test
    void signaturesGiveFieldsAndKinds() {
        assertThat(FeedShapeComparison.fieldsOf("fields:a,b.c,d | type=x > msg: text")).containsExactly("a", "b.c",
                "d");
        assertThat(FeedShapeComparison.fieldsOf("row:action=login")).isEmpty();
        assertThat(FeedShapeComparison.kindOf("fields:a,b | type=x, sub=y > msg: text")).contains("type=x, sub=y");
        assertThat(FeedShapeComparison.kindOf("fields:a,b")).isEmpty();
        assertThat(FeedShapeComparison.kindOf("text:sshd|Accepted password for <*>"))
                .contains("text:sshd|Accepted password for <*>");
    }

    @Test
    void timesAreReadForTheirZones() {
        assertThat(FeedShapeComparison.zonesIn("at 2026-01-01T10:00:00.123Z and 10:00:00-0500"))
                .containsExactly("-0500", "UTC");
        assertThat(FeedShapeComparison.zonesIn("Jan  1 10:00:00 host sshd")).containsExactly(
                FeedShapeComparison.NO_ZONE);
        assertThat(FeedShapeComparison.zonesIn("no time here, id 12:34")).isEmpty();
    }

    private static Map<String, Object> state(final String format, final Map<String, List<String>> shapes) {
        final Map<String, Object> kept = new LinkedHashMap<>();
        shapes.forEach((signature, texts) -> kept.put(signature, Map.of("count", 1, "examples",
                texts.stream().map(text -> Map.of("text", text)).toList())));
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("format", format);
        state.put("shapes", kept);
        return state;
    }
}
