/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.common.v2;

import stroom.query.language.functions.ValSerialiser;
import stroom.query.language.functions.ref.DataReader;
import stroom.query.language.functions.ref.DataWriter;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SimpleMetrics;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Output;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class KeyFactoryFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeyFactoryFactory.class);

    private KeyFactoryFactory() {
        // Non instantiable.
    }

    public static KeyFactory create(final KeyFactoryConfig keyFactoryConfig,
                                    final CompiledDepths compiledDepths) {
        final boolean flat = compiledDepths.getMaxDepth() == 0 &&
                             compiledDepths.getMaxGroupDepth() <= compiledDepths.getMaxDepth();
        if (flat) {
            if (keyFactoryConfig.addTimeToKey()) {
                if (compiledDepths.hasGroup()) {
                    return new FlatTimeGroupedKeyFactory();
                } else {
                    return new FlatTimeUngroupedKeyFactory();
                }
            } else {
                if (compiledDepths.hasGroup()) {
                    return new FlatGroupedKeyFactory();
                } else {
                    return new FlatUngroupedKeyFactory();
                }
            }
        } else {
            if (keyFactoryConfig.addTimeToKey()) {
                return new NestedTimeGroupedKeyFactory();

            } else {
                return new NestedGroupedKeyFactory();
            }
        }
    }

    private abstract static class AbstractKeyFactory implements KeyFactory {

        private final AtomicLong ungroupedItemSequenceNumber = new AtomicLong();
        private int bufferSize = 8;

        @Override
        public Set<Key> decodeSet(final Set<String> openGroups) {
            return SimpleMetrics.measure("Converting open groups", () -> {
                Set<Key> keys = Collections.emptySet();
                if (openGroups != null) {
                    keys = new HashSet<>();
                    for (final String encodedGroup : openGroups) {
                        try {
                            final ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encodedGroup));
                            try (final DataReader reader = new KryoDataReader(new ByteBufferInput(buffer))) {
                                keys.add(read(reader));
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.debug(e::getMessage, e);
                        }
                    }
                }
                return keys;
            });
        }

        @Override
        public String encode(final Key key, final ErrorConsumer errorConsumer) {
            return SimpleMetrics.measure("Encoding groups", () -> {
                try (final Output output = new Output(bufferSize, -1)) {
                    try (final DataWriter writer = new KryoDataWriter(output)) {
                        write(key, writer);
                    }
                    final byte[] bytes = output.toBytes();
                    bufferSize = Math.max(bufferSize, output.getBuffer().length);
                    return Base64.getEncoder().encodeToString(bytes);
                }
            });
        }

        @Override
        public long getUniqueId() {
            return ungroupedItemSequenceNumber.incrementAndGet();
        }
    }

    /**
     * Creates a flat group key. <GROUP VAL ARRAY>
     */
    private static class FlatGroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final DataWriter writer) {
            // Write single key part.
            final KeyPart keyPart = key.getKeyParts().getFirst();
            keyPart.write(writer);
        }

        @Override
        public Key read(final DataReader reader) {
            // Read single key part.
            final GroupKeyPart groupKeyPart = new GroupKeyPart(ValSerialiser.readArray(reader));
            final List<KeyPart> list = Collections.singletonList(groupKeyPart);
            return new Key(0, list);
        }
    }

    /**
     * Creates a flat unique key. <UNIQUE_ID>
     */
    private static class FlatUngroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final DataWriter writer) {
            // Write single key part.
            final KeyPart keyPart = key.getKeyParts().getFirst();
            keyPart.write(writer);
        }

        @Override
        public Key read(final DataReader reader) {
            // Read single key part.
            final UngroupedKeyPart keyPart = new UngroupedKeyPart(reader.readLong());
            final List<KeyPart> list = Collections.singletonList(keyPart);
            return new Key(0, list);
        }
    }

    /**
     * Creates a flat time based group key. <TIME_MS><GROUP VAL ARRAY>
     */
    private static class FlatTimeGroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final DataWriter writer) {
            // Write time millis since epoch.
            writer.writeLong(key.getTimeMs());

            // Write single key part.
            final KeyPart keyPart = key.getKeyParts().getFirst();
            keyPart.write(writer);
        }

        @Override
        public Key read(final DataReader reader) {
            // Read time millis since epoch.
            final long timeMs = reader.readLong();

            // Read single key part.
            final GroupKeyPart groupKeyPart = new GroupKeyPart(ValSerialiser.readArray(reader));
            final List<KeyPart> list = Collections.singletonList(groupKeyPart);
            return new Key(timeMs, list);
        }
    }

    /**
     * Creates flat time based unique key. <TIME_MS><UNIQUE_ID>
     */
    private static class FlatTimeUngroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final DataWriter writer) {
            // Write time millis since epoch.
            writer.writeLong(key.getTimeMs());

            // Write single key part.
            final KeyPart keyPart = key.getKeyParts().getFirst();
            keyPart.write(writer);
        }

        @Override
        public Key read(final DataReader reader) {
            // Read time millis since epoch.
            final long timeMs = reader.readLong();

            // Read single key part.
            final UngroupedKeyPart keyPart = new UngroupedKeyPart(reader.readLong());
            final List<KeyPart> list = Collections.singletonList(keyPart);
            return new Key(timeMs, list);
        }
    }

    /**
     * Creates a nested group key. <DEPTH><LIST OF GROUP VAL ARRAYS>
     */
    private static class NestedGroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final DataWriter writer) {
            // Write number of key parts (depth).
            writer.writeByteUnsigned(key.getKeyParts().size());

            // Write key parts.
            for (final KeyPart keyPart : key.getKeyParts()) {
                // Record if this is a grouped part.
                writer.writeBoolean(keyPart.isGrouped());
                keyPart.write(writer);
            }
        }

        @Override
        public Key read(final DataReader reader) {
            // Read number of key parts (depth).
            final int size = (reader.readByteUnsigned());

            // Read key parts.
            final List<KeyPart> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                // Determine if this is a grouped part.
                final boolean grouped = reader.readBoolean();
                if (grouped) {
                    list.add(new GroupKeyPart(ValSerialiser.readArray(reader)));
                } else {
                    list.add(new UngroupedKeyPart(reader.readLong()));
                }
            }
            return new Key(0, list);
        }
    }

    /**
     * Creates a nested time based group key. <DEPTH><TIME_MS><LIST OF GROUP VAL ARRAYS>
     */
    private static class NestedTimeGroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final DataWriter writer) {
            // Write number of key parts (depth).
            writer.writeByteUnsigned(key.getKeyParts().size());

            // Write time millis since epoch.
            writer.writeLong(key.getTimeMs());

            // Write key parts.
            for (final KeyPart keyPart : key.getKeyParts()) {
                // Record if this is a grouped part.
                writer.writeBoolean(keyPart.isGrouped());
                keyPart.write(writer);
            }
        }

        @Override
        public Key read(final DataReader reader) {
            // Read number of key parts (depth).
            final int size = reader.readByteUnsigned();

            // Read time millis since epoch.
            final long timeMs = reader.readLong();

            // Read key parts.
            final List<KeyPart> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                // Determine if this is a grouped part.
                final boolean grouped = reader.readBoolean();
                if (grouped) {
                    list.add(new GroupKeyPart(ValSerialiser.readArray(reader)));
                } else {
                    list.add(new UngroupedKeyPart(reader.readLong()));
                }
            }
            return new Key(timeMs, list);
        }
    }
}
