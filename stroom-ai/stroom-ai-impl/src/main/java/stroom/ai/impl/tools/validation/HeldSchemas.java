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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.pipeline.cache.PoolItem;
import stroom.pipeline.cache.SchemaKey;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.cache.StoredSchema;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;

/// The XML Schemas this Stroom holds (its XMLSchema documents), as the user may read them, and validation against
/// them, as Stroom's schema filter validates: each schema is found by its system id (e.g.
/// `file://event-logging-v4.1.0.xsd`) and compiled and cached by Stroom's schema pool. It also gives what the
/// checks need: the event-logging versions held, and the schema model of a version ([EventSchema]).
///
/// Stroom resolves an XML document's xsi:schemaLocation against its XMLSchema documents by system id, so doing the
/// same here validates against exactly what the instance has installed. Only schemas the user can view are held
/// for them.
@Singleton
public class HeldSchemas {

    /// The XML Schema instance namespace.
    public static final String XSI = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    /// The event-logging namespace (of every version, 4.x as much as 3.x).
    public static final String EVENT_LOGGING_NS = "event-logging:3";
    /// The namespace of a Data Splitter's (and a Lucene indexing XSLT's) records.
    public static final String RECORDS_NS = "records:2";
    /// The system id of the records schema Stroom ships.
    public static final String RECORDS_SYSTEM_ID = "file://records-v2.0.xsd";
    /// Why a schema location Stroom doesn't hold misleads. Seen on Stroom 7.13: output naming
    /// file:///event-logging-v4.1.0.xsd (three slashes) or event-logging-v4.1.0.xsd (no file:) was not refused as
    /// an unknown schema; the schema filter validated it against event-logging 3.2.3's instead, whose errors
    /// ("Value '4.1.0' is not facet-valid ... [3.0.0, ..., 3.2.3]") hide the cause.
    public static final String FALLBACK = "Stroom matches the system id exactly, and for one it doesn't hold its "
                                          + "schema filter validates against another schema of the namespace "
                                          + "(seen: event-logging 3.2.3's, refusing Version 4.1.0), so the errors "
                                          + "mislead.";
    /// What is said when this Stroom holds no event-logging schema.
    public static final String NO_EVENT_LOGGING = "This Stroom holds no event-logging XML schema (System/XML "
                                                  + "Schemas): ask the user to import Stroom's event-logging schema "
                                                  + "pack first.";
    /// The most errors a validation gives.
    public static final int MAX_ERRORS = 50;
    /// The most event-logging schema models kept, the least recently used dropped first.
    static final int MAX_EVENT_SCHEMAS = 8;

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HeldSchemas.class);
    private static final Pattern SCHEMA_LOCATION = Pattern.compile("schemaLocation=\"\\s*(\\S+)\\s+(\\S+)\\s*\"");
    private static final Pattern EVENT_LOGGING_VERSION = Pattern.compile("event-logging-v(\\d+(?:\\.\\d+)*)\\.xsd");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern FILE_PREFIX = Pattern.compile("^file:/*");

    private final Provider<XmlSchemaStore> xmlSchemaStoreProvider;
    private final DocumentAccess documentAccess;
    private final Provider<SchemaPool> schemaPoolProvider;
    // Each XSD's schema model, by its text: an XSD is parsed once however many users read it. Only the most
    // recently used are kept: each edit of an XSD is a new text.
    private final Map<String, EventSchema> eventSchemas = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, EventSchema> eldest) {
                    return size() > MAX_EVENT_SCHEMAS;
                }
            });

    /// @param xmlSchemaStoreProvider Lists Stroom's XMLSchema documents.
    /// @param documentAccess         Checks the user may view each, and reads it.
    /// @param schemaPoolProvider     Stroom's pool of compiled schemas.
    @Inject
    public HeldSchemas(final Provider<XmlSchemaStore> xmlSchemaStoreProvider,
                       final DocumentAccess documentAccess,
                       final Provider<SchemaPool> schemaPoolProvider) {
        this.xmlSchemaStoreProvider = xmlSchemaStoreProvider;
        this.documentAccess = documentAccess;
        this.schemaPoolProvider = schemaPoolProvider;
    }

    /// @return The XMLSchema documents the user may view, by system id (the first of a system id kept).
    public Map<String, XmlSchemaDoc> index() {
        final Map<String, XmlSchemaDoc> index = new TreeMap<>();
        final List<DocRef> refs;
        try {
            refs = NullSafe.list(xmlSchemaStoreProvider.get().list());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to list the XML schemas: {}", e.getMessage()), e);
            return index;
        }
        for (final DocRef ref : refs) {
            // Deleted since it was listed, or not this user's to read: not one of them.
            documentAccess.read(ref, XmlSchemaDoc.class)
                    .filter(doc -> NullSafe.isNonBlankString(doc.getSystemId()))
                    .ifPresent(doc -> index.putIfAbsent(doc.getSystemId().strip(), doc));
        }
        return index;
    }

    /// @return The system ids of the schemas the user may view, sorted.
    public List<String> systemIds() {
        return List.copyOf(index().keySet());
    }

    /// @return The event-logging versions this Stroom holds (that the user may view), newest first; empty if it
    /// holds none.
    public List<String> heldEventVersions() {
        return newestFirst(index().keySet().stream()
                .map(HeldSchemas::eventLoggingVersion)
                .filter(Objects::nonNull)
                .toList());
    }

    /// The event-logging versions this Stroom holds.
    ///
    /// @return The event-logging versions this Stroom holds, newest first.
    /// @throws ToolRefusal if it holds none.
    public List<String> eventVersions() {
        final List<String> versions = heldEventVersions();
        if (versions.isEmpty()) {
            throw new ToolRefusal(NO_EVENT_LOGGING);
        }
        return versions;
    }

    /// The event-logging version to read the schema of where nothing names one.
    ///
    /// @param chosen The version the user chose for the build, or null if none was chosen.
    /// @return It, else the newest this Stroom holds.
    /// @throws ToolRefusal if none was chosen and Stroom holds no event-logging schema.
    public String chosenOrLatest(final String chosen) {
        return NullSafe.isNonBlankString(chosen)
                ? chosen
                : eventVersions().getFirst();
    }

    /// The event-logging version an existing translation targets.
    ///
    /// @param keptVersion The `schema_version` kept with its mapping, or null.
    /// @param code        Its code, or null.
    /// @return The version kept with its mapping, else the one its code writes, else the newest this Stroom holds.
    /// @throws ToolRefusal if it comes to the newest and Stroom holds none.
    public String translationVersion(final String keptVersion, final String code) {
        if (NullSafe.isNonBlankString(keptVersion)) {
            return keptVersion;
        }
        final String written = eventLoggingVersion(code);
        return written != null
                ? written
                : eventVersions().getFirst();
    }

    /// The xsi:schemaLocation pairs in a document (an XSLT's literal output elements too) naming a system id this
    /// Stroom holds no schema for, in a namespace it holds schemas for: Stroom would validate against another of
    /// them. A namespace it holds none for (or schemas this user can't read) is not judged, nor is a value
    /// template, known only when it runs.
    ///
    /// @param root A document's root element.
    /// @return Why each such location fails, and what to write instead.
    public List<String> locationProblems(final Element root) {
        final Map<String, XmlSchemaDoc> index = index();
        final Map<String, List<String>> held = new LinkedHashMap<>();
        index.forEach((systemId, doc) -> {
            if (NullSafe.isNonBlankString(doc.getNamespaceURI())) {
                held.computeIfAbsent(doc.getNamespaceURI(), k -> new ArrayList<>()).add(systemId);
            }
        });
        final List<String> problems = new ArrayList<>();
        for (final Element element : XmlTrees.descendantsAndSelf(root)) {
            final String value = element.getAttributeNS(XSI, "schemaLocation");
            if (value.isEmpty() || value.contains("{")) {
                continue;
            }
            final List<String> parts = Arrays.stream(value.strip().split("\\s+")).toList();
            for (int i = 0; i + 1 < parts.size(); i += 2) {
                final String namespace = parts.get(i);
                final String systemId = parts.get(i + 1);
                if (!index.containsKey(systemId) && held.containsKey(namespace)) {
                    problems.add(unheldLocation(systemId, held.get(namespace)));
                }
            }
        }
        return problems;
    }

    /// @param systemId A schema's system id.
    /// @return The XMLSchema document holding it.
    /// @throws ToolRefusal if this Stroom holds no schema of that system id (that the user may view),
    /// saying what to write instead (`file://` and two slashes) or which event-logging versions it holds.
    public XmlSchemaDoc document(final String systemId) {
        final Map<String, XmlSchemaDoc> index = index();
        final XmlSchemaDoc doc = index.get(systemId);
        if (doc != null) {
            return doc;
        }
        final String fixed = twoSlashes(systemId);
        if (index.containsKey(fixed)) {
            throw new ToolRefusal(unheldLocation(systemId, List.of(fixed)));
        }
        final List<String> versions = newestFirst(index.keySet().stream()
                .map(HeldSchemas::eventLoggingVersion)
                .filter(Objects::nonNull)
                .toList());
        throw new ToolRefusal("Stroom has no XML schema '" + systemId + "'. Event-logging versions it "
                              + "holds, newest first: " + (versions.isEmpty()
                ? "none"
                : String.join(", ", versions)) + " (all in namespace event-logging:3, 4.x as much as 3.x)");
    }

    /// @param systemId A schema's system id.
    /// @return The XSD text itself.
    /// @throws ToolRefusal as [#document(String)].
    public String source(final String systemId) {
        return NullSafe.string(document(systemId).getData());
    }

    /// The model of an event-logging version's schema.
    ///
    /// @param version An event-logging version, e.g. `4.1.0`.
    /// @return The model of what its schema allows.
    /// @throws ToolRefusal if this Stroom holds no such schema, or it can't be read as one.
    public EventSchema eventSchema(final String version) {
        final String xsd = source(eventLoggingSystemId(version));
        try {
            final EventSchema known = eventSchemas.get(xsd);
            if (known != null) {
                return known;
            }
            // Parsed outside the map's lock, so one schema being read doesn't hold up the rest.
            final EventSchema parsed = EventSchema.parse(xsd);
            final EventSchema raced = eventSchemas.putIfAbsent(xsd, parsed);
            return raced != null
                    ? raced
                    : parsed;
        } catch (final RuntimeException e) {
            throw new ToolRefusal("The event-logging " + version + " schema in this Stroom can't be read: "
                                  + e.getMessage());
        }
    }

    /// @return How many schema models are kept, for tests.
    int eventSchemasKept() {
        return eventSchemas.size();
    }

    /// Validates XML against a schema this Stroom holds, compiled by Stroom's schema pool.
    ///
    /// @param systemId The schema's system id.
    /// @param xml      The document's text.
    /// @return Its errors; empty if it is valid.
    /// @throws ToolRefusal             if Stroom holds no such schema, or it doesn't compile.
    /// @throws XmlTrees.NotWellFormedException if the document is not well-formed.
    public List<SchemaErrors.SchemaError> validate(final String systemId, final String xml) {
        final String xsd = source(systemId);
        final SchemaPool pool = schemaPoolProvider.get();
        final SchemaKey key = new SchemaKey(XMLConstants.W3C_XML_SCHEMA_NS_URI, xsd, new FindXMLSchemaCriteria());
        final PoolItem<StoredSchema> item = pool.borrowObject(key, true);
        try {
            final Schema schema = NullSafe.get(item, PoolItem::getValue, StoredSchema::getSchema);
            if (schema == null) {
                final String why = NullSafe.stream(NullSafe.get(item, PoolItem::getValue,
                                StoredSchema::getErrorReceiver, StoredErrorReceiver::getList))
                        .map(Object::toString)
                        .collect(Collectors.joining("; "));
                throw new ToolRefusal("The schema '" + systemId + "' in this Stroom doesn't compile"
                                      + (NullSafe.isNonBlankString(why)
                        ? ": " + why.strip()
                        : ""));
            }
            return SchemaErrors.errorsFor(schema, xml);
        } finally {
            pool.returnObject(item, true);
        }
    }

    /// @param xml A document's text.
    /// @return The system id named in its xsi:schemaLocation (in its first 4,000 characters), or null.
    public static String declaredSystemId(final String xml) {
        final String head = NullSafe.string(xml).substring(0, Math.min(NullSafe.string(xml).length(), 4000));
        final Matcher matcher = SCHEMA_LOCATION.matcher(head);
        return matcher.find()
                ? matcher.group(2)
                : null;
    }

    /// @param version An event-logging version, e.g. `4.1.0`.
    /// @return Its schema's system id, e.g. `file://event-logging-v4.1.0.xsd`.
    public static String eventLoggingSystemId(final String version) {
        return "file://event-logging-v" + version + ".xsd";
    }

    /// @param text A system id, or an XSLT's or Events document's text, or null.
    /// @return The event-logging version it names (the first, for a document), e.g. `3.5.2`, or null.
    public static String eventLoggingVersion(final String text) {
        final Matcher matcher = EVENT_LOGGING_VERSION.matcher(NullSafe.string(text));
        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    /// @param versions Versions, e.g. `3.10.0` and `4.0.2`.
    /// @return Them once each, newest first, by number rather than as text.
    public static List<String> newestFirst(final Collection<String> versions) {
        final Set<String> unique = new LinkedHashSet<>(versions);
        return unique.stream()
                .sorted(Comparator.comparing(HeldSchemas::numbers, HeldSchemas::compareNumbers).reversed())
                .toList();
    }

    /// Why a schema location naming a system id Stroom doesn't hold fails, and what to write instead.
    ///
    /// @param systemId The system id named.
    /// @param held     The system ids Stroom holds for its namespace.
    /// @return The reason, e.g. "write 'file://event-logging-v4.1.0.xsd' (file: and two slashes)".
    public static String unheldLocation(final String systemId, final List<String> held) {
        final String fixed = twoSlashes(systemId);
        if (held.contains(fixed)) {
            return "xsi:schemaLocation names '" + systemId + "': write '" + fixed + "' (file: and two slashes). "
                   + FALLBACK;
        }
        final String ordered = held.stream()
                .sorted(Comparator.comparing(HeldSchemas::numbersIn, HeldSchemas::compareNumbers).reversed())
                .collect(Collectors.joining(", "));
        return "xsi:schemaLocation names '" + systemId + "', a schema this Stroom doesn't hold; for that namespace "
               + "it holds " + ordered + ". " + FALLBACK;
    }

    /// @param systemId A system id, e.g. `file:///x.xsd` or a bare `x.xsd`.
    /// @return It as Stroom names its schemas, `file://` and the file name.
    public static String twoSlashes(final String systemId) {
        return "file://" + FILE_PREFIX.matcher(systemId).replaceFirst("");
    }

    private static List<Integer> numbers(final String version) {
        return Arrays.stream(version.split("\\.")).map(Integer::valueOf).toList();
    }

    private static List<Integer> numbersIn(final String text) {
        final List<Integer> found = new ArrayList<>();
        final Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            found.add(Integer.valueOf(matcher.group()));
        }
        return found;
    }

    // Number by number; where one runs out first, the shorter is the lesser.
    private static int compareNumbers(final List<Integer> a, final List<Integer> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            final int compared = Integer.compare(a.get(i), b.get(i));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}
