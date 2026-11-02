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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.validation.SchemaErrors;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.pipeline.cache.PoolItem;
import stroom.pipeline.cache.SchemaKey;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.cache.StoredSchema;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.xml.converter.ds3.DS3ParserFactory;
import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.pipeline.xmlschema.XmlSchemaCache;
import stroom.pipeline.xmlschema.XmlSchemaCache.SchemaSet;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;

/// A Data Splitter checked as Stroom's DSParser checks one before it runs it ([DS3ParserFactory#configure], through
/// its schema filter): the converter must declare the `data-splitter:3` namespace and an `xsi:schemaLocation` for
/// it, Stroom must hold a schema of that namespace, and the converter must be valid against it. The schema is
/// found as the pipeline finds it, by namespace among all the schemas Stroom holds (as the processing user), not
/// among those the user may view. Without it Stroom's parser fails every stream (SchemaFilter's fatal errors), so
/// no Data Splitter can run there.
public class DataSplitterSchema {

    /// The namespace of a Data Splitter.
    public static final String NAMESPACE = DS3ParserFactory.NAMESPACE_URI;
    /// The system id of its schema, as the converters the tools write name it.
    public static final String SYSTEM_ID = DS3ParserFactory.SYSTEM_ID;
    /// How a Data Splitter's root element starts, for a converter that leaves out the namespace or schema location.
    public static final String ROOT = "<dataSplitter xmlns=\"" + NAMESPACE + "\" xmlns:xsi=\""
                                      + XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI + "\" xsi:schemaLocation=\""
                                      + NAMESPACE + " " + SYSTEM_ID + "\" version=\"3.0\">";
    /// What is said when Stroom holds no Data Splitter schema.
    public static final String NOT_HELD = "This Stroom holds no XML schema for the namespace " + NAMESPACE + " ("
                                          + "data-splitter-v3.0.xsd, in Stroom's core XML schemas content pack). "
                                          + "Stroom's DSParser checks every Data Splitter against it and fails the "
                                          + "stream without it, so no Data Splitter can run here until it is "
                                          + "installed: ask an administrator to import the core XML schemas content "
                                          + "pack.";

    // As Stroom's schema filter shortens the validator's messages: no `cvc-...:` code, no namespaces in quotes.
    private static final Pattern CVC = Pattern.compile("cvc-[^:]*:\\s*");
    private static final Pattern QUOTED_NAMESPACE = Pattern.compile("\"[^\"]*\":");

    private final Provider<XmlSchemaCache> schemaCacheProvider;
    private final Provider<SchemaPool> schemaPoolProvider;

    /// @param schemaCacheProvider The schemas Stroom holds, as its pipelines find them.
    /// @param schemaPoolProvider  Compiles a schema as Stroom does.
    @Inject
    public DataSplitterSchema(final Provider<XmlSchemaCache> schemaCacheProvider,
                              final Provider<SchemaPool> schemaPoolProvider) {
        this.schemaCacheProvider = schemaCacheProvider;
        this.schemaPoolProvider = schemaPoolProvider;
    }

    /// @param converter A Data Splitter's XML.
    /// @return What Stroom's DSParser would report reading it: nothing when it is valid; a fatal problem when Stroom
    /// would not run it at all (not well-formed, no namespace or schema location, no schema held, a schema that
    /// doesn't compile); else each schema error with its line and element.
    public Check check(final String converter) {
        final Element root;
        try {
            root = XmlTrees.parse(converter);
        } catch (final XmlTrees.NotWellFormedException e) {
            return Check.fatal("The Data Splitter is not well-formed XML (line " + e.getLine() + "): "
                               + e.getMessage());
        }
        final String namespace = root.getNamespaceURI();
        if (NullSafe.isBlankString(namespace)) {
            return Check.fatal("The Data Splitter's root element declares no namespace, which Stroom's DSParser "
                               + "refuses: start it with " + ROOT);
        }
        if (!NAMESPACE.equals(namespace)) {
            return Check.fatal("The Data Splitter's root element is in the namespace '" + namespace + "', not "
                               + NAMESPACE + ": start it with " + ROOT);
        }
        final String location = schemaLocations(root.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI,
                "schemaLocation")).get(NAMESPACE);
        if (location == null) {
            return Check.fatal("The Data Splitter has no xsi:schemaLocation for " + NAMESPACE + ", which Stroom's "
                               + "DSParser refuses (\"No schema locations specified\"): start it with " + ROOT);
        }
        final FindXMLSchemaCriteria criteria = new FindXMLSchemaCriteria();
        criteria.setNamespaceURI(NAMESPACE);
        final SchemaSet held = schemaCacheProvider.get().getSchemaSet(criteria);
        final XmlSchemaDoc doc = held == null
                ? null
                : held.getBestMatch(location, NAMESPACE);
        if (doc == null || NullSafe.isBlankString(doc.getData())) {
            return Check.fatal(NOT_HELD);
        }
        final SchemaPool pool = schemaPoolProvider.get();
        final PoolItem<StoredSchema> item = pool.borrowObject(new SchemaKey(XMLConstants.W3C_XML_SCHEMA_NS_URI,
                doc.getData(), criteria), true);
        try {
            final Schema schema = NullSafe.get(item, PoolItem::getValue, StoredSchema::getSchema);
            if (schema == null) {
                final String why = NullSafe.stream(NullSafe.get(item, PoolItem::getValue,
                                StoredSchema::getErrorReceiver, StoredErrorReceiver::getList))
                        .map(Object::toString)
                        .collect(Collectors.joining("; "));
                return Check.fatal("The " + NAMESPACE + " schema this Stroom holds (" + doc.getSystemId() + ") "
                                   + "doesn't compile, so Stroom's DSParser can't run any Data Splitter"
                                   + (NullSafe.isNonBlankString(why)
                        ? ": " + why.strip()
                        : ""));
            }
            final List<String> problems = new ArrayList<>();
            for (final SchemaErrors.SchemaError error : SchemaErrors.errorsFor(schema, converter)) {
                problems.add("line " + error.line() + " (" + error.path() + "): " + shorten(error.message()));
            }
            return new Check(problems, false);
        } finally {
            pool.returnObject(item, true);
        }
    }

    // The namespace to location pairs of an xsi:schemaLocation.
    private static Map<String, String> schemaLocations(final String value) {
        final Map<String, String> locations = new HashMap<>();
        final String[] parts = NullSafe.string(value).trim().split("\\s+");
        for (int i = 0; i + 1 < parts.length; i += 2) {
            locations.put(parts[i], parts[i + 1]);
        }
        return locations;
    }

    private static String shorten(final String message) {
        return QUOTED_NAMESPACE.matcher(CVC.matcher(NullSafe.string(message)).replaceAll("")).replaceAll("").strip();
    }


    // --------------------------------------------------------------------------------


    /// What checking a Data Splitter found.
    ///
    /// @param problems Each problem, with its line and element where it has one; empty when it is valid.
    /// @param fatal    Whether Stroom would not run it at all (a schema error alone still runs, with errors).
    public record Check(List<String> problems, boolean fatal) {

        /// @param problems Each problem.
        /// @param fatal    Whether Stroom would not run it at all.
        public Check {
            problems = List.copyOf(problems);
        }

        /// @param problem Why Stroom would not run it.
        /// @return A check that Stroom would not run it.
        static Check fatal(final String problem) {
            return new Check(List.of(problem), true);
        }

        /// @return Whether it is valid.
        public boolean valid() {
            return problems.isEmpty();
        }
    }
}
