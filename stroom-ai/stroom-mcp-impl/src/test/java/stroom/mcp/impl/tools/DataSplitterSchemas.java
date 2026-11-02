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

import stroom.ai.impl.tools.sample.DataSplitterSchema;
import stroom.pipeline.cache.PoolItem;
import stroom.pipeline.cache.PoolKey;
import stroom.pipeline.cache.SchemaKey;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.cache.StoredSchema;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.xmlschema.XmlSchemaCache;
import stroom.pipeline.xmlschema.XmlSchemaCache.SchemaSet;
import stroom.xmlschema.shared.XmlSchemaDoc;

import org.mockito.Mockito;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

/// As stroom-ai-impl's test helper of the same name: a [DataSplitterSchema] on a Stroom holding the Data Splitter
/// schema (Stroom's data-splitter-v3.0.xsd, a test fixture) or another, or none, as a test says, its schema pool
/// compiling the XSD as Stroom's does.
public final class DataSplitterSchemas {

    /// Stroom's data-splitter-v3.0.xsd (a test fixture).
    public static final String XSD = resource();

    private DataSplitterSchemas() {
        // Static only.
    }

    /// @param xsd The schema Stroom holds for data-splitter:3 when it is checked, or null for none.
    /// @return The check on that Stroom.
    public static DataSplitterSchema of(final Supplier<String> xsd) {
        final XmlSchemaCache cache = Mockito.mock(XmlSchemaCache.class);
        final SchemaSet set = Mockito.mock(SchemaSet.class);
        Mockito.when(cache.getSchemaSet(Mockito.any())).thenReturn(set);
        Mockito.when(set.getBestMatch(Mockito.any(), Mockito.eq(DataSplitterSchema.NAMESPACE))).thenAnswer(
                invocation -> xsd.get() == null
                        ? null
                        : XmlSchemaDoc.builder().uuid("data-splitter").name("data-splitter-v3.0")
                                .systemId(DataSplitterSchema.SYSTEM_ID).namespaceURI(DataSplitterSchema.NAMESPACE)
                                .data(xsd.get()).build());
        final SchemaPool pool = Mockito.mock(SchemaPool.class);
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
        return new DataSplitterSchema(() -> cache, () -> pool);
    }

    private static String resource() {
        try (final InputStream in = DataSplitterSchemas.class.getResourceAsStream("data-splitter-v3.0.xsd")) {
            if (in == null) {
                throw new IllegalStateException("No resource data-splitter-v3.0.xsd");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
