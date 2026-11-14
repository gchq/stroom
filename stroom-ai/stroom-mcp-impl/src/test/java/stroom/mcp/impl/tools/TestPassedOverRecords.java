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
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SourceTypeRows;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestPassedOverRecords {

    private static final SourceTypeModule GROUP = new SourceTypeModule(SourceTypeModule.GROUP, "M365 Messaging",
            "m365_messaging", List.of("ExchangeOnline"), null, null, null, null, "Workload",
            List.of(new SourceTypeModule.Row("ExchangeOnline", SourceTypeRows.VALUE, "ExchangeOnline"),
                    new SourceTypeModule.Row("SharePoint", SourceTypeRows.VALUE, "SharePoint")));
    private static final ErrorTriage.Marker LINE = ErrorTriage.marker("INFO", "translationFilter",
            XsltGenerator.PASSED_OVER + 1 + XsltGenerator.PASSED_OVER_END, null);

    @Test
    void onlyTheGroupsInfoLineIsAPassOver() {
        assertThat(PassedOverRecords.isPassOver(LINE)).isTrue();
        assertThat(PassedOverRecords.isPassOver(ErrorTriage.marker("WARNING", "translationFilter",
                LINE.message(), null))).isFalse();
        assertThat(PassedOverRecords.isPassOver(ErrorTriage.marker("INFO", "translationFilter",
                "Passed over the record", null))).isFalse();
    }

    @Test
    void theLineIsTakenOutAndTheRecordCountedByItsSourceType() {
        final PassedOverRecords passed = PassedOverRecords.of(GROUP);
        final ErrorTriage.Marker other = ErrorTriage.marker("INFO", "translationFilter", "Field x not read", null);

        final PassedOverRecords.Sifted sifted = passed.sift(List.of(LINE, other),
                SteppingWorld.passedOver(7, 0, 1, SteppingWorld.azureRecord("SharePoint")));
        final PassedOverRecords.Sifted kept = passed.sift(List.of(other),
                SteppingWorld.step(7, 0, 2, "<Events/>"));

        assertThat(sifted.passedOver()).isTrue();
        assertThat(sifted.markers()).isEmpty();
        assertThat(kept.passedOver()).isFalse();
        assertThat(kept.markers()).containsExactly(other);
        assertThat(passed.count()).isEqualTo(1);
        assertThat(passed.bySourceType()).isEqualTo(Map.of("SharePoint", 1));
        final Map<String, Object> result = new LinkedHashMap<>();
        passed.addTo(result);
        assertThat(result).containsEntry(PassedOverRecords.RECORDS_PASSED_OVER, 1);
    }

    @Test
    void aPassedOverRecordKeepsOnlyItsErrors() {
        final PassedOverRecords passed = PassedOverRecords.of(GROUP);
        final ErrorTriage.Marker truncated = ErrorTriage.marker("WARNING", "jsonParser",
                "String value truncated to length 10000, original length: 25486", null);
        final ErrorTriage.Marker warn = ErrorTriage.marker("WARN", "jsonParser", "Another warning", null);
        final ErrorTriage.Marker error = ErrorTriage.marker("ERROR", "jsonParser", "Unexpected character", null);
        final ErrorTriage.Marker fatal = ErrorTriage.marker("FATAL_ERROR", "pipeline", "Stream broken", null);

        final PassedOverRecords.Sifted sifted = passed.sift(List.of(truncated, LINE, warn, error, fatal),
                SteppingWorld.passedOver(7, 0, 1, SteppingWorld.azureRecord("SharePoint")));

        assertThat(sifted.passedOver()).isTrue();
        assertThat(sifted.markers()).containsExactly(error, fatal);
    }

    @Test
    void aRecordNotPassedOverKeepsItsWarnings() {
        final PassedOverRecords passed = PassedOverRecords.of(GROUP);
        final ErrorTriage.Marker truncated = ErrorTriage.marker("WARNING", "jsonParser",
                "String value truncated to length 10000, original length: 25486", null);

        final PassedOverRecords.Sifted sifted = passed.sift(List.of(truncated),
                SteppingWorld.step(7, 0, 2, "<Events/>"));

        assertThat(sifted.passedOver()).isFalse();
        assertThat(sifted.markers()).containsExactly(truncated);
        assertThat(passed.count()).isZero();
    }

    @Test
    void withoutAGroupMappingTheSourceTypeIsNotTold() {
        final PassedOverRecords passed = PassedOverRecords.of(null);

        passed.sift(List.of(LINE), SteppingWorld.passedOver(7, 0, 1, SteppingWorld.azureRecord("SharePoint")));

        assertThat(passed.bySourceType()).isEqualTo(Map.of(PassedOverRecords.NOT_TOLD, 1));
    }

    @Test
    void nothingPassedOverAddsNothing() {
        final Map<String, Object> result = new LinkedHashMap<>();
        PassedOverRecords.of(GROUP).addTo(result);
        assertThat(result).isEmpty();
    }

    @Test
    void fieldsAreReadFromJsonAsXmlAndFromRecords() {
        assertThat(PassedOverRecords.fieldsOf("<map xmlns=\"http://www.w3.org/2013/XSL/json\"><string "
                                              + "key=\"Workload\">Teams</string><map key=\"event\"><string "
                                              + "key=\"source\">a</string></map></map>"))
                .containsEntry("Workload", "Teams").containsEntry("event.source", "a");
        assertThat(PassedOverRecords.fieldsOf("<records xmlns=\"records:2\"><record><data name=\"src\" "
                                              + "value=\"db\"/><data name=\"msg\"><data name=\"user\" "
                                              + "value=\"bob\"/></data></record></records>"))
                .containsEntry("src", "db").containsEntry("msg.user", "bob");
        assertThat(PassedOverRecords.fieldsOf("not xml")).isNull();
    }
}
