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

import stroom.mcp.impl.BundledKnowledge;
import stroom.mcp.impl.McpTestClient;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// The joins guide (`stroom://guide/joins`) gives facts the agent copies, so every StroomQL example in it runs
/// through the REAL run_stroomql on the data its examples name: eval case 71's Lucene index "Staff activity" and
/// Data Connection "HR" ([AbstractEvalTest#questionData]).
class TestE2eJoinsGuide extends AbstractEvalTest {

    private static final Pattern EXAMPLE = Pattern.compile("```text\n(.*?)```", Pattern.DOTALL);

    @Test
    void everyExampleInTheGuideRuns() {
        final String guide = new BundledKnowledge().guide("joins").orElseThrow();
        final List<String> examples = new ArrayList<>();
        final Matcher matcher = EXAMPLE.matcher(guide);
        while (matcher.find()) {
            examples.add(matcher.group(1).strip());
        }
        assertThat(examples).hasSizeGreaterThanOrEqualTo(4);

        final QuestionData data = questionData(EvalCases.load("71_stroomql_join_exports_by_department"));
        try {
            final List<String> failed = new ArrayList<>();
            for (final String example : examples) {
                final String result = McpTestClient.text(client.call("run_stroomql", Map.of("query", example)));
                if (!result.startsWith("Ran the query.")) {
                    failed.add(example + "\n  -> " + result);
                }
            }
            assertThat(failed).isEmpty();
        } finally {
            data.remove();
        }
    }
}
