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

package stroom.ai.impl.extraction;

import stroom.ai.impl.extraction.ExtractionPreview.Preview;
import stroom.ai.impl.extraction.ExtractionPreview.Sample;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestExtractionPreview {

    private static final ExtractionField USER = new ExtractionField("UserId", "EventSource/User/Id", null);
    private static final ExtractionField ORG = new ExtractionField("Organisation",
            "EventSource/User/Organisation", null);

    @Test
    void aColumnWhoseFieldTheExtractionDoesntGiveWouldBeBlank() {
        // Turning extraction on fills every value from it, the indexed ones included.
        final Preview preview = ExtractionPreview.preview(List.of(ORG),
                List.of(sample(1, Map.of("User", "jsmith"), Map.of("Organisation", "Finance"))),
                Map.of("User", "${UserId}", "Events", "count()", "Note", "${annotation:Comment}"),
                List.of("UserId"));

        assertThat(preview.problems()).singleElement().asString()
                .startsWith("Column 'User' uses UserId, which the extraction doesn't give")
                .contains("would be blank");
    }

    @Test
    void aFieldNamedInAnotherCaseDoesntFillAColumn() {
        // Stroom matches a field's name exactly.
        assertThat(ExtractionPreview.blankColumns(List.of(new ExtractionField("userid", "User/Id", null)),
                Map.of("User", "${UserId}")))
                .singleElement().asString()
                .isEqualTo("Column 'User' uses UserId, but the extraction gives userid: Stroom matches a field's "
                           + "name exactly, so the column would be blank. Name the field UserId.");
        assertThat(ExtractionPreview.blankColumns(List.of(new ExtractionField("UserId", "User/Id", null)),
                Map.of("User", "${UserId}"))).isEmpty();
    }

    @Test
    void aFieldTheTableShowsNowIsComparedWithTheIndexedValues() {
        final Preview agrees = ExtractionPreview.preview(List.of(USER, ORG),
                List.of(sample(1, Map.of("User", "jsmith"), Map.of("UserId", "jsmith", "Organisation", "Fin")),
                        sample(2, Map.of("User", "akhan"), Map.of("UserId", "akhan", "Organisation", "Ops"))),
                Map.of("User", "${UserId}"), List.of("UserId", "EventTime"));

        assertThat(agrees.problems()).isEmpty();
        assertThat(agrees.notes()).anySatisfy(note -> assertThat(note)
                .startsWith("The data source has 'UserId' too"));
        assertThat(agrees.notes()).contains("'UserId' agrees with the indexed values column 'User' shows now, in "
                                            + "all 2 event(s).");

        final Preview differs = ExtractionPreview.preview(List.of(USER),
                List.of(sample(1, Map.of("User", "jsmith"), Map.of("UserId", "JSMITH"))),
                Map.of("User", "${UserId}"), List.of("UserId"));
        assertThat(differs.notes()).anySatisfy(note -> assertThat(note)
                .startsWith("'UserId' differs from the indexed values column 'User' shows now, in 1 of 1 "
                            + "event(s), e.g. 'jsmith' indexed, 'JSMITH' extracted"));
    }

    @Test
    void fieldsFoundInFewEventsOrOfTheWrongTypeAreFlagged() {
        final ExtractionField bytes = new ExtractionField("Bytes", "Size", "number");
        final ExtractionField when = new ExtractionField("When", "Time", "date");
        final Preview preview = ExtractionPreview.preview(List.of(ORG, bytes, when),
                List.of(sample(1, Map.of(), Map.of("Organisation", "Fin", "Bytes", "1,024", "When",
                                "2026-10-27T09:30:00.000Z")),
                        sample(2, Map.of(), Map.of("Bytes", "big", "When", "yesterday")),
                        sample(3, Map.of(), Map.of("When", "1761557400000"))),
                Map.of(), null);

        assertThat(preview.notes()).contains(
                "'Organisation' was found in 1 of 3 event(s).",
                "'Bytes' was found in 2 of 3 event(s).");
        assertThat(preview.notes()).anySatisfy(note -> assertThat(note)
                .startsWith("'Bytes' is a number, but 1 of its values aren't, e.g. 'big'"));
        assertThat(preview.notes()).anySatisfy(note -> assertThat(note)
                .startsWith("'When' is a date, but 1 of its values aren't, e.g. 'yesterday'")
                .contains("ISO 8601"));
    }

    @Test
    void aFieldFoundInNoEventIsFlaggedWithItsXPath() {
        final Preview preview = ExtractionPreview.preview(List.of(ORG),
                List.of(sample(1, Map.of(), Map.of())), Map.of(), null);

        assertThat(preview.notes()).anySatisfy(note -> assertThat(note)
                .startsWith("'Organisation' was found in none of the 1 event(s): check its XPath "
                            + "(EventSource/User/Organisation)"));
    }

    @Test
    void theTableShowsEachEventsValuesAndThoseNotStepped() {
        final Preview preview = ExtractionPreview.preview(List.of(ORG),
                List.of(sample(1, Map.of(), Map.of("Organisation", "A|B " + "x".repeat(80))),
                        new Sample(7, 2, Map.of(), null, List.of("Stepping did not finish in time"))),
                Map.of(), null);

        assertThat(preview.stepped()).isEqualTo(1);
        assertThat(preview.table()).startsWith("| StreamId | EventId | Organisation |\n|---|---|---|\n"
                                               + "| 7 | 1 | A\\|B xxx")
                .contains("…")
                .endsWith("| 7 | 2 | (not stepped) |");
        assertThat(preview.notes()).anySatisfy(note -> assertThat(note)
                .contains("7:2: Stepping did not finish in time"));
    }

    @Test
    void noEventSteppedMeansNoPreview() {
        final Preview preview = ExtractionPreview.preview(List.of(ORG),
                List.of(new Sample(7, 1, Map.of(), null, List.of("No stream 7"))), Map.of(), null);

        assertThat(preview.problems()).singleElement().asString()
                .startsWith("None of the events could be stepped")
                .contains("7:1: No stream 7");
    }

    @Test
    void theValuesAreReadAsTheSearchOutputReadsThem() {
        assertThat(ExtractionPreview.values("""
                <records xmlns="records:2"><record>
                  <data name="A" value=" 1 "/><data name="A" value="2"/><data name="B" value=""/>
                  <data value="no name"/><data name="C"><data name="D" value="4"/></data>
                </record></records>"""))
                .containsExactly(Map.entry("A", "1"), Map.entry("D", "4"));
        assertThat(ExtractionPreview.values("<records><bad")).isEmpty();
        assertThat(ExtractionPreview.values("")).isEmpty();
        // No entities from outside.
        assertThat(ExtractionPreview.values("<!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                                            + "<records><record><data name=\"A\" value=\"&x;\"/></record>"
                                            + "</records>")).doesNotContainValue("root:x:0:0");
    }

    @Test
    void anExpressionsFieldsAreFoundButNotAnAnnotations() {
        assertThat(ExtractionPreview.fieldsOf("concat(${UserId}, ${ Host }, ${annotation:Status})"))
                .containsExactly("UserId", "Host");
    }

    private static Sample sample(final long eventId,
                                 final Map<String, String> columns,
                                 final Map<String, String> extracted) {
        return new Sample(7, eventId, columns, extracted, List.of());
    }
}
