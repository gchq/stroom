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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.tools.compare.ActivityAccess.Compared;
import stroom.ai.impl.tools.compare.ActivityLocation.Located;
import stroom.ai.impl.tools.compare.ActivityScoring.Finding;
import stroom.ai.impl.tools.compare.ActivityScoring.Kind;
import stroom.ai.impl.tools.compare.ActivityScoring.Spec;
import stroom.ai.impl.tools.compare.ActivityScoring.Unit;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestActivityAccessAndLocation {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final Spec SPEC = new Spec(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 4, 6),
            LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 20), Unit.WEEK,
            List.<LocalDate[]>of(new LocalDate[]{LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 17)}), ZONE);

    @Test
    void accessOutsideTheClusterIsFound() {
        // Finance reach the finance sites, Legal the legal ones; p1 is in Finance and in the window reaches legal's.
        final List<Map<String, String>> graph = new ArrayList<>();
        for (final String who : List.of("p1", "f2", "f3", "f4")) {
            for (final String site : List.of("sites/finance", "sites/budget", "sites/payroll")) {
                graph.add(pair(who, site, 20));
            }
        }
        for (final String who : List.of("l1", "l2", "l3")) {
            for (final String site : List.of("sites/legal", "sites/contracts", "sites/cases")) {
                graph.add(pair(who, site, 20));
            }
        }
        final Table window = table(List.of(one("sites/finance", 30), one("sites/legal", 12),
                one("sites/contracts", 8), one("sites/hr", 5)));
        final Compared compared = ActivityAccess.compare("SharePoint", "url.path", List.of("p1"), table(graph),
                window, "2026-04-06 to 2026-04-19");

        final Finding finding = compared.finding().orElseThrow();
        assertThat(finding.kind()).isEqualTo(Kind.ACCESS);
        assertThat(finding.value()).isCloseTo(25 / 55.0, within(1e-9));
        assertThat(finding.baseline()).isZero();
        assertThat(finding.evidence()).contains("45% of the window's url.path events were on 3 value(s) outside the "
                                                + "person's access cluster (sites/legal, sites/contracts, sites/hr)")
                .contains("3 reached by no one in their cluster before");
        assertThat(compared.peers()).containsExactlyInAnyOrder("f2", "f3", "f4");
        assertThat(compared.notes()).anySatisfy(note -> assertThat(note).contains("3 people share the person's "
                                                                                  + "cluster"));
    }

    @Test
    void accessWithinTheClusterIsNotAFinding() {
        final List<Map<String, String>> graph = new ArrayList<>();
        for (final String who : List.of("p1", "f2", "f3")) {
            graph.add(pair(who, "sites/finance", 20));
            graph.add(pair(who, "sites/budget", 20));
        }
        final Compared compared = ActivityAccess.compare("SharePoint", "url.path", List.of("p1"), table(graph),
                table(List.of(one("sites/finance", 40), one("sites/budget", 3))), "w");

        assertThat(compared.finding()).isEmpty();
        assertThat(ActivityAccess.compare("SharePoint", "url.path", List.of("nobody"), table(graph),
                table(List.of()), "w").notes()).anySatisfy(note -> assertThat(note).contains("reached no url.path"));
    }

    @Test
    void aNewCountryTravelAndAbroadOnLeave() {
        final List<Map<String, String>> rows = new ArrayList<>();
        rows.add(hour("2026-03-10T00:00:00Z", "AU", null));
        rows.add(hour("2026-03-11T00:00:00Z", "AU", null));
        // On leave from 6 April: Romania at night, two hours after Australia.
        rows.add(hour("2026-04-08T09:00:00Z", "AU", null));
        rows.add(hour("2026-04-08T10:00:00Z", "RO", null));
        final Located located = ActivityLocation.locate("Cloud", SPEC, table(rows), false);

        assertThat(located.findings()).extracting(Finding::feature)
                .containsExactlyInAnyOrder("country:new", "country:travel", "country:on_leave");
        assertThat(located.findings()).filteredOn(finding -> finding.feature().equals("country:new"))
                .singleElement().satisfies(finding -> assertThat(finding.evidence())
                        .isEqualTo("Activity from RO, not seen in the baseline (AU), first at 2026-04-08 20:00"));
        assertThat(located.findings()).filteredOn(finding -> finding.feature().equals("country:on_leave"))
                .singleElement().satisfies(finding -> assertThat(finding.evidence())
                        .contains("on 1 day(s) of the person's leave: 2026-04-08 (RO)"));
        assertThat(located.notes()).anySatisfy(note -> assertThat(note).contains("countries only"));
    }

    @Test
    void impossibleTravelByTheDistanceBetweenPlaces() {
        final List<Map<String, String>> rows = new ArrayList<>();
        rows.add(hour("2026-03-10T00:00:00Z", "AU", "POINT (151.2 -33.9)"));
        // Sydney, then Bucharest three hours later: about 15,000 km.
        rows.add(hour("2026-04-13T08:00:00Z", "AU", "POINT (151.2 -33.9)"));
        rows.add(hour("2026-04-13T11:00:00Z", "RO", "POINT (26.1 44.4)"));
        // Then New Zealand a day and a half later, and back to Sydney six hours after: both believable.
        rows.add(hour("2026-04-15T01:00:00Z", "NZ", "POINT (174.8 -36.8)"));
        rows.add(hour("2026-04-15T07:00:00Z", "AU", "POINT (151.2 -33.9)"));
        final Located located = ActivityLocation.locate("VPN", SPEC, table(rows), true);

        assertThat(located.findings()).filteredOn(finding -> finding.feature().equals("place:travel"))
                .singleElement().satisfies(finding -> assertThat(finding.evidence())
                        .startsWith("From AU at 2026-04-13 18:00 to RO at 2026-04-13 21:00: 15232 km"));
        assertThat(ActivityLocation.distanceKm(new double[]{151.2, -33.9}, new double[]{151.2, -33.9})).isZero();
        assertThat(ActivityLocation.distanceKm(new double[]{0, 0}, new double[]{0, 1}))
                .isCloseTo(111.19, within(0.01));
        assertThat(ActivityLocation.point("POINT (26.1 44.4)")).containsExactly(26.1, 44.4);
        assertThat(ActivityLocation.point("")).isNull();
    }

    private static Map<String, String> pair(final String who, final String resource, final int events) {
        final Map<String, String> row = new LinkedHashMap<>();
        row.put(AggregateQuery.groupColumn(0), who);
        row.put(AggregateQuery.groupColumn(1), resource);
        row.put(ActivityProfiles.EVENTS, String.valueOf(events));
        return row;
    }

    private static Map<String, String> one(final String resource, final int events) {
        final Map<String, String> row = new LinkedHashMap<>();
        row.put(AggregateQuery.groupColumn(0), resource);
        row.put(ActivityProfiles.EVENTS, String.valueOf(events));
        return row;
    }

    private static Map<String, String> hour(final String hour, final String country, final String point) {
        final Map<String, String> row = new LinkedHashMap<>();
        row.put(AggregateQuery.BUCKET_COLUMN, hour);
        row.put(AggregateQuery.groupColumn(0), country);
        row.put(ActivityProfiles.EVENTS, "3");
        if (point != null) {
            row.put(ActivityLocation.PLACE, point);
        }
        return row;
    }

    private static Table table(final List<Map<String, String>> rows) {
        return new Table(rows, false, 1, List.of(), "test");
    }
}
