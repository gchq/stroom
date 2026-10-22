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
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The event-logging versions this Stroom holds, and the one a build's translation writes (the Stroom MCP server's
/// `tools/generation.py` `event_schema`, `event_versions`, `_remember_version`, `version_settled`,
/// `_version_chosen`, `chosen_or_latest`, `translation_version`, `_ask_version` and `target_version`).
///
/// The version the user chose for a build is kept in the build's record ([BuildRecords]), where the build has a
/// folder, so a later call (in this session or another) doesn't ask again what draft_translation_mapping asked; and
/// in this node's memory for the session, which is all a call without a build (or before its folder exists) has.
@Singleton
public class EventVersions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventVersions.class);

    /// The question put to the user.
    static final String VERSION_QUESTION = "Which event-logging schema version should the translation write?";
    /// Seen: an agent took the nearest 3.x to a missing 3.5.2 (3.4.2) where 4.1.0 was installed, as if namespace
    /// event-logging:3 bound it to 3.x.
    static final String ONE_NAMESPACE = "Every version, 4.x as much as 3.x, writes namespace event-logging:3: the "
                                        + "namespace doesn't limit the choice to 3.x.";
    /// How a version was settled: given in the call (schema_version).
    static final String GIVEN = "given";
    /// How a version was settled: the user picked it in the tools' form.
    static final String FORM = "form";
    /// How a version was settled: the one version this Stroom holds.
    static final String ONLY = "only";
    /// The most sessions remembered: the least recently used are forgotten first.
    static final int MAX_SESSIONS = 10_000;

    private final HeldSchemas heldSchemas;
    private final Consent consent;
    private final BuildMemory buildMemory;
    private final Provider<WriteGuard> guardProvider;
    private final Provider<BuildRecords> buildRecordsProvider;
    private final Map<String, Chosen> chosen = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Chosen> eldest) {
                    return size() > MAX_SESSIONS;
                }
            });

    /// @param heldSchemas          The event-logging schemas this Stroom holds.
    /// @param consent              Asks the user to pick a version.
    /// @param buildMemory          The build the session is working on, for a call that names none.
    /// @param guardProvider        Tells whether the build has a folder (and a record) yet.
    /// @param buildRecordsProvider Keeps the version chosen for a build.
    @Inject
    EventVersions(final HeldSchemas heldSchemas,
                  final Consent consent,
                  final BuildMemory buildMemory,
                  final Provider<WriteGuard> guardProvider,
                  final Provider<BuildRecords> buildRecordsProvider) {
        this.heldSchemas = heldSchemas;
        this.consent = consent;
        this.buildMemory = buildMemory;
        this.guardProvider = guardProvider;
        this.buildRecordsProvider = buildRecordsProvider;
    }

    /// @param version An event-logging version this Stroom holds.
    /// @return Its schema.
    /// @throws ToolRefusal if this Stroom doesn't hold it, or it can't be read.
    public EventSchema eventSchema(final String version) {
        return heldSchemas.eventSchema(version);
    }

    /// @return The event-logging versions this Stroom holds, newest first.
    /// @throws ToolRefusal if it holds none.
    public List<String> eventVersions() {
        return heldSchemas.eventVersions();
    }

    /// @param keptVersion The version kept with a translation's mapping, or null.
    /// @param code        The translation's code, or null.
    /// @return The event-logging version an existing translation targets: the one kept with its mapping, else the
    /// one its code writes, else the newest this Stroom holds.
    /// @throws ToolRefusal if it names none and this Stroom holds none.
    public String translationVersion(final String keptVersion, final String code) {
        return heldSchemas.translationVersion(keptVersion, code);
    }

    /// @param context The call's context.
    /// @param build   The build the call names, or null (the one the session works on).
    /// @return The version the user chose for the build, if one was.
    public Optional<String> chosen(final ToolCallContext context, final String build) {
        return chosenFor(context, build).map(Chosen::version);
    }

    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @return The event-logging version to read the schema of where nothing names one: the one the user chose
    /// for the build, else the newest this Stroom holds.
    /// @throws ToolRefusal if none was chosen and this Stroom holds none.
    public String chosenOrLatest(final ToolCallContext context, final String build) {
        return chosen(context, build).orElseGet(() -> eventVersions().getFirst());
    }

    /// For the agent, where the version was settled without it. Seen in Ask Stroom AI: the user picked 4.1.0 in
    /// the draft's form, and the agent, told only schema_version 4.1.0, asked them again with it as the one option.
    ///
    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @param version The version settled.
    /// @return What to tell the agent, or empty when it gave the version itself.
    public Optional<String> settled(final ToolCallContext context, final String build, final String version) {
        final String how = chosenFor(context, build).map(Chosen::how).orElse(null);
        if (FORM.equals(how)) {
            return Optional.of("The user chose event-logging " + version + " in the server's form: don't ask them "
                               + "for the version again.");
        }
        if (ONLY.equals(how)) {
            return Optional.of(version + " is the only event-logging version this Stroom holds: there is no version "
                               + "to ask about.");
        }
        return Optional.empty();
    }

    /// Remembers the version for the build, and how it was settled.
    ///
    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @param version The version.
    /// @param how     [#GIVEN], [#FORM] or [#ONLY].
    public void remember(final ToolCallContext context, final String build, final String version, final String how) {
        final String key = buildKey(context, build);
        chosen.put(sessionKey(context.session(), key), new Chosen(version, how));
        if (key.isEmpty()) {
            return;
        }
        try {
            // A build's record is made only in its folder: a call before the build has one keeps it in memory.
            if (guardProvider.get().buildFolder(key, false).isPresent()) {
                buildRecordsProvider.get().rememberEventLoggingVersion(key, version, how);
            }
        } catch (final RuntimeException e) {
            // A convenience: the session still remembers it.
            LOGGER.debug(() -> "Couldn't keep the event-logging version in build " + key + "'s record: "
                               + e.getMessage(), e);
        }
    }

    /// The event-logging version a new translation writes: the one given, else the one the user chose for the
    /// build, else the one this Stroom holds if it holds only one, else the user's pick (or the question for the
    /// agent to put to them). Never a version this Stroom doesn't hold.
    ///
    /// @param call          The call, whose id tells its forms apart.
    /// @param context       The call's context.
    /// @param schemaVersion The version the call gives, or null.
    /// @param build         The build the call names, or null.
    /// @return The version, or the question to return.
    /// @throws ToolRefusal if the version given isn't held, or this Stroom holds none.
    public Target target(final ToolCall call,
                         final ToolCallContext context,
                         final String schemaVersion,
                         final String build) {
        final List<String> versions = eventVersions();
        if (NullSafe.isNonBlankString(schemaVersion)) {
            if (!versions.contains(schemaVersion)) {
                throw new ToolRefusal("This Stroom holds no event-logging schema v" + schemaVersion + "; it holds "
                                      + String.join(", ", versions) + ". Ask the user which to write ("
                                      + versions.getFirst() + ", the newest, recommended), not the nearest to the one "
                                      + "asked for, then call again with schema_version= their choice. "
                                      + ONE_NAMESPACE);
            }
            remember(context, build, schemaVersion, GIVEN);
            return Target.of(schemaVersion);
        }
        final Optional<String> earlier = chosen(context, build);
        if (earlier.isPresent() && versions.contains(earlier.get())) {
            return Target.of(earlier.get());
        }
        final Target picked = versions.size() == 1
                ? Target.of(versions.getFirst())
                : ask(call, context, versions);
        if (picked.version() != null) {
            remember(context, build, picked.version(), versions.size() == 1
                    ? ONLY
                    : FORM);
        }
        return picked;
    }

    // The version the user picks in a form, or the question for the agent to put to them (no forms).
    private Target ask(final ToolCall call, final ToolCallContext context, final List<String> versions) {
        final Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < versions.size(); i++) {
            labels.put(i == 0
                    ? versions.get(i) + " (newest installed, recommended)"
                    : versions.get(i), versions.get(i));
        }
        final Optional<String> picked = consent.choose(call, context, "event_logging_version", VERSION_QUESTION,
                List.copyOf(labels.keySet()), null);
        if (picked.isPresent()) {
            return Target.of(labels.getOrDefault(picked.get(), picked.get()));
        }
        final Map<String, Object> question = new LinkedHashMap<>();
        question.put("status", "needs_guidance");
        question.put("question", VERSION_QUESTION);
        question.put("options", versions);
        question.put("recommended", versions.getFirst());
        question.put("hint", "Ask the user which event-logging schema version the translation should write, "
                             + "offering these versions (those this Stroom holds), " + versions.getFirst()
                             + ", the newest, first and recommended. " + ONE_NAMESPACE + " Then call again with "
                             + "schema_version= their choice.");
        return new Target(null, question);
    }

    // The session's memory first, then the build's record.
    private Optional<Chosen> chosenFor(final ToolCallContext context, final String build) {
        final String key = buildKey(context, build);
        final Chosen remembered = chosen.get(sessionKey(context.session(), key));
        if (remembered != null) {
            return Optional.of(remembered);
        }
        if (key.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (guardProvider.get().buildFolder(key, false).isEmpty()) {
                return Optional.empty();
            }
            return buildRecordsProvider.get().eventLoggingVersion(key)
                    .filter(kept -> NullSafe.isNonBlankString(text(kept.get("version"))))
                    .map(kept -> new Chosen(text(kept.get("version")), text(kept.get("how"))));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Couldn't read build " + key + "'s record for its event-logging version: "
                               + e.getMessage(), e);
            return Optional.empty();
        }
    }

    // The build named, else the one the session works on, else none ("").
    private String buildKey(final ToolCallContext context, final String build) {
        if (NullSafe.isNonBlankString(build)) {
            return build;
        }
        return buildMemory.current(context == null
                ? null
                : context.session()).orElse("");
    }

    private static String sessionKey(final ToolSession session, final String buildKey) {
        return (session == null
                ? "-"
                : session.kind() + ":" + session.id()) + "\n" + buildKey;
    }

    private static String text(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// The version a translation writes, or the question to ask first.
    ///
    /// @param version  The version, or null while it is asked.
    /// @param question What to return for the user to answer (`needs_guidance`), or null.
    public record Target(String version, Map<String, Object> question) {

        /// @param version A version.
        /// @return It, settled.
        static Target of(final String version) {
            return new Target(version, null);
        }
    }

    // A version chosen, and how.
    private record Chosen(String version, String how) {

    }
}
