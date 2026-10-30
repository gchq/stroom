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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.ElicitationSchema;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/// Confirmations (key details) and approvals (risky actions) before a write tool acts, and choices the user makes
/// in a form (ported from the Stroom MCP server's `utils/consent.py`).
///
/// The user is always asked directly, as a form ([UserQuestions#canAsk()]), so the model never holds the answer:
/// in the chat the run stops while the form waits, and the same call is made again with the answer (so a tool that
/// asks must be safe to re-run up to its question); over `/mcp` the call waits on the client's elicitation. Where
/// the user can't be asked (an MCP client that can't show forms, a chat the user is away from), nothing is done and
/// the tool is refused, saying why (see [FormsRequired]). Unlike the Stroom MCP server, no pending id is ever handed
/// to the model to pass back once the user agrees: a model holding one could approve its own call.
///
/// What the user settled on in the call's forms ([#edited], [#changes]) is kept for the call, for the tool and for
/// [BuildPlan#withNext] to read.
@Singleton
public class Consent {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Consent.class);

    /// The most characters a form's message holds.
    public static final int FORM_MESSAGE_LIMIT = 600;
    // The most a form's message runs to with its documents' links, well inside what a form may hold (2,000).
    private static final int LINKED_MESSAGE_LIMIT = 1_500;
    // Characters of one detail's value in a form.
    private static final int DETAIL_LIMIT = 160;
    private static final int HEADING_LIMIT = 150;

    private final Provider<AiAuditLog> auditLogProvider;
    // What each call's forms settled, kept while the call's context is.
    private final Map<ToolCallContext, CallState> calls = Collections.synchronizedMap(new WeakHashMap<>());

    /// @param auditLogProvider Records each request, grant and refusal.
    @Inject
    public Consent(final Provider<AiAuditLog> auditLogProvider) {
        this.auditLogProvider = auditLogProvider;
    }

    /// Asks the user, in a form, to agree before the tool acts, and returns once they have.
    ///
    /// @param call    The call asking, whose id tells its forms apart in a chat.
    /// @param context The call's context: how to ask the user, and the session.
    /// @param request What is asked.
    /// @throws ToolRefusal if the user did not agree, or can't be asked a form now (see [FormsRequired]): nothing
    ///                     is to be done.
    /// @throws stroom.ai.impl.AiChatRunManager.WaitingForUserException in a chat, while the form waits for the
    ///                                                                 user: the call is made again with the
    ///                                                                 answer.
    public void require(final ToolCall call,
                        final ToolCallContext context,
                        final Request request) {
        final CallState state = state(context);
        state.edits.clear();
        if (!context.userQuestions().canAsk()) {
            audit(context.session(), request.kind().word(), request, "refused: no form can be shown", false);
            throw new ToolRefusal(FormsRequired.cannotAsk(context.session(), call.name(), request.summary()));
        }
        ask(call, context, request, digest(request.action(), request.details()), state);
    }

    /// The option the user picks in a form. Seen: an agent given the choices asked in the chat, on Gemma and on
    /// Claude alike, with no picker. Cancelling the form stops the call, as declining a confirmation does.
    ///
    /// @param call     The call asking.
    /// @param context  The call's context.
    /// @param action   The tool asking, e.g. `create_feed`.
    /// @param question The question.
    /// @param options  The options to choose from.
    /// @param title    The question as its answer's heading, where the question itself is long, or null for the
    ///                 question.
    /// @return The option picked; empty when the session can't show a form now, or the form would be too large to
    /// show (the agent then asks the user), or the answer is not one of the options.
    /// @throws ToolRefusal if the user made no choice.
    public Optional<String> choose(final ToolCall call,
                                   final ToolCallContext context,
                                   final String action,
                                   final String question,
                                   final List<String> options,
                                   final String title) {
        if (NullSafe.isEmptyCollection(options) || !context.userQuestions().canAsk()) {
            return Optional.empty();
        }
        final String key = "choice-" + XsltCode.sha256(action + "\n" + question).substring(0, 16);
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("title", heading(NullSafe.nonBlankStringElse(title, question)));
        field.put("enum", List.copyOf(options));
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("choice"));
        schema.put("properties", Map.of("choice", field));
        if (unshowable(context, action, question, schema)) {
            return Optional.empty();
        }
        final ElicitResult answer = context.userQuestions().ask(call.name(),
                UserQuestions.formCallId(call.id(), key), question, schema);
        final Object given = answer.action() == ElicitResult.Action.ACCEPT
                ? NullSafe.map(answer.content()).get("choice")
                : null;
        final String chosen = given == null
                ? null
                : NullSafe.trim(String.valueOf(given));
        auditLogProvider.get().processed(context.session(), null, "choice", action + ": " + question,
                NullSafe.isBlankString(chosen)
                        ? "declined"
                        : "chosen: " + chosen, NullSafe.isNonBlankString(chosen));
        if (NullSafe.isBlankString(chosen)) {
            throw new ToolRefusal("The user made no choice: " + question);
        }
        return options.contains(chosen)
                ? Optional.of(chosen)
                : Optional.empty();
    }

    /// Several questions put to the user in one form, an option picked for each, as [#choose] picks one.
    ///
    /// @param call      The call asking.
    /// @param context   The call's context.
    /// @param action    The tool asking, e.g. `build_translation_xslt`.
    /// @param intro     What the questions are about, the form's message.
    /// @param questions The questions by key, each with its options (the recommended one first), in the order asked.
    /// @return The option picked for each question, by its key; empty when the session can't show a form now, or
    /// the form would be too large to show (the agent then asks the user).
    /// @throws ToolRefusal if the user made no choice.
    public Optional<Map<String, String>> chooseEach(final ToolCall call,
                                                    final ToolCallContext context,
                                                    final String action,
                                                    final String intro,
                                                    final Map<String, Question> questions) {
        if (NullSafe.isEmptyMap(questions) || !context.userQuestions().canAsk()) {
            return Optional.empty();
        }
        final Map<String, Object> properties = new LinkedHashMap<>();
        final Map<String, String> fieldKeys = new LinkedHashMap<>();
        questions.forEach((key, question) -> {
            final String fieldKey = "q" + (fieldKeys.size() + 1);
            fieldKeys.put(fieldKey, key);
            final Map<String, Object> field = new LinkedHashMap<>();
            field.put("type", "string");
            field.put("title", heading(question.text()));
            field.put("enum", List.copyOf(question.options()));
            properties.put(fieldKey, field);
        });
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.copyOf(fieldKeys.keySet()));
        schema.put("properties", properties);
        if (unshowable(context, action, intro, schema)) {
            return Optional.empty();
        }
        final String digest = XsltCode.sha256(action + "\n" + intro + "\n" + questions.keySet()).substring(0, 16);
        final ElicitResult answer = context.userQuestions().ask(call.name(),
                UserQuestions.formCallId(call.id(), "choices-" + digest), intro, schema);
        final Map<String, Object> content = answer.action() == ElicitResult.Action.ACCEPT
                ? NullSafe.map(answer.content())
                : Map.of();
        final Map<String, String> chosen = new LinkedHashMap<>();
        fieldKeys.forEach((fieldKey, key) -> {
            final String given = NullSafe.trim(NullSafe.get(content.get(fieldKey), String::valueOf));
            if (questions.get(key).options().contains(given)) {
                chosen.put(key, given);
            }
        });
        auditLogProvider.get().processed(context.session(), null, "choice", action + ": " + intro,
                chosen.isEmpty()
                        ? "declined"
                        : "chosen: " + chosen, !chosen.isEmpty());
        if (chosen.isEmpty()) {
            throw new ToolRefusal("The user made no choice: " + intro);
        }
        return Optional.of(chosen);
    }

    // A form too large to show is asked in the chat instead, as when the session can't show forms: shown, it would
    // be refused, stopping the call where the user can never answer it (seen: a stand-ins form of a question for
    // each of ~150 values, and a hand-edit question naming every field of a wide mapping).
    private boolean unshowable(final ToolCallContext context,
                               final String action,
                               final String message,
                               final Map<String, Object> schema) {
        final String problem = ElicitationSchema.check(message, schema);
        if (problem == null) {
            return false;
        }
        LOGGER.warn(() -> LogUtil.message("Form for '{}' from {} not shown, as {}: asked in the chat instead",
                heading(message, 80), action, problem));
        auditLogProvider.get().processed(context.session(), null, "choice", action + ": " + heading(message),
                "not asked in a form, as " + problem, false);
        return true;
    }

    /// @param context  The call's context.
    /// @param key      An editable value's key.
    /// @param proposed What the tool proposed.
    /// @return What the user settled on for it in the call's forms: their correction, else the proposal.
    public String edited(final ToolCallContext context, final String key, final String proposed) {
        return NullSafe.nonBlankStringElse(state(context).edits.get(key), proposed);
    }

    /// @param context The call's context.
    /// @return The values the user changed in the call's forms, by key.
    public Map<String, Change> changes(final ToolCallContext context) {
        return Map.copyOf(state(context).changes);
    }

    /// What a form shows: the summary and its details, short. A form is a question, not a document: what the user
    /// reviews at length (a template, a layout) is shown in the chat first. Seen in testing: an index template's
    /// confirmation ran to 2,000 characters (every field, every note, the component templates), the client declined
    /// the form as too long, and the user could never agree the template. Each detail is cut short, and details
    /// past [#FORM_MESSAGE_LIMIT] are left to the chat; a form cut so is logged, as its caller's details should be
    /// shorter.
    ///
    /// @param summary What the user is asked to agree to.
    /// @param details Its details.
    /// @return The form's message.
    public static String formMessage(final String summary, final Map<String, Object> details) {
        return formMessage(summary, details, List.of());
    }

    /// [#formMessage(String, Map)], each document the request names a link that opens it, by its UUID: where
    /// the summary names it in quotes, and a detail that is its name.
    ///
    /// @param summary   What the user is asked to agree to.
    /// @param details   Its details.
    /// @param documents The documents it names.
    /// @return The form's message.
    public static String formMessage(final String summary,
                                     final Map<String, Object> details,
                                     final List<DocRef> documents) {
        final String text = NullSafe.string(summary).strip();
        final String head = text.length() <= 300
                ? text
                : heading(text, 300);
        // What is cut is measured as the user reads it, without the links (seen: a late detail, the index
        // template, cut once its pipeline was a link).
        String message = head;
        String withLinks = linked(head, documents);
        final List<String> lines = new ArrayList<>();
        final List<String> linkedLines = new ArrayList<>();
        NullSafe.map(details).forEach((key, value) -> {
            final String shown = shortDetail(value);
            lines.add("- " + key + ": " + shown);
            linkedLines.add("- " + key + ": " + documents.stream()
                    .filter(docRef -> shown.equals(docRef.getName()))
                    .findFirst()
                    .map(Consent::docLink)
                    .orElse(shown));
        });
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (message.length() + line.length() + 30 > FORM_MESSAGE_LIMIT) {
                final int shown = i;
                LOGGER.warn(() -> LogUtil.message(
                        "Form for '{}' cut: {} of its {} details shown",
                        heading(text, 80),
                        shown,
                        lines.size()));
                return within(withLinks, message, "\n- … (the rest is in the chat)");
            }
            final String gap = i == 0
                    ? "\n\n"
                    : "\n";
            message += gap + line;
            withLinks += gap + linkedLines.get(i);
        }
        return within(withLinks, message, "");
    }

    // The message with its links, unless they make it longer than a form can show; then without them.
    private static String within(final String withLinks, final String plain, final String tail) {
        return withLinks.length() + tail.length() <= LINKED_MESSAGE_LIMIT
                ? withLinks + tail
                : plain + tail;
    }

    /// @param text A question.
    /// @return It as the heading of its answer: its first line, cut at a word near 150 characters.
    public static String heading(final String text) {
        return heading(text, HEADING_LIMIT);
    }

    /// @param text  A question.
    /// @param limit The most characters.
    /// @return Its first line, cut at a word near the limit.
    public static String heading(final String text, final int limit) {
        final String stripped = NullSafe.string(text).strip();
        final String first = stripped.isEmpty()
                ? ""
                : stripped.lines().findFirst().orElse("");
        return first.length() <= limit
                ? first
                : cutAtWord(first, limit) + "…";
    }

    /// The id of what a `needs_review` reply shows the user in the chat (a promotion's layout, an index template,
    /// the records kept as Unknown), which the call after the review passes back as reviewed. Seen: promote_build
    /// called with reviewed=true from the start, its layout never shown before the approval form. What it shows
    /// changing since gives another id, so it is reviewed again.
    ///
    /// @param shown What the reply shows.
    /// @return Its review id.
    public static String reviewId(final String shown) {
        return XsltCode.sha256(shown).substring(0, 12);
    }

    /// @param reviewed What the call gave as reviewed.
    /// @param shown    What the reply shows.
    /// @return Whether reviewed is the review id of exactly this (true, or another's id, is not).
    public static boolean wasReviewed(final Object reviewed, final String shown) {
        return reviewed instanceof final String text && text.strip().toLowerCase(Locale.ROOT).equals(reviewId(shown));
    }

    /// @param reviewed What the call gave as reviewed, or null.
    /// @return What a `needs_review` reply says when reviewed was given but isn't its id; empty if none was given.
    public static String reviewNote(final Object reviewed) {
        if (reviewed == null || Boolean.FALSE.equals(reviewed) || "".equals(reviewed)) {
            return "";
        }
        return " reviewed takes the review_id of a needs_review reply, once the user has seen what it shows: "
               + (Boolean.TRUE.equals(reviewed) || "true".equalsIgnoreCase(String.valueOf(reviewed).strip())
                ? "true is not one."
                : "the one given was for something else, or what is shown has changed since: show this.");
    }

    private void ask(final ToolCall call,
                     final ToolCallContext context,
                     final Request request,
                     final String digest,
                     final CallState state) {
        final String kind = request.kind().word();
        final String key = kind + "-" + digest.substring(0, 16);
        final ElicitResult answer = context.userQuestions().ask(call.name(), UserQuestions.formCallId(call.id(), key),
                formMessage(request.summary(), request.details(), request.documents()), schema(request));
        final Map<String, Object> content = NullSafe.map(answer.content());
        final boolean accepted = answer.action() == ElicitResult.Action.ACCEPT;
        // With a value to edit, accepting the form is the confirmation; otherwise its yes or no is.
        final boolean agreed = accepted && (!request.editable().isEmpty() || isTrue(content.get("value")));
        final boolean unanswered = answer.action() == ElicitResult.Action.CANCEL;
        final Map<String, String> edits = settled(request.editable(), content);
        final Map<String, String> changed = new LinkedHashMap<>();
        edits.forEach((name, value) -> {
            if (!value.equals(request.editable().get(name).proposed())) {
                changed.put(name, value);
            }
        });
        audit(context.session(), kind, request, agreed
                ? "granted in a form" + (changed.isEmpty()
                        ? ""
                        : ", edited " + changed)
                : unanswered
                        ? "unanswered"
                        : "declined", agreed);
        if (!agreed) {
            throw new ToolRefusal(notAgreed(request.summary(), request.action(), unanswered));
        }
        state.edits.putAll(edits);
        changed.forEach((name, theirs) -> state.changes.put(name,
                new Change(request.editable().get(name).proposed(), theirs)));
    }

    // With a value to edit, the form is just that field, prefilled: accepting it confirms (clients step through
    // fields one at a time, so a yes or no beside it would be a second prompt).
    private static Map<String, Object> schema(final Request request) {
        final Map<String, Object> properties = new LinkedHashMap<>();
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        if (request.editable().isEmpty()) {
            final Map<String, Object> field = new LinkedHashMap<>();
            field.put("type", "boolean");
            field.put("title", NullSafe.nonBlankStringElse(heading(request.summary()),
                    request.kind() == Kind.APPROVAL
                            ? "Approve"
                            : "Confirm"));
            field.put("description", request.summary());
            properties.put("value", field);
            schema.put("required", List.of("value"));
        } else {
            request.editable().forEach((name, editable) -> {
                final Map<String, Object> field = new LinkedHashMap<>();
                field.put("type", "string");
                field.put("title", editable.title());
                field.put("default", editable.proposed());
                field.put("description", "Proposed: " + editable.proposed() + ". Accept to confirm it, or change it "
                                         + "first; left empty, the proposal stands. Decline to stop.");
                properties.put(name, field);
            });
        }
        schema.put("properties", properties);
        return schema;
    }

    // Each editable value as answered, or the proposal where the answer is empty.
    private static Map<String, String> settled(final Map<String, Editable> editable, final Map<String, Object> answer) {
        final Map<String, String> settled = new LinkedHashMap<>();
        editable.forEach((name, value) -> settled.put(name, NullSafe.nonBlankStringElse(
                NullSafe.trim(answer.get(name) == null
                        ? null
                        : String.valueOf(answer.get(name))), value.proposed())));
        return settled;
    }

    /// What the agent is told when a form wasn't accepted. A form closed or run out without an answer (cancel) is
    /// not the user declining (asked for by the user: Stroom answers cancel when its form expires, and an agent
    /// took 'did not agree' for a refusal).
    ///
    /// @param summary    What the user was asked to agree to.
    /// @param action     The tool.
    /// @param unanswered True if the form was closed or ran out unanswered.
    /// @return What the agent is told.
    static String notAgreed(final String summary, final String action, final boolean unanswered) {
        if (unanswered) {
            return "No answer yet to: " + summary + ". The form was closed or ran out before the user answered; "
                   + "they didn't decline, and nothing was done. When the user is back, call " + action + " again "
                   + "with the same arguments: they are asked again.";
        }
        return "The user did not agree to: " + summary;
    }

    private static String digest(final String action, final Map<String, Object> details) {
        final List<Object> request = new ArrayList<>();
        request.add(action);
        request.add(NullSafe.map(details));
        return XsltCode.sha256(PyJson.dumpsSortedKeys(request));
    }

    private void audit(final ToolSession session,
                       final String kind,
                       final Request request,
                       final String outcome,
                       final boolean success) {
        auditLogProvider.get().processed(session, null, kind, request.action() + ": " + request.summary(), outcome,
                success);
    }

    private CallState state(final ToolCallContext context) {
        return calls.computeIfAbsent(context, ignored -> new CallState());
    }

    private static boolean isTrue(final Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    // The summary with each document it names in quotes a link.
    private static String linked(final String summary, final List<DocRef> documents) {
        String out = summary;
        for (final DocRef docRef : documents) {
            final String quoted = "'" + docRef.getName() + "'";
            final int at = out.indexOf(quoted);
            if (NullSafe.isNonBlankString(docRef.getName()) && at >= 0) {
                out = out.substring(0, at) + "'" + docLink(docRef) + "'" + out.substring(at + quoted.length());
            }
        }
        return out;
    }

    // A link that opens the document, as the chat's answers write one.
    private static String docLink(final DocRef docRef) {
        final StringBuilder name = new StringBuilder();
        for (final char c : docRef.getName().toCharArray()) {
            if ("\\`*_[]()<>#!|~".indexOf(c) >= 0) {
                name.append('\\');
            }
            name.append(c);
        }
        return "[" + name + "](?action=open-doc&docType=" + docRef.getType() + "&docUuid=" + docRef.getUuid() + ")";
    }

    private static String shortDetail(final Object value) {
        final String raw;
        if (value instanceof final Collection<?> collection) {
            raw = String.join(", ", collection.stream().map(PyText::str).toList());
        } else {
            raw = PyText.str(value);
        }
        final String text = String.join(" ", raw.strip().split("\\s+"));
        return text.length() <= DETAIL_LIMIT
                ? text
                : cutAtWord(text, DETAIL_LIMIT) + "…";
    }

    // The text cut at the last word break before the limit, without a trailing ',', ';' or ':'.
    private static String cutAtWord(final String text, final int limit) {
        final String cut = text.substring(0, limit);
        final int space = cut.lastIndexOf(' ');
        String word = space < 0
                ? cut
                : cut.substring(0, space);
        while (!word.isEmpty() && ",;:".indexOf(word.charAt(word.length() - 1)) >= 0) {
            word = word.substring(0, word.length() - 1);
        }
        return word;
    }


    // --------------------------------------------------------------------------------


    /// The kinds of request.
    public enum Kind {
        /// The key details of a change, e.g. a feed's name.
        CONFIRMATION("confirmation"),
        /// A risky action, e.g. a promotion.
        APPROVAL("approval");

        private final String word;

        Kind(final String word) {
            this.word = word;
        }

        /// @return The kind as the audit log names it, e.g. `confirmation`.
        public String word() {
            return word;
        }
    }

    /// A value the user may correct in the form itself.
    ///
    /// @param title    Its field's title, e.g. `Feed name`.
    /// @param proposed The tool's proposal, e.g. `ACME-VPN-V1.0`.
    public record Editable(String title, String proposed) {

    }

    /// A value the user changed in a form.
    ///
    /// @param proposed What the tool proposed.
    /// @param theirs   What the user settled on.
    public record Change(String proposed, String theirs) {

    }

    /// A question asked with [#chooseEach].
    ///
    /// @param text    The question.
    /// @param options Its options, the recommended one first.
    public record Question(String text, List<String> options) {

    }

    /// What a tool asks the user to agree to.
    ///
    /// @param kind     A confirmation or an approval.
    /// @param action   The tool, e.g. `create_feed`.
    /// @param summary  What the user agrees to, in a line, e.g. `Create feed 'ACME-VPN-V1.0'`.
    /// @param details  Its details, e.g. `{"feed name": "ACME-VPN-V1.0"}`.
    /// @param editable  The values the user may correct in the form, by key.
    /// @param documents The documents it names (in quotes in the summary, or as a detail's value), which its form
    ///                  links to open them; not part of what is agreed to.
    public record Request(Kind kind,
                          String action,
                          String summary,
                          Map<String, Object> details,
                          Map<String, Editable> editable,
                          List<DocRef> documents) {

        /// @param kind      A confirmation or an approval.
        /// @param action    The tool.
        /// @param summary   What the user agrees to.
        /// @param details   Its details.
        /// @param editable  The values the user may correct in the form.
        /// @param documents The documents it names, which its form links.
        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(action, "action");
            summary = NullSafe.string(summary);
            details = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(details)));
            editable = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(editable)));
            documents = List.copyOf(NullSafe.list(documents));
        }

        /// @param action  The tool.
        /// @param summary What the user confirms.
        /// @param details Its details.
        /// @return A confirmation.
        public static Request confirmation(final String action,
                                           final String summary,
                                           final Map<String, Object> details) {
            return new Request(Kind.CONFIRMATION, action, summary, details, Map.of(), List.of());
        }

        /// @param action  The tool.
        /// @param summary What the user approves.
        /// @param details Its details.
        /// @return An approval.
        public static Request approval(final String action,
                                       final String summary,
                                       final Map<String, Object> details) {
            return new Request(Kind.APPROVAL, action, summary, details, Map.of(), List.of());
        }

        /// @param key      The value's key, e.g. `name`.
        /// @param title    Its field's title, e.g. `Feed name`.
        /// @param proposed The proposal.
        /// @return This request, with the value editable in its form.
        public Request withEditable(final String key, final String title, final String proposed) {
            final Map<String, Editable> more = new LinkedHashMap<>(editable);
            more.put(key, new Editable(title, proposed));
            return new Request(kind, action, summary, details, more, documents);
        }

        /// @param named The documents the request names, by their names in it.
        /// @return This request, its form linking them by their UUIDs (asked for by the user: seen, the pipeline an
        /// approval names not opening from it).
        public Request withDocuments(final DocRef... named) {
            final List<DocRef> more = new ArrayList<>(documents);
            for (final DocRef docRef : named) {
                if (docRef != null) {
                    more.add(docRef);
                }
            }
            return new Request(kind, action, summary, details, editable, more);
        }
    }

    // What a call's forms settled: the value of each editable field, and those the user changed.
    private static final class CallState {

        private final Map<String, String> edits = new LinkedHashMap<>();
        private final Map<String, Change> changes = new LinkedHashMap<>();
    }
}
