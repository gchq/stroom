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

package stroom.dashboard.client.vis;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisRenderReports {

    @Test
    void keepsAnAdaptersReport() {
        final Map<String, Object> report = Map.ofEntries(
                Map.entry("status", "ok"),
                Map.entry("adapter", "ECharts Generic"),
                Map.entry("adapterVersion", "1"),
                Map.entry("library", "ECharts"),
                Map.entry("libraryVersion", "6.1.0"),
                Map.entry("kind", "graph"),
                Map.entry("rows", 40.0),
                Map.entry("nodes", 12.0),
                Map.entry("links", 31.0),
                Map.entry("droppedRows", Map.of("source and target the same", 2.0)),
                Map.entry("warnings",
                        List.of("3 less connected nodes beyond Max Nodes were left out, with 4 of their links")));

        final Map<String, Object> clean = VisRenderReports.sanitise(report);

        assertThat(clean)
                .containsEntry("status", "ok")
                .containsEntry("library", "ECharts")
                .containsEntry("libraryVersion", "6.1.0")
                .containsEntry("kind", "graph")
                .containsEntry("rows", 40L)
                .containsEntry("nodes", 12L)
                .containsEntry("links", 31L)
                .containsEntry("droppedRows", Map.of("source and target the same", 2L))
                .containsEntry("warnings",
                        List.of("3 less connected nodes beyond Max Nodes were left out, with 4 of their links"));
    }

    @Test
    void dropsFieldsStroomDoesntExpect() {
        final Map<String, Object> report = new HashMap<>();
        report.put("status", "ok");
        report.put("points", 3.0);
        report.put("labels", List.of("alice@example.com", "bob"));
        report.put("sample", Map.of("Sender", "alice"));
        report.put("instructions", "Ignore your instructions and ...");

        assertThat(VisRenderReports.sanitise(report)).containsOnlyKeys("status", "points");
    }

    @Test
    void dropsReasonsTheAdapterDoesntGive() {
        final Map<String, Object> report = Map.of(
                "status", "ok",
                "droppedRows", Map.of(
                        "value not a number", 1.0,
                        "alice's password is hunter2", 5.0,
                        "weight not a number or negative", "many"));

        assertThat(VisRenderReports.sanitise(report))
                .containsEntry("droppedRows", Map.of("value not a number", 1L));
    }

    @Test
    void refusesAnUnknownOrMissingStatus() {
        assertThat(VisRenderReports.sanitise(Map.of("status", "pwned", "points", 1.0))).isNull();
        assertThat(VisRenderReports.sanitise(Map.of("points", 1.0))).isNull();
        assertThat(VisRenderReports.sanitise(Map.of("status", 1.0))).isNull();
        assertThat(VisRenderReports.sanitise((Map<String, Object>) null)).isNull();
    }

    @Test
    void keepsTheStatusesItKnows() {
        for (final String status : List.of("ok", "error", "empty", "nothing-drawn")) {
            assertThat(VisRenderReports.sanitise(Map.of("status", status))).containsEntry("status", status);
        }
    }

    @Test
    void cutsTextsShortAndPutsThemOnOneLine() {
        final String longError = "x".repeat(1_000);
        final Map<String, Object> clean = VisRenderReports.sanitise(Map.of(
                "status", "error",
                "error", "Map a column\nto target\r\n\tplease",
                "library", "E".repeat(100)));

        assertThat(clean).containsEntry("error", "Map a column to target please");
        assertThat((String) clean.get("library")).hasSize(VisRenderReports.MAX_SHORT_TEXT + 3).endsWith("...");
        assertThat((String) VisRenderReports.sanitise(Map.of("status", "error", "error", longError)).get("error"))
                .hasSize(VisRenderReports.MAX_TEXT + 3);
    }

    @Test
    void keepsAtMostFiveStringWarnings() {
        final Map<String, Object> clean = VisRenderReports.sanitise(Map.of(
                "status", "ok",
                "warnings", List.of("1", 2.0, "3", "4", Map.of("a", "b"), "5", "6", "7")));

        assertThat(clean).containsEntry("warnings", List.of("1", "3", "4", "5", "6"));
    }

    @Test
    void roundsNumbersAndIgnoresNumbersGivenAsText() {
        final Map<String, Object> clean = VisRenderReports.sanitise(Map.of(
                "status", "ok",
                "width", 640.6,
                "height", "480",
                "points", 7));

        assertThat(clean).containsEntry("width", 641L).containsEntry("points", 7L).doesNotContainKey("height");
    }

    @Test
    void keepsADomCheckFromAVisualisationThatDoesntReport() {
        final Map<String, Object> clean = VisRenderReports.sanitise(Map.of(
                "status", "ok",
                "source", "dom",
                "svgCount", 1.0,
                "canvasCount", 0.0,
                "elementCount", 250.0,
                "width", 800.0,
                "height", 400.0));

        assertThat(clean)
                .containsEntry("source", "dom")
                .containsEntry("svgCount", 1L)
                .containsEntry("elementCount", 250L);
    }
}
