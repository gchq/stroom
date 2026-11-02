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
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/// The event-logging versions this Stroom holds, and the one a build's translation writes.
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
    /// Why a version held in more than one XML schema document is a question.
    static final String DUPLICATES = "Stroom's schema filter validates with whichever document of a version it "
                                     + "lists first, unless the pipeline's schema group tells them apart.";
    /// What a kept translation mapping names the XML schema document it was generated against by, where this
    /// Stroom holds its version in more than one.
    public static final String KEPT_SCHEMA_UUID = "schema_uuid";
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

    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @param version An event-logging version this Stroom holds.
    /// @return Its schema, read from the XML schema document the user chose for the build where this Stroom holds
    /// the version in more than one.
    /// @throws ToolRefusal if this Stroom doesn't hold it, or it can't be read.
    public EventSchema eventSchema(final ToolCallContext context, final String build, final String version) {
        final Optional<String> uuid = chosenDoc(context, build, version);
        return uuid.isPresent()
                ? heldSchemas.eventSchema(version, uuid.get())
                : heldSchemas.eventSchema(version);
    }

    /// The schema a kept translation mapping was generated against: the XML schema document kept with it
    /// (`schema_uuid`) while this Stroom still holds it, else the first of the version, as Stroom takes.
    ///
    /// @param payload The kept `{schema_version, schema_uuid, mapping}`, or null.
    /// @param version The event-logging version it targets.
    /// @return Its schema.
    /// @throws ToolRefusal if this Stroom doesn't hold the version, or it can't be read.
    public EventSchema keptSchema(final Map<String, Object> payload, final String version) {
        final Optional<String> kept = keptDoc(payload, version);
        return kept.isPresent()
                ? heldSchemas.eventSchema(version, kept.get())
                : heldSchemas.eventSchema(version);
    }

    /// As [#keptSchema(Map, String)], falling back to the document the user chose for the build.
    ///
    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @param payload The kept `{schema_version, schema_uuid, mapping}`, or null.
    /// @param version The event-logging version it targets.
    /// @return Its schema.
    /// @throws ToolRefusal if this Stroom doesn't hold the version, or it can't be read.
    public EventSchema keptSchema(final ToolCallContext context,
                                  final String build,
                                  final Map<String, Object> payload,
                                  final String version) {
        final Optional<String> kept = keptDoc(payload, version);
        return kept.isPresent()
                ? heldSchemas.eventSchema(version, kept.get())
                : eventSchema(context, build, version);
    }

    /// @param payload The kept `{schema_version, schema_uuid, mapping}`, or null.
    /// @param version The event-logging version it targets.
    /// @return The UUID of the XML schema document kept with it, while this Stroom holds it for the version.
    public Optional<String> keptDoc(final Map<String, Object> payload, final String version) {
        final Object uuid = payload == null
                ? null
                : payload.get(KEPT_SCHEMA_UUID);
        if (!(uuid instanceof final String kept) || kept.isBlank()) {
            return Optional.empty();
        }
        return docsOf(version).stream()
                .map(XmlSchemaDoc::getUuid)
                .filter(kept::equals)
                .findFirst();
    }

    /// The XML schema documents Stroom's schema filter validates with for a schema group, where this Stroom holds
    /// an event-logging version in more than one: the one of that group, when only one is. A pipeline whose
    /// schema filter names a group is validated with that group's document; without one Stroom takes the first.
    ///
    /// @param schemaGroup The schema filter's schema group, or null.
    /// @return The UUID of the document, by system id; empty for no group.
    public Map<String, String> docsInGroup(final String schemaGroup) {
        if (NullSafe.isBlankString(schemaGroup)) {
            return Map.of();
        }
        final String group = schemaGroup.strip();
        final Map<String, String> docs = new LinkedHashMap<>();
        heldSchemas.all().forEach((systemId, held) -> {
            if (HeldSchemas.eventLoggingVersion(systemId) != null && held.size() > 1) {
                final List<XmlSchemaDoc> inGroup = held.stream()
                        .filter(doc -> group.equals(groupOf(doc)))
                        .toList();
                if (inGroup.size() == 1) {
                    docs.put(systemId, inGroup.getFirst().getUuid());
                }
            }
        });
        return docs;
    }

    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @return The UUID of the XML schema document the user chose for the build's version, by its system id, where
    /// this Stroom holds that version in more than one; empty otherwise.
    public Map<String, String> chosenDocs(final ToolCallContext context, final String build) {
        return chosenFor(context, build)
                .flatMap(kept -> chosenDoc(context, build, kept.version())
                        .map(uuid -> Map.of(HeldSchemas.eventLoggingSystemId(kept.version()), uuid)))
                .orElse(Map.of());
    }

    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @param version An event-logging version.
    /// @return The UUID of the XML schema document of that version the user chose for the build, while this Stroom
    /// still holds it; empty if none was chosen.
    public Optional<String> chosenDoc(final ToolCallContext context, final String build, final String version) {
        return chosenFor(context, build)
                .filter(kept -> kept.version().equals(version) && kept.uuid() != null)
                .map(Chosen::uuid)
                .filter(uuid -> docsOf(version).stream().anyMatch(doc -> uuid.equals(doc.getUuid())));
    }

    /// The schema group a translation pipeline's schema filter needs so that Stroom validates with the XML schema
    /// document the user chose: Stroom's schema filter takes the first document of a system id it lists, so only
    /// a schema group tells two apart.
    ///
    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @return The chosen document's schema group, where this Stroom holds the build's version in more than one
    /// document and their groups differ; empty when there is nothing to tell apart.
    /// @throws ToolRefusal if another document of the version shares the chosen one's schema group with different
    ///                     content: Stroom can't tell them apart when it runs.
    public Optional<String> schemaGroupFor(final ToolCallContext context, final String build) {
        final Optional<Chosen> kept = chosenFor(context, build);
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        final String version = kept.get().version();
        final List<XmlSchemaDoc> docs = docsOf(version);
        final Optional<String> uuid = chosenDoc(context, build, version);
        if (docs.size() < 2 || uuid.isEmpty()) {
            return Optional.empty();
        }
        final XmlSchemaDoc doc = docs.stream().filter(held -> uuid.get().equals(held.getUuid())).findFirst()
                .orElseThrow();
        // Copies with the same content validate alike: whichever Stroom takes, nothing to tell apart.
        final List<XmlSchemaDoc> differing = docs.stream()
                .filter(other -> !other.getUuid().equals(doc.getUuid()))
                .filter(other -> !NullSafe.string(other.getData()).equals(NullSafe.string(doc.getData())))
                .toList();
        if (differing.isEmpty()) {
            return Optional.empty();
        }
        final String group = groupOf(doc);
        final List<XmlSchemaDoc> sameGroup = differing.stream()
                .filter(other -> group.equals(groupOf(other)))
                .toList();
        if (group.isEmpty() || !sameGroup.isEmpty()) {
            throw new ToolRefusal("The user chose event-logging " + version + " from " + label(doc) + ", but "
                                  + differing.stream().map(this::label).collect(Collectors.joining(", "))
                                  + " holds the same version with different content, and "
                                  + (group.isEmpty()
                    ? "the chosen one has no schema group"
                    : "shares its schema group") + ". Stroom's schema filter takes whichever of a version it lists "
                                  + "first, and only a schema group of the chosen one's own makes it take that one. "
                                  + "Ask the user to delete the one not wanted, or give the chosen one a schema "
                                  + "group of its own, then call again.");
        }
        return Optional.of(group);
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
        final String doc = chosenDoc(context, build, version)
                .flatMap(uuid -> docsOf(version).stream().filter(held -> uuid.equals(held.getUuid())).findFirst())
                .map(held -> " The user chose its XML schema document " + label(held) + " of the "
                             + docsOf(version).size() + " this Stroom holds: don't ask them again.")
                .orElse("");
        if (FORM.equals(how)) {
            return Optional.of("The user chose event-logging " + version + " in the server's form: don't ask them "
                               + "for the version again." + doc);
        }
        if (ONLY.equals(how)) {
            // Never "nothing to ask" for a version held twice: which document was a question.
            return Optional.of(doc.isEmpty() && docsOf(version).size() < 2
                    ? version + " is the only event-logging version this Stroom holds: there is no version to ask "
                      + "about."
                    : version + " is the only event-logging version this Stroom holds." + doc);
        }
        return doc.isEmpty()
                ? Optional.empty()
                : Optional.of(doc.strip());
    }

    /// Remembers the version for the build, and how it was settled.
    ///
    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @param version The version.
    /// @param how     [#GIVEN], [#FORM] or [#ONLY].
    public void remember(final ToolCallContext context, final String build, final String version, final String how) {
        remember(context, build, version, how, null);
    }

    /// Remembers the version for the build, how it was settled, and the XML schema document chosen where this
    /// Stroom holds the version in more than one.
    ///
    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @param version The version.
    /// @param how     [#GIVEN], [#FORM] or [#ONLY].
    /// @param uuid    The UUID of the document chosen, or null.
    public void remember(final ToolCallContext context,
                         final String build,
                         final String version,
                         final String how,
                         final String uuid) {
        final String key = buildKey(context, build);
        chosen.put(sessionKey(context.session(), key), new Chosen(version, how, uuid));
        if (key.isEmpty()) {
            return;
        }
        try {
            // A build's record is made only in its folder: a call before the build has one keeps it in memory.
            if (guardProvider.get().buildFolder(key, false).isPresent()) {
                buildRecordsProvider.get().rememberEventLoggingVersion(key, version, how, uuid);
            }
        } catch (final RuntimeException e) {
            // A convenience: the session still remembers it.
            LOGGER.debug(() -> LogUtil.message(
                    "Couldn't keep the event-logging version in build {}'s record: {}",
                    key,
                    e.getMessage()),
                    e);
        }
    }

    /// The event-logging version a new translation writes: the one given, else the one the user chose for the
    /// build, else the one this Stroom holds if it holds only one, else the user's pick (or the question for the
    /// agent to put to them). Never a version this Stroom doesn't hold. Where this Stroom holds the version in
    /// more than one XML schema document (seen: imported twice, with different UUIDs), the user is also asked which
    /// document, unless they chose one for the build already, or the call gives its UUID as schema_version.
    ///
    /// @param call          The call, whose id tells its forms apart.
    /// @param context       The call's context.
    /// @param schemaVersion The version the call gives (or the UUID of one of its documents), or null.
    /// @param build         The build the call names, or null.
    /// @return The version, or the question to return.
    /// @throws ToolRefusal if the version given isn't held, or this Stroom holds none.
    public Target target(final ToolCall call,
                         final ToolCallContext context,
                         final String schemaVersion,
                         final String build) {
        final List<String> versions = eventVersions();
        if (NullSafe.isNonBlankString(schemaVersion)) {
            final Optional<XmlSchemaDoc> byUuid = eventDoc(schemaVersion.strip());
            if (byUuid.isPresent()) {
                final String version = HeldSchemas.eventLoggingVersion(byUuid.get().getSystemId());
                remember(context, build, version, GIVEN, byUuid.get().getUuid());
                return Target.of(version);
            }
            if (!versions.contains(schemaVersion)) {
                throw new ToolRefusal("This Stroom holds no event-logging schema v" + schemaVersion + "; it holds "
                                      + String.join(", ", versions) + ". Ask the user which to write ("
                                      + versions.getFirst() + ", the newest, recommended), not the nearest to the one "
                                      + "asked for, then call again with schema_version= their choice. "
                                      + ONE_NAMESPACE);
            }
            return withDoc(call, context, build, schemaVersion, GIVEN);
        }
        final Optional<Chosen> earlier = chosenFor(context, build);
        if (earlier.isPresent() && versions.contains(earlier.get().version())) {
            return withDoc(call, context, build, earlier.get().version(), earlier.get().how());
        }
        if (versions.size() == 1) {
            return withDoc(call, context, build, versions.getFirst(), ONLY);
        }
        final Target picked = ask(call, context, versions);
        return picked.version() == null
                ? picked
                : withDoc(call, context, build, picked.version(), FORM);
    }

    // The version settled, once the user has chosen which of its XML schema documents where it is held in more
    // than one; else the question for the agent to put to them (no forms).
    private Target withDoc(final ToolCall call,
                           final ToolCallContext context,
                           final String build,
                           final String version,
                           final String how) {
        final List<XmlSchemaDoc> docs = docsOf(version);
        final Optional<String> earlier = chosenDoc(context, build, version);
        if (docs.size() < 2 || earlier.isPresent()) {
            final Optional<Chosen> kept = chosenFor(context, build);
            final Chosen settled = new Chosen(version, how, earlier.orElse(null));
            if (kept.isEmpty() || !kept.get().equals(settled)) {
                remember(context, build, version, how, earlier.orElse(null));
            }
            return Target.of(version);
        }
        final Map<String, String> labels = new LinkedHashMap<>();
        docs.forEach(doc -> labels.put(label(doc), doc.getUuid()));
        final String question = docQuestion(version, docs.size());
        final Optional<String> picked = consent.choose(call, context, "event_logging_schema_document", question,
                List.copyOf(labels.keySet()), null);
        if (picked.isPresent()) {
            remember(context, build, version, how, labels.getOrDefault(picked.get(), picked.get()));
            return Target.of(version);
        }
        // The version is settled: the answer needs only the document.
        remember(context, build, version, how, null);
        final List<Map<String, Object>> options = docs.stream()
                .map(doc -> {
                    final Map<String, Object> option = new LinkedHashMap<>();
                    option.put("uuid", doc.getUuid());
                    option.put("path", heldSchemas.path(doc));
                    option.put("schema_group", NullSafe.string(doc.getSchemaGroup()));
                    return option;
                })
                .toList();
        final Map<String, Object> asked = new LinkedHashMap<>();
        asked.put("status", "needs_guidance");
        asked.put("question", question);
        asked.put("options", options);
        asked.put("hint", "Ask the user which of these XML schema documents of event-logging " + version + " the "
                          + "translation is checked against and its pipeline validates with, giving each one's path "
                          + "and schema group. " + DUPLICATES + " Then call again with schema_version= the UUID of "
                          + "the one they chose.");
        return new Target(null, asked);
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
                    .map(kept -> new Chosen(text(kept.get("version")), text(kept.get("how")),
                            text(kept.get("uuid"))));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Couldn't read build {}'s record for its event-logging version: {}",
                    key,
                    e.getMessage()),
                    e);
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

    // The XML schema documents holding a version (more than one where it was imported twice).
    private List<XmlSchemaDoc> docsOf(final String version) {
        return heldSchemas.documents(HeldSchemas.eventLoggingSystemId(version));
    }

    // The event-logging XML schema document of that UUID, where a call gives one as schema_version.
    private Optional<XmlSchemaDoc> eventDoc(final String uuid) {
        return heldSchemas.all().entrySet().stream()
                .filter(entry -> HeldSchemas.eventLoggingVersion(entry.getKey()) != null)
                .flatMap(entry -> entry.getValue().stream())
                .filter(doc -> uuid.equals(doc.getUuid()))
                .findFirst();
    }

    // A document as the user is shown it: where it is, its UUID and its schema group.
    private String label(final XmlSchemaDoc doc) {
        final String group = groupOf(doc);
        return heldSchemas.path(doc) + " (UUID " + doc.getUuid() + ", schema group " + (group.isEmpty()
                ? "none"
                : group) + ")";
    }

    private static String groupOf(final XmlSchemaDoc doc) {
        return NullSafe.string(doc.getSchemaGroup()).strip();
    }

    /// @param version An event-logging version.
    /// @param count   How many XML schema documents hold it.
    /// @return The question put to the user about which of them.
    static String docQuestion(final String version, final int count) {
        return "This Stroom holds event-logging " + version + " in " + count + " XML schema documents. Which should "
               + "the translation be checked against, and its pipeline validate with?";
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

    // A version chosen, how, and the UUID of the XML schema document chosen where it is held in more than one (or
    // null).
    private record Chosen(String version, String how, String uuid) {

    }
}
