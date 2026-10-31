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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The processor filters the build tools read: what each pipeline's filters read, and who may read them.
class TestProcessorFilters {

    private final ProcessorFilterAccess processorFilterAccess = Mockito.mock(ProcessorFilterAccess.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final ProcessorFilters processorFilters = new ProcessorFilters(processorFilterAccess,
            () -> Mockito.mock(ProcessorFilterService.class), documentAccess,
            new PipelineFeeds(processorFilterAccess, documentAccess, Mockito.mock(StreamReader.class)));

    @Test
    void aPipelinesFeedsAreReadAsPipelineFeedsReadsThem() {
        // Before, only a top-level Feed term's value (or feed doc's name) was read: find_reference_data and
        // review_coverage missed the feeds a dictionary names, and those listed between commas, as
        // PipelineFeeds.filterFeeds says it once did (6 of 40 production filters).
        Mockito.when(documentAccess.read(Mockito.any(DocRef.class), Mockito.eq(DictionaryDoc.class)))
                .thenReturn(Optional.of(DictionaryDoc.builder().uuid("d1").name("Firewall feeds")
                        .data("FORTIGATE-V1\nPaloAlto-V2\n").build()));
        final ProcessorFilter byDictionary = withPipeline(TestPipelineFeeds.filter(1, true,
                TestPipelineFeeds.term("Feed", Condition.IN_DICTIONARY, null,
                        new DocRef(DictionaryDoc.TYPE, "d1", "Firewall feeds"))), "fw");
        final ProcessorFilter byList = withPipeline(TestPipelineFeeds.filter(2, true,
                TestPipelineFeeds.term("Feed", Condition.IN, "ACME-A,ACME-B", null)), "acme");
        final ProcessorFilter byRef = withPipeline(TestPipelineFeeds.filter(3, true,
                TestPipelineFeeds.term("Feed", Condition.IS_DOC_REF, null, new DocRef("Feed", "f9", "ACME-VPN"))),
                "acme");

        final Map<String, Set<String>> feeds = processorFilters.feedsByPipeline(List.of(byDictionary, byList,
                byRef));

        assertThat(feeds.get("fw")).containsExactly("FORTIGATE-V1", "PaloAlto-V2");
        assertThat(feeds.get("acme")).containsExactly("ACME-A", "ACME-B", "ACME-VPN");
    }

    @Test
    void whoMayManageProcessorsIsProcessorFilterAccesssSay() {
        Mockito.when(processorFilterAccess.canManageProcessors()).thenReturn(true, false);

        assertThat(processorFilters.canManageProcessors()).isTrue();
        assertThat(processorFilters.canManageProcessors()).isFalse();
    }

    private static ProcessorFilter withPipeline(final ProcessorFilter filter, final String pipelineUuid) {
        return filter.copy().pipelineUuid(pipelineUuid).build();
    }
}
