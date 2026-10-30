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

package stroom.pipeline.stepping.client;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.stepping.client.PipelinePath.Step;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelinePath {

    // Source -> parser -> filter -> (writerA -> appenderA, writerB, writerC -> (appenderC1, appenderC2))
    private static final PipelineElement SOURCE = element("Source");
    private static final PipelineElement PARSER = element("parser");
    private static final PipelineElement FILTER = element("filter");
    private static final PipelineElement WRITER_A = element("writerA");
    private static final PipelineElement APPENDER_A = element("appenderA");
    private static final PipelineElement WRITER_B = element("writerB");
    private static final PipelineElement WRITER_C = element("writerC");
    private static final PipelineElement APPENDER_C1 = element("appenderC1");
    private static final PipelineElement APPENDER_C2 = element("appenderC2");

    private static final Map<PipelineElement, List<PipelineElement>> CHILDREN = Map.of(
            SOURCE, List.of(PARSER),
            PARSER, List.of(FILTER),
            FILTER, List.of(WRITER_A, WRITER_B, WRITER_C),
            WRITER_A, List.of(APPENDER_A),
            WRITER_C, List.of(APPENDER_C1, APPENDER_C2));

    private final Map<String, Severity> severities = new HashMap<>();
    private final Set<String> filtered = new HashSet<>();

    @Test
    void testFollow_firstChildByDefault() {
        final List<Step> steps = path().follow(null, Map.of());

        assertThat(elements(steps))
                .containsExactly(SOURCE, PARSER, FILTER, WRITER_A, APPENDER_A);
        assertThat(steps.get(3).alternatives())
                .containsExactly(WRITER_A, WRITER_B, WRITER_C);
        assertThat(steps.get(3).isAfterFork())
                .isTrue();
        // Only the element after the fork has alternatives
        assertThat(steps.get(2).isAfterFork())
                .isFalse();
        assertThat(steps.get(4).alternatives())
                .isEmpty();
    }

    @Test
    void testFollow_selectedBranch() {
        final List<Step> steps = path().follow(APPENDER_C2, Map.of(FILTER.getId(), WRITER_B.getId()));

        // The selection wins over the choice made at the fork
        assertThat(elements(steps))
                .containsExactly(SOURCE, PARSER, FILTER, WRITER_C, APPENDER_C2);
        assertThat(steps.get(4).alternatives())
                .containsExactly(APPENDER_C1, APPENDER_C2);
    }

    @Test
    void testFollow_rememberedChoice() {
        final List<Step> steps = path().follow(PARSER, Map.of(FILTER.getId(), WRITER_B.getId()));

        assertThat(elements(steps))
                .containsExactly(SOURCE, PARSER, FILTER, WRITER_B);
    }

    @Test
    void testFollow_choiceOfMissingChildIgnored() {
        final List<Step> steps = path().follow(null, Map.of(FILTER.getId(), "gone"));

        assertThat(elements(steps))
                .containsExactly(SOURCE, PARSER, FILTER, WRITER_A, APPENDER_A);
    }

    @Test
    void testFollow_worstBranch() {
        severities.put(WRITER_B.getId(), Severity.WARNING);
        severities.put(APPENDER_C1.getId(), Severity.ERROR);

        final List<Step> steps = path().follow(null, Map.of());

        // writerC's branch has an error, which is worse than writerB's warning
        assertThat(elements(steps))
                .containsExactly(SOURCE, PARSER, FILTER, WRITER_C, APPENDER_C1);
    }

    @Test
    void testFollow_infoDoesNotChooseBranch() {
        severities.put(WRITER_B.getId(), Severity.INFO);

        final List<Step> steps = path().follow(null, Map.of());

        assertThat(elements(steps))
                .containsExactly(SOURCE, PARSER, FILTER, WRITER_A, APPENDER_A);
    }

    @Test
    void testFollow_hiddenSeverityAndFilters() {
        severities.put(WRITER_B.getId(), Severity.WARNING);
        severities.put(APPENDER_C2.getId(), Severity.FATAL_ERROR);
        severities.put(WRITER_A.getId(), Severity.ERROR);
        filtered.add(APPENDER_C1.getId());

        final List<Step> steps = path().follow(WRITER_A, Map.of());

        final Step writerStep = steps.get(3);
        assertThat(writerStep.element())
                .isEqualTo(WRITER_A);
        // The worst of writerB's and writerC's branches; writerA's own error isn't hidden
        assertThat(writerStep.hiddenSeverity())
                .isEqualTo(Severity.FATAL_ERROR);
        assertThat(writerStep.hiddenFilters())
                .isTrue();
    }

    @Test
    void testFollow_nothingHidden() {
        filtered.add(WRITER_A.getId());

        final List<Step> steps = path().follow(WRITER_A, Map.of());

        assertThat(steps.get(3).hiddenSeverity())
                .isNull();
        assertThat(steps.get(3).hiddenFilters())
                .isFalse();
    }

    @Test
    void testFollow_rootOnly() {
        final PipelinePath path = new PipelinePath(SOURCE, element -> null, element -> null, element -> false);

        assertThat(elements(path.follow(SOURCE, Map.of())))
                .containsExactly(SOURCE);
    }

    @Test
    void testRememberPathTo() {
        final Map<String, String> choices = new HashMap<>();

        path().rememberPathTo(APPENDER_C2, choices);

        // Only the forks are recorded
        assertThat(choices)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        FILTER.getId(), WRITER_C.getId(),
                        WRITER_C.getId(), APPENDER_C2.getId()));
    }

    @Test
    void testRememberPathTo_unknownElement() {
        final Map<String, String> choices = new HashMap<>();

        path().rememberPathTo(element("unknown"), choices);
        path().rememberPathTo(null, choices);

        assertThat(choices)
                .isEmpty();
    }

    @Test
    void testRememberedChoiceKeptWhenSelectionMovesUp() {
        final Map<String, String> choices = new HashMap<>();
        final PipelinePath path = path();
        path.rememberPathTo(WRITER_B, choices);

        assertThat(elements(path.follow(SOURCE, choices)))
                .containsExactly(SOURCE, PARSER, FILTER, WRITER_B);
    }

    @Test
    void testBranchSeverityAndFilters() {
        severities.put(APPENDER_C1.getId(), Severity.WARNING);
        severities.put(WRITER_C.getId(), Severity.INFO);
        filtered.add(APPENDER_C2.getId());

        final PipelinePath path = path();

        assertThat(path.getBranchSeverity(WRITER_C))
                .isEqualTo(Severity.WARNING);
        assertThat(path.getBranchSeverity(WRITER_B))
                .isNull();
        assertThat(path.hasBranchFilters(WRITER_C))
                .isTrue();
        assertThat(path.hasBranchFilters(WRITER_A))
                .isFalse();
    }

    private PipelinePath path() {
        return new PipelinePath(
                SOURCE,
                CHILDREN::get,
                element -> severities.get(element.getId()),
                element -> filtered.contains(element.getId()));
    }

    private static List<PipelineElement> elements(final List<Step> steps) {
        return steps.stream()
                .map(Step::element)
                .toList();
    }

    private static PipelineElement element(final String id) {
        return new PipelineElement(id, "XMLWriter");
    }
}
