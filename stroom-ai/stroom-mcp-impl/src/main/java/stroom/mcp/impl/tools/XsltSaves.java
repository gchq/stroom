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

import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.generation.CodeDiff;
import stroom.ai.impl.tools.generation.HandEdits;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.validation.XsltChecks;
import stroom.ai.impl.tools.validation.XsltSafety;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Creating and changing an XSLT in a build, as the Stroom MCP server's `tools/translation.py` `create_xslt` and
/// `update_xslt` do: checked with check_xslt first (and refused without the imports standing instructions
/// require), kept with the mapping or plan it was generated from in its Documentation tab, the change pending in
/// its version history until the build is promoted, tagged as saved with a kept mapping, and the build's next step
/// added. A change that undoes an edit made by hand in Stroom's editor since the tools saved the XSLT is refused,
/// saying what it changed (`hand_edit_gate`); where the change is to a field the edit changed too, the user is asked
/// whether to keep their edit or use the proposed field.
///
/// Ported for draft_cef_mapping (a CEF plan) with what the save tools (`save_xslt`, `build_translation_xslt`) will
/// reuse when they are ported. A kept translation mapping's payload is written as given, and what it generates now is
/// not regenerated here (the generator's schema reading comes with the translation tools), so a hand edit of a
/// translation counts in whole.
@Singleton
public class XsltSaves {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltSaves.class);

    /// The kind of a kept translation mapping.
    public static final String TRANSLATION = "translation";
    /// The kind of a kept index plan.
    public static final String INDEX = "index";
    /// The kind of a kept CEF plan.
    public static final String CEF = "cef";

    /// Why an XSLT tagged as saved with a mapping has none now.
    static final String LOST = "%s '%s' was saved by stroom-mcp with the %s it is generated from, kept in its "
                               + "Documentation tab (hidden, after a note asking that it be left), and it is gone: "
                               + "deleted, or edited so it no longer reads. Until it is back the tools treat the "
                               + "XSLT as written by hand (no regenerating, no changes=, no generated Field mapping "
                               + "section), and its version history went with it. Tell the user. To recover: "
                               + "rebuild_mapping uuid='%s' stream_ids=<the sample streams> reads the mapping back "
                               + "from the XSLT and proves it on the sample before saving it.";
    static final String KEEP = "Keep my hand edit";
    static final String OVERWRITE = "Use the proposed field";
    static final String OVERWRITE_ALL = "Overwrite the XSLT with the change";
    static final String PER_FIELD = "Decide field by field";

    private static final Map<String, String> ROLE_STAGE = Map.of("Events", "translation", "Records", "records",
            "Indexing", "indexing", "Reference", "reference", "CEF", "cef");
    private static final Map<String, String> CARRY = Map.of(
            TRANSLATION, "carry it into the mapping: rebuild_mapping uuid='%s' stream_ids=<the sample streams> reads "
                         + "it from the XSLT, proven on the sample (or build_translation_xslt changes=)",
            INDEX, "carry it into the index plan: a field the edit added as a field of index_plan (its name, type, "
                   + "and source: the XPath it reads, from the Event), one it removed left out, a source it changed "
                   + "changed",
            CEF, "carry it into the CEF plan (overrides), as the edit made it");
    private static final Pattern EVENTS = Pattern.compile("<(?:\\w+:)?Events[\\s/>]");
    private static final Pattern TEXT_OUTPUT = Pattern.compile("<xsl:output[^>]*method=\"text\"");
    private static final Pattern ARRAY = Pattern.compile("<(?:\\w+:)?array[\\s/>]");
    private static final Pattern RECORDS = Pattern.compile("<(?:\\w+:)?records[\\s/>]");
    private static final int REMEMBERED = 1_000;

    private final DocumentAccess documentAccess;
    private final WriteGuard guard;
    private final CheckXsltTool checkXslt;
    private final BuildPlan plan;
    private final AgentInstructions agentInstructions;
    private final Consent consent;
    private final Provider<SecurityContext> securityContextProvider;
    // The user's answers about an XSLT's hand edits, as it is now, kept for the session: asked once, however many
    // calls.
    private final Map<List<String>, Map<String, String>> remembered = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<List<String>, Map<String, String>> eldest) {
                    return size() > REMEMBERED;
                }
            });

    /// @param documentAccess          Reads and writes XSLTs as the user.
    /// @param guard                   Makes them in the build, checks they are the tools' own, and tags them.
    /// @param checkXslt               Checks the code before it is saved.
    /// @param plan                    Gives the sample's format and the build's next step.
    /// @param agentInstructions       Reads the imports standing instructions require.
    /// @param consent                 Asks the user about hand edits.
    /// @param securityContextProvider Names the user, for the version history.
    @Inject
    XsltSaves(final DocumentAccess documentAccess,
              final WriteGuard guard,
              final CheckXsltTool checkXslt,
              final BuildPlan plan,
              final AgentInstructions agentInstructions,
              final Consent consent,
              final Provider<SecurityContext> securityContextProvider) {
        this.documentAccess = documentAccess;
        this.guard = guard;
        this.checkXslt = checkXslt;
        this.plan = plan;
        this.agentInstructions = agentInstructions;
        this.consent = consent;
        this.securityContextProvider = securityContextProvider;
    }

    /// Creates an XSLT in the build folder (`create_xslt`). It is checked with check_xslt first and not saved if
    /// that fails. The mapping or plan it was generated from is kept with it, and the pipeline's documentation is
    /// generated from it.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param name    The XSLT's name.
    /// @param code    The complete XSLT.
    /// @param kept    The mapping or plan it was generated from (`translation`, `index` or `cef`, with the payload
    ///                kept: a CEF plan's compact JSON, an index plan's whole JSON), or null for one written by hand.
    /// @param change  What the change is, for the version history, or null (`Created`).
    /// @return The saved XSLT's `type`, `uuid`, `name` and `version`, with the build's next step.
    /// @throws ToolRefusal if check_xslt fails, a required import is missing, or the build has an XSLT so named.
    public Map<String, Object> create(final ToolCallContext context,
                                      final String build,
                                      final String name,
                                      final String code,
                                      final KeptMapping.Kept kept,
                                      final String change) {
        checked(code, build, null);
        // A plan says what an XSLT is for; a discovery XSLT reads a source's records, as one writing Records does.
        final String role = kept != null && INDEX.equals(kept.kind())
                ? "Indexing"
                : kept != null && CEF.equals(kept.kind())
                        ? "CEF"
                        : xsltRole(code);
        refuseWithoutRequiredImports(context.session(), code, build, role);
        final XsltDoc saved = guard.createFilled(XsltDoc.TYPE, name, build, ref -> {
            final XsltDoc made = read(ref.getUuid());
            final String data = code;
            String description = described(made.getDescription(), kept);
            description = pending(context, description, null, NullSafe.nonBlankStringElse(change, "Created"), data);
            description = XsltVersionHistory.preview(description, data, Instant.now());
            return write(made.copy().data(data).description(description).build());
        }, false, role);
        tagKept(saved, kept != null);
        return plan.withNext(context, build, summary(saved));
    }

    /// Replaces an XSLT's code, after check_xslt passes (`update_xslt`). Only XSLTs the tools created (including
    /// working copies of production XSLTs) can be changed. Code that undoes an edit made by hand since the tools
    /// saved the XSLT is refused (unless `discardHandEdit`: the user said to drop it); where the change is to a field
    /// the edit changed too, the user is asked whether to keep their edit or overwrite it.
    ///
    /// @param call            The call, for the user's form.
    /// @param context         The call's context.
    /// @param uuid            The XSLT.
    /// @param code            The complete new XSLT.
    /// @param kept            The mapping or plan the new code was generated from, or null.
    /// @param change          What the change is, for the version history, or null (`Changed`).
    /// @param discardHandEdit True only when the user said to drop their hand edit.
    /// @param handEditChoices The user's answers from an earlier `needs_guidance` reply, or null.
    /// @return The saved XSLT's `type`, `uuid`, `name` and `version`, with the build's next step; or the question
    ///         about hand edits to put to the user (`status: needs_guidance`, nothing saved).
    /// @throws ToolRefusal if it can't be read, check_xslt fails, the tools didn't create it, a required import is
    ///                     missing, or the code undoes a hand edit.
    public Map<String, Object> update(final ToolCall call,
                                      final ToolCallContext context,
                                      final String uuid,
                                      final String code,
                                      final KeptMapping.Kept kept,
                                      final String change,
                                      final boolean discardHandEdit,
                                      final Map<String, String> handEditChoices) {
        final XsltDoc doc = read(uuid);
        checked(code, null, doc.getData());
        final DocRef ref = new DocRef(XsltDoc.TYPE, uuid, doc.getName());
        guard.checkManaged(context.session(), ref);
        final Optional<String> build = guard.buildOf(ref);
        if (build.isPresent()) {
            refuseWithoutRequiredImports(context.session(), code, build.get(), kept != null
                                                                               && INDEX.equals(kept.kind())
                    ? "Indexing"
                    : kept != null && CEF.equals(kept.kind())
                            ? "CEF"
                            : null);
        }
        if (!discardHandEdit) {
            final Optional<Map<String, Object>> gate = handEditGate(call, context, doc, code, kept,
                    handEditChoices);
            if (gate.isPresent()) {
                return gate.get();
            }
        }
        final XsltVersionHistory.Previous previous = new XsltVersionHistory.Previous(doc.getData(),
                doc.getUpdateUser(), doc.getUpdateTimeMs());
        // The history lives in the description, not the code: one an earlier version kept in the code moves there.
        String description = XsltVersionHistory.adopt(doc.getDescription(), doc.getData());
        final String data = XsltVersionHistory.strip(code);
        description = described(description, kept);
        description = pending(context, description, previous, NullSafe.nonBlankStringElse(change, "Changed"), data);
        description = XsltVersionHistory.preview(description, data, Instant.now());
        final XsltDoc saved = write(doc.copy().data(data).description(description).build());
        tagKept(saved, kept != null);
        final Map<String, Object> summary = summary(saved);
        return plan.nextFor(context, ref, summary);
    }

    /// Why an XSLT has no kept mapping, when it should have one: tagged as saved with one, and none reads now
    /// (`lost_mapping`).
    ///
    /// @param ref         The XSLT.
    /// @param description Its description.
    /// @return Why, or empty if it has a mapping or was never saved with one.
    public Optional<String> lostMapping(final DocRef ref, final String description) {
        if (KeptMapping.readMapping(description).isPresent()) {
            return Optional.empty();
        }
        final Set<String> tags;
        try {
            tags = guard.tags(ref);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        if (!tags.contains(WriteGuard.KEPT_MAPPING)) {
            return Optional.empty();
        }
        return Optional.of(String.format(LOST, NullSafe.nonBlankStringElse(ref.getType(), XsltDoc.TYPE),
                ref.getName(), "mapping (or plan)", ref.getUuid()));
    }

    /// The imports standing instructions require of a stage's XSLTs, for the build (`_required_for`).
    ///
    /// @param session The session.
    /// @param stage   The stage, e.g. `cef`.
    /// @param build   The build, or null.
    /// @return The required imports; none if they can't be read.
    public List<RequiredImport> requiredFor(final ToolSession session, final String stage, final String build) {
        try {
            final List<String> folders = build == null
                    ? List.of()
                    : guard.buildFolder(build, false).map(folder -> List.of(folder.path())).orElse(List.of());
            return agentInstructions.importsRequired(session, stage, folders, List.of());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the imports required of " + stage + " XSLTs", e);
            return List.of();
        }
    }

    /// The build subfolder an XSLT belongs in, from what it writes (`xslt_role`).
    ///
    /// @param code An XSLT.
    /// @return `Events` (a translation), `Reference`, `CEF` (CEF lines or Kafka records), `Indexing` (documents for
    /// an index, from Events), `Records` (records from a source's own), or null when it can't be told.
    static String xsltRole(final String code) {
        if (EVENTS.matcher(code).find()) {
            return "Events";
        }
        if (code.contains("<referenceData") || code.contains("reference-data:2")) {
            return "Reference";
        }
        if (code.contains("kafka-records") || TEXT_OUTPUT.matcher(code).find()) {
            return "CEF";
        }
        if (ARRAY.matcher(code).find() || (RECORDS.matcher(code).find() && code.contains("event-logging:"))) {
            // Documents for an index, from Events.
            return "Indexing";
        }
        if (RECORDS.matcher(code).find()) {
            // Records from a source's own: written as a Records stream.
            return "Records";
        }
        return null;
    }

    // check_xslt, or a refusal. previous: the code being replaced, whose refused calls stay allowed: a working copy
    // keeps what its production XSLT already did, written by a person.
    private void checked(final String xslt, final String build, final String previous) {
        String inputNamespace = null;
        if (build != null) {
            // A source's own XML in no namespace: its <records><record> are not a Data Splitter's records:2.
            Optional<Map<String, Object>> sample;
            try {
                sample = plan.sampleFormat(build);
            } catch (final RuntimeException e) {
                sample = Optional.empty();
            }
            if (sample.isPresent() && "xml".equals(sample.get().get("format"))) {
                inputNamespace = NullSafe.getOrElse(sample.get().get("namespace"), String::valueOf, "");
            }
        }
        final Map<String, Object> result = checkXslt.check(xslt, null, inputNamespace);
        List<String> errors = NullSafe.list((List<?>) result.get("errors")).stream().map(String::valueOf).toList();
        if (previous != null && !previous.isEmpty()) {
            final Set<String> kept = unsafe(previous).stream()
                    .map(XsltSafety.Unsafe::what)
                    .collect(Collectors.toSet());
            final Set<String> allowed = unsafe(XsltChecks.unescaped(xslt).text()).stream()
                    .filter(unsafe -> kept.contains(unsafe.what()))
                    .map(XsltSafety.Unsafe::why)
                    .collect(Collectors.toSet());
            errors = errors.stream().filter(error -> !allowed.contains(error)).toList();
        }
        if (!errors.isEmpty()) {
            throw new ToolRefusal("XSLT not saved: " + String.join("; ", errors));
        }
    }

    private static List<XsltSafety.Unsafe> unsafe(final String code) {
        try {
            return XsltSafety.unsafeInXslt(code);
        } catch (final RuntimeException e) {
            return List.of();
        }
    }

    // An XSLT of a stage whose standing instructions require shared imports, without them, is refused (asked for by
    // the user): generated ones get them from the generator; one written by hand must add them.
    private void refuseWithoutRequiredImports(final ToolSession session,
                                              final String code,
                                              final String build,
                                              final String role) {
        final String stage = ROLE_STAGE.get(NullSafe.nonBlankStringElse(role, NullSafe.string(xsltRole(code))));
        if (stage == null) {
            return;
        }
        final List<RequiredImport> required = requiredFor(session, stage, build);
        final Set<String> have = new LinkedHashSet<>(SharedXslt.importsOf(code));
        final List<RequiredImport> missing = required.stream().filter(r -> !have.contains(r.href())).toList();
        if (missing.isEmpty()) {
            return;
        }
        final Set<String> from = new TreeSet<>();
        missing.forEach(r -> from.add(NullSafe.nonBlankStringElse(r.from(), "(root)")));
        throw new ToolRefusal("Standing instructions require every " + stage + " XSLT to import "
                              + missing.stream().map(r -> "'" + r.href() + "'").collect(Collectors.joining(", "))
                              + " (the AGENTS doc in " + String.join(", ", from) + "): add "
                              + missing.stream().map(r -> "<xsl:import href=\"" + r.href() + "\"/>")
                                      .collect(Collectors.joining(" "))
                              + " first in the stylesheet, or build it from a mapping (build_translation_xslt adds "
                              + "them)");
    }

    // The description carrying the mapping or plan the code came from.
    private static String described(final String description, final KeptMapping.Kept kept) {
        return kept == null
                ? description
                : KeptMapping.withMapping(description, kept.kind(), kept.payload());
    }

    // The description with this change pending: the build's changes become one line of the XSLT's version history
    // when the build is promoted, not a line a save. The saved code's digest goes with it: a later difference from
    // what its mapping generates is then a hand edit only when the code changed since; otherwise the generator did
    // (build_status said "edited by hand" after an upgrade).
    private String pending(final ToolCallContext context,
                           final String description,
                           final XsltVersionHistory.Previous previous,
                           final String change,
                           final String code) {
        return XsltVersionHistory.withPending(description, previous, user(), change, agentLine(context),
                Instant.now(), code);
    }

    // Who made an agent change: the tools and the client, as the session names it.
    private static String agentLine(final ToolCallContext context) {
        final String client = NullSafe.get(context, ToolCallContext::session, ToolSession::caller,
                ToolCaller::name);
        return "Stroom AI tools" + (NullSafe.isBlankString(client)
                ? ""
                : ", " + client);
    }

    private String user() {
        try {
            final UserRef userRef = securityContextProvider.get().getUserRef();
            return NullSafe.nonBlankStringElse(NullSafe.get(userRef, UserRef::getDisplayName),
                    NullSafe.nonBlankStringElse(NullSafe.get(userRef, UserRef::getSubjectId), "-"));
        } catch (final RuntimeException e) {
            return "-";
        }
    }

    // Tags an XSLT saved with its mapping, so a mapping later deleted from its Documentation tab is noticed. A tag
    // is a safeguard: the save stands without it.
    private void tagKept(final XsltDoc saved, final boolean kept) {
        if (!kept) {
            return;
        }
        try {
            guard.tag(List.of(new DocRef(XsltDoc.TYPE, saved.getUuid(), saved.getName())),
                    Set.of(WriteGuard.KEPT_MAPPING));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to tag XSLT " + saved.getUuid() + " as kept with its mapping", e);
        }
    }

    // Empty when new code keeps the edit made by hand since the tools saved the XSLT (doc, as it is now), or the
    // user said to overwrite what it undoes; otherwise the question to return (needs_guidance), or a refusal saying
    // what to carry. kept: the mapping or plan the new code is from, to tell which of its fields the agent's change
    // touches that the edit touched too: those the user decides.
    private Optional<Map<String, Object>> handEditGate(final ToolCall call,
                                                       final ToolCallContext context,
                                                       final XsltDoc doc,
                                                       final String code,
                                                       final KeptMapping.Kept proposed,
                                                       final Map<String, String> choices) {
        final String current = NullSafe.string(doc.getData());
        if (!XsltVersionHistory.untouched(doc.getDescription(), current).equals(Optional.of(false))) {
            // As the tools saved it, or saved before they recorded what (no edit to tell).
            return Optional.empty();
        }
        final Optional<KeptMapping.Kept> kept = KeptMapping.readMapping(doc.getDescription());
        final String base = kept.map(XsltSaves::keptCode).orElse(null);
        final List<HandEdits.Undone> items = HandEdits.undone(base, current, code);
        if (items.isEmpty()) {
            return Optional.empty();
        }
        final String by = NullSafe.isNonBlankString(doc.getUpdateUser())
                ? " (by " + doc.getUpdateUser() + ")"
                : "";
        final Map<String, HandEdits.Collision> clashes = kept.isPresent() && proposed != null
                                                         && proposed.kind().equals(kept.get().kind())
                ? HandEdits.collisions(proposed.kind(), kept.get().payload(), proposed.payload(), items)
                : Map.of();
        final Map<String, String> decided = remembered(context, doc.getUuid(), current);
        NullSafe.map(choices).forEach((label, choice) -> {
            if (clashes.containsKey(label) || "*".equals(label)) {
                decided.put(label, choice);
            }
        });
        final String name = doc.getName();
        // One question first (asked for by the user): overwrite the XSLT with the change, every hand edit dropped,
        // or decide field by field. Answered per field already, it isn't asked.
        String upfront = null;
        if (!clashes.isEmpty() && !decided.containsKey("*") && !decided.keySet().containsAll(clashes.keySet())) {
            final Set<String> hit = new LinkedHashSet<>();
            clashes.values().forEach(clash -> clash.undone().forEach(u -> hit.add(u.toString())));
            final Set<String> all = new LinkedHashSet<>();
            items.forEach(u -> all.add(u.toString()));
            all.removeAll(hit);
            final int others = all.size();
            upfront = "XSLT '" + name + "' was edited by hand in Stroom" + by + ", and the agent's change changes "
                      + clashes.size() + " field" + plural(clashes.size()) + " the edit changed too: "
                      + String.join(", ", clashes.keySet())
                      + (others > 0
                    ? " (the edit changed " + others + " other thing" + plural(others) + " as well)"
                    : "")
                      + ". Overwrite the XSLT with the change, dropping your hand edits, or decide field by field?";
            final Optional<String> chosen = consent.choose(call, context, "hand_edit", upfront,
                    List.of(OVERWRITE_ALL, PER_FIELD), "XSLT '" + name + "', edited by hand: overwrite it, or "
                                                       + "decide field by field?");
            if (chosen.isPresent()) {
                decided.put("*", OVERWRITE_ALL.equals(chosen.get())
                        ? "overwrite"
                        : "per_field");
                upfront = null;
            }
        }
        if ("overwrite".equals(decided.get("*"))) {
            return Optional.empty();
        }
        final List<Map<String, Object>> asked = new ArrayList<>();
        for (final Map.Entry<String, HandEdits.Collision> clash : clashes.entrySet()) {
            final String label = clash.getKey();
            if (decided.containsKey(label)) {
                continue;
            }
            final HandEdits.Collision collision = clash.getValue();
            final String edit = collision.undone().stream()
                    .limit(3)
                    .map(u -> (u.again()
                            ? "took out "
                            : "added ") + u.item())
                    .collect(Collectors.joining("; "));
            final String question = "XSLT '" + name + "', field " + label + ": you edited it by hand in Stroom" + by
                                    + ", which " + edit + ". The agent's change "
                                    + (NullSafe.isNonEmptyString(collision.proposes())
                    ? "proposes " + collision.proposes()
                    : "removes it")
                                    + (NullSafe.isNonEmptyString(collision.had())
                                       && NullSafe.isNonEmptyString(collision.proposes())
                    ? " (the plan had " + collision.had() + ")"
                    : "")
                                    + ". Keep your hand edit, or use the proposed field?";
            // Unanswered up front (no form): the per-field questions go to the agent with it, for one round in the
            // chat.
            final Optional<String> chosen = upfront != null
                    ? Optional.empty()
                    : consent.choose(call, context, "hand_edit", question, List.of(KEEP, OVERWRITE),
                            label + ", edited by hand: keep your edit, or use the proposed one?");
            if (chosen.isEmpty()) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("field", label);
                entry.put("question", question);
                entry.put("options", options("keep", KEEP, "overwrite", OVERWRITE));
                asked.add(entry);
            } else {
                decided.put(label, KEEP.equals(chosen.get())
                        ? "keep"
                        : "overwrite");
            }
        }
        if (!asked.isEmpty()) {
            return Optional.of(guidance(upfront, asked));
        }
        final List<String> overwritten = decided.entrySet().stream()
                .filter(e -> "overwrite".equals(e.getValue()) && clashes.containsKey(e.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        final List<HandEdits.Undone> left = items.stream()
                .filter(u -> overwritten.stream().noneMatch(label -> clashes.get(label).undone().contains(u)))
                .toList();
        if (left.isEmpty()) {
            return Optional.empty();
        }
        throw new ToolRefusal(notSaved(doc, by, kept.orElse(null), base, current, left, decided, clashes));
    }

    private static Map<String, Object> guidance(final String upfront, final List<Map<String, Object>> asked) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "needs_guidance");
        out.put("saved", null);
        if (upfront != null) {
            final Map<String, Object> handEdit = new LinkedHashMap<>();
            handEdit.put("question", upfront);
            handEdit.put("options", options("overwrite", OVERWRITE_ALL, "per_field", PER_FIELD));
            out.put("hand_edit", handEdit);
        }
        out.put("hand_edit_collisions", asked);
        out.put("hint", "Not saved: the user edited these fields by hand since the tools saved the XSLT, and your "
                        + "change changes them too. " + (upfront != null
                ? "Ask the user the hand_edit question first, exactly, offering both options. To overwrite: call "
                  + "again with the same arguments and hand_edit_choices={'*': 'overwrite'}. Field by field: ask "
                  + "each of the hand_edit_collisions questions too, "
                : "Ask the user each question exactly, offering both options, ")
                        + "then call again with the same arguments and hand_edit_choices={field: 'keep' or "
                        + "'overwrite'}. Don't choose for them.");
        return out;
    }

    private static String notSaved(final XsltDoc doc,
                                   final String by,
                                   final KeptMapping.Kept kept,
                                   final String base,
                                   final String current,
                                   final List<HandEdits.Undone> left,
                                   final Map<String, String> decided,
                                   final Map<String, HandEdits.Collision> clashes) {
        final String carry = kept != null && CARRY.containsKey(kept.kind())
                ? String.format(CARRY.get(kept.kind()), doc.getUuid())
                : "write code that keeps it";
        final List<String> keptFields = decided.entrySet().stream()
                .filter(e -> "keep".equals(e.getValue()) && clashes.containsKey(e.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        final String shown = kept != null && base != null
                ? " What the edit changed (- what its " + (TRANSLATION.equals(kept.kind())
                ? "mapping"
                : kept.kind() + " plan") + " generates, + the XSLT): "
                  + String.join(" | ", CodeDiff.diff(base, current)) + "."
                : "";
        final String them = keptFields.size() == 1
                ? "it"
                : "them";
        final String keeps = keptFields.isEmpty()
                ? ""
                : " The user keeps their edit of " + String.join(", ", keptFields) + ": leave your change to " + them
                  + " out, and make " + them + " as the edit has " + them + ".";
        return "Not saved: XSLT '" + doc.getName() + "' was edited by hand since the tools saved it" + by + ", and "
               + "this code undoes the edit: it " + left.stream().limit(8).map(Object::toString)
                       .collect(Collectors.joining("; ")) + "." + shown + keeps + " Tell the user, and " + carry
               + "; then save again. Only if the user says to drop their edit: discard_hand_edit=true.";
    }

    // What the mapping or plan kept with an XSLT generates now: the code the tools saved, but for their own
    // changes. Null when it no longer generates (the whole XSLT then counts as the edit's), or for a translation
    // (regenerated by the translation tools).
    private static String keptCode(final KeptMapping.Kept kept) {
        try {
            if (INDEX.equals(kept.kind())) {
                return FieldPlan.fromJson(kept.payload()).xslt();
            }
            if (CEF.equals(kept.kind())) {
                return CefPlan.fromJson(kept.payload()).xslt();
            }
        } catch (final RuntimeException e) {
            return null;
        }
        return null;
    }

    private Map<String, String> remembered(final ToolCallContext context, final String uuid, final String current) {
        final String session = NullSafe.get(context, ToolCallContext::session, ToolSession::id);
        return remembered.computeIfAbsent(List.of(NullSafe.string(session), user(), NullSafe.string(uuid),
                XsltCode.sha256(current).substring(0, 16)), key -> new LinkedHashMap<>());
    }

    private XsltDoc read(final String uuid) {
        return documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("XSLT " + uuid + " can't be read: it doesn't exist, or you "
                                                   + "don't have permission to view it"));
    }

    @SuppressWarnings("unchecked")
    private XsltDoc write(final XsltDoc doc) {
        final DocumentActionHandler<XsltDoc> handler = documentAccess.handler(XsltDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no XSLT store"));
        return handler.writeDocument(doc);
    }

    private static Map<String, Object> summary(final XsltDoc doc) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", NullSafe.nonBlankStringElse(doc.getType(), XsltDoc.TYPE));
        out.put("uuid", doc.getUuid());
        out.put("name", doc.getName());
        out.put("version", doc.getVersion());
        return out;
    }

    private static Map<String, Object> options(final String a, final String aSaid, final String b, final String bSaid) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(a, aSaid);
        out.put(b, bSaid);
        return out;
    }

    private static String plural(final int n) {
        return n > 1
                ? "s"
                : "";
    }
}
