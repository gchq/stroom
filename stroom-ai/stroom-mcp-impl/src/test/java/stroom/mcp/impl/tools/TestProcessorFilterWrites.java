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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

/// The processing tools' filters: a new filter, the stream types a feed gives, and a filter the same as one already
/// there.
class TestProcessorFilterWrites {

    private ProcessingWorld w;

    @BeforeEach
    void setUp() {
        w = new ProcessingWorld();
    }

    @ParameterizedTest
    @CsvSource({
            // The processor Stroom made with this filter: enabled, so the filter is the one choice.
            "false, false, true",
            // One already there keeps its state,
            "true, false, false",
            // unless the user approved this run.
            "true, true, true"})
    void aNewFiltersDisabledProcessor(final boolean existing, final boolean enabled, final boolean enablesProcessor) {
        if (existing) {
            w.idFilter("p1", 5, 1);
        }
        w.processor = Processor.builder().id(70).enabled(false).build();

        w.filterWrites.createFilter(ProcessingWorld.P1, ExpressionOperator.builder().build(), 10, 1, null, enabled);

        if (enablesProcessor) {
            Mockito.verify(w.processorService).setEnabled(70, true);
        } else {
            Mockito.verify(w.processorService, Mockito.never()).setEnabled(anyInt(), anyBoolean());
        }
    }

    @Test
    void streamTypesComeFromTheFeed() {
        w.feedAt("System/Feeds", "ACME-USERS", "f", "Raw Reference");

        assertThat(w.filterWrites.feedStreamType("ACME-USERS")).isEqualTo("Raw Reference");
        assertThat(w.filterWrites.feedStreamType("NO-SUCH-FEED")).isEqualTo("Raw Events");
    }

    @Test
    void aFeedIsNamedByReferenceAndIsTheSameFilterAsOneNamingIt() {
        // Asked for by the user: Feed IS_DOC_REF, not EQUALS a name, so Stroom keeps the filter as a dependency of
        // the feed.
        w.feedAt("System/Feeds", "ACME", "feed-uuid", "Raw Events");

        final ExpressionTerm feed = w.filterWrites.feedTerm("ACME");

        assertThat(feed).isEqualTo(ExpressionTerm.builder().field("Feed").condition(Condition.IS_DOC_REF)
                .docRef(new DocRef("Feed", "feed-uuid", "ACME")).build());
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND).addTerm(feed).build();
        final ProcessorFilter byName = w.filter("tp", 9, true, ExpressionOperator.builder().op(Op.AND)
                .addTerm(ProcessorFilterWrites.term("Feed", "ACME")).build(), null);
        assertThatThrownBy(() -> ProcessorFilterWrites.refuseDuplicate(List.of(byName), expression, null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("already selects these streams");
        assertThatThrownBy(() -> w.filterWrites.feedTerm("NO-SUCH-FEED"))
                .hasMessageStartingWith("No feed named 'NO-SUCH-FEED' in this Stroom. If the user named it, they "
                                        + "may mean a different Stroom cluster");
    }

    @Test
    void aFiltersFeedsAndStreamsAreReadAtAnyDepth() {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTerm(ProcessorFilterWrites.term("Id", 6))
                        .addTerm(ProcessorFilterWrites.term("Id", "x"))
                        .build())
                .addTerm(ExpressionTerm.builder().field("Feed").condition(Condition.IS_DOC_REF)
                        .docRef(new DocRef("Feed", "f", "ACME")).build())
                .addTerm(ProcessorFilterWrites.term("Feed", "OLD-ACME"))
                .addTerm(ProcessorFilterWrites.pipelineTerm(ProcessingWorld.EV))
                .build();

        assertThat(ProcessorFilterWrites.selectedIds(expression)).containsExactly(6L);
        assertThat(ProcessorFilterWrites.pipelineUuids(ProcessorFilterWrites.pipelineTerms(expression)))
                .containsExactly("ev");
        assertThat(ProcessorFilterWrites.namesPipeline(expression)).isTrue();
    }

    @Test
    void createdAfterIsAnIsoTime() {
        assertThat(ProcessorFilterWrites.isoMs("2026-10-09T21:06:40Z")).isEqualTo(ProcessingWorld.FEED_MADE);
        assertThat(ProcessorFilterWrites.isoMs("2026-10-09T21:06:40")).isEqualTo(ProcessingWorld.FEED_MADE);
        assertThatThrownBy(() -> ProcessorFilterWrites.isoMs("yesterday"))
                .hasMessage("created_after 'yesterday' is not an ISO time, e.g. 2026-10-09T21:30:00Z");
    }
}
