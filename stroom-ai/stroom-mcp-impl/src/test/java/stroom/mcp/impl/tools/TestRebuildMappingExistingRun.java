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

import static org.assertj.core.api.Assertions.assertThat;

/// How an existing pipeline's XSLT ran over the sample, as rebuild_mapping tells a build that started from it
/// (decided by the user, 2026-11-24): the records that wrote no Event and those kept as Unknown, listed.
class TestRebuildMappingExistingRun {

    @Test
    void recordsWritingNoEventAndThoseKeptAsUnknownAreListed() {
        final Map<String, String> outputs = new LinkedHashMap<>();
        outputs.put("7:1:1", "<Events><Event><EventDetail><Authenticate/></EventDetail></Event></Events>");
        outputs.put("7:1:2", "<Events/>");
        outputs.put("7:1:3", "<evt:Events><evt:Event><evt:EventDetail><evt:Unknown><evt:Data/></evt:Unknown>"
                             + "</evt:EventDetail></evt:Event></evt:Events>");
        outputs.put("7:1:4", "");

        final Map<String, Object> run = RebuildMappingTool.existingRun(outputs);

        assertThat(run).containsEntry("records", 4).containsEntry("events", 2).containsEntry("no_event", 2)
                .containsEntry("no_event_records", List.of("7:1:2", "7:1:4")).containsEntry("unknown", 1)
                .containsEntry("unknown_records", List.of("7:1:3"));
        assertThat(String.valueOf(run.get("note"))).contains("step_records", "catalogue_event_types");
    }

    @Test
    void aCleanRunListsNothing() {
        final Map<String, Object> run = RebuildMappingTool.existingRun(Map.of("7:1:1",
                "<Events><Event><EventDetail><View/></EventDetail></Event></Events>"));
        assertThat(run).containsEntry("no_event", 0).containsEntry("unknown", 0).doesNotContainKeys(
                "no_event_records", "unknown_records");
    }
}
