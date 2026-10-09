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

package stroom.sqlstore.impl.pipeline;

import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.MultiRefDataValueProxy;
import stroom.pipeline.refdata.store.RefDataStore.StorageType;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.offheapstore.TypedByteBuffer;
import stroom.util.shared.NullSafe;
import stroom.util.xml.XMLReaderPool;

import com.sun.xml.fastinfoset.sax.SAXDocumentSerializer;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class SqlStoreValueProxy implements RefDataValueProxy {

    private final String key;
    private final String rawValue;
    private final MapDefinition mapDefinition;

    private MapDefinition successfulMapDefinition = null;
    private RefDataValue resolvedValue = null;

    public SqlStoreValueProxy(final String key,
                              final String rawValue,
                              final MapDefinition mapDefinition) {
        this.key = key;
        this.rawValue = rawValue;
        this.mapDefinition = mapDefinition;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getMapName() {
        return mapDefinition.getMapName();
    }

    @Override
    public List<MapDefinition> getMapDefinitions() {
        return Collections.singletonList(mapDefinition);
    }

    @Override
    public Optional<MapDefinition> getSuccessfulMapDefinition() {
        return Optional.ofNullable(successfulMapDefinition);
    }

    @Override
    public Optional<RefDataValue> supplyValue() {
        if (resolvedValue == null) {
            resolvedValue = resolveValue();
        }
        return Optional.of(resolvedValue);
    }

    private RefDataValue resolveValue() {
        if (NullSafe.isEmptyString(rawValue)) {
            return new StringValue("");
        }

        final String trimmed = rawValue.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                final SAXDocumentSerializer serializer = new SAXDocumentSerializer();
                serializer.setOutputStream(bos);
                serializer.startDocument();

                // Borrowed rather than constructed. A value is resolved per lookup, so this runs
                // once per event on a reference-data path, and building a parser each time is the
                // sort of cost a pool exists for.
                XMLReaderPool.getDefault().use(xmlReader -> {
                    xmlReader.setContentHandler(serializer);
                    xmlReader.parse(new InputSource(new StringReader(trimmed)));
                    return null;
                });

                serializer.endDocument();
                return new FastInfosetValue(ByteBuffer.wrap(bos.toByteArray()));
            } catch (final Exception e) {
                return new StringValue(rawValue);
            }
        }

        return new StringValue(rawValue);
    }

    @Override
    public boolean consumeBytes(final Consumer<TypedByteBuffer> typedByteBufferConsumer) {
        final RefDataValue refDataValue = supplyValue().orElse(null);
        if (refDataValue instanceof FastInfosetValue) {
            typedByteBufferConsumer.accept(new TypedByteBuffer(
                    FastInfosetValue.TYPE_ID,
                    ((FastInfosetValue) refDataValue).getByteBuffer()));
        } else if (refDataValue instanceof StringValue) {
            typedByteBufferConsumer.accept(new TypedByteBuffer(
                    StringValue.TYPE_ID,
                    ByteBuffer.wrap(((StringValue) refDataValue).getValue().getBytes(StandardCharsets.UTF_8))));
        }
        return true;
    }

    @Override
    public boolean consumeValue(final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory) {
        final RefDataValueProxyConsumer consumer = refDataValueProxyConsumerFactory.getConsumer(StorageType.OFF_HEAP);
        try {
            final boolean wasFound = consumer.consume(this);
            if (wasFound) {
                successfulMapDefinition = mapDefinition;
            }
            return wasFound;
        } catch (final XPathException e) {
            throw new RuntimeException("Error consuming reference data value: " + e.getMessage(), e);
        }
    }

    @Override
    public RefDataValueProxy merge(final RefDataValueProxy additionalProxy) {
        return MultiRefDataValueProxy.merge(this, additionalProxy);
    }
}
