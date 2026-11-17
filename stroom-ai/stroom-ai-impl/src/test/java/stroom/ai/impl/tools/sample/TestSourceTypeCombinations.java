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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Pairs of readings inferred only where the records' shapes justify them (decided 2026-11-17): offered first, with
/// the evidence and the rows proposed, where no single reading separates the shapes and each part splits the other's
/// values into families; never where a single reading does, where the second part's values share their shapes
/// (they collapse to `*`), or where the second part is kind-like.
class TestSourceTypeCombinations {

    private static final String PAIR = "system + event>logger_name";

    @Test
    void thePairIsOfferedFirstWhereOnlyBothFieldsTogetherTellTheShapesApart() {
        final List<SourceTypeFields.Candidate> found = SourceTypeFields.candidates(CombinationFixtures.byPair(8),
                false);
        final SourceTypeFields.Candidate pair = found.getFirst();

        assertThat(pair.field()).isEqualTo(PAIR);
        assertThat(pair.combination()).isTrue();
        assertThat(pair.held()).isTrue();
        assertThat(pair.values()).containsExactlyInAnyOrder("Teams ‖ System", "Teams ‖ Application",
                "Skype for Business ‖ System", "Skype for Business ‖ Application");
        assertThat(pair.noValue()).isZero();
        assertThat(pair.separation()).isEqualTo(1.0);
        // The evidence: each part splits the other's values into families of their own.
        assertThat(pair.combined().evidence()).hasSize(3);
        assertThat(pair.combined().evidence().get(0)).startsWith("within system = ")
                .contains("event>logger_name", "have shapes of their own");
        assertThat(pair.combined().evidence().get(1)).startsWith("within event>logger_name = ")
                .contains("system", "Teams", "Skype for Business");
        // A row a pair, each part exactly: the shapes differ by both.
        assertThat(pair.combined().rows()).extracting(row -> row.row().name() + " " + row.row().said())
                .containsExactly("Skype for Business Application = Skype for Business + = Application",
                        "Skype for Business System = Skype for Business + = System",
                        "Teams Application = Teams + = Application", "Teams System = Teams + = System");
        assertThat(pair.combined().rows()).allSatisfy(row -> assertThat(row.records()).isEqualTo(8));
        // The single readings are still offered after it.
        assertThat(found).extracting(SourceTypeFields.Candidate::field).contains("system");
    }

    @Test
    void aSecondFieldWhoseValuesShareTheirShapesWithinTheFirstsMakesNoPairAndCollapsesToAny() {
        final List<Map<String, Object>> records = CombinationFixtures.records(8,
                CombinationFixtures.Variant.SAME_SHAPE);

        final List<SourceTypeFields.Candidate> found = SourceTypeFields.candidates(records, false);

        assertThat(found).noneMatch(SourceTypeFields.Candidate::combination);
        assertThat(found.getFirst().field()).isEqualTo("system");
        // Given by the user, the pair's rows collapse the loggers to * within each system.
        assertThat(SourceTypeCombinations.propose(records, PAIR)).extracting(row -> row.row().said())
                .containsExactlyInAnyOrder("= Teams + *", "= Skype for Business + *");
    }

    @Test
    void aKindLikeSecondFieldIsNeverAPart() {
        // By its name: the same shapes, but the inner field is an event type.
        final List<Map<String, Object>> named = CombinationFixtures.byPair(8).stream()
                .map(record -> {
                    final Map<String, Object> copy = new LinkedHashMap<>(record);
                    copy.put("event", ((String) record.get("event")).replace("logger_name", "event_type"));
                    return copy;
                })
                .toList();
        assertThat(SourceTypeFields.candidates(named, true)).noneMatch(candidate -> candidate.combination()
                                                                                    || candidate.field()
                                                                                            .contains("event_type"));
        // By its values: each a single shape, kinds of event within a source.
        final List<SourceTypeFields.Candidate> coded = SourceTypeFields.candidates(CombinationFixtures.records(8,
                CombinationFixtures.Variant.KIND_CODES), true);
        assertThat(coded).noneMatch(SourceTypeFields.Candidate::combination);
    }

    @Test
    void aSingleFieldSeparatingWellMakesNoPair() {
        // The logger's names say the system too: the logger alone tells every source.
        final List<SourceTypeFields.Candidate> found = SourceTypeFields.candidates(CombinationFixtures.records(8,
                CombinationFixtures.Variant.UNIQUE_LOGGERS), false);

        assertThat(found).noneMatch(SourceTypeFields.Candidate::combination);
        assertThat(found).extracting(SourceTypeFields.Candidate::field).contains("event>logger_name");
    }

    @Test
    void tooFewRecordsMakeNoPair() {
        assertThat(SourceTypeCombinations.pair(CombinationFixtures.byPair(2), List.of(), 0.6, true)).isEmpty();
    }

    @Test
    void noPairIsOfferedOnAFeedThatDoesntLookAggregatedUnlessTheUserSaysItIs() {
        // No single reading offered: a single source's records, as far as the head shows.
        assertThat(SourceTypeCombinations.pair(CombinationFixtures.byPair(8), List.of(), 0.6, false)).isEmpty();
        assertThat(SourceTypeCombinations.pair(CombinationFixtures.byPair(8), List.of(), 0.6, true))
                .hasValueSatisfying(pair -> assertThat(pair.field()).isEqualTo(PAIR));
    }

    @Test
    void aLevelIsNeverAPart() {
        // The inner field is a severity: its values may differ in shape (errors carry more), but never say a source.
        final List<Map<String, Object>> levels = CombinationFixtures.byPair(8).stream()
                .map(record -> {
                    final Map<String, Object> copy = new LinkedHashMap<>(record);
                    copy.put("event", ((String) record.get("event")).replace("logger_name", "severity"));
                    return copy;
                })
                .toList();

        assertThat(SourceTypeFields.candidates(levels, true)).noneMatch(SourceTypeFields.Candidate::combination);
    }

    @Test
    void aPartWithNoValueFallsToTheNoValueRowUnlessAnyTakesIt() {
        final List<Map<String, Object>> records = new ArrayList<>(CombinationFixtures.byPair(8));
        final Map<String, Object> plain = new LinkedHashMap<>(records.getFirst());
        plain.put("event", "Teams started on the portal after a restart");
        records.add(plain);

        final List<SourceTypeRows.Proposed> rows = SourceTypeCombinations.propose(records, PAIR);
        final List<SourceTypeRows.Row> ordered = new ArrayList<>(rows.stream()
                .map(SourceTypeRows.Proposed::row).toList());
        ordered.add(SourceTypeRows.noValue());
        final String key = SourceTypeReading.of(PAIR).valueOf(plain);

        assertThat(key).isEqualTo("Teams ‖ ");
        assertThat(SourceTypeRows.first(ordered, key)).map(SourceTypeRows.Row::name)
                .contains(SourceTypeRows.NO_VALUE);
        ordered.addFirst(new SourceTypeRows.Row("Teams any", SourceTypeRows.TUPLE, "= Teams + *"));
        assertThat(SourceTypeRows.first(ordered, key)).map(SourceTypeRows.Row::name).contains("Teams any");
    }
}
