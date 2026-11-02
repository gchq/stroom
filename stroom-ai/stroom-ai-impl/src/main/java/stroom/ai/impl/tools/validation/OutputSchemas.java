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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// Checks what a pipeline's XSLT writes for the element after it against the schema Stroom holds for its
/// namespace: records:2 (an IndexingFilter), reference-data:2 (a ReferenceDataFilter) and kafka-records:1 (a
/// StandardKafkaProducer). Those elements read only the elements they know and pass over the rest, with no error,
/// so without a schema filter in the pipeline, output in the wrong shape is lost silently (seen in Stroom's own
/// Indexing template, which has none). Event-logging Events are checked by check_events, and JSON by the schema
/// filter every Elasticsearch template has.
public class OutputSchemas {

    /// The namespaces checked.
    public static final Set<String> NAMESPACES = Set.of(HeldSchemas.RECORDS_NS, "reference-data:2",
            "kafka-records:1");

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OutputSchemas.class);

    private final HeldSchemas heldSchemas;

    /// @param heldSchemas The XML schemas Stroom holds.
    @Inject
    public OutputSchemas(final HeldSchemas heldSchemas) {
        this.heldSchemas = heldSchemas;
    }

    /// Validates output against the schema held for its root element's namespace, when that is one of
    /// [#NAMESPACES]: the one its xsi:schemaLocation names if that is held for the namespace, else the newest held
    /// for it.
    ///
    /// @param output An XSLT's output for one record.
    /// @return Its errors, with the namespace and schema; empty when its namespace isn't checked, Stroom holds no
    /// schema for it (the user may view), or it isn't well-formed (stepping reports that itself).
    public Optional<Failed> check(final String output) {
        final String namespace = rootNamespace(output);
        if (namespace == null || !NAMESPACES.contains(namespace)) {
            return Optional.empty();
        }
        final String systemId = systemIdFor(namespace, HeldSchemas.declaredSystemId(output));
        if (systemId == null) {
            return Optional.empty();
        }
        try {
            final List<SchemaErrors.SchemaError> errors = heldSchemas.validate(systemId, output);
            return errors.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Failed(namespace, systemId, errors));
        } catch (final XmlTrees.NotWellFormedException | ToolRefusal e) {
            LOGGER.debug("Output in {} not checked against {}: {}", namespace, systemId, e.getMessage());
            return Optional.empty();
        }
    }

    /// @param output XML, or null.
    /// @return The namespace of its root element, or null if it has none or isn't XML.
    static String rootNamespace(final String output) {
        if (NullSafe.isBlankString(output)) {
            return null;
        }
        XMLStreamReader reader = null;
        try {
            reader = factory().createXMLStreamReader(new StringReader(output));
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    return NullSafe.isBlankString(reader.getNamespaceURI())
                            ? null
                            : reader.getNamespaceURI();
                }
            }
            return null;
        } catch (final XMLStreamException e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (final XMLStreamException e) {
                    LOGGER.debug("Unable to close the reader", e);
                }
            }
        }
    }

    // The schema to validate against: the one named, if it is held for the namespace, else the newest held for it.
    private String systemIdFor(final String namespace, final String declared) {
        final Map<String, XmlSchemaDoc> held = heldSchemas.index();
        if (declared != null && held.get(declared) != null
            && namespace.equals(held.get(declared).getNamespaceURI())) {
            return declared;
        }
        final List<String> forNamespace = held.entrySet().stream()
                .filter(entry -> namespace.equals(entry.getValue().getNamespaceURI()))
                .map(Map.Entry::getKey)
                .toList();
        return forNamespace.isEmpty()
                ? null
                : HeldSchemas.newestSystemIdsFirst(forNamespace).getFirst();
    }

    private static XMLInputFactory factory() {
        final XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return factory;
    }

    // --------------------------------------------------------------------------------


    /// Output that fails the schema of its namespace.
    ///
    /// @param namespace The output's namespace.
    /// @param systemId  The schema it was validated against.
    /// @param errors    What the schema reports, in order.
    public record Failed(String namespace, String systemId, List<SchemaErrors.SchemaError> errors) {

    }
}
