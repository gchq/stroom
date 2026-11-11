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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.Shapes;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `set_shape_handling` - records, in a build's survey doc, that some kinds of event are left untranslated on purpose,
/// or no longer are. The user confirms the change ([Consent]) before the doc is written.
class SetShapeHandlingTool extends AbstractBuiltInTool {

    static final String NAME = "set_shape_handling";
    static final String BUILD_ARG = "build";
    static final String FEED_ARG = "feed";
    static final String SHAPES_ARG = "shapes";
    static final String HANDLING_ARG = "handling";
    static final String REASON_ARG = "reason";
    static final String DROP = "drop";
    static final String TRANSLATE = "translate";
    static final String CONFIRM_ARG = "confirm";
    // The status of a result whose table is for the user as it is (Stroom AI's chat shows it itself).
    private static final String NEEDS_SHOWING = "needs_showing";

    private final SurveyDocuments surveyDocuments;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    SetShapeHandlingTool(final SurveyDocuments surveyDocuments, final Consent consent, final BuildPlan plan) {
        this.surveyDocuments = surveyDocuments;
        this.consent = consent;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean isReadOnly() {
        // It writes the build's survey doc.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms the change in a form before the survey doc is written.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The build's survey doc is changed as the user, who needs Edit on it.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Record, in the build's survey doc, that some kinds of event are left untranslated on "
                             + "purpose (or no longer are). Called first, it gives back a table of those kinds for "
                             + "the user; called again with confirm=true, the user confirms in a short form: "
                             + "dropping records means they never "
                             + "reach Events. Afterwards survey_feed marks those kinds and gives their locations "
                             + "expect='none', and step_records counts such a record as clean when it produces no "
                             + "Event (and flags it if it produces one). Translate them with a drop rule in the "
                             + "mapping (build_translation_xslt), so they don't reach the 'no mapping matched' "
                             + "warning.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "The build holding the feed's survey doc.")
                        .addStringProperty(FEED_ARG, "The surveyed feed.")
                        .addProperty(SHAPES_ARG, stringArraySchema("Kinds of event: their signatures from "
                                                                   + "survey_feed, or text found in exactly one "
                                                                   + "signature, e.g. 'loggerName=org.jgroups'."))
                        .addEnumProperty(HANDLING_ARG, List.of(DROP, TRANSLATE), "drop: the pipeline deliberately "
                                                                                 + "writes no Event for these "
                                                                                 + "records; translate: undo that.")
                        .addStringProperty(REASON_ARG, "Why, in the user's words, e.g. 'cluster housekeeping, no "
                                                       + "security value'.")
                        .addBooleanProperty(CONFIRM_ARG, "True once the table of these kinds (from the call "
                                                         + "without it) is shown: the user confirms in a short "
                                                         + "form.")
                        .required(BUILD_ARG, FEED_ARG, SHAPES_ARG, HANDLING_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(setHandling(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> setHandling(final ToolCall call, final ToolCallContext context) {
        final String build = required(call, BUILD_ARG);
        final String feed = required(call, FEED_ARG);
        final List<String> names = stringListArg(call, SHAPES_ARG);
        final String handling = stringArg(call, HANDLING_ARG);
        if (!DROP.equals(handling) && !TRANSLATE.equals(handling)) {
            throw new ToolRefusal("handling must be 'drop' or 'translate'");
        }
        final String reason = NullSafe.string(rawArg(call, REASON_ARG));
        final Optional<SurveyRecord> record = surveyDocuments.load(build, feed);
        final Map<String, Object> state = record.map(found -> SurveyDoc.readState(found.text())).orElse(null);
        if (state == null) {
            throw new ToolRefusal("No survey of '" + feed + "' in build '" + build + "': run survey_feed with "
                                  + "build first");
        }
        final List<String> signatures;
        signatures = SurveyDoc.resolve(state, names);
        if (DROP.equals(handling) && reason.isBlank()) {
            throw new ToolRefusal("Give the reason for leaving these records untranslated (the user's words)");
        }
        final Map<String, Map<String, Object>> shapes = (Map<String, Map<String, Object>>) state.get("shapes");
        final Map<String, Long> counts = new LinkedHashMap<>();
        shapes.forEach((signature, shape) -> counts.put(signature, ((Number) shape.get("count")).longValue()));
        final String verb = DROP.equals(handling)
                ? "Leave untranslated (no Events)"
                : "Translate again";
        if (!confirming(call)) {
            // The kinds as a table for the chat, the form after it short and plain (asked for by the user: seen, the
            // kinds run together in the form's message and cut short).
            return plan.withNext(context, build, shown(verb, feed, signatures, counts, context.session().isChat()));
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("feed", feed);
        details.put("build", build);
        details.put("reason", reason);
        details.put("kinds", signatures.size() + ", in the table shown");
        consent.require(call, context, Consent.Request.confirmation(NAME,
                verb + ": " + signatures.size() + " kind(s) of event in feed '" + feed + "'", details));
        SurveyDoc.setHandling(state, signatures, handling, reason.strip(), SurveyDoc.at(Instant.now()));
        surveyDocuments.write(context.session(), record.get(), SurveyDoc.render(state));
        final List<Map<String, Object>> left = new ArrayList<>();
        SurveyDoc.dropped(state).forEach((signature, why) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("shape", signature);
            entry.put("reason", why);
            left.add(entry);
        });
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        result.put("handling", handling);
        result.put("shapes", signatures);
        result.put("left_untranslated", left);
        // The survey's examples of these kinds, to step with step_records.
        result.put("locations", locations(shapes, signatures, DROP.equals(handling)));
        result.put("hint", DROP.equals(handling)
                ? "Add a drop rule for these kinds to the mapping (EventRule with drop=true and conditions that "
                  + "match them), regenerate, and step_records over the survey's locations: those records should "
                  + "produce no Event."
                : "Add rules to translate these kinds, regenerate and step_records over their locations.");
        return plan.withNext(context, build, result);
    }

    // The kinds of event, a row each, for the user to see before the form; Stroom AI's chat shows the table itself.
    private static Map<String, Object> shown(final String verb,
                                             final String feed,
                                             final List<String> signatures,
                                             final Map<String, Long> counts,
                                             final boolean shownByChat) {
        final List<String> lines = new ArrayList<>(List.of("## " + verb + ": kinds of event in '" + feed + "'", "",
                "| # | Kind of event | Records | Share |", "| --- | --- | --- | --- |"));
        for (int i = 0; i < signatures.size(); i++) {
            final String signature = signatures.get(i);
            lines.add("| " + (i + 1) + " | " + code(signature) + " | " + counts.get(signature) + " | "
                      + Shapes.share(counts, signature) + "% |");
        }
        final String form = "call set_shape_handling again with the same arguments and confirm=true: a short form "
                            + "asks the user to agree.";
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", NEEDS_SHOWING);
        result.put("table", String.join("\n", lines));
        result.put("hint", shownByChat
                ? "The chat shows the user this table itself, as it is: don't repeat or restyle it. Now " + form
                : "Show the table to the user in your reply, as it is, then " + form);
        return result;
    }

    // A signature in a table cell: as code (its <n> placeholders kept), its pipes escaped.
    private static String code(final String signature) {
        return "`" + signature.replace("`", "'").replace("|", "\\|").replace("\n", " ") + "`";
    }

    private boolean confirming(final ToolCall call) {
        final Object given = argsMap(call).get(CONFIRM_ARG);
        return Boolean.TRUE.equals(given) || "true".equals(String.valueOf(given));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> locations(final Map<String, Map<String, Object>> shapes,
                                                       final List<String> signatures,
                                                       final boolean drop) {
        final List<Map<String, Object>> locations = new ArrayList<>();
        for (final String signature : signatures) {
            for (final Map<String, Object> example : NullSafe.list(
                    (List<Map<String, Object>>) shapes.get(signature).get("examples"))) {
                final Map<String, Object> location = new LinkedHashMap<>(NullSafe.map(
                        (Map<String, Object>) example.get("location")));
                location.put("shape", signature);
                if (drop) {
                    location.put("expect", "none");
                }
                locations.add(location);
            }
        }
        return locations;
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }
}
