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

package stroom.mcp.impl.e2e;

import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The raw streams an eval build is scored on ([AbstractEvalTest#coveredByFilters]), read from the processor filters
/// in a real Stroom: seen in case 66, the sample uploaded twice, the build's filter on one stream, and the other
/// counted by the scorer as passed over and as having no Events stream.
class TestE2eEvalBuildRaws extends AbstractEvalTest {

    private static final String SAMPLE = "{\"user\": \"alice\", \"action\": \"logon\"}\n";

    @Inject
    private ProcessorFilterService processorFilterService;

    @Test
    void aRawStreamOutsideTheBuildsFilterIsNotScored() {
        final String build = "eval-raws-1";
        final String feed = "EVAL-RAWS";
        createFeed(build, feed);
        final long kept = upload(feed, SAMPLE);
        final long again = upload(feed, SAMPLE);
        assertThat(coveredByFilters(List.of(kept, again))).as("no filter yet: every raw stream")
                .containsExactly(kept, again);

        final Map<String, Object> pipeline = createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (JSON)"));
        // The build's filter, as create_processor_filter makes one with stream_ids: the first stream's id.
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, kept)
                .build();
        asUser(admin, () -> processorFilterService.create(CreateProcessFilterRequest.builder()
                .processorType(ProcessorType.PIPELINE)
                .pipeline(new DocRef(PipelineDoc.TYPE, uuidOf(pipeline)))
                .queryData(QueryData.builder().dataSource(MetaFields.STREAM_STORE_DOC_REF).expression(expression)
                        .build())
                .enabled(false)
                .priority(10)
                .build()));

        assertThat(coveredByFilters(List.of(kept, again))).as("the build's filter on the first stream alone")
                .containsExactly(kept);
    }
}
