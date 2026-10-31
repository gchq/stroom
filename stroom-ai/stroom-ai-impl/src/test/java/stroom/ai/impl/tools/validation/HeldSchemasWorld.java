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
import stroom.docref.DocRef;
import stroom.pipeline.cache.PoolItem;
import stroom.pipeline.cache.PoolKey;
import stroom.pipeline.cache.SchemaKey;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.cache.StoredSchema;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.xmlschema.shared.XmlSchemaDoc;

import org.mockito.Mockito;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

/// A Stroom holding XML schemas, for the validation tests: its XMLSchema documents (each viewable by the user),
/// read through mocks of the store and document access, and a schema pool that compiles each XSD as Stroom's
/// does.
public final class HeldSchemasWorld {

    /// The event-logging 4.1.0 XSD (a test fixture).
    public static final String XSD_4_1_0 = resource("event-logging-v4.1.0.xsd");

    private final XmlSchemaStore store = Mockito.mock(XmlSchemaStore.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final SchemaPool pool = Mockito.mock(SchemaPool.class);
    private final List<DocRef> refs = new ArrayList<>();
    private final HeldSchemas heldSchemas = new HeldSchemas(() -> store, documentAccess, () -> pool);

    public HeldSchemasWorld() {
        Mockito.when(store.list()).thenReturn(refs);
        Mockito.when(pool.borrowObject(Mockito.any(), Mockito.anyBoolean())).thenAnswer(invocation -> {
            final SchemaKey key = invocation.getArgument(0);
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                return new PoolItem<>(new PoolKey<>(key), new StoredSchema(
                        factory.newSchema(new StreamSource(new StringReader(key.getData()))),
                        new StoredErrorReceiver()));
            } catch (final SAXException e) {
                return new PoolItem<>(new PoolKey<>(key), new StoredSchema(null, new StoredErrorReceiver()));
            }
        });
    }

    /// Adds a schema the user may view.
    ///
    /// @param systemId  Its system id.
    /// @param namespace Its namespace.
    /// @param xsd       Its text.
    /// @return This world.
    public HeldSchemasWorld hold(final String systemId, final String namespace, final String xsd) {
        final DocRef ref = new DocRef(XmlSchemaDoc.TYPE, "schema-" + refs.size(), systemId);
        refs.add(ref);
        final XmlSchemaDoc doc = XmlSchemaDoc.builder().uuid(ref.getUuid()).name(ref.getName()).systemId(systemId)
                .namespaceURI(namespace).data(xsd).build();
        Mockito.when(documentAccess.read(ref, XmlSchemaDoc.class)).thenReturn(Optional.of(doc));
        return this;
    }

    /// Adds a schema the user may not view.
    ///
    /// @param systemId Its system id.
    /// @return This world.
    HeldSchemasWorld holdUnviewable(final String systemId) {
        final DocRef ref = new DocRef(XmlSchemaDoc.TYPE, "schema-" + refs.size(), systemId);
        refs.add(ref);
        Mockito.when(documentAccess.read(ref, XmlSchemaDoc.class)).thenReturn(Optional.empty());
        return this;
    }

    /// @return The schemas, as [HeldSchemas] holds them.
    public HeldSchemas heldSchemas() {
        return heldSchemas;
    }

    /// @return The mocked schema pool.
    SchemaPool pool() {
        return pool;
    }

    /// @param name A test resource's name, in this package.
    /// @return Its text.
    static String resource(final String name) {
        try (final InputStream in = HeldSchemasWorld.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("No resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
