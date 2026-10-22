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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.time.Duration;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/// Confirmations (key details) and approvals (risky actions) before a write tool acts, and choices the user makes
/// in a form (ported from the Stroom MCP server's `utils/consent.py`).
///
/// The user is asked directly, as a form, wherever the session can ask ([UserQuestions#canAsk()]), so the model
/// never holds the answer: in the chat the run stops while the form waits, and the same call is made again with the
/// answer (so a tool that asks must be safe to re-run up to its question); over `/mcp` the call waits on the
/// client's elicitation. Otherwise (a client that can't show forms, a chat the user is away from) the tool returns a
/// round instead of acting: a pending id with a plain-language summary, which the agent shows the user, and the call
/// is made again with the id once they agree. An id is bound to the action, the exact details and the user, is used
/// once, expires within the hour, and is held by the session it was given in (this node's memory: the Stroom MCP
/// server's signed ids, valid on any replica, are not ported, as Stroom keeps an MCP session on one node).
///
/// What the user settled on in the call's forms ([#edited], [#changes]) is kept for the call, for the tool and for
/// [BuildPlan#withNext] to read.
@Singleton
public class Consent {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Consent.class);

    /// How long a pending id can be used.
    public static final Duration TTL = Duration.ofHours(1);
    /// The most characters a form's message holds.
    public static final int FORM_MESSAGE_LIMIT = 600;
    // Characters of one detail's value in a form.
    private static final int DETAIL_LIMIT = 160;
    private static final int HEADING_LIMIT = 150;
    private static final String BASE32 = "abcdefghijklmnopqrstuvwxyz234567";

    private final SecurityContext securityContext;
    private final Provider<AiAuditLog> auditLogProvider;
    private final LongSupplier clock;
    private final SecureRandom random = new SecureRandom();
    // What each call's forms settled, kept while the call's context is.
    private final Map<ToolCallContext, CallState> calls = Collections.synchronizedMap(new WeakHashMap<>());
    // The pending ids given in each session, by id.
    private final Map<String, Map<String, Pending>> pending = new ConcurrentHashMap<>();

    /// @param securityContext  Says who the user is, whom an id is bound to.
    /// @param auditLogProvider Records each request, grant and refusal.
    @Inject
    public Consent(final SecurityContext securityContext, final Provider<AiAuditLog> auditLogProvider) {
        this(securityContext, auditLogProvider, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    ///
    /// @param securityContext  Says who the user is.
    /// @param auditLogProvider Records each request, grant and refusal.
    /// @param clock            The time now, in milliseconds.
    Consent(final SecurityContext securityContext,
            final Provider<AiAuditLog> auditLogProvider,
            final LongSupplier clock) {
        this.securityContext = securityContext;
        this.auditLogProvider = auditLogProvider;
        this.clock = clock;
    }

    /// Asks the user to agree before the tool acts.
    ///
    /// Throws when the user declines, the form closes unanswered, or the id given is not one for this exact
    /// request. Ids are single use: when a later gate in the same call may still stop it, make the request with
    /// [Request#keep()] so the id survives the call being made again for that gate, and [#discard] it once the
    /// action is done. Without a form (an id passed back), the proposal of an editable value stands: a user who
    /// wants another value says so, and the tool is called again.
    ///
    /// @param call    The call asking, whose id tells its forms apart in a chat.
    /// @param context The call's context: how to ask the user, and the session.
    /// @param request What is asked.
    /// @return Empty when the user has agreed; otherwise the round the tool should return (as its JSON result),
    /// with `status` `needs_confirmation` or `needs_approval`.
    /// @throws ToolRefusal if the user did not agree, or the id doesn't fit the request.
    /// @throws stroom.ai.impl.AiChatRunManager.WaitingForUserException in a chat, while the form waits for the
    ///                                                                 user: the call is made again with the
    ///                                                                 answer.
    public Optional<Map<String, Object>> require(final ToolCall call,
                                                 final ToolCallContext context,
                                                 final Request request) {
        final CallState state = state(context);
        state.edits.clear();
        final String kind = request.kind().word();
        final String digest = digest(request.action(), request.details());
        if (NullSafe.isNonBlankString(request.token())) {
            useToken(context, request, digest);
            return Optional.empty();
        }
        if (context.userQuestions().canAsk()) {
            ask(call, context, request, digest, state);
            return Optional.empty();
        }
        final String id = issue(context, request.kind(), request.action(), digest);
        audit(context.session(), kind, request, "requested", false);
        final Map<String, Object> round = new LinkedHashMap<>();
        round.put("status", "needs_" + kind);
        round.put(kind + "_id", id);
        round.put("summary", request.summary());
        round.put("details", request.details());
        round.put("hint", "Show the summary and details to the user. If they agree, call " + request.action()
                          + " again with the same arguments plus " + kind + "_id='" + id + "'. If they change a "
                          + "detail, call again with the new values and no id to get a fresh " + kind + ".");
        return Optional.of(round);
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
    /// @return The option picked; empty when the session can't show a form now (the agent then asks the user), or
    /// the answer is not one of the options.
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

    /// An id for the same request, once the user has agreed to it in this call ([#require] gave empty): the call
    /// is made again for a round that isn't theirs (the agent describing fields), and they aren't asked again.
    ///
    /// @param context The call's context.
    /// @param kind    The kind of request.
    /// @param action  The tool, e.g. `create_feed`.
    /// @param details The request's details, exactly as agreed.
    /// @return The id.
    public String reissue(final ToolCallContext context,
                          final Kind kind,
                          final String action,
                          final Map<String, Object> details) {
        return issue(context, kind, action, digest(action, details));
    }

    /// Spends a kept id once the action it covered is done.
    ///
    /// @param context The call's context.
    /// @param token   The id, or null.
    public void discard(final ToolCallContext context, final String token) {
        if (NullSafe.isBlankString(token)) {
            return;
        }
        final Pending found = sessionIds(context.session()).get(normalise(token));
        if (found != null) {
            found.spent = true;
        }
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
        final String text = NullSafe.string(summary).strip();
        String message = text.length() <= 300
                ? text
                : heading(text, 300);
        final List<String> lines = new ArrayList<>();
        NullSafe.map(details).forEach((key, value) -> lines.add("- " + key + ": " + shortDetail(value)));
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (message.length() + line.length() + 30 > FORM_MESSAGE_LIMIT) {
                final int shown = i;
                LOGGER.warn(() -> "Form for '" + heading(text, 80) + "' cut: " + shown + " of its " + lines.size()
                                  + " details shown");
                return message + "\n- … (the rest is in the chat)";
            }
            message += (i == 0
                    ? "\n\n"
                    : "\n") + line;
        }
        return message;
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

    private void useToken(final ToolCallContext context, final Request request, final String digest) {
        final String kind = request.kind().word();
        final String token = normalise(request.token());
        final Map<String, Pending> ids = sessionIds(context.session());
        sweep(ids);
        final Pending found = ids.get(token);
        if (found == null) {
            throw new ToolRefusal("This " + kind + " id is not one the server issued: pass it back exactly as it was "
                                  + "given (46 characters, e.g. " + prefix(kind) + "-…….……), or request the "
                                  + kind + " again");
        }
        if (found.expiresMs <= clock.getAsLong()) {
            throw new ToolRefusal("This " + kind + " id has expired; request the " + kind + " again");
        }
        if (!found.binding.equals(binding(request.kind(), request.action(), digest))) {
            throw new ToolRefusal("This " + kind + " id was issued for a different request; request the " + kind
                                  + " again");
        }
        if (found.spent) {
            throw new ToolRefusal("This " + kind + " id was already used; request the " + kind + " again");
        }
        if (!request.keep()) {
            found.spent = true;
        }
        if (!found.granted) {
            found.granted = true;
            audit(context.session(), kind, request, "granted via id", true);
        }
    }

    private void ask(final ToolCall call,
                     final ToolCallContext context,
                     final Request request,
                     final String digest,
                     final CallState state) {
        final String kind = request.kind().word();
        final String key = kind + "-" + digest.substring(0, 16);
        final ElicitResult answer = context.userQuestions().ask(call.name(), UserQuestions.formCallId(call.id(), key),
                formMessage(request.summary(), request.details()), schema(request));
        final Map<String, Object> content = NullSafe.map(answer.content());
        final boolean accepted = answer.action() == ElicitResult.Action.ACCEPT;
        // With a value to edit, accepting the form is the confirmation; otherwise its yes or no is.
        final boolean agreed = accepted && (!request.editable().isEmpty() || truthy(content.get("value")));
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

    private synchronized String issue(final ToolCallContext context,
                                      final Kind kind,
                                      final String action,
                                      final String digest) {
        // Every session's ids that can no longer matter are dropped, and the sessions left with none.
        pending.values().forEach(this::sweep);
        pending.values().removeIf(Map::isEmpty);
        final Map<String, Pending> ids = sessionIds(context.session());
        final String id = prefix(kind.word()) + "-" + base32(15) + "." + base32(10);
        ids.put(id, new Pending(binding(kind, action, digest), clock.getAsLong() + TTL.toMillis()));
        return id;
    }

    private Map<String, Pending> sessionIds(final ToolSession session) {
        return pending.computeIfAbsent(session.kind() + ":" + session.id(), key -> new ConcurrentHashMap<>());
    }

    // Ids are remembered for a while after they expire, to say so.
    private void sweep(final Map<String, Pending> ids) {
        final long now = clock.getAsLong();
        ids.values().removeIf(found -> found.expiresMs + TTL.toMillis() < now);
    }

    private String binding(final Kind kind, final String action, final String digest) {
        final String user = NullSafe.get(securityContext.getUserRef(), UserRef::getUuid);
        return kind.word() + "\n" + action + "\n" + digest + "\n" + user;
    }

    private static String digest(final String action, final Map<String, Object> details) {
        final List<Object> request = new ArrayList<>();
        request.add(action);
        request.add(NullSafe.map(details));
        return XsltCode.sha256(PyJson.dumpsSortedKeys(request));
    }

    private String base32(final int bytes) {
        final byte[] data = new byte[bytes];
        random.nextBytes(data);
        final StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (final byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
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

    private static String normalise(final String token) {
        return NullSafe.string(token).strip().toLowerCase(Locale.ROOT);
    }

    private static String prefix(final String kind) {
        return kind.substring(0, Math.min(4, kind.length()));
    }

    private static boolean truthy(final Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String shortDetail(final Object value) {
        final String raw;
        if (value instanceof final Collection<?> collection) {
            raw = String.join(", ", collection.stream().map(Consent::pythonStr).toList());
        } else {
            raw = pythonStr(value);
        }
        final String text = String.join(" ", raw.strip().split("\\s+"));
        return text.length() <= DETAIL_LIMIT
                ? text
                : cutAtWord(text, DETAIL_LIMIT) + "…";
    }

    // A value as Python's str gives it, for the few that differ from Java's.
    private static String pythonStr(final Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof final Boolean bool) {
            return bool
                    ? "True"
                    : "False";
        }
        return String.valueOf(value);
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

        /// @return The kind as the results name it, e.g. `confirmation` (`needs_confirmation`,
        /// `confirmation_id`).
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

    /// What a tool asks the user to agree to.
    ///
    /// @param kind     A confirmation or an approval.
    /// @param action   The tool, e.g. `create_feed`: the round tells the agent to call it again.
    /// @param summary  What the user agrees to, in a line, e.g. `Create feed 'ACME-VPN-V1.0'`.
    /// @param details  Its details, e.g. `{"feed name": "ACME-VPN-V1.0"}`; the id is bound to them exactly.
    /// @param token    The id the call was given back, or null.
    /// @param keep     True to keep the id usable after this call (a later gate may stop it), until discarded.
    /// @param editable The values the user may correct in the form, by key.
    public record Request(Kind kind,
                          String action,
                          String summary,
                          Map<String, Object> details,
                          String token,
                          boolean keep,
                          Map<String, Editable> editable) {

        /// @param kind     A confirmation or an approval.
        /// @param action   The tool.
        /// @param summary  What the user agrees to.
        /// @param details  Its details.
        /// @param token    The id the call was given back, or null.
        /// @param keep     True to keep the id usable after this call.
        /// @param editable The values the user may correct in the form.
        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(action, "action");
            summary = NullSafe.string(summary);
            details = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(details)));
            editable = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(editable)));
        }

        /// @param action  The tool.
        /// @param summary What the user confirms.
        /// @param details Its details.
        /// @param token   The `confirmation_id` the call was given back, or null.
        /// @return A confirmation.
        public static Request confirmation(final String action,
                                           final String summary,
                                           final Map<String, Object> details,
                                           final String token) {
            return new Request(Kind.CONFIRMATION, action, summary, details, token, false, Map.of());
        }

        /// @param action  The tool.
        /// @param summary What the user approves.
        /// @param details Its details.
        /// @param token   The `approval_id` the call was given back, or null.
        /// @return An approval.
        public static Request approval(final String action,
                                       final String summary,
                                       final Map<String, Object> details,
                                       final String token) {
            return new Request(Kind.APPROVAL, action, summary, details, token, false, Map.of());
        }

        /// @return This request, with its id kept usable after the call, until [Consent#discard].
        public Request keepingId() {
            return new Request(kind, action, summary, details, token, true, editable);
        }

        /// @param key      The value's key, e.g. `name`.
        /// @param title    Its field's title, e.g. `Feed name`.
        /// @param proposed The proposal.
        /// @return This request, with the value editable in its form.
        public Request withEditable(final String key, final String title, final String proposed) {
            final Map<String, Editable> more = new LinkedHashMap<>(editable);
            more.put(key, new Editable(title, proposed));
            return new Request(kind, action, summary, details, token, keep, more);
        }
    }

    // What a call's forms settled: the value of each editable field, and those the user changed.
    private static final class CallState {

        private final Map<String, String> edits = new LinkedHashMap<>();
        private final Map<String, Change> changes = new LinkedHashMap<>();
    }

    // An id given in a session.
    private static final class Pending {

        private final String binding;
        private final long expiresMs;
        private volatile boolean spent;
        private volatile boolean granted;

        private Pending(final String binding, final long expiresMs) {
            this.binding = binding;
            this.expiresMs = expiresMs;
        }
    }
}
