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

package stroom.mcp.impl.e2e;

import stroom.mcp.impl.e2e.InsiderDataset.Options;
import stroom.mcp.impl.e2e.InsiderDataset.Period;
import stroom.mcp.impl.e2e.InsiderDataset.Source;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// `compare_activity`'s population screen (`scope: population`) over `/mcp` on the insider-risk dataset
/// ([InsiderDataset], every day from December to September at a low scale, each source its own Elasticsearch
/// index, with everyone's content delivery network fetches): everyone scored from ES|QL aggregates with the person a
/// group of each, the leaver, the privileged-misuse admin, the drift to later hours and the curious viewer ranked
/// above the role change and the decoys (the approved migration and on-call week given as exceptions); `rare` on the
/// proxy's domains flags the leaver's personal cloud domain as rare and new, and leaves the content delivery
/// network's subdomains out as CDN noise; `where` limits what is screened.
@SuppressWarnings("unchecked")
class TestE2eInsiderScreen extends AbstractInsiderE2eTest {

    /// Every day from December to September, a sixth of the routine actions, 12 background people.
    static final Options OPTIONS = new Options(InsiderDataset.SEED, 12, 0.15, List.of(new Period(
            LocalDate.of(2025, 12, 1), LocalDate.of(2026, 9, 30))));
    private static final List<String> PLANTS = List.of(InsiderDataset.LEAVER, InsiderDataset.ADMIN,
            InsiderDataset.DRIFT, InsiderDataset.VIEWER);
    private static final List<String> NOT_ABOVE = List.of(InsiderDataset.ROLE_CHANGE, "P20001", "P20002");

    @Test
    void theScreenRanksThePlantsAboveTheRoleChangeAndTheDecoys() {
        final Loaded loaded = load(OPTIONS, true);

        final Map<String, Object> screen = screen(loaded, List.of(Source.values()), Map.of());
        dump("screen", screen);

        assertThat(screen).containsEntry("scope", "population");
        assertThat((Integer) screen.get("population")).isEqualTo(loaded.dataset().people().size());
        final List<String> ranked = ((List<Map<String, Object>>) screen.get("ranked")).stream()
                .map(person -> String.valueOf(person.get("person")))
                .toList();
        assertThat(ranked).hasSizeLessThanOrEqualTo(10).containsAll(PLANTS);
        final int lowestPlant = PLANTS.stream().mapToInt(ranked::indexOf).max().orElseThrow();
        for (final String decoy : NOT_ABOVE) {
            assertThat(ranked.indexOf(decoy)).as(decoy + " in " + ranked).satisfiesAnyOf(
                    place -> assertThat(place).isEqualTo(-1),
                    place -> assertThat(place).isGreaterThan(lowestPlant));
        }
        assertThat(String.valueOf(screen.get("notes"))).contains("never a query a person");
        assertThat(String.valueOf(screen.get("next_step"))).contains("compare_activity with person "
                                                                     + ranked.getFirst());
    }

    @Test
    void rareOnTheProxysDomainsFlagsThePersonalCloudAndLeavesTheCdnOut() {
        final Loaded loaded = load(OPTIONS, true);

        final Map<String, Object> screen = screen(loaded, List.of(Source.PROXY), Map.of("rare",
                "destination.domain"));
        dump("rare", screen);

        final Map<String, Object> rare = ((List<Map<String, Object>>) screen.get("rare")).getFirst();
        final List<Map<String, Object>> values = (List<Map<String, Object>>) rare.get("values");
        assertThat(values).anySatisfy(value -> {
            assertThat(value).containsEntry("value", InsiderDataset.PERSONAL_CLOUD);
            assertThat((List<String>) value.get("new_for")).contains(InsiderDataset.LEAVER);
        });
        assertThat(String.valueOf(values)).doesNotContain(InsiderDataset.CDN);
        assertThat(String.valueOf(rare.get("note"))).contains("CDN noise").contains(InsiderDataset.CDN)
                .contains("rare=url.category");
    }

    @Test
    void whereLimitsTheEventsScreened() {
        final Loaded loaded = load(OPTIONS, false);

        final Map<String, Object> screen = screen(loaded, List.of(Source.ENDPOINT), Map.of("where",
                List.of(Map.of("field", "process.name", "value", InsiderDataset.NEW_TOOL))));
        dump("where", screen);

        assertThat(screen.get("population")).isEqualTo(1);
        assertThat(screen.get("where")).isEqualTo(List.of("process.name = " + InsiderDataset.NEW_TOOL));
        assertThat(((List<Map<String, Object>>) screen.get("sources")).getFirst()).containsEntry("people", 1);
    }

    private Map<String, Object> screen(final Loaded loaded,
                                       final List<Source> sources,
                                       final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("scope", "population");
        arguments.put("windowFrom", "2026-06-01");
        arguments.put("windowTo", "2026-09-30");
        arguments.put("baselineFrom", "2025-12-01");
        arguments.put("timeZone", InsiderDataset.ZONE.getId());
        arguments.put("sources", sources.stream().filter(loaded.indexes()::containsKey).map(loaded::index).toList());
        arguments.put("exceptions", List.of("P20001 2026-05-11..2026-05-15: approved data-centre migration",
                "P20002 2026-07-20..2026-07-24: on-call roster"));
        arguments.putAll(more);
        return json("compare_activity", arguments);
    }
}
