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
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.pipeline.shared.stepping.StepLocation;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestPassedOverRecords {

    private static final String AN_EVENT = "<Events xmlns=\"event-logging:3\" Version=\"4.1.0\"><Event/></Events>";
    private static final SourceTypeModule GROUP = new SourceTypeModule(SourceTypeModule.GROUP, "M365 Messaging",
            "m365_messaging", List.of("ExchangeOnline"), null, null, null, null, "Workload",
            List.of(new SourceTypeModule.Row("ExchangeOnline", SourceTypeRows.VALUE, "ExchangeOnline"),
                    new SourceTypeModule.Row("SharePoint", SourceTypeRows.VALUE, "SharePoint"),
                    new SourceTypeModule.Row("Exchange (other)", SourceTypeRows.PATTERN, "^Exchange")));
    private static final ErrorTriage.Marker LINE = ErrorTriage.marker("INFO", "translationFilter",
            XsltGenerator.PASSED_OVER + 1 + XsltGenerator.PASSED_OVER_END, null);

    private final StreamReader streamReader = Mockito.mock(StreamReader.class);

    @Test
    void onlyAnOlderXsltsInfoLineIsAPassOverLine() {
        assertThat(PassedOverRecords.isPassOver(LINE)).isTrue();
        assertThat(PassedOverRecords.isPassOver(ErrorTriage.marker("WARNING", "translationFilter",
                LINE.message(), null))).isFalse();
        assertThat(PassedOverRecords.isPassOver(ErrorTriage.marker("INFO", "translationFilter",
                "Passed over the record", null))).isFalse();
    }

    @Test
    void aRecordOfAnotherSourceTypeIsToldFromTheRegistryWithNoLineLogged() {
        // The group's XSLT logs nothing of the records it passes over (a line a document made Stroom write an Error
        // stream for every raw stream each group processed): they are told by the registry's rows.
        final PassedOverRecords passed = group();
        final ErrorTriage.Marker other = ErrorTriage.marker("INFO", "translationFilter", "Field x not read", null);

        final PassedOverRecords.Sifted sifted = passed.sift(List.of(other),
                SteppingWorld.passedOver(7, 0, 1, SteppingWorld.azureRecord("SharePoint")), at(7, 0, 1));
        final PassedOverRecords.Sifted kept = passed.sift(List.of(other),
                SteppingWorld.stepFrom(7, 0, 2, SteppingWorld.azureRecord("ExchangeOnline"), AN_EVENT), at(7, 0, 2));

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
    void theRowsAreTriedInOrderTheFirstMatchWinningAsTheXsltTriesThem() {
        final PassedOverRecords passed = group();

        // ExchangeOnline matches its own row before the pattern: the group's. ExchangeArchive only the pattern's.
        assertThat(passed.sift(List.of(), SteppingWorld.passedOver(7, 0, 1,
                SteppingWorld.azureRecord("ExchangeOnline")), at(7, 0, 1)).passedOver()).isFalse();
        assertThat(passed.sift(List.of(), SteppingWorld.passedOver(7, 0, 2,
                SteppingWorld.azureRecord("ExchangeArchive")), at(7, 0, 2)).passedOver()).isTrue();
        // A value no row matches is of no source type, passed over as the XSLT passes it over.
        assertThat(passed.sift(List.of(), SteppingWorld.passedOver(7, 0, 3,
                SteppingWorld.azureRecord("PowerBI")), at(7, 0, 3)).passedOver()).isTrue();

        assertThat(passed.bySourceType()).isEqualTo(Map.of("Exchange (other)", 1, SourceTypeScope.UNMATCHED, 1));
    }

    @Test
    void aListMembersRowMatchesAnItemOfTheValue() {
        final SourceTypeModule roles = new SourceTypeModule(SourceTypeModule.GROUP, "Directory", "directory",
                List.of("Directory"), null, null, null, null, "Workload",
                List.of(new SourceTypeModule.Row("Directory", SourceTypeRows.MEMBER, "Directory Services", ",")));
        final PassedOverRecords passed = PassedOverRecords.of(roles, "translationFilter", "translationFilter",
                new PartAttributes(streamReader));

        assertThat(passed.sift(List.of(), SteppingWorld.passedOver(7, 0, 1,
                SteppingWorld.azureRecord("DNS Server, Directory Services")), at(7, 0, 1)).passedOver()).isFalse();
        assertThat(passed.sift(List.of(), SteppingWorld.passedOver(7, 0, 2,
                SteppingWorld.azureRecord("DNS Server")), at(7, 0, 2)).passedOver()).isTrue();
    }

    @Test
    void aMetaFieldIsReadFromTheRecordsPart() {
        // A source type field of the part's meta: the record's part's attribute tells it, whatever the record holds.
        final SourceTypeModule roles = new SourceTypeModule(SourceTypeModule.GROUP, "Directory", "directory",
                List.of("Directory"), null, null, null, null, "meta:ServedRoles",
                List.of(new SourceTypeModule.Row("Directory", SourceTypeRows.MEMBER, "Directory Services", ","),
                        new SourceTypeModule.Row("DNS", SourceTypeRows.MEMBER, "DNS Server", ",")));
        Mockito.when(streamReader.partAttributes(7L, 0)).thenReturn(Map.of("ServedRoles",
                "DNS Server, Directory Services"));
        Mockito.when(streamReader.partAttributes(7L, 1)).thenReturn(Map.of("ServedRoles", "DNS Server"));
        Mockito.when(streamReader.partAttributes(7L, 2)).thenReturn(Map.of());
        final PassedOverRecords passed = PassedOverRecords.of(roles, "translationFilter", "translationFilter",
                new PartAttributes(streamReader));

        assertThat(passed.sift(List.of(), SteppingWorld.passedOver(7, 0, 1,
                SteppingWorld.azureRecord("x")), at(7, 0, 1)).passedOver()).isFalse();
        assertThat(passed.sift(List.of(), SteppingWorld.passedOver(7, 1, 1,
                SteppingWorld.azureRecord("x")), at(7, 1, 1)).passedOver()).isTrue();
        assertThat(passed.sift(List.of(), SteppingWorld.passedOver(7, 2, 1,
                SteppingWorld.azureRecord("x")), at(7, 2, 1)).passedOver()).isTrue();

        assertThat(passed.bySourceType()).isEqualTo(Map.of("DNS", 1, SourceTypeScope.UNMATCHED, 1));
    }

    @Test
    void aRecordThePipelineWroteAnEventForIsNeverPassedOver() {
        // Its fields read here as another source type's, but the group's XSLT translated it: it is the group's.
        final PassedOverRecords passed = group();

        final PassedOverRecords.Sifted sifted = passed.sift(List.of(),
                SteppingWorld.stepFrom(7, 0, 1, SteppingWorld.azureRecord("SharePoint"), AN_EVENT), at(7, 0, 1));

        assertThat(sifted.passedOver()).isFalse();
        assertThat(passed.count()).isZero();
    }

    @Test
    void anOlderXsltsLineIsTakenOutAndTheRegistryStillDecides() {
        final PassedOverRecords passed = group();

        // The line taken out of an own record's markers (an older XSLT logs it once a document).
        final PassedOverRecords.Sifted own = passed.sift(List.of(LINE),
                SteppingWorld.stepFrom(7, 0, 1, SteppingWorld.azureRecord("ExchangeOnline"), AN_EVENT), at(7, 0, 1));
        final PassedOverRecords.Sifted other = passed.sift(List.of(LINE),
                SteppingWorld.passedOverLogged(7, 0, 2, SteppingWorld.azureRecord("SharePoint")), at(7, 0, 2));

        assertThat(own.passedOver()).isFalse();
        assertThat(own.markers()).isEmpty();
        assertThat(other.passedOver()).isTrue();
        assertThat(passed.bySourceType()).isEqualTo(Map.of("SharePoint", 1));
    }

    @Test
    void aPassedOverRecordKeepsOnlyItsErrors() {
        final PassedOverRecords passed = group();
        final ErrorTriage.Marker truncated = ErrorTriage.marker("WARNING", "jsonParser",
                "String value truncated to length 10000, original length: 25486", null);
        final ErrorTriage.Marker warn = ErrorTriage.marker("WARN", "jsonParser", "Another warning", null);
        final ErrorTriage.Marker error = ErrorTriage.marker("ERROR", "jsonParser", "Unexpected character", null);
        final ErrorTriage.Marker fatal = ErrorTriage.marker("FATAL_ERROR", "pipeline", "Stream broken", null);

        final PassedOverRecords.Sifted sifted = passed.sift(List.of(truncated, warn, error, fatal),
                SteppingWorld.passedOver(7, 0, 1, SteppingWorld.azureRecord("SharePoint")), at(7, 0, 1));

        assertThat(sifted.passedOver()).isTrue();
        assertThat(sifted.markers()).containsExactly(error, fatal);
    }

    @Test
    void aRecordNotPassedOverKeepsItsWarnings() {
        final PassedOverRecords passed = group();
        final ErrorTriage.Marker truncated = ErrorTriage.marker("WARNING", "jsonParser",
                "String value truncated to length 10000, original length: 25486", null);

        final PassedOverRecords.Sifted sifted = passed.sift(List.of(truncated),
                SteppingWorld.stepFrom(7, 0, 2, SteppingWorld.azureRecord("ExchangeOnline"), "<Events/>"),
                at(7, 0, 2));

        assertThat(sifted.passedOver()).isFalse();
        assertThat(sifted.markers()).containsExactly(truncated);
        assertThat(passed.count()).isZero();
    }

    @Test
    void withoutAGroupMappingOnlyAnOlderXsltsLineTellsOneAndItsSourceTypeIsNotTold() {
        final PassedOverRecords passed = PassedOverRecords.none();

        passed.sift(List.of(LINE), SteppingWorld.passedOverLogged(7, 0, 1, SteppingWorld.azureRecord("SharePoint")),
                at(7, 0, 1));
        passed.sift(List.of(), SteppingWorld.passedOver(7, 0, 2, SteppingWorld.azureRecord("SharePoint")),
                at(7, 0, 2));

        assertThat(passed.bySourceType()).isEqualTo(Map.of(PassedOverRecords.NOT_TOLD, 1));
    }

    @Test
    void nothingPassedOverAddsNothing() {
        final Map<String, Object> result = new LinkedHashMap<>();
        group().addTo(result);
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

    private PassedOverRecords group() {
        return PassedOverRecords.of(GROUP, "translationFilter", "translationFilter", new PartAttributes(streamReader));
    }

    private static StepLocation at(final long stream, final long part, final long record) {
        return new StepLocation(stream, part, record);
    }
}
