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

import stroom.mcp.impl.McpTestClient;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// survey_feed on a stream bigger than a part's budget, through Stroom (asked for by the user; seen in chat 163: a
/// 25,000-record stream surveyed to its 10,000th record, its two rarest kinds of event, near its end, never seen nor
/// classified): the user is asked whether to read deeper and how far; read to the end, the rare kind is found and
/// the survey is covered; stopped, the survey is covered as far as they chose, and they aren't asked again.
@SuppressWarnings("unchecked")
class TestE2eSurveyDepth extends AbstractE2eTest {

    private static final int RECORDS = 30_000;
    private static final int RARE_AT = 27_000;

    @Test
    void readToTheEndTheRareKindFurtherInIsFound() {
        final String build = "e2e-depth-end";
        createFeed(build, "E2E-DEPTH-END");
        final long raw = upload("E2E-DEPTH-END", sample());
        client.answeringWith(form -> answer(form, "Read to the end"));
        final Map<String, Object> survey = json("survey_feed", args("feed", "E2E-DEPTH-END", "build", build,
                "stream_ids", List.of(raw)));
        client.answeringWith(McpTestClient::agree);
        dump("survey_feed read to the end", survey);

        assertThat(client.forms().getLast().message()).startsWith("Survey of feed 'E2E-DEPTH-END': 1 part(s) read "
                                                                 + "only in part.");
        assertThat(String.valueOf(survey.get("shapes"))).contains("csrf_block");
        assertThat((List<Map<String, Object>>) survey.get("depth")).singleElement().satisfies(part ->
                assertThat(part).containsEntry("whole", true).containsEntry("records_read", (long) RECORDS));
        assertThat(survey.get("coverage")).isEqualTo("covered: every part read to its end");
    }

    @Test
    void stoppedTheSurveyIsCoveredAsFarAsTheUserChoseAndTheyArentAskedAgain() {
        final String build = "e2e-depth-stop";
        createFeed(build, "E2E-DEPTH-STOP");
        final long raw = upload("E2E-DEPTH-STOP", sample());
        final int before = client.forms().size();
        final Map<String, Object> survey = json("survey_feed", args("feed", "E2E-DEPTH-STOP", "build", build,
                "stream_ids", List.of(raw)));
        dump("survey_feed stopped", survey);

        // The first suggestion, Stop here, as the test client's user takes it.
        assertThat(client.forms()).hasSize(before + 1);
        assertThat(survey.get("saturated")).isEqualTo(true);
        assertThat((String) survey.get("coverage")).startsWith("covered as far as the user chose: [stream " + raw
                                                               + " part 1 read to ");
        json("survey_feed", args("feed", "E2E-DEPTH-STOP", "build", build, "stream_ids", List.of(raw)));
        assertThat(client.forms()).hasSize(before + 1);
    }

    // The suggestion starting so.
    private static ElicitResult answer(final ElicitFormRequest form, final String starting) {
        final Map<String, Object> field = (Map<String, Object>) ((Map<String, Object>) form.requestedSchema()
                .get("properties")).get("answer");
        if (field == null) {
            return McpTestClient.agree(form);
        }
        final String chosen = ((List<?>) field.get("examples")).stream()
                .map(String::valueOf)
                .filter(suggestion -> suggestion.startsWith(starting))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No suggestion starting '" + starting + "': " + field));
        return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("answer", chosen), null);
    }

    // Key=value logins, one a line, with one rare kind of event far past a part's budget of 10,000 records.
    private static String sample() {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < RECORDS; i++) {
            lines.add("time=2026-11-06T%02d:%02d:%02dZ action=%s user=u%d src=10.0.%d.%d".formatted(i / 3600 % 24,
                    i / 60 % 60, i % 60, i == RARE_AT
                            ? "csrf_block"
                            : "login", i % 7, i % 200, i % 250));
        }
        return String.join("\n", lines) + "\n";
    }
}
