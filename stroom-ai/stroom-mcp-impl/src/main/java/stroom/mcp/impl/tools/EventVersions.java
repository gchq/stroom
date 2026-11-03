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
import stroom.ai.impl.tools.validation.EventsNamespaces;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    /// The first question put to the user where this Stroom also holds a fork of the event-logging schema and
    /// nothing settles which the translation writes: the variant, by namespace, before any version, as a fork's
    /// versions are its own.
    static final String VARIANT_QUESTION = "Which Events schema should the translation write: event-logging, or the "
                                           + "environment's own fork?";
    private static final Pattern SYSTEM_ID_VERSION = Pattern.compile("-v(\\d+(?:\\.\\d+)*)\\.xsd");
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
    /// How a schema was settled: inferred from what the environment's translations already write
    /// ([EventsTargetInference]).
    static final String INFERRED = "inferred";
    /// The most sessions remembered: the least recently used are forgotten first.
    static final int MAX_SESSIONS = 10_000;

    private final HeldSchemas heldSchemas;
    private final Consent consent;
    private final BuildMemory buildMemory;
    private final Provider<WriteGuard> guardProvider;
    private final Provider<BuildRecords> buildRecordsProvider;
    private final Provider<EventsTargetInference> inferenceProvider;
    private final Map<String, Chosen> chosen = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Chosen> eldest) {
                    return size() > MAX_SESSIONS;
                }
            });
    // The variant (namespace) chosen for a build in a session, before its version is.
    private final Map<String, Variant> variants = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Variant> eldest) {
                    return size() > MAX_SESSIONS;
                }
            });

    /// @param heldSchemas          The event-logging schemas this Stroom holds.
    /// @param consent              Asks the user to pick a version.
    /// @param buildMemory          The build the session is working on, for a call that names none.
    /// @param guardProvider        Tells whether the build has a folder (and a record) yet.
    /// @param buildRecordsProvider Keeps the version chosen for a build.
    /// @param inferenceProvider    Infers the Events namespace the environment's translations write.
    @Inject
    EventVersions(final HeldSchemas heldSchemas,
                  final Consent consent,
                  final BuildMemory buildMemory,
                  final Provider<WriteGuard> guardProvider,
                  final Provider<BuildRecords> buildRecordsProvider,
                  final Provider<EventsTargetInference> inferenceProvider) {
        this.heldSchemas = heldSchemas;
        this.consent = consent;
        this.buildMemory = buildMemory;
        this.guardProvider = guardProvider;
        this.buildRecordsProvider = buildRecordsProvider;
        this.inferenceProvider = inferenceProvider;
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
        return Stream.concat(docsOf(version).stream(), heldSchemas.forks().stream())
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
                        .map(uuid -> Map.of(fork(uuid)
                                .map(doc -> doc.getSystemId().strip())
                                .orElseGet(() -> HeldSchemas.eventLoggingSystemId(kept.version())), uuid)))
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
                .filter(uuid -> fork(uuid).isPresent()
                                || docsOf(version).stream().anyMatch(doc -> uuid.equals(doc.getUuid())));
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
        final Optional<XmlSchemaDoc> chosenFork = uuid.flatMap(this::fork);
        if (chosenFork.isPresent()) {
            // A fork is in a schema group with event-logging (what makes it one): the pipeline validates with it.
            return Optional.of(groupOf(chosenFork.get()));
        }
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
        if (NullSafe.isBlankString(keptVersion)) {
            // A fork's code names no event-logging version: the fork's own.
            final Optional<XmlSchemaDoc> fork = forkWrittenBy(code);
            if (fork.isPresent()) {
                return forkVersion(fork.get());
            }
        }
        return heldSchemas.translationVersion(keptVersion, code);
    }

    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @return The fork of the event-logging schema chosen for the build, while this Stroom holds it; empty for
    /// event-logging, or nothing chosen.
    public Optional<XmlSchemaDoc> chosenFork(final ToolCallContext context, final String build) {
        return chosenFor(context, build)
                .map(Chosen::uuid)
                .flatMap(this::fork);
    }

    /// The fork of the event-logging schema an XSLT writes Events in: the one whose system id its
    /// xsi:schemaLocation names, else the only one this Stroom holds for the namespace it writes.
    ///
    /// @param code An XSLT, or null.
    /// @return The fork's document; empty if it writes event-logging:3, no Events, or a namespace no fork has.
    public Optional<XmlSchemaDoc> forkWrittenBy(final String code) {
        final List<XmlSchemaDoc> forks = heldSchemas.forks();
        if (forks.isEmpty() || NullSafe.isBlankString(code)) {
            return Optional.empty();
        }
        final Set<String> namespaces = new LinkedHashSet<>();
        forks.forEach(fork -> namespaces.add(NullSafe.string(fork.getNamespaceURI()).strip()));
        final Optional<String> written = EventsNamespaces.writtenBy(code, namespaces);
        if (written.isEmpty()) {
            return Optional.empty();
        }
        final List<XmlSchemaDoc> inNamespace = forks.stream()
                .filter(fork -> written.get().equals(NullSafe.string(fork.getNamespaceURI()).strip()))
                .toList();
        final String declared = HeldSchemas.declaredSystemId(code);
        return inNamespace.stream()
                .filter(fork -> fork.getSystemId().strip().equals(declared))
                .findFirst()
                .or(() -> inNamespace.size() == 1
                        ? Optional.of(inNamespace.getFirst())
                        : Optional.empty());
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
        final Optional<XmlSchemaDoc> chosenFork = chosenDoc(context, build, version).flatMap(this::fork);
        if (chosenFork.isPresent()) {
            final String which = "the environment's fork of the event-logging schema, " + forkLabel(chosenFork.get());
            if (INFERRED.equals(how)) {
                return Optional.of("The translation writes " + which + ", as the environment's translations already "
                                   + "do (their imports or template write it): don't ask the user which schema or "
                                   + "namespace. " + EventsNamespaces.SCHEMA_GROUP_NOTE);
            }
            if (FORM.equals(how)) {
                return Optional.of("The user chose " + which + " in the server's form: don't ask them again.");
            }
            return Optional.empty();
        }
        final Optional<String> said = settledEventLogging(context, build, version, how);
        final Variant variant = variants.get(sessionKey(context == null
                ? null
                : context.session(), buildKey(context, build)));
        if (variant != null && FORM.equals(variant.how()) && EventsNamespaces.isStandard(variant.namespace())) {
            // Chosen over the environment's fork: not to be asked again.
            return Optional.of(("The user chose event-logging (namespace " + EventsNamespaces.STANDARD + ") over "
                                + "the environment's fork in the server's form: don't ask them which schema again. "
                                + said.orElse("")).strip());
        }
        return said;
    }

    // What to tell the agent of an event-logging version settled without it.
    private Optional<String> settledEventLogging(final ToolCallContext context,
                                                 final String build,
                                                 final String version,
                                                 final String how) {
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

    /// The Events schema a new translation writes, settled in two steps, as a fork needn't share event-logging's
    /// versions: first its variant (the namespace: event-logging:3, or an environment's fork), then its version
    /// within that variant.
    ///
    /// The variant: the one given (a namespace, or a document's UUID), else the one chosen for the build, else the
    /// one the environment's translations already write ([EventsTargetInference]), else event-logging:3 where this
    /// Stroom holds no fork, else the user's pick. The version: the one given, else the only one the variant holds,
    /// else the user's pick. Never a version the variant doesn't hold. Where this Stroom holds an event-logging
    /// version in more than one XML schema document (seen: imported twice, with different UUIDs), the user is also
    /// asked which document, unless they chose one for the build already, or the call gives its UUID.
    ///
    /// @param call          The call, whose id tells its forms apart.
    /// @param context       The call's context.
    /// @param schemaVersion The version the call gives, a namespace, or the UUID of a schema document; or null.
    /// @param build         The build the call names, or null.
    /// @return The version, or the question to return.
    /// @throws ToolRefusal if the version given isn't held in its variant, or this Stroom holds none.
    public Target target(final ToolCall call,
                         final ToolCallContext context,
                         final String schemaVersion,
                         final String build) {
        final List<String> versions = eventVersions();
        final String given = NullSafe.isNonBlankString(schemaVersion)
                ? schemaVersion.strip()
                : null;
        if (given != null) {
            final Optional<XmlSchemaDoc> givenFork = fork(given);
            if (givenFork.isPresent()) {
                rememberVariant(context, build, new Variant(namespaceOf(givenFork.get()), GIVEN));
                return forkTarget(context, build, givenFork.get(), GIVEN);
            }
            final Optional<XmlSchemaDoc> byUuid = eventDoc(given);
            if (byUuid.isPresent()) {
                final String version = HeldSchemas.eventLoggingVersion(byUuid.get().getSystemId());
                rememberVariant(context, build, new Variant(EventsNamespaces.STANDARD, GIVEN));
                remember(context, build, version, GIVEN, byUuid.get().getUuid());
                return Target.of(version);
            }
            if (EventsNamespaces.isStandard(given) || !heldSchemas.forksIn(given).isEmpty()) {
                // The variant alone: its version next.
                final Variant variant = new Variant(given, GIVEN);
                rememberVariant(context, build, variant);
                return inVariant(call, context, build, variant, null);
            }
        } else {
            final Optional<Chosen> earlier = chosenFor(context, build);
            if (earlier.isPresent() && earlier.get().uuid() != null && fork(earlier.get().uuid()).isPresent()) {
                // A fork chosen for the build, still held.
                return Target.of(earlier.get().version());
            }
            if (earlier.isPresent() && versions.contains(earlier.get().version())) {
                return withDoc(call, context, build, earlier.get().version(), earlier.get().how());
            }
        }
        final Optional<Variant> variant = variant(context, build);
        if (variant.isEmpty()) {
            return askVariant(call, context, build, given);
        }
        return inVariant(call, context, build, variant.get(), given);
    }

    // The variant settled without asking: the one chosen for the build (a fork's document, or event-logging's
    // version), else the one the environment's translations write, else event-logging:3 where this Stroom holds no
    // fork; empty where it is the user's to choose.
    private Optional<Variant> variant(final ToolCallContext context, final String build) {
        final List<XmlSchemaDoc> forks = heldSchemas.forks();
        if (forks.isEmpty()) {
            return Optional.of(new Variant(EventsNamespaces.STANDARD, null));
        }
        final Variant remembered = variants.get(sessionKey(context == null
                ? null
                : context.session(), buildKey(context, build)));
        if (remembered != null) {
            return Optional.of(remembered);
        }
        final Optional<Chosen> earlier = chosenFor(context, build);
        if (earlier.isPresent()) {
            final Optional<XmlSchemaDoc> chosenFork = NullSafe.isNonBlankString(earlier.get().uuid())
                    ? fork(earlier.get().uuid())
                    : Optional.empty();
            return Optional.of(new Variant(chosenFork.map(EventVersions::namespaceOf)
                    .orElse(EventsNamespaces.STANDARD), earlier.get().how()));
        }
        // What the environment's translations already write settles it, without asking.
        final Optional<EventsTargetInference.Inferred> inferred = inferenceProvider.get()
                .infer(context == null
                        ? null
                        : context.session(), buildKey(context, build));
        if (inferred.isPresent() && (EventsNamespaces.isStandard(inferred.get().namespace())
                                     || !heldSchemas.forksIn(inferred.get().namespace()).isEmpty())) {
            return Optional.of(new Variant(inferred.get().namespace(), INFERRED));
        }
        return Optional.empty();
    }

    // The version within a variant: the one given, else the only one it holds, else the user's pick (or the
    // question for the agent to put to them).
    private Target inVariant(final ToolCall call,
                             final ToolCallContext context,
                             final String build,
                             final Variant variant,
                             final String version) {
        if (EventsNamespaces.isStandard(variant.namespace())) {
            final List<String> versions = eventVersions();
            if (version != null) {
                if (!versions.contains(version)) {
                    throw new ToolRefusal("This Stroom holds no event-logging schema v" + version + "; it holds "
                                          + String.join(", ", versions) + ". Ask the user which to write ("
                                          + versions.getFirst() + ", the newest, recommended), not the nearest to "
                                          + "the one asked for, then call again with schema_version= their choice. "
                                          + ONE_NAMESPACE);
                }
                return withDoc(call, context, build, version, GIVEN);
            }
            if (versions.size() == 1) {
                return withDoc(call, context, build, versions.getFirst(), ONLY);
            }
            final Target picked = askEventLoggingVersion(call, context, versions);
            return picked.version() == null
                    ? picked
                    : withDoc(call, context, build, picked.version(), FORM);
        }
        final List<XmlSchemaDoc> held = heldSchemas.forksIn(variant.namespace());
        final List<XmlSchemaDoc> ofVersion = version == null
                ? held
                : held.stream().filter(fork -> version.equals(forkVersion(fork))).toList();
        if (ofVersion.isEmpty()) {
            throw new ToolRefusal("This Stroom holds no version " + version + " of the environment's fork (namespace "
                                  + variant.namespace() + "); it holds " + String.join(", ", held.stream()
                    .map(this::forkVersion)
                    .distinct()
                    .toList()) + ". A fork's versions are its own, not event-logging's. Ask the user which to "
                                  + "write, then call again with schema_version= the uuid of that fork document.");
        }
        if (ofVersion.size() == 1) {
            final String how = version != null
                    ? GIVEN
                    : NullSafe.nonBlankStringElse(variant.how(), ONLY);
            return forkTarget(context, build, ofVersion.getFirst(), how);
        }
        return askForkVersion(call, context, build, variant.namespace(), ofVersion);
    }

    // A fork settled for the build: the version its Events are written with, and its document.
    private Target forkTarget(final ToolCallContext context,
                              final String build,
                              final XmlSchemaDoc fork,
                              final String how) {
        final String version = forkVersion(fork);
        remember(context, build, version, how, fork.getUuid());
        return Target.of(version);
    }

    // The Version a fork's Events are written with: its schema's version attribute, else the version its system id
    // names.
    private String forkVersion(final XmlSchemaDoc fork) {
        final String declared = heldSchemas.eventSchema(fork).version();
        if (NullSafe.isNonBlankString(declared)) {
            return declared.strip();
        }
        final Matcher matcher = SYSTEM_ID_VERSION.matcher(NullSafe.string(fork.getSystemId()));
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new ToolRefusal("The XML schema '" + fork.getSystemId() + "' (namespace " + fork.getNamespaceURI()
                              + ") declares no version, so the Version its Events are written with isn't known. Ask "
                              + "the user to set the schema's version attribute.");
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

    // The first step where it is uncertain: the variant (event-logging:3, or a fork's namespace) the user picks in
    // a form, then its version; or the question for the agent to put to them (no forms).
    private Target askVariant(final ToolCall call,
                              final ToolCallContext context,
                              final String build,
                              final String version) {
        final Map<String, String> labels = new LinkedHashMap<>();
        labels.put("event-logging, namespace " + EventsNamespaces.STANDARD + " (versions "
                   + String.join(", ", eventVersions()) + ")", EventsNamespaces.STANDARD);
        forkNamespaces().forEach((namespace, forks) -> labels.put("this environment's fork, namespace " + namespace
                                                                  + " (versions " + String.join(", ", forks.stream()
                .map(this::forkVersion)
                .distinct()
                .toList()) + ")", namespace));
        final Optional<String> picked = consent.choose(call, context, "events_schema_variant", VARIANT_QUESTION,
                List.copyOf(labels.keySet()), null);
        if (picked.isPresent()) {
            final Variant variant = new Variant(labels.get(picked.get()), FORM);
            rememberVariant(context, build, variant);
            return inVariant(call, context, build, variant, version);
        }
        final List<Map<String, Object>> options = new ArrayList<>();
        options.add(Map.of("namespace", EventsNamespaces.STANDARD, "schema", "event-logging", "versions",
                eventVersions()));
        forkNamespaces().forEach((namespace, forks) -> options.add(Map.of("namespace", namespace, "schema",
                "the environment's fork", "versions", forks.stream().map(this::forkVersion).distinct().toList())));
        final Map<String, Object> asked = new LinkedHashMap<>();
        asked.put("status", "needs_guidance");
        asked.put("question", VARIANT_QUESTION);
        asked.put("options", options);
        asked.put("hint", "This Stroom holds the environment's own fork of the event-logging schema, and nothing the "
                          + "environment's translations write settled which to use. First ask the user which schema "
                          + "the translation writes: event-logging (namespace " + EventsNamespaces.STANDARD + ") or "
                          + "the fork (as the environment's other feeds may), then call again with schema_version= "
                          + "the namespace chosen; its version is settled, or asked, next. A fork's versions are its "
                          + "own: don't ask for a version until the namespace is chosen.");
        return new Target(null, asked);
    }

    // The second step for event-logging: the version the user picks in a form, or the question for the agent to
    // put to them (no forms).
    private Target askEventLoggingVersion(final ToolCall call,
                                          final ToolCallContext context,
                                          final List<String> versions) {
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
        final Map<String, Object> asked = new LinkedHashMap<>();
        asked.put("status", "needs_guidance");
        asked.put("question", VERSION_QUESTION);
        asked.put("options", versions);
        asked.put("recommended", versions.getFirst());
        asked.put("hint", "Ask the user which event-logging schema version the translation should write, offering "
                          + "these versions (those this Stroom holds), " + versions.getFirst() + ", the newest, first "
                          + "and recommended. " + ONE_NAMESPACE + " Then call again with schema_version= their "
                          + "choice.");
        return new Target(null, asked);
    }

    // The second step for a fork held in more than one document: the one the user picks in a form, remembered for
    // the build with its document; or the question for the agent to put to them (no forms).
    private Target askForkVersion(final ToolCall call,
                                  final ToolCallContext context,
                                  final String build,
                                  final String namespace,
                                  final List<XmlSchemaDoc> forks) {
        final Map<String, XmlSchemaDoc> labels = new LinkedHashMap<>();
        forks.forEach(fork -> labels.put(forkVersion(fork) + ": " + heldSchemas.path(fork), fork));
        final String question = forkVersionQuestion(namespace);
        final Optional<String> picked = consent.choose(call, context, "events_fork_version", question,
                List.copyOf(labels.keySet()), null);
        if (picked.isPresent()) {
            return forkTarget(context, build, labels.get(picked.get()), FORM);
        }
        final Map<String, Object> asked = new LinkedHashMap<>();
        asked.put("status", "needs_guidance");
        asked.put("question", question);
        asked.put("forks", forks.stream()
                .map(fork -> {
                    final Map<String, Object> option = new LinkedHashMap<>();
                    option.put("uuid", fork.getUuid());
                    option.put("version", forkVersion(fork));
                    option.put("namespace", namespaceOf(fork));
                    option.put("path", heldSchemas.path(fork));
                    return option;
                })
                .toList());
        asked.put("hint", "The translation writes the environment's fork (namespace " + namespace + "), which this "
                          + "Stroom holds in more than one document (forks). Ask the user which version to write, "
                          + "giving each one's version and path, then call again with schema_version= the uuid of "
                          + "the one chosen.");
        return new Target(null, asked);
    }

    /// @param namespace A fork's namespace.
    /// @return The question put to the user about which of its versions to write.
    static String forkVersionQuestion(final String namespace) {
        return "Which version of the environment's fork (namespace " + namespace + ") should the translation write?";
    }

    // The forks this Stroom holds, by namespace.
    private Map<String, List<XmlSchemaDoc>> forkNamespaces() {
        final Map<String, List<XmlSchemaDoc>> byNamespace = new LinkedHashMap<>();
        heldSchemas.forks().forEach(fork -> byNamespace.computeIfAbsent(namespaceOf(fork), k -> new ArrayList<>())
                .add(fork));
        return byNamespace;
    }

    // Remembers the variant chosen for the build in the session, so that a version given next is read within it.
    private void rememberVariant(final ToolCallContext context, final String build, final Variant variant) {
        variants.put(sessionKey(context == null
                ? null
                : context.session(), buildKey(context, build)), variant);
    }

    private static String namespaceOf(final XmlSchemaDoc fork) {
        return NullSafe.string(fork.getNamespaceURI()).strip();
    }

    // A fork as the user is shown it: where it is, and its namespace.
    private String forkLabel(final XmlSchemaDoc fork) {
        return heldSchemas.path(fork) + " (this environment's fork, namespace "
               + NullSafe.string(fork.getNamespaceURI()).strip() + ")";
    }

    // A fork this Stroom holds, by its document's UUID.
    private Optional<XmlSchemaDoc> fork(final String uuid) {
        return heldSchemas.forks().stream()
                .filter(doc -> uuid.equals(doc.getUuid()))
                .findFirst();
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

    // A variant of the Events schema, by its namespace, and how it was settled (or null).
    private record Variant(String namespace, String how) {

    }
}
