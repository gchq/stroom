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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/// The indexing tools' models given as arguments.
class TestIndexingArgs {

    @Test
    void aPartialPlanIsRefusedSayingWhatIsMissingAndThatLeavingItOutUsesAPlanThereIs() {
        // As save_xslt's index_plan, seen in a Haiku eval run: the validator's own wording named neither the key
        // missing nor that the argument could be left out.
        final ToolCall call = BuildToolCalls.call("create_index_doc",
                "{\"plan\": {\"backend\": \"lucene\", \"index_name\": \"acme\", \"time_field\": \"EventTime\"}}");

        final ToolRefusal refusal = catchThrowableOfType(ToolRefusal.class, () -> IndexingArgs.plan(call, "plan"));

        assertThat(refusal.getMessage()).doesNotContain("validation error").doesNotContain("Field required")
                .startsWith("plan isn't a valid field plan: `fields` is missing. Leave plan out to use the plan "
                            + "kept with the indexing XSLT of the pipeline named, else the one "
                            + "draft_index_mapping drafted or amended in this session");
    }

    @Test
    void noPlanGivenIsNone() {
        assertThat(IndexingArgs.plan(BuildToolCalls.call("create_index_doc", "{}"), "plan")).isNull();
    }
}
