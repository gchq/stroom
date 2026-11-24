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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.TestAbortedException;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The evaluation set run with its reference solutions: for each of its cases (`eval/*.yaml`, [EvalCases]), the
/// case's own converter or Data Splitter and field mapping go through the REAL tools on a real Stroom the way an agent
/// would, with no model ([AbstractEvalTest#runReference]).
///
/// A case passes when its Events hold what it expects, they are indexed and found, and nothing else was found
/// wrong on the way. Each case's score is written to `build/eval-reference/<case>.json`. All 61 builds take about 25
/// minutes; `STROOM_EVAL_CASES=12,35` (or `-DtestEvalCases=12,35`) runs only those.
///
/// A case that starts from another (`setup`, [AbstractEvalTest#setUp]) has that case's reference run and promoted
/// first, then its own reference build on top, scored with what it changed of the setup's build.
///
/// A question (`kind: question`, cases 62 to 64, 71 to 75, 77 and 78) builds nothing: its data is put in Stroom (an
/// Elasticsearch index, a Lucene index, Data Connections) and its reference tool calls are made, e.g. ES|QL with
/// esql_query, a join with run_stroomql, a sequence with sequence_query, a user's timeline with activity_timeline
/// ([AbstractEvalTest#runQuestionReference]),
/// the answer scored against the one the case gives, worked out by `esql/proxy-uploads-generator.py` or by brute
/// force ([TestEvalCases]). A question on Elasticsearch is skipped without an Elasticsearch node.
///
/// `create_index_doc` adds the fields of the plan drafted for the index in the session, so nothing else sets them;
/// the processor filters aren't deleted after each case, as each case starts from an empty Stroom.
class TestE2eEvalReference extends AbstractEvalTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestE2eEvalReference.class);

    private static final Path RESULTS = Path.of("build", "eval-reference");

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theReferenceSolutionReachesIndexedEvents(final String id) {
        final Map<String, Object> evalCase = EvalCases.load(id);
        final EvalScore score = new EvalScore(id);
        final String tag = id.split("_", 2)[0];
        final String feed = "EVAL-" + tag;
        final long startMs = System.currentTimeMillis();
        try {
            if (EvalCases.isQuestion(evalCase)) {
                runQuestionReference(evalCase, score);
            } else {
                final Setup setup = setUp(evalCase, tag, feed);
                runReference(evalCase, score, "eval-" + tag, feed, setup);
            }
        } catch (final TestAbortedException e) {
            // No Elasticsearch node for a question's index: skipped, not failed.
            throw e;
        } catch (final AssertionError | RuntimeException e) {
            // A step failing is the case's problem.
            LOGGER.info(() -> id + " failed at a step", e);
            score.problem(e.getClass().getSimpleName() + ": " + firstLines(e.getMessage()));
        }
        score.note("ran in " + (System.currentTimeMillis() - startMs) / 1000 + " s");
        write(RESULTS, id, score.toMap());
        LOGGER.info(score::summary);
        assertThat(score.passed()).as(score.summary()).isTrue();
    }
}
