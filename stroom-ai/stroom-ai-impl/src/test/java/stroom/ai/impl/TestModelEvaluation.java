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

package stroom.ai.impl;

import stroom.ai.impl.ModelEvaluationWorld.Run;
import stroom.ai.impl.ModelEvaluationWorld.ToolCall;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Runs real prompts against a real model through the real agent loop (see [ModelEvaluationWorld]), to compare the
/// tools the agent chooses, and what it answers, before and after a change. It is not part of the build: it needs
/// a model, costs tokens, and a model's answers vary from run to run.
///
/// Run with `-DrunModelEvaluation=true`, the model given by `-DtestAiEvalBaseUrl` (an OpenAI-compatible API, e.g.
/// `https://api.openai.com/v1/`) and `-DtestAiEvalModel`, and its API key in the `STROOM_AI_EVAL_API_KEY`
/// environment variable. `-DtestAiEvalScenarios=a,b` runs only the scenarios named. The scenarios are in
/// `stroom/ai/impl/eval/scenarios.json`. A report of each scenario's tool calls and checks is written to
/// `build/ai-eval/` (or `-DtestAiEvalReportDir`), and the test fails if any check failed.
@EnabledIfSystemProperty(named = "runModelEvaluation", matches = "true")
class TestModelEvaluation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestModelEvaluation.class);

    static final String SCENARIOS_RESOURCE = "/stroom/ai/impl/eval/scenarios.json";
    static final String API_KEY_ENV = "STROOM_AI_EVAL_API_KEY";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int RESULT_EXCERPT_CHARS = 300;

    @TempDir
    Path tempDir;

    @Test
    void runScenarios() throws IOException {
        final String baseUrl = requiredProperty("testAiEvalBaseUrl");
        final String modelName = requiredProperty("testAiEvalModel");
        final ChatModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(Objects.requireNonNullElse(System.getenv(API_KEY_ENV), "none"))
                .modelName(modelName)
                .timeout(Duration.ofMinutes(5))
                .build();
        final Set<String> only = Arrays.stream(System.getProperty("testAiEvalScenarios", "").split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());

        final List<ScenarioResult> results = new ArrayList<>();
        for (final Scenario scenario : readScenarios().scenarios()) {
            if (!only.isEmpty() && !only.contains(scenario.name())) {
                continue;
            }
            LOGGER.info("Running scenario {}", scenario.name());
            results.add(run(model, scenario));
        }

        final Path reportDir = Path.of(System.getProperty("testAiEvalReportDir", "build/ai-eval"));
        final Report report = new Report(modelName, baseUrl, ZonedDateTime.now(ZoneOffset.UTC).toString(), results);
        final Path written = writeReport(reportDir, report);
        LOGGER.info("Model evaluation report written to {}", written.toAbsolutePath());

        assertThat(results)
                .as("Scenarios with failed checks (see %s)", written.toAbsolutePath())
                .filteredOn(result -> !result.passed())
                .extracting(ScenarioResult::name)
                .isEmpty();
    }

    /// Runs a scenario in a world of its own and checks what happened.
    ///
    /// @param model    The model.
    /// @param scenario The scenario.
    /// @return What happened, and the result of each check.
    ScenarioResult run(final ChatModel model, final Scenario scenario) throws IOException {
        final Path dir = Files.createTempDirectory(tempDir, scenario.name());
        final List<AppPermission> permissions = Objects.requireNonNullElse(scenario.appPermissions(),
                List.<String>of()).stream().map(AppPermission::valueOf).toList();
        final ModelEvaluationWorld world = new ModelEvaluationWorld(dir, permissions);
        final Run run = world.ask(model, scenario.prompt(), scenario.tab());
        final List<CheckResult> checks = check(scenario, run);
        return new ScenarioResult(scenario.name(), scenario.prompt(),
                run.toolCalls().stream().map(ToolCall::tool).toList(),
                run.toolCalls().stream().map(call -> new ToolCallSummary(call.tool(), call.arguments(),
                        excerpt(call.result()))).toList(),
                run.answer(), run.stoppedBecause(), run.durationMs(), checks,
                checks.stream().allMatch(CheckResult::passed));
    }

    /// @param scenario A scenario.
    /// @param run      What happened when it was run.
    /// @return The result of each of its checks, the expected tools first.
    static List<CheckResult> check(final Scenario scenario, final Run run) {
        final List<CheckResult> results = new ArrayList<>();
        final List<String> called = run.toolCalls().stream().map(ToolCall::tool).toList();
        for (final String expected : Objects.requireNonNullElse(scenario.expectedTools(), List.<String>of())) {
            final List<String> options = Arrays.stream(expected.split("\\|")).map(String::strip).toList();
            results.add(new CheckResult("toolCalled " + expected, options.stream().anyMatch(called::contains),
                    "called: " + called));
        }
        final String outcome = outcome(run);
        for (final Check check : Objects.requireNonNullElse(scenario.checks(), List.<Check>of())) {
            results.add(check(check, run, outcome));
        }
        return results;
    }

    private static CheckResult check(final Check check, final Run run, final String outcome) {
        final String value = Objects.requireNonNullElse(check.value(), "");
        final String name = check.type() + (check.tool() != null
                ? " " + check.tool()
                : "") + " " + value;
        return switch (check.type()) {
            case "outcomeContains" -> new CheckResult(name, outcome.contains(value), null);
            case "outcomeDoesNotContain" -> new CheckResult(name, !outcome.contains(value), null);
            case "outcomeMatches" -> new CheckResult(name, Pattern.compile(value).matcher(outcome).find(), null);
            case "toolCalled" -> new CheckResult(name, callsOf(run, check.tool()).findAny().isPresent(), null);
            case "toolNotCalled" -> new CheckResult(name, callsOf(run, check.tool()).findAny().isEmpty(), null);
            case "toolArgumentsMatch" -> new CheckResult(name, callsOf(run, check.tool())
                    .anyMatch(call -> Pattern.compile(value).matcher(Objects.toString(call.arguments(), "")).find()),
                    null);
            case "noToolArgumentsMatch" -> new CheckResult(name, callsOf(run, check.tool())
                    .noneMatch(call -> Pattern.compile(value).matcher(Objects.toString(call.arguments(), "")).find()),
                    null);
            default -> new CheckResult(name, false, "Unknown check type '" + check.type() + "'");
        };
    }

    private static Stream<ToolCall> callsOf(final Run run, final String tool) {
        return run.toolCalls().stream().filter(call -> call.tool().equals(tool));
    }

    /// @param run What happened when a scenario was run.
    /// @return What the user would see: the answer, and any questions the agent asked them with `ask_user`.
    static String outcome(final Run run) {
        final StringBuilder sb = new StringBuilder(Objects.requireNonNullElse(run.answer(), ""));
        run.toolCalls().stream()
                .filter(call -> "ask_user".equals(call.tool()))
                .forEach(call -> sb.append('\n').append(call.arguments()));
        return sb.toString();
    }

    private static Scenarios readScenarios() throws IOException {
        try (final InputStream in = TestModelEvaluation.class.getResourceAsStream(SCENARIOS_RESOURCE)) {
            assertThat(in).as(SCENARIOS_RESOURCE).isNotNull();
            return JsonUtil.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), Scenarios.class);
        }
    }

    private static Path writeReport(final Path dir, final Report report) {
        try {
            Files.createDirectories(dir);
            final String stamp = FILE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
            Files.writeString(dir.resolve("report-" + stamp + ".json"), JsonUtil.writeValueAsString(report, true),
                    StandardCharsets.UTF_8);
            final Path markdown = dir.resolve("report-" + stamp + ".md");
            Files.writeString(markdown, markdown(report), StandardCharsets.UTF_8);
            return markdown;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @param report A report.
    /// @return It as Markdown: a summary table, then each scenario's tool calls, answer and checks.
    static String markdown(final Report report) {
        final StringBuilder sb = new StringBuilder("# Model evaluation\n\n")
                .append("Model `").append(report.model()).append("` at ").append(report.baseUrl())
                .append(", ").append(report.ranAt()).append(".\n\n")
                .append("| Scenario | Passed | Tools called | Time |\n| --- | --- | --- | --- |\n");
        for (final ScenarioResult result : report.results()) {
            sb.append("| ").append(result.name())
                    .append(" | ").append(result.passed()
                            ? "yes"
                            : "**no**")
                    .append(" | ").append(String.join(", ", result.toolsCalled()))
                    .append(" | ").append(result.durationMs() / 1000).append("s |\n");
        }
        for (final ScenarioResult result : report.results()) {
            sb.append("\n## ").append(result.name()).append("\n\n> ").append(result.prompt()).append("\n\n");
            sb.append("Tool calls:\n");
            if (result.toolCalls().isEmpty()) {
                sb.append("- (none)\n");
            }
            result.toolCalls().forEach(call -> sb.append("- `").append(call.tool()).append("` ")
                    .append(oneLine(call.arguments())).append('\n'));
            if (result.stoppedBecause() != null) {
                sb.append("\nStopped: ").append(result.stoppedBecause()).append('\n');
            }
            sb.append("\nAnswer:\n\n").append(Objects.requireNonNullElse(result.answer(), "(none)").strip())
                    .append("\n\nChecks:\n");
            result.checks().forEach(check -> sb.append("- ").append(check.passed()
                            ? "PASS "
                            : "FAIL ").append(check.check())
                    .append(check.detail() == null
                            ? ""
                            : " (" + check.detail() + ")")
                    .append('\n'));
        }
        return sb.toString();
    }

    private static String excerpt(final String text) {
        if (text == null) {
            return null;
        }
        return text.length() > RESULT_EXCERPT_CHARS
                ? text.substring(0, RESULT_EXCERPT_CHARS) + "…"
                : text;
    }

    private static String oneLine(final String text) {
        return Objects.requireNonNullElse(text, "").replaceAll("\\s+", " ");
    }

    private static String requiredProperty(final String name) {
        final String value = System.getProperty(name);
        assertThat(value).as("System property %s", name).isNotBlank();
        return value;
    }


    // --------------------------------------------------------------------------------


    /// The scenarios file.
    ///
    /// @param scenarios The scenarios, in the order to run them.
    record Scenarios(List<Scenario> scenarios) {

    }

    /// A prompt to run, with what is expected of it.
    ///
    /// @param name           A short name, used in the report and to pick scenarios to run.
    /// @param description    What it tests.
    /// @param prompt         The user's question.
    /// @param tab            The tab attached with it (see [ModelEvaluationWorld#ask]), or null.
    /// @param appPermissions The application permissions the user has, as [AppPermission] names.
    /// @param expectedTools  The tools to be called, each at least once; `a|b` for either.
    /// @param checks         Checks on what happened.
    record Scenario(String name,
                    String description,
                    String prompt,
                    String tab,
                    List<String> appPermissions,
                    List<String> expectedTools,
                    List<Check> checks) {

    }

    /// A check on what happened in a scenario. The outcome is the answer with any questions asked with ask_user.
    ///
    /// @param type  One of `outcomeContains`, `outcomeDoesNotContain`, `outcomeMatches` (a regular expression),
    ///              `toolCalled`, `toolNotCalled`, `toolArgumentsMatch` and `noToolArgumentsMatch` (a regular
    ///              expression over a call's arguments).
    /// @param tool  The tool, for the tool checks.
    /// @param value The text or regular expression.
    record Check(String type, String tool, String value) {

    }

    /// The report of a run of the scenarios.
    record Report(String model, String baseUrl, String ranAt, List<ScenarioResult> results) {

    }

    /// What happened in a scenario.
    record ScenarioResult(String name,
                          String prompt,
                          List<String> toolsCalled,
                          List<ToolCallSummary> toolCalls,
                          String answer,
                          String stoppedBecause,
                          long durationMs,
                          List<CheckResult> checks,
                          boolean passed) {

    }

    /// A tool call, with the start of what it returned.
    record ToolCallSummary(String tool, String arguments, String resultExcerpt) {

    }

    /// The result of a check.
    ///
    /// @param check  The check, in words.
    /// @param passed True if it passed.
    /// @param detail More about it, or null.
    record CheckResult(String check, boolean passed, String detail) {

    }
}
